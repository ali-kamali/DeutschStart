package com.deutschstart.app.ui.grammar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deutschstart.app.data.local.GrammarTopicEntity
import com.deutschstart.app.ui.components.GermanTextWithXRay
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarListScreen(
    onNavigateToDetail: (String) -> Unit,
    viewModel: GrammarViewModel = hiltViewModel()
) {
    val topics by viewModel.topics.collectAsState()

    // Trigger explicit reload once to ensure cache is warm
    LaunchedEffect(Unit) {
        viewModel.refreshLinguistics()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Grammar Topics") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(topics) { topic ->
                GrammarTopicItem(topic, onClick = { onNavigateToDetail(topic.id) })
            }
        }
    }
}

@Composable
fun GrammarTopicItem(topic: GrammarTopicEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(topic.title, style = MaterialTheme.typography.titleMedium)
            if (!topic.description.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(topic.description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarDetailScreen(
    topicId: String,
    onNavigateBack: () -> Unit,
    viewModel: GrammarViewModel = hiltViewModel()
) {
    // Load topic on entry
    LaunchedEffect(topicId) {
        viewModel.selectTopic(topicId)
    }

    val topic by viewModel.selectedTopic.collectAsState()
    val gson = Gson()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(topic?.title ?: "Loading...") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (topic == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Parse Content JSON
                val sectionsType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val sections: List<Map<String, Any>> = try {
                    gson.fromJson(topic!!.contentJson, sectionsType)
                } catch (e: Exception) {
                    emptyList()
                }

                items(sections) { section ->
                    GrammarSection(section, viewModel)
                }
            }
        }
    }
}

@Composable
fun GrammarSection(section: Map<String, Any>, viewModel: GrammarViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val title = section["title"] as? String
            val content = section["content"] as? String
            
            if (!title.isNullOrEmpty()) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
            }
            
            if (!content.isNullOrEmpty()) {
                // Use X-Ray renderer for content!
                GermanTextWithXRay(
                    text = content,
                    linguisticsEngine = viewModel.linguisticsEngine
                )
            }
        }
    }
}
