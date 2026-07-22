package com.example.todotimer.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.todotimer.MainActivity
import com.example.todotimer.R
import com.example.todotimer.data.RoutineProgress
import com.example.todotimer.data.TaskItem

class RoutineNotificationManager(context: Context) {
    private val appContext = context.applicationContext

    fun sync(tasks: List<TaskItem>, progress: RoutineProgress?) {
        if (progress == null || progress.isStarted || !canPostNotifications()) {
            NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
            return
        }
        val firstTask = progress.currentTaskId?.let { id -> tasks.firstOrNull { it.id == id } }
        if (firstTask == null) {
            NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
            return
        }
        createChannel(appContext)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Routine ready • ${progress.routineName}")
            .setContentText("Start with ${firstTask.title}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${progress.routineName} is scheduled now. Start with ${firstTask.title}."),
            )
            .setContentIntent(openRoutinesIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(false)
            .addAction(0, "Start routine", startRoutineIntent(progress.runId))
            .build()
        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
    }

    private fun openRoutinesIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_ROUTINES
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun startRoutineIntent(runId: Long): PendingIntent {
        val intent = Intent(appContext, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_START_ROUTINE
            putExtra(TimerActionReceiver.EXTRA_ROUTINE_RUN_ID, runId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            runId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val CHANNEL_ID = "routine_reminders"
        private const val NOTIFICATION_ID = 10_042

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.routine_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.routine_channel_description)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
