package com.example.todotimer.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.todotimer.di.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LongTimerReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LongTimerReminderScheduler.ACTION_LONG_TIMER_REMINDER) return
        val taskId = intent.getLongExtra(LongTimerReminderScheduler.EXTRA_TASK_ID, -1L)
        val startedAt = intent.getLongExtra(LongTimerReminderScheduler.EXTRA_STARTED_AT, -1L)
        if (taskId < 0L || startedAt < 0L) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AppGraph.repository(context)
                    .claimLongTimerReminder(taskId, startedAt)
                    ?.let { task -> NotificationHelper.showLongTimerReminder(context, task) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
