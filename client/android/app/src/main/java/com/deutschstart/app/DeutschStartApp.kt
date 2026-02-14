package com.deutschstart.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.deutschstart.app.util.DailyResetWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class DeutschStartApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationHelper: com.deutschstart.app.notification.NotificationHelper

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
        scheduleDailyReset()
        scheduleNotificationWorkers()
    }

    private fun scheduleDailyReset() {
        scheduleDailyWork<DailyResetWorker>("daily_reset_worker", 0, 5) // 00:05
    }

    private fun scheduleNotificationWorkers() {
        // Goal Progress: 7 PM (19:00)
        scheduleDailyWork<com.deutschstart.app.notification.GoalProgressWorker>("goal_progress_worker", 19, 0)
        
        // Due Cards: 8 PM (20:00)
        scheduleDailyWork<com.deutschstart.app.notification.DueCardsWorker>("due_cards_worker", 20, 0)
        
        // Streak Risk: 9 PM (21:00)
        scheduleDailyWork<com.deutschstart.app.notification.StreakReminderWorker>("streak_risk_worker", 21, 0)
    }

    private inline fun <reified T : androidx.work.ListenableWorker> scheduleDailyWork(uniqueName: String, hour: Int, minute: Int) {
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance()

        dueDate.set(Calendar.HOUR_OF_DAY, hour)
        dueDate.set(Calendar.MINUTE, minute)
        dueDate.set(Calendar.SECOND, 0)

        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }

        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis
        
        val workRequest = PeriodicWorkRequestBuilder<T>(24, TimeUnit.HOURS)
            .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            uniqueName,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
