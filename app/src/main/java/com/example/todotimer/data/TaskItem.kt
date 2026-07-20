package com.example.todotimer.data

data class TaskItem(
    val id: Long,
    val objectiveId: Long,
    val parentTaskId: Long?,
    val title: String,
    val reminderAt: Long?,
    val isDone: Boolean,
    val createdAt: Long,
    val completedAt: Long?,
    val trackedMillis: Long,
    val timerStartedAt: Long?,
) {
    val isSubtask: Boolean get() = parentTaskId != null
    val isTimerRunning: Boolean get() = timerStartedAt != null
}
