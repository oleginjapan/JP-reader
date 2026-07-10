package com.example.jpreader.ui

import android.content.ContentResolver
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jpreader.ai.AnthropicProvider
import com.example.jpreader.ai.AiProvider
import com.example.jpreader.ai.ComprehensionQuestion
import com.example.jpreader.ai.OpenAiProvider
import com.example.jpreader.ai.StoryRequest
import com.example.jpreader.ai.StoryResult
import com.example.jpreader.ai.XaiProvider
import com.example.jpreader.anki.AnkiDroidRepository
import com.example.jpreader.settings.ApiKeyStore
import com.example.jpreader.settings.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val jlptLevels = listOf("N5", "N4", "N3", "N2", "N1")

/** One generated story, already tokenized into furigana segments. */
data class ReadingStory(
    val segments: List<StorySegment>,
    val wordsUsed: List<String>,
    val questions: List<ComprehensionQuestion>
)

sealed class ReadingUiState {
    object Idle : ReadingUiState()
    object Loading : ReadingUiState()
    data class Loaded(val stories: List<ReadingStory>, val selectedIndex: Int) : ReadingUiState()
    data class Error(val message: String) : ReadingUiState()
}

class ReadingViewModel(
    private val resolver: ContentResolver,
    private val keyStore: ApiKeyStore
) : ViewModel() {

    private val ankiRepo = AnkiDroidRepository(resolver)
    private val providerImpls: Map<Provider, AiProvider> = mapOf(
        Provider.OPENAI to OpenAiProvider(),
        Provider.ANTHROPIC to AnthropicProvider(),
        Provider.XAI to XaiProvider()
    )

    val selectedLevel = mutableStateOf(jlptLevels[1]) // default N4
    val uiState = mutableStateOf<ReadingUiState>(ReadingUiState.Idle)

    fun setLevel(level: String) {
        selectedLevel.value = level
    }

    fun selectStory(index: Int) {
        val current = uiState.value
        if (current is ReadingUiState.Loaded) {
            uiState.value = current.copy(selectedIndex = index)
        }
    }

    /** One request returns a batch of stories (default 10), not one story per API call. */
    fun generateStories() {
        val provider = keyStore.getDefaultProvider()
        val apiKey = keyStore.getKey(provider)
        if (apiKey.isNullOrBlank()) {
            uiState.value = ReadingUiState.Error("Add an API key in Settings first")
            return
        }
        if (!ankiRepo.isAnkiDroidAvailable()) {
            uiState.value = ReadingUiState.Error("AnkiDroid isn't installed or reachable")
            return
        }

        uiState.value = ReadingUiState.Loading
        viewModelScope.launch {
            try {
                val level = selectedLevel.value
                val prevLevel = jlptLevels.getOrNull(jlptLevels.indexOf(level) + 1) ?: level

                val rawVocab = ankiRepo.getVocabForLevel(level) + ankiRepo.getVocabForLevel(prevLevel)
                val enriched = ankiRepo.joinWithReviewStats(rawVocab)
                // Bigger pool than a single-story request, since a few stories together
                // need some variety to draw from — sized down while iterating at 4 stories.
                val storyVocab = ankiRepo.selectStoryVocab(enriched, count = 20)
                val knownReadings = storyVocab.associate { it.expression to it.reading }

                val grammar = ankiRepo.getGrammarForLevel(level) + ankiRepo.getGrammarForLevel(prevLevel)
                val grammarSample = grammar.shuffled().take(8)

                val request = StoryRequest(
                    level = level,
                    vocab = storyVocab.map { it.expression },
                    grammar = grammarSample.map { it.pattern },
                    storyCount = 4
                )

                val impl = providerImpls.getValue(provider)
                val result = impl.generateStories(apiKey, request)

                result.onSuccess { rawStories: List<StoryResult> ->
                    // Kuromoji tokenization is CPU work — off the main thread.
                    val readingStories = withContext(Dispatchers.Default) {
                        rawStories.map { story ->
                            ReadingStory(
                                segments = KuromojiFurigana.toSegments(story.japaneseText, knownReadings),
                                wordsUsed = story.wordsActuallyUsed,
                                questions = story.questions
                            )
                        }
                    }
                    uiState.value = ReadingUiState.Loaded(readingStories, selectedIndex = 0)

                    // Tag whichever supplied words got used anywhere in the batch,
                    // so future batches favor variety.
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    val allUsed = rawStories.flatMap { it.wordsActuallyUsed }.toSet()
                    storyVocab
                        .filter { note -> allUsed.any { it.contains(note.expression) } }
                        .forEach { note -> ankiRepo.tagNoteUsedInReading(note.noteId, "used_in_reading:$today") }

                }.onFailure { e ->
                    uiState.value = ReadingUiState.Error(e.message ?: "Story generation failed")
                }
            } catch (e: Exception) {
                uiState.value = ReadingUiState.Error(e.message ?: "Unexpected error")
            }
        }
    }
}
