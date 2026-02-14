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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleDailyReset()
    }

    private fun scheduleDailyReset() {
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance()

        // Set execution around midnight (e.g., 00:05 AM)
        dueDate.set(Calendar.HOUR_OF_DAY, 0)
        dueDate.set(Calendar.MINUTE, 5)
        dueDate.set(Calendar.SECOND, 0)

        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }

        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis
        
        val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyResetWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_reset_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            dailyWorkRequest
        )
    }
}
