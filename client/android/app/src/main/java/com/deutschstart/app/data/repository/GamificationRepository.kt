package com.deutschstart.app.data.repository

import com.deutschstart.app.data.local.UserProgressDao
import com.deutschstart.app.data.local.UserProgressEntity
import com.deutschstart.app.data.model.Badge
import com.deutschstart.app.data.model.XpReason
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class GamificationRepository @Inject constructor(
    private val dao: UserProgressDao
) {
    private val gson = Gson()
    private val xpMutex = Mutex() // Prevent concurrent XP awards from racing

    /** Seed the default row once at startup if it doesn't exist. */
    suspend fun ensureProgressExists() {
        if (dao.getProgress() == null) {
            dao.insertOrUpdate(UserProgressEntity())
        }
    }

    fun observeProgress(): Flow<UserProgressEntity> {
        // Safe: after ensureProgressExists() is called in HomeViewModel.init,
        // the row always exists. We still fall back defensively.
        return dao.observeProgress().map { it ?: UserProgressEntity() }
    }

    suspend fun getProgress(): UserProgressEntity {
        return dao.getProgress() ?: run {
            val default = UserProgressEntity()
            dao.insertOrUpdate(default)
            default
        }
    }

    /**
     * Called on app start to check if the streak is still valid.
     * If the user missed a day: consume a freeze (if available) or reset streak.
     */
    suspend fun checkAndUpdateStreak() {
        val progress = getProgress()
        val now = System.currentTimeMillis()

        val lastActive = Calendar.getInstance().apply { timeInMillis = progress.lastActiveDate }
        val today = Calendar.getInstance().apply { timeInMillis = now }

        lastActive.toMidnight()
        today.toMidnight()

        val diffDays = daysBetween(lastActive, today)

        when {
            diffDays <= 1L -> {
                // Same day or consecutive day — streak is fine.
                // Actual streak increment happens in awardXp when crossing 10 XP threshold.
            }
            diffDays == 2L && progress.streakFreezes > 0 -> {
                // Missed exactly one day, consume one freeze to preserve streak.
                dao.updateStreakFreezes(progress.streakFreezes - 1)
            }
            else -> {
                // Missed 2+ days (or 1 day with no freezes) — streak broken.
                dao.updateStreak(0, progress.longestStreak, now)
            }
        }
    }

    /**
     * Award XP for a given reason. Handles:
     * - Daily goal crossing bonus
     * - Streak increment when crossing 10 XP threshold for the day
     * - Badge checks
     *
     * Thread-safe via mutex to prevent race conditions from rapid card ratings.
     */
    suspend fun awardXp(amount: Int, reason: XpReason) {
        xpMutex.withLock {
            val now = System.currentTimeMillis()
            val progress = getProgress()

            // Check for daily goal completion (crossing the threshold)
            val crossedGoal = (progress.dailyXp < progress.dailyGoal) &&
                    (progress.dailyXp + amount >= progress.dailyGoal)

            var finalAmount = amount
            if (crossedGoal) {
                finalAmount += XpReason.DAILY_GOAL_COMPLETE.xpAmount
            }

            dao.addXp(finalAmount.toLong(), now)

            // Handle Streak Increment
            // Rule: "First time crossing 10 dailyXp today -> streak++"
            // progress is the snapshot BEFORE addXp updated lastActiveDate.
            if (progress.dailyXp < 10 && (progress.dailyXp + finalAmount) >= 10) {
                handleStreakIncrement(progress, now)
            }

            // Check Badge Unlocks
            checkBadges(progress.copy(totalXp = progress.totalXp + finalAmount))
        }
    }

    private suspend fun handleStreakIncrement(progress: UserProgressEntity, now: Long) {
        val lastActiveCal = Calendar.getInstance().apply { timeInMillis = progress.lastActiveDate }
        val todayCal = Calendar.getInstance().apply { timeInMillis = now }

        lastActiveCal.toMidnight()
        todayCal.toMidnight()

        // Only increment if lastActive was before today
        // (i.e., the streak hasn't already been incremented today)
        if (lastActiveCal.timeInMillis >= todayCal.timeInMillis) return

        val diffDays = daysBetween(lastActiveCal, todayCal)

        val newStreak = when {
            diffDays == 1L -> {
                // Consecutive day: increment streak
                progress.currentStreak + 1
            }
            diffDays == 2L && progress.streakFreezes > 0 -> {
                // Gap was bridged by freeze in checkAndUpdateStreak: increment
                progress.currentStreak + 1
            }
            progress.currentStreak == 0 -> {
                // Starting fresh (streak was broken earlier or first time)
                1
            }
            else -> {
                // Shouldn't happen if checkAndUpdateStreak ran properly,
                // but defensively reset to 1
                1
            }
        }

        val newLongest = max(progress.longestStreak, newStreak)
        dao.updateStreak(newStreak, newLongest, now)

        // Check streak badges
        checkStreakBadges(newStreak)
    }

    suspend fun updateDailyGoal(goal: Int) {
        dao.updateDailyGoal(goal.coerceIn(20, 100))
    }

    /**
     * Called by DailyResetWorker at midnight.
     * Resets daily XP, checks streak continuity, and resets monthly freezes on the 1st.
     */
    suspend fun resetDaily() {
        dao.resetDailyXp()

        val today = Calendar.getInstance()
        if (today.get(Calendar.DAY_OF_MONTH) == 1) {
            dao.resetMonthlyFreezes(System.currentTimeMillis())
        }

        checkAndUpdateStreak()
    }

    private suspend fun checkBadges(currentProgress: UserProgressEntity) {
        val earnedBadgeIds = parseBadges(currentProgress.badges).toMutableSet()
        val newBadges = mutableListOf<String>()

        // Night Owl (Review after 10 PM)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour >= 22 && !earnedBadgeIds.contains(Badge.NightOwl.id)) {
            newBadges.add(Badge.NightOwl.id)
        }

        // First Steps (Total XP > 0)
        if (currentProgress.totalXp > 0 && !earnedBadgeIds.contains(Badge.FirstSteps.id)) {
            newBadges.add(Badge.FirstSteps.id)
        }

        if (newBadges.isNotEmpty()) {
            earnedBadgeIds.addAll(newBadges)
            dao.updateBadges(gson.toJson(earnedBadgeIds))
        }
    }

    private suspend fun checkStreakBadges(streak: Int) {
        val progress = getProgress()
        val earnedBadgeIds = parseBadges(progress.badges).toMutableSet()
        val newBadges = mutableListOf<String>()

        if (streak >= 7 && !earnedBadgeIds.contains(Badge.WeekWarrior.id)) {
            newBadges.add(Badge.WeekWarrior.id)
        }
        if (streak >= 30 && !earnedBadgeIds.contains(Badge.MonthMarathon.id)) {
            newBadges.add(Badge.MonthMarathon.id)
        }

        if (newBadges.isNotEmpty()) {
            earnedBadgeIds.addAll(newBadges)
            dao.updateBadges(gson.toJson(earnedBadgeIds))
        }
    }

    private fun parseBadges(json: String): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Utility ---

    private fun Calendar.toMidnight() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun daysBetween(from: Calendar, to: Calendar): Long {
        return (to.timeInMillis - from.timeInMillis) / (24 * 60 * 60 * 1000)
    }
}
