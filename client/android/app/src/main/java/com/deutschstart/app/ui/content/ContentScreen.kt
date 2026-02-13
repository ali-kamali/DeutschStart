package com.deutschstart.app.ui.content

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deutschstart.app.data.repository.ContentStatus

@Composable
fun ContentScreen(
    viewModel: ContentViewModel = hiltViewModel(),
    onStartLearning: () -> Unit = {}
) {
    val status by viewModel.status.collectAsState()
    
    // Auto-check on entry
    LaunchedEffect(Unit) {
        viewModel.checkForUpdates()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Content Management", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))

        when (val s = status) {
            is ContentStatus.Idle, is ContentStatus.Checking -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Checking for updates...")
            }
            is ContentStatus.UpToDate -> {
                Text("Content is up to date!", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onStartLearning,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Start Learning")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.checkForUpdates() }) {
                    Text("Check Again")
                }
            }
            is ContentStatus.UpdateAvailable -> {
                Text("Update Available: ${s.version}")
                Text("Size: ${s.size / 1024 / 1024} MB")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.downloadUpdate(s.version) }) {
                    Text("Download & Install")
                }
            }
            is ContentStatus.Downloading -> {
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Downloading... ${(s.progress * 100).toInt()}%")
            }
            is ContentStatus.Installing -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(s.stage)
            }
            is ContentStatus.Success -> {
                Text("Installation Complete!", color = MaterialTheme.colorScheme.primary)
                Text("Your new vocabulary is ready.")
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onStartLearning,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Start Learning")
                }
            }
            is ContentStatus.Error -> {
                Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.checkForUpdates() }) {
                    Text("Retry")
                }
            }
        }
    }
}
