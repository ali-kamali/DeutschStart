package com.deutschstart.app.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deutschstart.app.data.local.UserProgressDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class StreakReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userProgressDao: UserProgressDao,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val progress = userProgressDao.getProgress() ?: return Result.success()
            
            // Streak risk logic:
            // 1. Must have an active streak > 0
            // 2. Must NOT have crossed the daily XP threshold (10 XP) for today
            // Note: checks raw dailyXp. "Streak increment" logic in Repo happens at 10 XP.
            
            if (progress.currentStreak > 0 && progress.dailyXp < 10) {
                notificationHelper.showStreakRiskNotification(progress.currentStreak)
            }
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
