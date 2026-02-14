package com.deutschstart.app.playlist

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, "Back")
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Smart Playlist",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { /* Settings dialog */ }) {
                Icon(Icons.Default.Settings, "Settings")
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        when {
            state.isLoading -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Loading playlist...")
            }
            state.error != null -> {
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.loadPlaylist(config = state.config) }) {
                    Text("Retry")
                }
            }
            else -> {
                PlaylistContent(
                    state = state,
                    onPlay = { viewModel.play() },
                    onPause = { viewModel.pause() },
                    onSkipForward = { viewModel.skipForward() },
                    onSkipBackward = { viewModel.skipBackward() },
                    onGermanSpeedChange = { viewModel.setGermanSpeed(it) },
                    onEnglishSpeedChange = { viewModel.setEnglishSpeed(it) },
                    onToggleSentences = { viewModel.toggleSentences(!it) }, // Toggle logic is inverted in UI ("Vocab Only" = !includeSentences)
                    onPlayKaikki = { viewModel.playKaikkiAudio() },
                    onLoadNextBatch = { viewModel.loadNextBatch() }
                )
            }
        }
    }
}

@Composable
fun PlaylistContent(
    state: PlaylistUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onGermanSpeedChange: (Float) -> Unit,
    onEnglishSpeedChange: (Float) -> Unit,
    onToggleSentences: (Boolean) -> Unit,
    onPlayKaikki: () -> Unit,
    onLoadNextBatch: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Progress indicator
        Text(
            "Card ${state.currentCardIndex + 1} / ${state.totalCards}",
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Card Content
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.currentCard != null) {
                    // German Word
                    Text(
                        text = state.currentCard.word,
                        style = MaterialTheme.typography.displayMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    // IPA & Kaikki Audio
                    if (state.kaikkiInfo?.ipa != null || !state.currentCard.kaikkiAudioPath.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (state.kaikkiInfo?.ipa != null) {
                                Text(
                                    text = "/${state.kaikkiInfo.ipa}/",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            if (!state.currentCard.kaikkiAudioPath.isNullOrBlank()) {
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = onPlayKaikki) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = "Play Native Audio",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Senses / Definitions
                    if (state.kaikkiInfo?.senses?.isNotEmpty() == true) {
                        Spacer(Modifier.height(8.dp))
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            state.kaikkiInfo.senses.forEach { sense ->
                                Text(
                                    text = sense,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // English Translation
                    Text(
                        text = state.currentCard.translationEn,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                    
                    if (!state.currentSentence.isNullOrBlank()) {
                        Spacer(Modifier.height(24.dp))
                        Divider()
                        Spacer(Modifier.height(16.dp))
                        
                        // Example Sentence (German)
                        Text(
                            text = state.currentSentence,
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            textAlign = TextAlign.Center
                        )
                        
                        // Example Sentence (English)
                        if (!state.currentSentenceEn.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = state.currentSentenceEn,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (state.config.includeSentences) {
                         Text(
                            text = "(No example sentence)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                } else {
                    Text("Loading card data...")
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(8.dp)
        )
        
        Spacer(Modifier.height(24.dp))
        
        // Status text / Completion UI
        if (state.playbackState is PlaybackState.Completed && !state.loopEnabled) {
            // Playlist finished and loop is off — show "Load Next Batch"
            Text(
                "Playlist Complete!",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onLoadNextBatch) {
                Icon(Icons.Default.SkipNext, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Load Next 20 Cards")
            }
        } else {
            val statusText = when (state.playbackState) {
                is PlaybackState.Idle -> "Ready to play"
                is PlaybackState.Loading -> "Loading..."
                is PlaybackState.Playing -> "Playing"
                is PlaybackState.Paused -> "Paused"
                is PlaybackState.Completed -> "Looping..."
                is PlaybackState.Error -> "Error: ${(state.playbackState as PlaybackState.Error).message}"
            }
            
            Text(
                statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(Modifier.height(32.dp))
        
        // Playback controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onSkipBackward,
                enabled = state.currentCardIndex > 0
            ) {
                Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(32.dp))
            }
            
            FloatingActionButton(
                onClick = {
                    when (state.playbackState) {
                        is PlaybackState.Playing -> onPause()
                        else -> onPlay()
                    }
                },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    if (state.playbackState is PlaybackState.Playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Play/Pause",
                    Modifier.size(32.dp)
                )
            }
            
            IconButton(
                onClick = onSkipForward,
                enabled = state.currentCardIndex < state.totalCards - 1
            ) {
                Icon(Icons.Default.SkipNext, "Next", Modifier.size(32.dp))
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Options Area
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // "Vocab Only" Toggle (Inverse of Include Sentences)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vocab Only Loop (No Sentences)")
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = !state.config.includeSentences,
                    onCheckedChange = { onToggleSentences(it) }
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Speed Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SpeedControl("DE Speed", state.germanSpeed, onGermanSpeedChange)
                SpeedControl("EN Speed", state.englishSpeed, onEnglishSpeedChange)
            }
        }
    }
}

@Composable
fun SpeedControl(label: String, currentSpeed: Float, onSpeedChange: (Float) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    
    Box {
        OutlinedButton(onClick = { menuExpanded = true }) {
            Text("$label: ${currentSpeed}x")
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                DropdownMenuItem(
                    text = { Text("${speed}x") },
                    onClick = {
                        onSpeedChange(speed)
                        menuExpanded = false
                    }
                )
            }
        }
    }
}
