package com.deutschstart.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ComprehensionMeter(
    knownPercent: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier,
    label: String = "Comprehension"
) {
    val (color, statusText) = when {
        knownPercent >= 0.90f -> Color(0xFF4CAF50) to "Excellent" // Green
        knownPercent >= 0.70f -> Color(0xFFFFC107) to "Good"      // Amber
        else -> Color(0xFFF44336) to "Challenging"                // Red
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$label: ${(knownPercent * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { knownPercent.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
