package com.deutschstart.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.deutschstart.app.util.LinguisticsEngine

@Composable
fun GermanTextWithXRay(
    text: String,
    linguisticsEngine: LinguisticsEngine,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge
) {
    val defaultColor = MaterialTheme.colorScheme.onSurface
    val annotatedString = remember(text, defaultColor) {
        buildAnnotatedString {
            val words = text.split(Regex("(?<=\\s)|(?=\\s)|(?<=[.,!?])|(?=[.,!?])"))
            
            for (word in words) {
                val cleanWord = word.trim()
                if (cleanWord.isEmpty()) {
                    append(word)
                    continue
                }

                // 1. Accusative Triggers (Orange)
                if (cleanWord.lowercase() == "den" || cleanWord.lowercase() == "einen") {
                    withStyle(SpanStyle(color = Color(0xFFFFA500), fontWeight = FontWeight.Bold)) { // Orange
                        append(word)
                    }
                    continue
                }
                
                // 2. Gender Coloring (Blue/Red/Green)
                if (cleanWord.first().isUpperCase()) {
                    val gender = linguisticsEngine.getGender(cleanWord)
                    if (gender != null) {
                        val color = when (gender.lowercase()) {
                            "m" -> Color(0xFF2196F3) // Blue
                            "f" -> Color(0xFFF44336) // Red
                            "n" -> Color(0xFF4CAF50) // Green
                            else -> defaultColor
                        }
                        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                            append(word)
                        }
                        continue
                    }
                }

                // Default
                append(word)
            }
        }
    }

    Text(
        text = annotatedString,
        modifier = modifier,
        style = style
    )
}
