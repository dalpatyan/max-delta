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

object NotificationHelper {
    private const val CHANNEL_ID = "task_reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        TimerNotificationManager.createChannel(context)
        RoutineNotificationManager.createChannel(context)
    }

    fun showReminder(context: Context, item: TaskItem) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        createChannel(context)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReminderScheduler.EXTRA_ITEM_ID, item.id)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val type = if (item.isSubtask) "Subtask reminder" else "Task reminder"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(type)
            .setContentText(item.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.title))
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(item.id.hashCode(), notification)
    }

    fun showLongTimerReminder(context: Context, item: TaskItem) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        createChannel(context)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_ONGOING
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            LONG_TIMER_NOTIFICATION_BASE + item.id.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("One-hour check-in")
            .setContentText("${item.title} has been running for more than an hour.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${item.title} has been running for more than an hour. Keep going or stop the timer."),
            )
            .setContentIntent(contentIntent)
            .addAction(0, "Mark done", timerActionIntent(context, item.id, true))
            .addAction(0, "Stop working", timerActionIntent(context, item.id, false))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(longTimerNotificationId(item.id), notification)
    }

    fun cancelLongTimerReminder(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(longTimerNotificationId(taskId))
    }

    private fun timerActionIntent(
        context: Context,
        taskId: Long,
        markDone: Boolean,
    ): PendingIntent {
        val intent = Intent(context, TimerActionReceiver::class.java).apply {
            action = if (markDone) {
                TimerActionReceiver.ACTION_MARK_DONE
            } else {
                TimerActionReceiver.ACTION_STOP_TIMER
            }
            putExtra(TimerActionReceiver.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            (if (markDone) 70_000 else 80_000) + taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun longTimerNotificationId(taskId: Long): Int =
        LONG_TIMER_NOTIFICATION_BASE + taskId.hashCode()

    private const val LONG_TIMER_NOTIFICATION_BASE = 20_000
}
