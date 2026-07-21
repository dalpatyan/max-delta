package com.example.todotimer.data

data class PendingTaskSummary(
    val id: Long,
    val title: String,
    val objectiveTitle: String,
    val isSubtask: Boolean,
    val reminderAt: Long?,
)
