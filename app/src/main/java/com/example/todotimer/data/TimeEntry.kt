package com.example.todotimer.data

data class TimeEntry(
    val id: Long,
    val taskId: Long,
    val startedAt: Long,
    val endedAt: Long,
)
