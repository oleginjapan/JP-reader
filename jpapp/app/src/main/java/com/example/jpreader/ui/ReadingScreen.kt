package com.example.jpreader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jpreader.ai.ComprehensionQuestion

@Composable
fun ReadingScreen(viewModel: ReadingViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState
    val level by viewModel.selectedLevel

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LevelPicker(selected = level, onSelect = { viewModel.setLevel(it) })

        Button(
            onClick = { viewModel.generateStories() },
            enabled = state !is ReadingUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state is ReadingUiState.Loading) "Writing 4 stories…" else "Generate 4 stories")
        }

        when (val s = state) {
            is ReadingUiState.Idle -> Text(
                "Pick a level and tap Generate. One request writes a batch of 4 standalone " +
                    "stories from words you're currently learning in AnkiDroid. Read one, answer " +
                    "its questions, then move to the next.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            is ReadingUiState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            is ReadingUiState.Error -> Text(
                "Couldn't generate stories: ${s.message}",
                color = MaterialTheme.colorScheme.error
            )
            is ReadingUiState.Loaded -> {
                Text(
                    "Story ${s.selectedIndex + 1} of ${s.stories.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val story = s.stories.getOrNull(s.selectedIndex)
                if (story != null) {
                    ElevatedCard {
                        FuriganaStory(segments = story.segments, modifier = Modifier.padding(16.dp))
                    }

                    if (story.questions.isNotEmpty()) {
                        ComprehensionSection(story.questions)
                    }

                    if (story.wordsUsed.isNotEmpty()) {
                        Text(
                            "Words practiced: ${story.wordsUsed.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.selectStory(s.selectedIndex - 1) },
                        enabled = s.selectedIndex > 0,
                        modifier = Modifier.weight(1f)
                    ) { Text("Previous") }
                    Button(
                        onClick = { viewModel.selectStory(s.selectedIndex + 1) },
                        enabled = s.selectedIndex < s.stories.size - 1,
                        modifier = Modifier.weight(1f)
                    ) { Text("Next story") }
                }
            }
        }
    }
}

@Composable
private fun ComprehensionSection(questions: List<ComprehensionQuestion>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Comprehension check", style = MaterialTheme.typography.titleSmall)
        questions.forEach { q -> ComprehensionQuestionCard(q) }
    }
}

@Composable
private fun ComprehensionQuestionCard(q: ComprehensionQuestion) {
    var revealed by remember { mutableStateOf(false) }
    OutlinedCard(modifier = Modifier.fillMaxWidth().clickable { revealed = !revealed }) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(q.question, style = MaterialTheme.typography.bodyMedium)
            if (revealed) {
                Text(
                    q.answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    "Tap to reveal answer",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun LevelPicker(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        jlptLevels.forEach { level ->
            FilterChip(
                selected = level == selected,
                onClick = { onSelect(level) },
                label = { Text(level) }
            )
        }
    }
}
