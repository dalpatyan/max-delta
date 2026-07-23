package com.example.todotimer.data

import java.time.Instant
import java.time.DayOfWeek
import java.time.ZoneId

enum class RoutineFrequency(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    CUSTOM("Custom");

    fun alignFirstOccurrence(
        scheduledAt: Long,
        customDays: Set<DayOfWeek> = emptySet(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        if (this != CUSTOM) return scheduledAt
        require(customDays.isNotEmpty()) { "Choose at least one day" }
        val scheduled = Instant.ofEpochMilli(scheduledAt).atZone(zoneId)
        if (scheduled.dayOfWeek in customDays) return scheduledAt
        val daysUntilSelected = (1..6).first { offset ->
            scheduled.plusDays(offset.toLong()).dayOfWeek in customDays
        }
        return scheduled.plusDays(daysUntilSelected.toLong()).toInstant().toEpochMilli()
    }

    fun nextOccurrence(
        scheduledAt: Long,
        customDays: Set<DayOfWeek> = emptySet(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val scheduled = Instant.ofEpochMilli(scheduledAt).atZone(zoneId)
        val next = when (this) {
            DAILY -> scheduled.plusDays(1)
            WEEKLY -> scheduled.plusWeeks(1)
            MONTHLY -> scheduled.plusMonths(1)
            CUSTOM -> {
                require(customDays.isNotEmpty()) { "Choose at least one day" }
                val daysUntilNext = (1..7).first { offset ->
                    scheduled.plusDays(offset.toLong()).dayOfWeek in customDays
                }
                scheduled.plusDays(daysUntilNext.toLong())
            }
        }
        return next.toInstant().toEpochMilli()
    }
}

object RoutineDays {
    fun encode(days: Set<DayOfWeek>): String = days
        .sortedBy { it.value }
        .joinToString(",") { it.value.toString() }

    fun decode(encoded: String?): Set<DayOfWeek> = encoded
        .orEmpty()
        .split(',')
        .mapNotNull { raw -> raw.trim().toIntOrNull() }
        .filter { value -> value in 1..7 }
        .map { value -> DayOfWeek.of(value) }
        .toSet()
}

data class Routine(
    val id: Long,
    val name: String,
    val frequency: RoutineFrequency,
    val nextRunAt: Long,
    val isEnabled: Boolean,
    val taskIds: List<Long>,
    val customDays: Set<DayOfWeek> = emptySet(),
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun nextOccurrence(
        scheduledAt: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long = frequency.nextOccurrence(scheduledAt, customDays, zoneId)
}

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
