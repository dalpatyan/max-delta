package com.example.todotimer.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.todotimer.di.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(ReminderScheduler.EXTRA_ITEM_ID, -1L)
        if (itemId < 0L) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val item = AppGraph.database(context).findById(itemId)
                if (item != null && !item.isDone) NotificationHelper.showReminder(context, item)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
