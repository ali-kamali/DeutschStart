package com.deutschstart.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProgressDao {
    @Query("SELECT * FROM user_progress WHERE id = 'global'")
    fun observeProgress(): Flow<UserProgressEntity?>

    @Query("SELECT * FROM user_progress WHERE id = 'global'")
    suspend fun getProgress(): UserProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(progress: UserProgressEntity)

    @Query("UPDATE user_progress SET dailyXp = dailyXp + :amount, totalXp = totalXp + :amount, lastActiveDate = :now WHERE id = 'global'")
    suspend fun addXp(amount: Long, now: Long)

    @Query("UPDATE user_progress SET dailyXp = 0 WHERE id = 'global'")
    suspend fun resetDailyXp()

    @Query("UPDATE user_progress SET currentStreak = :streak, longestStreak = :longest, lastActiveDate = :now WHERE id = 'global'")
    suspend fun updateStreak(streak: Int, longest: Int, now: Long)
    
    @Query("UPDATE user_progress SET streakFreezes = :freezes WHERE id = 'global'")
    suspend fun updateStreakFreezes(freezes: Int)

    @Query("UPDATE user_progress SET streakFreezes = 3, lastStreakFreezeReset = :now WHERE id = 'global'")
    suspend fun resetMonthlyFreezes(now: Long)

    @Query("UPDATE user_progress SET dailyGoal = :goal WHERE id = 'global'")
    suspend fun updateDailyGoal(goal: Int)
    
    @Query("UPDATE user_progress SET badges = :jsonBadges WHERE id = 'global'")
    suspend fun updateBadges(jsonBadges: String)
}
