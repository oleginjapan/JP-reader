package com.example.jpreader.anki

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.BaseColumns

/**
 * Talks to AnkiDroid via its public FlashCardsContract ContentProvider.
 * AnkiDroid must be installed and have granted us
 * "com.ichi2.anki.permission.READ_WRITE_DATABASE".
 *
 * We never touch AnkiDroid's SQLite file directly — only through this contract,
 * so AnkiDroid's own scheduler stays the single source of truth for retention.
 */
object FlashCardsContract {
    const val AUTHORITY = "com.ichi2.anki.flashcards"
    val NOTE_URI: Uri = Uri.parse("content://$AUTHORITY/notes")
    val CARD_URI: Uri = Uri.parse("content://$AUTHORITY/cards")

    object Note {
        const val _ID = BaseColumns._ID
        const val FLDS = "flds"       // fields joined by \u001f
        const val TAGS = "tags"       // space-separated
        const val MID = "mid"         // note type id
    }

    object Card {
        const val NOTE_ID = "note_id"
        const val DECK_ID = "deck_id"
        const val QUESTION = "question"
        const val ANSWER = "answer"
        const val DUE = "due"
        // Reviewing a card is done by inserting into the "reviewInfo" URI documented
        // in the AnkiDroid API README — see reviewCard() below for the real shape.
    }
}

/** How well AnkiDroid's scheduler thinks the user knows this word, based on card state. */
enum class LearningState { NEW, LEARNING, KNOWN }

/** A single vocabulary or grammar note pulled from AnkiDroid, level-tagged. */
data class JlptNote(
    val noteId: Long,
    val expression: String,
    val reading: String,
    val meaning: String,
    val level: String,          // "N5".."N1"
    val timesReviewed: Int,     // used to weight "struggling vs mastered" selection
    val lapses: Int,
    val intervalDays: Int = 0,  // AnkiDroid's "ivl" field, in days
    val queue: Int = 0          // AnkiDroid's "queue" field: -1 suspended, 0 new, 1/3 learning, 2 review
) {
    val learningState: LearningState get() = when {
        queue == 0 -> LearningState.NEW
        intervalDays >= 21 -> LearningState.KNOWN   // matches AnkiDroid's own "mature" threshold
        else -> LearningState.LEARNING
    }
}

data class GrammarPoint(
    val pattern: String,
    val meaning: String,
    val level: String
)

class AnkiDroidRepository(private val resolver: ContentResolver) {

    /** True if AnkiDroid is installed and reachable. Call before anything else. */
    fun isAnkiDroidAvailable(): Boolean =
        resolver.acquireContentProviderClient(FlashCardsContract.NOTE_URI)?.also { it.close() } != null

    /**
     * Pull vocabulary notes for a given JLPT level, e.g. "N4".
     * Filters on the note's tags containing "jlpt_N4", matching how the provided
     * JLPT_N5-N1.apkg deck is actually tagged (deck names in that file are unreliable).
     */
    fun getVocabForLevel(level: String, limit: Int = 200): List<JlptNote> {
        val selection = "${FlashCardsContract.Note.TAGS} LIKE ?"
        val args = arrayOf("%jlpt_$level%")
        val notes = mutableListOf<JlptNote>()

        resolver.query(FlashCardsContract.NOTE_URI, null, selection, args, null)?.use { cursor ->
            val fldsIdx = cursor.getColumnIndex(FlashCardsContract.Note.FLDS)
            val idIdx = cursor.getColumnIndex(FlashCardsContract.Note._ID)
            while (cursor.moveToNext() && notes.size < limit) {
                val flds = cursor.getString(fldsIdx).split('\u001f')
                // Field order in this deck's note type:
                // 0 Expression, 1 English definition, 2 Reading, 3 Grammar, 4 Additional definitions
                if (flds.size < 3) continue
                notes.add(
                    JlptNote(
                        noteId = cursor.getLong(idIdx),
                        expression = flds[0],
                        reading = flds[2],
                        meaning = flds.getOrElse(1) { "" },
                        level = level,
                        timesReviewed = 0, // filled in by joinWithReviewStats()
                        lapses = 0
                    )
                )
            }
        }
        return notes
    }

    /**
     * Enriches notes with review counts, lapses, interval, and queue state by joining
     * against the `cards` table — this is what lets us classify each word as
     * new / learning / known (see JlptNote.learningState).
     */
    fun joinWithReviewStats(notes: List<JlptNote>): List<JlptNote> {
        if (notes.isEmpty()) return notes
        val idToNote = notes.associateBy { it.noteId }
        val projection = arrayOf(
            FlashCardsContract.Card.NOTE_ID, "reps", "lapses", "ivl", "queue"
        )
        val selection = "${FlashCardsContract.Card.NOTE_ID} IN (${notes.joinToString(",") { it.noteId.toString() }})"
        val result = idToNote.toMutableMap()

        resolver.query(FlashCardsContract.CARD_URI, projection, selection, null, null)?.use { c ->
            val noteIdx = c.getColumnIndex(FlashCardsContract.Card.NOTE_ID)
            val repsIdx = c.getColumnIndex("reps")
            val lapsesIdx = c.getColumnIndex("lapses")
            val ivlIdx = c.getColumnIndex("ivl")
            val queueIdx = c.getColumnIndex("queue")
            while (c.moveToNext()) {
                val nid = c.getLong(noteIdx)
                val existing = result[nid] ?: continue
                result[nid] = existing.copy(
                    timesReviewed = c.getInt(repsIdx),
                    lapses = c.getInt(lapsesIdx),
                    intervalDays = c.getInt(ivlIdx),
                    queue = c.getInt(queueIdx)
                )
            }
        }
        return result.values.toList()
    }

    /**
     * Picks the word pool for a story: mostly words currently being learned (reinforce them),
     * a smaller share of known words (so the story reads naturally), and excludes
     * never-reviewed ("new") words by default since the user hasn't actually learned them yet.
     */
    fun selectStoryVocab(notes: List<JlptNote>, count: Int): List<JlptNote> {
        val learning = notes.filter { it.learningState == LearningState.LEARNING }.shuffled()
        val known = notes.filter { it.learningState == LearningState.KNOWN }.shuffled()
        val learningShare = (count * 0.7).toInt()
        return (learning.take(learningShare) + known.take(count - learningShare)).shuffled()
    }

    /**
     * Pulls grammar points at a given level from the JLPT_Grammar deck.
     * That deck's note type doesn't tag by jlpt_N# the way the vocab deck does — its
     * "Level" field (e.g. "N4") is plain text inside one of the fields, not a tag — so
     * we match on FLDS containing the level string. Coarser than the vocab query, but
     * workable since the deck is small and level strings are distinctive (e.g. "N4").
     */
    fun getGrammarForLevel(level: String, limit: Int = 60): List<GrammarPoint> {
        val selection = "${FlashCardsContract.Note.FLDS} LIKE ?"
        val args = arrayOf("%$level%")
        val points = mutableListOf<GrammarPoint>()

        resolver.query(FlashCardsContract.NOTE_URI, null, selection, args, null)?.use { cursor ->
            val fldsIdx = cursor.getColumnIndex(FlashCardsContract.Note.FLDS)
            while (cursor.moveToNext() && points.size < limit) {
                val flds = cursor.getString(fldsIdx).split('\u001f')
                // This deck's fields, by observed position: [English, Japanese, Level, Meaning, Romanized, Vocab/title]
                if (flds.size < 4) continue
                if (flds.getOrNull(2)?.trim() != level) continue // exact level match, not just substring
                points.add(
                    GrammarPoint(
                        pattern = flds.getOrElse(5) { flds[1] },
                        meaning = flds.getOrElse(3) { "" },
                        level = level
                    )
                )
            }
        }
        return points.distinctBy { it.pattern }
    }

    /**
     * Tags a note after it's used in a generated story, e.g. "used_in_reading:2026-07-08",
     * so future story generation can favor variety and AnkiDroid's own browser can filter by it.
     */
    fun tagNoteUsedInReading(noteId: Long, dateTag: String) {
        val uri = Uri.withAppendedPath(FlashCardsContract.NOTE_URI, noteId.toString())
        val current = resolver.query(uri, arrayOf(FlashCardsContract.Note.TAGS), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else "" } ?: ""
        val updated = (current.split(" ").filter { it.isNotBlank() } + dateTag).joinToString(" ")
        val values = ContentValues().apply { put(FlashCardsContract.Note.TAGS, updated) }
        resolver.update(uri, values, null, null)
    }
}
