package com.example.todotimer.di

import android.content.Context
import com.example.todotimer.data.TaskDatabase
import com.example.todotimer.data.TaskRepository
import com.example.todotimer.reminder.ReminderScheduler
import com.example.todotimer.reminder.RoutineScheduler
import com.example.todotimer.system.TaskStateNotifier

object AppGraph {
    @Volatile
    private var databaseInstance: TaskDatabase? = null

    @Volatile
    private var repositoryInstance: TaskRepository? = null

    fun database(context: Context): TaskDatabase = databaseInstance ?: synchronized(this) {
        databaseInstance ?: TaskDatabase(context.applicationContext).also {
            databaseInstance = it
        }
    }

    fun repository(context: Context): TaskRepository = repositoryInstance ?: synchronized(this) {
        repositoryInstance ?: TaskRepository(
            database = database(context),
            reminderScheduler = ReminderScheduler(context.applicationContext),
            routineScheduler = RoutineScheduler(context.applicationContext),
            taskStateNotifier = TaskStateNotifier(context.applicationContext),
        ).also {
            repositoryInstance = it
        }
    }
}
