package com.example.todotimer

import android.app.Application
import com.example.todotimer.reminder.NotificationHelper

class TodoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}
