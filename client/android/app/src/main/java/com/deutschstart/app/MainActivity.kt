package com.deutschstart.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.deutschstart.app.ui.grammar.GrammarListScreen
import com.deutschstart.app.ui.grammar.GrammarDetailScreen
import com.deutschstart.app.ui.home.HomeViewModel
import com.deutschstart.app.ui.learning.FlashcardScreen
import com.deutschstart.app.ui.theme.DeutschStartTheme
import com.deutschstart.app.ui.components.ComprehensionMeter
import com.deutschstart.app.ui.components.GamificationSection
import com.deutschstart.app.ui.components.ConfettiOverlay
import com.deutschstart.app.ui.components.DailyGoalSetter
import dagger.hilt.android.AndroidEntryPoint
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.runtime.*

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
                onNavigateToPractice = { navController.navigate("flashcards") },
                onNavigateToPlaylist = { navController.navigate("playlist") },
                onNavigateToGrammar = { navController.navigate("grammar_list") },
                onNavigateToLeeches = { navController.navigate("leeches") },
                onNavigateToMicroLesson = { navController.navigate("micro_lesson") }
            )
        }
        composable("content") {
            ContentScreen(
                onStartLearning = {
                    navController.navigate("flashcards") {
                        popUpTo("home") { inclusive = false }
                    }
                }
            )
        }
        composable("flashcards") {
            FlashcardScreen(
                onSessionComplete = {
                    navController.popBackStack("home", inclusive = false)
                }
            )
        }
        composable("playlist") {
            com.deutschstart.app.playlist.PlaylistScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("grammar_list") {
            GrammarListScreen(
                onNavigateToDetail = { topicId ->
                    navController.navigate("grammar_detail/$topicId")
                }
            )
        }
        composable(
            "grammar_detail/{topicId}",
            arguments = listOf(navArgument("topicId") { type = NavType.StringType })
        ) { backStackEntry ->
            val topicId = backStackEntry.arguments?.getString("topicId") ?: return@composable
            GrammarDetailScreen(
                topicId = topicId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("leeches") {
            com.deutschstart.app.ui.leech.LeechScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "micro_lesson",
            deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "deutschstart://micro_lesson" })
        ) {
            com.deutschstart.app.ui.microlesson.MicroLessonScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun HomeScreen(
    onNavigateToContent: () -> Unit,
    onNavigateToPractice: () -> Unit,
    onNavigateToPlaylist: () -> Unit,
    onNavigateToGrammar: () -> Unit,
    onNavigateToLeeches: () -> Unit,
    onNavigateToMicroLesson: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val userProgress by viewModel.userProgress.collectAsState()
    
    // Confetti Logic
    var showConfetti by remember { mutableStateOf(false) }
    
    // Watch for goal completion
    // We need to track previous value to trigger only on crossing threshold
    // But since we don't have previous value easily here, we can just trigger if they JUST arrived at the screen and it's done? 
    // No, better to trigger if dailyXp >= dailyGoal AND we haven't shown it yet this session? 
    // Or just let the user see it once per session if they are above goal? 
    // Ideally, the ViewModel would expose a one-shot event.
    // For simplicity: If dailyXp >= dailyGoal, we can show it, but we need to dismiss it after 3s.
    // The ConfettiOverlay runs for 3s then stops.
    // We can just trigger it if (dailyXp >= dailyGoal) and we launch a side effect to check if we should show it.
    // Let's us a simple state: hasShownConfetti
    var hasShownConfetti by remember { mutableStateOf(false) }
    
    LaunchedEffect(userProgress, state) {
        val progress = userProgress
        if (progress != null && progress.dailyXp >= progress.dailyGoal && 
            progress.dailyXp > 0 && !hasShownConfetti) {
             showConfetti = true
             hasShownConfetti = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top // Changed from Center to Top for scrolling/layout
            // We might want to make it scrollable if content overflows
        ) {
            Spacer(Modifier.height(16.dp))
            if (state.isLoading) {
                 Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                 }
            } else if (state.totalWords == 0) {
                // Empty state — guide user to download content
                // ... (Existing empty state logic)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    }
                }
            } else {
                // Dashboard with stats
                Text("Willkommen zurück!", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))

                // GAMIFICATION SECTION
                userProgress?.let { progress ->
                    GamificationSection(userProgress = progress)
                    Spacer(Modifier.height(16.dp))
                }

                // Quick Micro-Lesson Button
                val context = androidx.compose.ui.platform.LocalContext.current
                val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        onNavigateToMicroLesson()
                    } else {
                        // Navigate anyway, just no notifications
                        onNavigateToMicroLesson()
                    }
                }

                Button(
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            val permission = android.Manifest.permission.POST_NOTIFICATIONS
                            if (androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, 
                                    permission
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                onNavigateToMicroLesson()
                            } else {
                                launcher.launch(permission)
                            }
                        } else {
                            onNavigateToMicroLesson()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text("Quick 5min ⚡", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(16.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCard("Learned", "${state.learnedWords}")
                    StatCard("Due Now", "${state.dueWords}")
                    StatCard("Total", "${state.totalWords}")
                }
                
                Spacer(Modifier.height(24.dp))
                ComprehensionMeter(
                    knownPercent = state.comprehension,
                    label = "Global Comprehension"
                )

                Spacer(Modifier.height(24.dp))

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

                OutlinedButton(
                    onClick = onNavigateToPlaylist,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = state.totalWords > 0
                ) {
                    Text("Smart Playlist", style = MaterialTheme.typography.titleMedium)
                }
                
                if (state.leechCount > 0) {
                     Spacer(Modifier.height(16.dp))
                     // ... Leech Card (kept as is)
                     Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        onClick = onNavigateToLeeches
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${state.leechCount} Leech${if (state.leechCount > 1) "es" else ""}", 
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "Cards blocking your progress", 
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            TextButton(onClick = onNavigateToLeeches) { 
                                Text("Fix", color = MaterialTheme.colorScheme.error) 
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onNavigateToGrammar,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.School, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Learn (Grammar)", style = MaterialTheme.typography.titleMedium)
                }
                
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onNavigateToContent) {
                    Text("Manage Content")
                }
                
                // Daily Goal Setter
                userProgress?.let { progress ->
                    Spacer(Modifier.height(16.dp))
                    DailyGoalSetter(
                        currentGoal = progress.dailyGoal, 
                        onGoalChanged = { viewModel.updateDailyGoal(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // Confetti Overlay
        ConfettiOverlay(trigger = showConfetti)
        
        // Reset trigger after animation (3s) to define end of "event"? 
        // The ConfettiOverlay handles its own animation lifecycle. 
        // We just toggle it on.
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
