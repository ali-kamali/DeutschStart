package com.deutschstart.app.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deutschstart.app.data.local.UserProgressDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class GoalProgressWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userProgressDao: UserProgressDao,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val progress = userProgressDao.getProgress() ?: return Result.success()
            
            // Show nudges if user has some progress but hasn't finished goal
            if (progress.dailyXp > 0 && progress.dailyXp < progress.dailyGoal) {
                notificationHelper.showGoalProgressNotification(progress.dailyXp, progress.dailyGoal)
            }
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
