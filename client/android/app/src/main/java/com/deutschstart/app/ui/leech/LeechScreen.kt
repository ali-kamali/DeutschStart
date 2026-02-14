package com.deutschstart.app.ui.leech

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deutschstart.app.data.local.VocabularyEntity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeechScreen(
    onNavigateBack: () -> Unit,
    viewModel: LeechViewModel = hiltViewModel()
) {
    val leeches by viewModel.leeches.collectAsState()
    var editingItem by remember { mutableStateOf<VocabularyEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leech Cards") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (leeches.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No leeches! 🎉", style = MaterialTheme.typography.headlineMedium)
                    Text("Great job keeping up!", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(Modifier.padding(16.dp)) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(16.dp))
                            Text(
                                "These cards are suspended because you struggle with them. Fix them by adding a mnemonic or suspend them forever.",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                items(leeches) { item ->
                    LeechCard(
                        item = item,
                        onFix = { editingItem = item },
                        onSuspendForever = { viewModel.suspendForever(item) }
                    )
                }
            }
        }
    }

    if (editingItem != null) {
        val item = editingItem!!
        var mnemonic by remember { mutableStateOf(item.genderMnemonic ?: "") }

        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("Fix Leech") },
            text = {
                Column {
                    WordLabel(item)
                    Spacer(Modifier.height(16.dp))
                    Text("Lapses: ${item.lapses} | Reviews: ${item.reps}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = mnemonic,
                        onValueChange = { mnemonic = it },
                        label = { Text("Mnemonic / Note") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.fixLeech(item, mnemonic)
                    editingItem = null
                }) {
                    Text("Save & Unsuspend")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LeechCard(
    item: VocabularyEntity,
    onFix: () -> Unit,
    onSuspendForever: () -> Unit
) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WordLabel(item)
                Text("Failed ${item.lapses} times", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
            
            Spacer(Modifier.height(8.dp))
            if (!item.genderMnemonic.isNullOrBlank()) {
                Text("Note: ${item.genderMnemonic}", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                Spacer(Modifier.height(8.dp))
            }
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSuspendForever) {
                    Text("Suspend Forever", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onFix) {
                    Text("Fix")
                }
            }
        }
    }
}

@Composable
private fun WordLabel(item: VocabularyEntity) {
    val genderColor = when (item.gender) {
        "m" -> Color(0xFF2196F3) // Blue
        "f" -> Color(0xFFE91E63) // Red
        "n" -> Color(0xFF4CAF50) // Green
        else -> MaterialTheme.colorScheme.onSurface
    }
    val display = buildString {
        if (!item.article.isNullOrBlank()) append("${item.article} ")
        append(item.word)
        append(" — ${item.translationEn}")
    }
    Text(
        text = display,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = genderColor
    )
}
