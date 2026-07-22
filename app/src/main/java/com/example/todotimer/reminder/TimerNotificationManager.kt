package com.example.todotimer.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.todotimer.MainActivity
import com.example.todotimer.R
import com.example.todotimer.data.RoutineProgress
import com.example.todotimer.data.TaskItem
import com.example.todotimer.util.TimeTracking

class TimerNotificationManager(context: Context) {
    private val appContext = context.applicationContext

    fun sync(tasks: List<TaskItem>, routineProgress: RoutineProgress?) {
        if (!canPostNotifications()) {
            cancel()
            return
        }
        val runningTask = tasks.firstOrNull { it.isTimerRunning }
        val nextRoutineTask = routineProgress
            ?.takeIf { it.isStarted }
            ?.currentTaskId
            ?.let { taskId -> tasks.firstOrNull { it.id == taskId && !it.isDone } }
        val displayedTask = runningTask ?: nextRoutineTask
        if (displayedTask == null) {
            cancel()
            return
        }

        createChannel(appContext)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notificationTitle(runningTask, routineProgress))
            .setContentText(displayedTask.title)
            .setSubText(notificationSubText(displayedTask, runningTask, routineProgress))
            .setContentIntent(openOngoingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (runningTask != null) {
                    setWhen(runningTask.timerStartedAt ?: System.currentTimeMillis())
                    setUsesChronometer(true)
                    setShowWhen(true)
                    addAction(0, "Mark done", markDoneIntent(runningTask.id))
                    addAction(0, "Stop working", stopIntent(runningTask.id))
                } else if (routineProgress != null) {
                    setShowWhen(false)
                    addAction(0, "Start tracking", startRoutineIntent(routineProgress.runId))
                    addAction(0, "Mark done", markDoneIntent(displayedTask.id))
                }
            }
            .build()
        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
    }

    private fun notificationTitle(
        runningTask: TaskItem?,
        progress: RoutineProgress?,
    ): String = when {
        progress != null && (runningTask == null || progress.currentTaskId == runningTask.id) ->
            if (runningTask == null) "Next in ${progress.routineName}" else progress.routineName
        runningTask?.isSubtask == true -> "Working on subtask"
        else -> "Working on task"
    }

    private fun notificationSubText(
        task: TaskItem,
        runningTask: TaskItem?,
        progress: RoutineProgress?,
    ): String = if (progress != null && progress.currentTaskId == task.id) {
        "Step ${progress.currentPosition + 1} of ${progress.totalSteps}"
    } else if (runningTask != null) {
        "Already logged ${TimeTracking.format(task.trackedMillis)}"
    } else {
        "Ready to start"
    }

    private fun openOngoingIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_ONGOING
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopIntent(taskId: Long): PendingIntent = actionIntent(
        requestCode = 20_000 + taskId.hashCode(),
        action = TimerActionReceiver.ACTION_STOP_TIMER,
        taskId = taskId,
    )

    private fun markDoneIntent(taskId: Long): PendingIntent = actionIntent(
        requestCode = 30_000 + taskId.hashCode(),
        action = TimerActionReceiver.ACTION_MARK_DONE,
        taskId = taskId,
    )

    private fun actionIntent(requestCode: Int, action: String, taskId: Long): PendingIntent {
        val intent = Intent(appContext, TimerActionReceiver::class.java).apply {
            this.action = action
            putExtra(TimerActionReceiver.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun startRoutineIntent(runId: Long): PendingIntent {
        val intent = Intent(appContext, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_START_ROUTINE
            putExtra(TimerActionReceiver.EXTRA_ROUTINE_RUN_ID, runId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            40_000 + runId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancel() {
        NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val CHANNEL_ID = "active_task_timer"
        private const val NOTIFICATION_ID = 10_041

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.timer_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.timer_channel_description)
                setSound(null, null)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
