package com.deutschstart.app.ui.microlesson

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deutschstart.app.data.model.MicroLessonUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MicroLessonScreen(
    onNavigateBack: () -> Unit,
    viewModel: MicroLessonViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Micro-Lesson") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(targetState = uiState, label = "micro_lesson_state") { state ->
                when (state) {
                    is MicroLessonUiState.Loading -> LoadingContent()
                    is MicroLessonUiState.NothingToDo -> NothingToDoContent(onNavigateBack)
                    is MicroLessonUiState.Active -> ActiveContent(state, viewModel)
                    is MicroLessonUiState.Completed -> CompletedContent(state, onNavigateBack)
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Preparing your lesson...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun NothingToDoContent(onNavigateBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("🎉", fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "All caught up!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "No cards to review right now.\nDownload more content or wait for scheduled reviews.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onNavigateBack) {
                Text("Go Back")
            }
        }
    }
}

@Composable
private fun ActiveContent(
    state: MicroLessonUiState.Active,
    viewModel: MicroLessonViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Stage indicator
        StageProgressBar(state.stage)
        Spacer(Modifier.height(16.dp))

        when (state.stage) {
            MicroLessonUiState.Stage.VOCAB -> VocabStage(state, viewModel)
            MicroLessonUiState.Stage.GRAMMAR -> GrammarStage(state, viewModel)
            MicroLessonUiState.Stage.PLAYLIST -> PlaylistStage(state, viewModel)
        }
    }
}

@Composable
private fun StageProgressBar(currentStage: MicroLessonUiState.Stage) {
    val stages = MicroLessonUiState.Stage.entries
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        stages.forEach { stage ->
            val isActive = stage == currentStage
            val isDone = stage.stageId < currentStage.stageId
            val color = when {
                isDone -> MaterialTheme.colorScheme.primary
                isActive -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .height(6.dp)
                ) {}
                Spacer(Modifier.height(4.dp))
                Text(
                    when (stage) {
                        MicroLessonUiState.Stage.VOCAB -> "Vocab"
                        MicroLessonUiState.Stage.GRAMMAR -> "Grammar"
                        MicroLessonUiState.Stage.PLAYLIST -> "Playlist"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun VocabStage(
    state: MicroLessonUiState.Active,
    viewModel: MicroLessonViewModel
) {
    val card = state.currentVocabCard
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Progress
        Text(
            "Card ${state.vocabProgress + 1} of ${state.vocabTarget}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = { state.vocabProgress.toFloat() / state.vocabTarget.coerceAtLeast(1) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
        Spacer(Modifier.height(24.dp))

        if (card != null) {
            // Word card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Show article + word
                        val articlePrefix = card.gender?.let { g ->
                            when (g.lowercase()) {
                                "m" -> "der "
                                "f" -> "die "
                                "n" -> "das "
                                else -> ""
                            }
                        } ?: ""
                        Text(
                            "$articlePrefix${card.word}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            card.translationEn ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Rating buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RatingButton("Again", MaterialTheme.colorScheme.error) { viewModel.onCardRated(1) }
                RatingButton("Hard", MaterialTheme.colorScheme.tertiary) { viewModel.onCardRated(2) }
                RatingButton("Good", MaterialTheme.colorScheme.primary) { viewModel.onCardRated(3) }
                RatingButton("Easy", MaterialTheme.colorScheme.secondary) { viewModel.onCardRated(4) }
            }
        } else {
            Text("No cards available", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun RatingButton(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = Modifier.width(76.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun GrammarStage(
    state: MicroLessonUiState.Active,
    viewModel: MicroLessonViewModel
) {
    val topic = state.grammarTopic

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "📖 Grammar Quiz",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(24.dp))

        if (topic != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        topic.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        topic.description ?: "",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            
            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { viewModel.onGrammarStageComplete(skipped = false) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Complete Quiz")
            }
        } else {
            // No grammar topic — auto-skip
            Text(
                "No grammar topics available",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { viewModel.onGrammarStageComplete(skipped = true) }) {
            Icon(Icons.Default.SkipNext, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Skip")
        }
    }
}

@Composable
private fun PlaylistStage(
    state: MicroLessonUiState.Active,
    viewModel: MicroLessonViewModel
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            "🎧 Playlist Review",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Review these words passively. Tap Finish when done.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.playlistWords) { word ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val articlePrefix = word.gender?.let { g ->
                                when (g.lowercase()) {
                                    "m" -> "der "
                                    "f" -> "die "
                                    "n" -> "das "
                                    else -> ""
                                }
                            } ?: ""
                            Text(
                                "$articlePrefix${word.word}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                word.translationEn ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.onPlaylistComplete() },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Finish", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun CompletedContent(
    state: MicroLessonUiState.Completed,
    onNavigateBack: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("🎉", fontSize = 72.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Lesson Complete!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "+${state.xpBonus} XP Bonus",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onNavigateBack) {
                Text("Back to Home")
            }
        }
    }
}
