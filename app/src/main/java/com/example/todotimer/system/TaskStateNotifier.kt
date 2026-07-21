package com.example.todotimer.system

import android.content.Context
import com.example.todotimer.data.TaskItem
import com.example.todotimer.reminder.TimerNotificationManager
import com.example.todotimer.widget.TaskWidgetUpdater

class TaskStateNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val timerNotificationManager = TimerNotificationManager(appContext)

    fun sync(tasks: List<TaskItem>) {
        timerNotificationManager.sync(tasks)
        TaskWidgetUpdater.updateAll(appContext)
    }
}
