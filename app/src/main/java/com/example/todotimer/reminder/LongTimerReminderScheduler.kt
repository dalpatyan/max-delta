package com.example.todotimer.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.todotimer.data.TaskItem

class LongTimerReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun schedule(task: TaskItem) {
        cancelAlarm(task.id)
        val startedAt = task.timerStartedAt ?: return
        if (task.longTimerRemindedAt != null || task.isDone) return
        val triggerAt = (startedAt + REMINDER_AFTER_MILLIS)
            .coerceAtLeast(System.currentTimeMillis() + MINIMUM_DELAY_MILLIS)
        val pendingIntent = pendingIntent(
            taskId = task.id,
            startedAt = startedAt,
            lookupFlag = PendingIntent.FLAG_UPDATE_CURRENT,
        ) ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent,
            )
        }
    }

    fun cancel(taskId: Long) {
        cancelAlarm(taskId)
        NotificationHelper.cancelLongTimerReminder(appContext, taskId)
    }

    private fun cancelAlarm(taskId: Long) {
        alarmManager.cancel(
            pendingIntent(
                taskId = taskId,
                startedAt = 0L,
                lookupFlag = PendingIntent.FLAG_NO_CREATE,
            ) ?: return,
        )
    }

    private fun pendingIntent(
        taskId: Long,
        startedAt: Long,
        lookupFlag: Int,
    ): PendingIntent? {
        val intent = Intent(appContext, LongTimerReminderReceiver::class.java).apply {
            action = ACTION_LONG_TIMER_REMINDER
            data = Uri.parse("todotimer://long-timer/$taskId")
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_STARTED_AT, startedAt)
        }
        return PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_BASE + taskId.hashCode(),
            intent,
            lookupFlag or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_LONG_TIMER_REMINDER =
            "com.example.todotimer.action.LONG_TIMER_REMINDER"
        const val EXTRA_TASK_ID = "long_timer_task_id"
        const val EXTRA_STARTED_AT = "long_timer_started_at"
        const val REMINDER_AFTER_MILLIS = 60L * 60L * 1_000L
        private const val MINIMUM_DELAY_MILLIS = 1_000L
        private const val REQUEST_CODE_BASE = 50_000
    }
}
