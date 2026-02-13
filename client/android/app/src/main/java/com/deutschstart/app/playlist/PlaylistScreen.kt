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
    
    LaunchedEffect(Unit) {
        viewModel.loadPlaylist(limit = 20)
    }
    
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
                Button(onClick = { viewModel.loadPlaylist() }) {
                    Text("Retry")
                }
            }
            else -> {
                PlaylistContent(
                    state = state,
                    onPlay = { viewModel.play() },
                    onPause = { viewModel.pause() },
                    onSkipForward = { viewModel.skipForward() },
                    onSkipBackward = { viewModel.skipBackward() }
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
    onSkipBackward: () -> Unit
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
        
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(8.dp)
        )
        
        Spacer(Modifier.height(48.dp))
        
        // Status text
        val statusText = when (state.playbackState) {
            is PlaybackState.Idle -> "Ready to play"
            is PlaybackState.Loading -> "Loading..."
            is PlaybackState.Playing -> "Playing"
            is PlaybackState.Paused -> "Paused"
            is PlaybackState.Completed -> "Playlist Complete!"
            is PlaybackState.Error -> "Error: ${(state.playbackState as PlaybackState.Error).message}"
        }
        
        Text(
            statusText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.height(48.dp))
        
        // Playback controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Skip backward
            IconButton(
                onClick = onSkipBackward,
                enabled = state.currentCardIndex > 0
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Previous card",
                    modifier = Modifier.size(40.dp)
                )
            }
            
            // Play/Pause
            FloatingActionButton(
                onClick = {
                    when (state.playbackState) {
                        is PlaybackState.Playing -> onPause()
                        else -> onPlay()
                    }
                },
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (state.playbackState is PlaybackState.Playing) {
                        Icons.Default.Pause
                    } else {
                        Icons.Default.PlayArrow
                    },
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(40.dp)
                )
            }
            
            // Skip forward
            IconButton(
                onClick = onSkipForward,
                enabled = state.currentCardIndex < state.totalCards - 1
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next card",
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        // Mode indicator
        Card(
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Mode: ${state.config.mode.name.replace("_", " ")}",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    "Thinking gap: ${state.config.thinkingGapMs / 1000}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
