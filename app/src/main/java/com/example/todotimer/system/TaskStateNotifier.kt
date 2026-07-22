package com.example.todotimer.system

import android.content.Context
import com.example.todotimer.data.TaskItem
import com.example.todotimer.data.RoutineProgress
import com.example.todotimer.reminder.RoutineNotificationManager
import com.example.todotimer.reminder.TimerNotificationManager
import com.example.todotimer.widget.TaskWidgetUpdater

class TaskStateNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val timerNotificationManager = TimerNotificationManager(appContext)
    private val routineNotificationManager = RoutineNotificationManager(appContext)

    fun sync(
        tasks: List<TaskItem>,
        routineProgress: RoutineProgress?,
        readyRoutineProgress: RoutineProgress?,
    ) {
        routineNotificationManager.sync(tasks, readyRoutineProgress)
        timerNotificationManager.sync(tasks, routineProgress)
        TaskWidgetUpdater.updateAll(appContext)
    }
}
