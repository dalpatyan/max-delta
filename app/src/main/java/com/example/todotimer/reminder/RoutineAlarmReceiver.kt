package com.example.todotimer.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.todotimer.di.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RoutineScheduler.ACTION_ROUTINE_DUE) return
        val routineId = intent.getLongExtra(RoutineScheduler.EXTRA_ROUTINE_ID, -1L)
        val scheduledAt = intent.getLongExtra(RoutineScheduler.EXTRA_SCHEDULED_AT, -1L)
        if (routineId < 0L || scheduledAt < 0L) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AppGraph.repository(context).activateRoutine(routineId, scheduledAt)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
