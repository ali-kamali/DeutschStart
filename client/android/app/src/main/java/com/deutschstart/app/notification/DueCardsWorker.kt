package com.deutschstart.app.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deutschstart.app.data.local.VocabularyDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DueCardsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val vocabularyDao: VocabularyDao,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val dueCount = vocabularyDao.getDueCountSuspend(now)
            
            if (dueCount >= 5) {
                notificationHelper.showDueCardsNotification(dueCount)
            }
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
