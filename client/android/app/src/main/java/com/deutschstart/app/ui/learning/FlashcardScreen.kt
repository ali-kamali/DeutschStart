package com.deutschstart.app.ui.learning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun FlashcardScreen(
    viewModel: FlashcardViewModel = hiltViewModel(),
    onSessionComplete: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.isSessionComplete) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Session Complete!", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "${state.cardsCompleted} cards reviewed",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { viewModel.loadSession() }) {
                Text("Review More")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSessionComplete) {
                Text("Finish")
            }
        }
        return
    }

    val card = state.currentCard ?: return

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Real progress bar
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${state.cardsCompleted + 1} / ${state.totalSessionSize}",
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(24.dp))

        // Card Area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable { if (!state.isFlipped) viewModel.flipCard() },
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Front — always visible
                Text(text = card.word, style = MaterialTheme.typography.displayMedium)

                // Show article + POS cleanly (avoid leading space when article is null)
                val subtitle = listOfNotNull(card.article, card.partOfSpeech)
                    .joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(text = subtitle, style = MaterialTheme.typography.labelLarge)
                }

                Spacer(Modifier.height(16.dp))
                IconButton(onClick = { viewModel.playAudio() }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Audio")
                }

                // Back — shown after flip
                if (state.isFlipped) {
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    Text(text = card.translationEn, style = MaterialTheme.typography.headlineSmall)

                    Spacer(Modifier.height(24.dp))

                    state.sentences.forEach { sentence ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    sentence["german"] ?: "",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    sentence["english"] ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                            val audioPath = sentence["audio_path"]
                            if (!audioPath.isNullOrEmpty()) {
                                IconButton(onClick = { viewModel.playSentenceAudio(audioPath) }) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Play Sentence"
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    Spacer(Modifier.height(32.dp))
                    Text("Tap to Flip", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Rating controls
        if (state.isFlipped) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { viewModel.rateCard(1) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Again") }
                Button(
                    onClick = { viewModel.rateCard(2) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) { Text("Hard") }
                Button(
                    onClick = { viewModel.rateCard(3) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text("Good") }
                Button(
                    onClick = { viewModel.rateCard(4) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) { Text("Easy") }
            }
        } else {
            Button(
                onClick = { viewModel.flipCard() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show Answer")
            }
        }
    }
}
