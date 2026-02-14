package com.deutschstart.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_progress")
data class UserProgressEntity(
    @PrimaryKey val id: String = "global",
    val totalXp: Long = 0,
    val dailyXp: Long = 0,           // Resets at midnight
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastActiveDate: Long = 0,    // Unix timestamp of last XP gain
    val streakFreezes: Int = 3,      // Max 3 per month
    val lastStreakFreezeReset: Long = 0, // Timestamp of last monthly freeze reset
    val badges: String = "[]",       // JSON list of unlocked badge IDs
    val dailyGoal: Int = 50          // User configurable 20-100
)
