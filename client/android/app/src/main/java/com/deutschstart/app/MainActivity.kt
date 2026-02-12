package com.deutschstart.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.deutschstart.app.ui.content.ContentScreen
import com.deutschstart.app.ui.home.HomeViewModel
import com.deutschstart.app.ui.learning.FlashcardScreen
import com.deutschstart.app.ui.theme.DeutschStartTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeutschStartTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToContent = { navController.navigate("content") },
                onNavigateToPractice = { navController.navigate("flashcards") }
            )
        }
        composable("content") {
            ContentScreen()
        }
        composable("flashcards") {
            FlashcardScreen(
                onSessionComplete = {
                    navController.popBackStack("home", inclusive = false)
                }
            )
        }
    }
}

@Composable
fun HomeScreen(
    onNavigateToContent: () -> Unit,
    onNavigateToPractice: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.isLoading) {
            CircularProgressIndicator()
        } else if (state.totalWords == 0) {
            // Empty state — guide user to download content
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Welcome to DeutschStart!",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Download a content pack to start learning German.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onNavigateToContent) {
                Text("Download Content")
            }
        } else {
            // Dashboard with stats
            Text("Willkommen zurück!", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(32.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard("Learned", "${state.learnedWords}")
                StatCard("Due Now", "${state.dueWords}")
                StatCard("Total", "${state.totalWords}")
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onNavigateToPractice,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = state.totalWords > 0
            ) {
                Text(
                    if (state.dueWords > 0) "Review ${state.dueWords} Due Words"
                    else "Practice",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onNavigateToContent) {
                Text("Manage Content")
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String) {
    Card(
        modifier = Modifier.size(100.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
