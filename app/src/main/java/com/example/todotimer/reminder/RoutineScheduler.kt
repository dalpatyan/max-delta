package com.example.todotimer.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.todotimer.data.Routine

class RoutineScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun schedule(routine: Routine) {
        cancel(routine.id)
        if (!routine.isEnabled || routine.taskIds.isEmpty()) return
        val pendingIntent = pendingIntent(
            routineId = routine.id,
            scheduledAt = routine.nextRunAt,
            lookupFlag = PendingIntent.FLAG_UPDATE_CURRENT,
        ) ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                routine.nextRunAt,
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                routine.nextRunAt,
                pendingIntent,
            )
        }
    }

    fun cancel(routineId: Long) {
        alarmManager.cancel(
            pendingIntent(
                routineId = routineId,
                scheduledAt = 0L,
                lookupFlag = PendingIntent.FLAG_NO_CREATE,
            ) ?: return,
        )
    }

    private fun pendingIntent(
        routineId: Long,
        scheduledAt: Long,
        lookupFlag: Int,
    ): PendingIntent? {
        val intent = Intent(appContext, RoutineAlarmReceiver::class.java).apply {
            action = ACTION_ROUTINE_DUE
            data = Uri.parse("todotimer://routine/$routineId")
            putExtra(EXTRA_ROUTINE_ID, routineId)
            putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
        }
        return PendingIntent.getBroadcast(
            appContext,
            routineId.hashCode(),
            intent,
            lookupFlag or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_ROUTINE_DUE = "com.example.todotimer.action.ROUTINE_DUE"
        const val EXTRA_ROUTINE_ID = "routine_id"
        const val EXTRA_SCHEDULED_AT = "routine_scheduled_at"
    }
}
