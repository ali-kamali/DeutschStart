package com.deutschstart.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun DailyGoalSetter(
    currentGoal: Int,
    onGoalChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember(currentGoal) { mutableStateOf(currentGoal.toFloat()) }

    Column(modifier = modifier) {
        Text(
            text = "Daily Goal: ${sliderPosition.roundToInt()} XP",
            style = MaterialTheme.typography.labelLarge
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("20", style = MaterialTheme.typography.labelSmall)
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = { onGoalChanged(sliderPosition.roundToInt()) },
                valueRange = 20f..100f,
                steps = 7, // (100-20)/10 = 8 intervals -> 7 steps (30,40,50,60,70,80,90)
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("100", style = MaterialTheme.typography.labelSmall)
        }
    }
}
