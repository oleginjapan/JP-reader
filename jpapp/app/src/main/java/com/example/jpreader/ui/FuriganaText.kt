package com.example.jpreader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/** One segment of story text: either plain text, or a kanji word with its reading. */
sealed class StorySegment {
    data class Plain(val text: String) : StorySegment()
    data class KanjiWord(val kanji: String, val reading: String) : StorySegment()
}

/**
 * Renders a story with tap-to-reveal furigana, matching the reference layout
 * (small reading centered above larger kanji, revealed per-word on tap).
 *
 * `segments` should come from parsing the AI's story output against the
 * known JLPT vocab list, so we know exactly which spans are kanji words and
 * what their correct reading is (rather than guessing from the raw text).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FuriganaStory(segments: List<StorySegment>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is StorySegment.Plain -> Text(text = segment.text, fontSize = 18.sp)
                is StorySegment.KanjiWord -> FuriganaWord(segment.kanji, segment.reading)
            }
        }
    }
}

@Composable
private fun FuriganaWord(kanji: String, reading: String) {
    var revealed by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { revealed = !revealed }
    ) {
        Text(
            text = if (revealed) reading else " ",
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
        )
        Text(text = kanji, fontSize = 18.sp)
    }
}
