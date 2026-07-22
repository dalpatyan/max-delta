package com.example.todotimer.data

import java.time.Instant
import java.time.ZoneId

enum class RoutineFrequency(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly");

    fun nextOccurrence(
        scheduledAt: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val scheduled = Instant.ofEpochMilli(scheduledAt).atZone(zoneId)
        val next = when (this) {
            DAILY -> scheduled.plusDays(1)
            WEEKLY -> scheduled.plusWeeks(1)
            MONTHLY -> scheduled.plusMonths(1)
        }
        return next.toInstant().toEpochMilli()
    }
}

data class Routine(
    val id: Long,
    val name: String,
    val frequency: RoutineFrequency,
    val nextRunAt: Long,
    val isEnabled: Boolean,
    val taskIds: List<Long>,
    val createdAt: Long,
    val updatedAt: Long,
)

data class RoutineProgress(
    val runId: Long,
    val routineId: Long,
    val routineName: String,
    val scheduledAt: Long,
    val startedAt: Long?,
    val currentPosition: Int,
    val taskIds: List<Long>,
) {
    val currentTaskId: Long? get() = taskIds.getOrNull(currentPosition)
    val totalSteps: Int get() = taskIds.size
    val isStarted: Boolean get() = startedAt != null
}

object RoutineOrdering {
    fun move(taskIds: List<Long>, fromIndex: Int, toIndex: Int): List<Long> {
        if (fromIndex !in taskIds.indices || toIndex !in taskIds.indices || fromIndex == toIndex) {
            return taskIds
        }
        return taskIds.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
    }
}
