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
import com.example.todotimer.data.TaskItem
import com.example.todotimer.util.TimeTracking

class TimerNotificationManager(context: Context) {
    private val appContext = context.applicationContext

    fun sync(tasks: List<TaskItem>) {
        val runningTask = tasks.firstOrNull { it.isTimerRunning }
        if (runningTask == null || !canPostNotifications()) {
            NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
            return
        }
        createChannel(appContext)
        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_ONGOING
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = Intent(appContext, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_STOP_TIMER
            putExtra(TimerActionReceiver.EXTRA_TASK_ID, runningTask.id)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            appContext,
            runningTask.id.hashCode(),
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (runningTask.isSubtask) "Working on subtask" else "Working on task")
            .setContentText(runningTask.title)
            .setSubText("Already logged ${TimeTracking.format(runningTask.trackedMillis)}")
            .setContentIntent(openPendingIntent)
            .setWhen(runningTask.timerStartedAt ?: System.currentTimeMillis())
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop working", stopPendingIntent)
            .build()
        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val CHANNEL_ID = "active_task_timer"
        private const val NOTIFICATION_ID = 10_041

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.timer_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.timer_channel_description)
                setSound(null, null)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
