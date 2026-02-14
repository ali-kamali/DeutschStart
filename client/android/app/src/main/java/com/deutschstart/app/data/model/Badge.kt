package com.deutschstart.app.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Badge(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    object WordCollector : Badge(
        id = "word_100",
        title = "Word Collector",
        description = "Learned 100 words",
        icon = Icons.Default.School
    )

    object WeekWarrior : Badge(
        id = "streak_7",
        title = "Week Warrior",
        description = "7-day streak",
        icon = Icons.Default.LocalFireDepartment
    )

    object MonthMarathon : Badge(
        id = "streak_30",
        title = "Month Marathon",
        description = "30-day streak",
        icon = Icons.Default.EmojiEvents
    ) // Note: using EmojiEvents as a placeholder for a trophy

    object NightOwl : Badge(
        id = "night_owl",
        title = "Night Owl",
        description = "Review after 10 PM",
        icon = Icons.Default.DarkMode
    )

    object FirstSteps : Badge(
        id = "first_steps",
        title = "First Steps",
        description = "Completed first review",
        icon = Icons.Default.Flag
    )
    
    object GrammarGuru : Badge(
        id = "grammar_master",
        title = "Grammar Guru",
        description = "Mastered 5 grammar topics",
        icon = Icons.Default.MenuBook
    )

    companion object {
        val allBadges = listOf(
            FirstSteps,
            WordCollector,
            WeekWarrior,
            MonthMarathon,
            NightOwl,
            GrammarGuru
        )

        fun fromId(id: String): Badge? {
            return allBadges.find { it.id == id }
        }
    }
}
