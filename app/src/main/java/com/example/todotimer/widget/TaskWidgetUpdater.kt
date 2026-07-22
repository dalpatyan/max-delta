package com.example.todotimer.widget

import android.content.Context

object TaskWidgetUpdater {
    fun updateAll(context: Context) {
        PendingTasksWidgetProvider.updateAll(context.applicationContext)
        AddTaskWidgetProvider.updateAll(context.applicationContext)
    }
}
