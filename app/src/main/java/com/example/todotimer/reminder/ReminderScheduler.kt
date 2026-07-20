package com.example.todotimer.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.todotimer.data.TaskItem

class ReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun schedule(item: TaskItem) {
        cancel(item.id)
        val triggerAt = item.reminderAt ?: return
        if (item.isDone || triggerAt <= System.currentTimeMillis()) return

        val pendingIntent = pendingIntent(item.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
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

    fun cancel(itemId: Long) {
        alarmManager.cancel(pendingIntent(itemId, PendingIntent.FLAG_NO_CREATE) ?: return)
    }

    fun hasExactAlarmAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun pendingIntent(itemId: Long, lookupFlag: Int): PendingIntent? {
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            data = Uri.parse("todotimer://reminder/$itemId")
            putExtra(EXTRA_ITEM_ID, itemId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            itemId.hashCode(),
            intent,
            lookupFlag or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_REMINDER = "com.example.todotimer.ACTION_REMINDER"
        const val EXTRA_ITEM_ID = "item_id"
    }
}
