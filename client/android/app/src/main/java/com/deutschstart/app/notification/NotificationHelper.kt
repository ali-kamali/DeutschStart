package com.deutschstart.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.deutschstart.app.MainActivity
import com.deutschstart.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_STUDY = "study_reminders"
        const val CHANNEL_STREAK = "streak_protection"
        const val CHANNEL_ACHIEVEMENT = "achievements"
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_STUDY,
                    context.getString(R.string.notif_channel_study),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Study reminders" },

                NotificationChannel(
                    CHANNEL_STREAK,
                    context.getString(R.string.notif_channel_streak),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Streak protection" },

                NotificationChannel(
                    CHANNEL_ACHIEVEMENT,
                    context.getString(R.string.notif_channel_achievement),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Badges and goals" }
            )

            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannels(channels)
        }
    }

    fun showDueCardsNotification(count: Int) {
        if (!hasPermission()) return

        val deepLinkIntent = Intent(
            Intent.ACTION_VIEW,
            "deutschstart://micro_lesson".toUri(),
            context,
            MainActivity::class.java
        )
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            1001, 
            deepLinkIntent, 
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_STUDY)
            .setSmallIcon(R.mipmap.ic_launcher_round) // Fallback icon
            .setContentTitle("DeutschStart")
            .setContentText(context.getString(R.string.notif_due_cards, count))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notify(1, notification)
    }

    fun showStreakRiskNotification(streak: Int) {
        if (!hasPermission()) return

        val deepLinkIntent = Intent(
            Intent.ACTION_VIEW,
            "deutschstart://micro_lesson".toUri(),
            context,
            MainActivity::class.java
        )
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            1002, 
            deepLinkIntent, 
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_STREAK)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("DeutschStart")
            .setContentText(context.getString(R.string.notif_streak_risk, streak))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notify(2, notification)
    }

    fun showGoalProgressNotification(current: Long, goal: Int) {
        if (!hasPermission()) return

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 
            1003, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_STUDY)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("DeutschStart")
            .setContentText(context.getString(R.string.notif_goal_progress, current, goal))
            .setProgress(goal, current.toInt(), false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notify(3, notification)
    }

    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun notify(id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission might have been revoked
        }
    }
}
