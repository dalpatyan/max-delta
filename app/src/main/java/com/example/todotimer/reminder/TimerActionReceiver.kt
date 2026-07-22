package com.example.todotimer.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.todotimer.di.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in supportedActions) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = AppGraph.repository(context)
                when (action) {
                    ACTION_STOP_TIMER -> {
                        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                        if (taskId >= 0L) repository.stopTimer(taskId)
                    }
                    ACTION_MARK_DONE -> {
                        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                        if (taskId >= 0L) repository.setDone(taskId, true)
                    }
                    ACTION_START_ROUTINE -> {
                        val runId = intent.getLongExtra(EXTRA_ROUTINE_RUN_ID, -1L)
                        if (runId >= 0L) repository.startRoutine(runId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_STOP_TIMER = "com.example.todotimer.action.STOP_TIMER"
        const val ACTION_MARK_DONE = "com.example.todotimer.action.MARK_DONE"
        const val ACTION_START_ROUTINE = "com.example.todotimer.action.START_ROUTINE"
        const val EXTRA_TASK_ID = "timer_task_id"
        const val EXTRA_ROUTINE_RUN_ID = "routine_run_id"
        private val supportedActions = setOf(
            ACTION_STOP_TIMER,
            ACTION_MARK_DONE,
            ACTION_START_ROUTINE,
        )
    }
}
