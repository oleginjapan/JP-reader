package com.example.jpreader.ui

import com.atilika.kuromoji.ipadic.Tokenizer

/**
 * Converts raw story text into furigana segments using Kuromoji, a real Japanese
 * morphological tokenizer — this replaces the earlier approach of only matching
 * against the vocab list we happened to supply. Kuromoji gives a reading for
 * essentially any word, so every kanji in the story gets a correct tap target,
 * not just the ones the AI was told to use.
 *
 * `knownReadings` (expression -> reading, sourced from AnkiDroid) takes priority
 * over Kuromoji's own reading when both exist, so a word's furigana in a story
 * matches exactly how the user studies it as a flashcard.
 */
object KuromojiFurigana {

    // Loading the dictionary is the expensive part (a few hundred ms), so the
    // Tokenizer is built once and reused. Call toSegments() off the main thread —
    // ReadingViewModel does this inside its coroutine already.
    private val tokenizer: Tokenizer by lazy { Tokenizer() }

    fun toSegments(text: String, knownReadings: Map<String, String> = emptyMap()): List<StorySegment> {
        val segments = mutableListOf<StorySegment>()
        val plainBuffer = StringBuilder()

        fun flushPlain() {
            if (plainBuffer.isNotEmpty()) {
                segments.add(StorySegment.Plain(plainBuffer.toString()))
                plainBuffer.clear()
            }
        }

        tokenizer.tokenize(text).forEach { token ->
            val surface = token.surface
            if (containsKanji(surface)) {
                val kuromojiReading = token.reading
                    ?.takeIf { it != "*" && it.isNotBlank() }
                    ?.let(::katakanaToHiragana)
                val reading = knownReadings[surface] ?: kuromojiReading

                if (reading != null) {
                    flushPlain()
                    segments.add(StorySegment.KanjiWord(surface, reading))
                } else {
                    // Kuromoji couldn't resolve a reading (rare, e.g. unusual names) —
                    // fall back to plain text rather than showing a wrong reading.
                    plainBuffer.append(surface)
                }
            } else {
                plainBuffer.append(surface)
            }
        }
        flushPlain()
        return segments
    }

    private fun containsKanji(text: String): Boolean =
        text.any { it.code in 0x4E00..0x9FFF }

    private fun katakanaToHiragana(s: String): String = buildString {
        for (c in s) {
            append(if (c.code in 0x30A1..0x30F6) (c.code - 0x60).toChar() else c)
        }
    }
}
