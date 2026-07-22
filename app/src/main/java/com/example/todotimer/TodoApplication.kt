package com.example.todotimer

import android.app.Application
import com.example.todotimer.reminder.NotificationHelper
import com.example.todotimer.di.AppGraph

class TodoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        AppGraph.repository(this)
    }
}
