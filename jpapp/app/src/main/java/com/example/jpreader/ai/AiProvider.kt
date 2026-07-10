package com.example.jpreader.ai

import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * NOTE ON DESIGN: story generation uses each provider's official developer API
 * with a user-supplied API key, entered once in Settings. This intentionally
 * does NOT sign into chatgpt.com / claude.ai / grok.com with a Google account —
 * those sites don't offer programmatic access that way, and scripting a login
 * would violate their terms. API keys are the supported, stable path.
 *
 * Each generation call asks for a BATCH of stories (default 10) in one request,
 * rather than one request per story — fewer API calls, and the model can vary
 * vocabulary usage across the batch so the 10 stories don't repeat themselves.
 */

data class StoryRequest(
    val level: String,                 // target JLPT level, e.g. "N4"
    val vocab: List<String>,           // expressions to try to use
    val grammar: List<String>,         // grammar points to try to use
    val approxSentences: Int = 10,
    val storyCount: Int = 4            // kept small while iterating; 10 tends to risk truncated/malformed JSON
)

data class StoryResult(
    val japaneseText: String,
    val wordsActuallyUsed: List<String>,
    val questions: List<ComprehensionQuestion> = emptyList()
)

data class ComprehensionQuestion(val question: String, val answer: String)

interface AiProvider {
    val displayName: String
    suspend fun testConnection(apiKey: String): Result<Unit>
    suspend fun generateStories(apiKey: String, request: StoryRequest): Result<List<StoryResult>>
}

private val http = OkHttpClient()
private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class QuestionJson(val question: String, val answer: String)

@Serializable
private data class StoryJson(
    val text: String,
    val used: List<String> = emptyList(),
    val questions: List<QuestionJson> = emptyList()
)

private fun buildBatchPrompt(r: StoryRequest): String = """
    Write ${r.storyCount} different short Japanese reading-practice stories for a JLPT ${r.level} learner.
    Each story should be approximately ${r.approxSentences} sentences and independent of the others
    (different scenario/characters), so they don't feel repetitive as a set.
    Across the ${r.storyCount} stories, vary which of these words get used so the set as a whole
    covers more of the list rather than every story reusing the same few words:
    ${r.vocab.joinToString("、")}
    Where natural, incorporate these grammar points across the set: ${r.grammar.joinToString("、")}
    Do not exceed JLPT ${r.level} difficulty.

    For each story, also write 2 simple reading-comprehension questions IN JAPANESE about
    that story's content (not about grammar/vocab), at the same JLPT ${r.level} difficulty,
    each with its correct answer written IN JAPANESE as well.

    Respond with ONLY a raw JSON array, no markdown code fences, no commentary before or after.
    Each element must be an object with exactly three fields:
    "text" (the Japanese story), "used" (a JSON array of which supplied words this story actually used),
    and "questions" (a JSON array of exactly 2 objects, each with "question" and "answer", both in Japanese).
    Example shape: [{"text": "...", "used": ["..."], "questions": [{"question": "...", "answer": "..."}, {"question": "...", "answer": "..."}]}, ...]
""".trimIndent()

/** Strips markdown fences some models add despite instructions, then parses the JSON array. */
private fun parseBatchResponse(raw: String): List<StoryResult> {
    val cleaned = raw.trim()
        .removePrefix("```json").removePrefix("```")
        .removeSuffix("```")
        .trim()
    val parsed = json.decodeFromString<List<StoryJson>>(cleaned)
    return parsed.map { s ->
        StoryResult(
            japaneseText = s.text,
            wordsActuallyUsed = s.used,
            questions = s.questions.map { ComprehensionQuestion(it.question, it.answer) }
        )
    }
}

class OpenAiProvider : AiProvider {
    override val displayName = "ChatGPT (OpenAI)"

    @Serializable data class Msg(val role: String, val content: String)
    @Serializable data class Req(val model: String, val messages: List<Msg>)
    @Serializable data class Choice(val message: Msg)
    @Serializable data class Resp(val choices: List<Choice>)

    private fun call(apiKey: String, body: Req): String {
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(json.encodeToString(body).toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("OpenAI error ${resp.code}: ${resp.body?.string()}")
            val parsed = json.decodeFromString<Resp>(resp.body!!.string())
            return parsed.choices.first().message.content
        }
    }

    override suspend fun testConnection(apiKey: String): Result<Unit> = runCatching {
        call(apiKey, Req("gpt-4o-mini", listOf(Msg("user", "reply with OK"))))
        Unit
    }

    override suspend fun generateStories(apiKey: String, request: StoryRequest): Result<List<StoryResult>> = runCatching {
        val text = call(apiKey, Req("gpt-4o-mini", listOf(Msg("user", buildBatchPrompt(request)))))
        parseBatchResponse(text)
    }
}

class AnthropicProvider : AiProvider {
    override val displayName = "Claude (Anthropic)"

    @Serializable data class Msg(val role: String, val content: String)
    @Serializable data class Req(val model: String, val max_tokens: Int, val messages: List<Msg>)
    @Serializable data class Block(val text: String)
    @Serializable data class Resp(val content: List<Block>)

    private fun call(apiKey: String, body: Req): String {
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(json.encodeToString(body).toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Anthropic error ${resp.code}: ${resp.body?.string()}")
            val parsed = json.decodeFromString<Resp>(resp.body!!.string())
            return parsed.content.joinToString("") { it.text }
        }
    }

    override suspend fun testConnection(apiKey: String): Result<Unit> = runCatching {
        call(apiKey, Req("claude-sonnet-4-6", 20, listOf(Msg("user", "reply with OK"))))
        Unit
    }

    override suspend fun generateStories(apiKey: String, request: StoryRequest): Result<List<StoryResult>> = runCatching {
        // 10 stories of ~10 sentences needs more headroom than a single story did.
        val text = call(apiKey, Req("claude-sonnet-4-6", 8192, listOf(Msg("user", buildBatchPrompt(request)))))
        parseBatchResponse(text)
    }
}

class XaiProvider : AiProvider {
    override val displayName = "Grok (xAI)"

    @Serializable data class Msg(val role: String, val content: String)
    @Serializable data class Req(val model: String, val messages: List<Msg>)
    @Serializable data class Choice(val message: Msg)
    @Serializable data class Resp(val choices: List<Choice>)

    private fun call(apiKey: String, body: Req): String {
        val req = Request.Builder()
            .url("https://api.x.ai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(json.encodeToString(body).toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("xAI error ${resp.code}: ${resp.body?.string()}")
            val parsed = json.decodeFromString<Resp>(resp.body!!.string())
            return parsed.choices.first().message.content
        }
    }

    override suspend fun testConnection(apiKey: String): Result<Unit> = runCatching {
        call(apiKey, Req("grok-3-latest", listOf(Msg("user", "reply with OK"))))
        Unit
    }

    override suspend fun generateStories(apiKey: String, request: StoryRequest): Result<List<StoryResult>> = runCatching {
        val text = call(apiKey, Req("grok-3-latest", listOf(Msg("user", buildBatchPrompt(request)))))
        parseBatchResponse(text)
    }
}
