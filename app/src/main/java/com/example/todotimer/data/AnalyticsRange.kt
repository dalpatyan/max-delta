package com.example.todotimer.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class AnalyticsRange(
    val startMillis: Long,
    val endExclusiveMillis: Long,
    val label: String,
) {
    init {
        require(endExclusiveMillis > startMillis) { "Analytics range must have a positive duration" }
    }
}

object AnalyticsRanges {
    fun today(nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): AnalyticsRange {
        val date = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        return fromDates(date, date, "Today", zoneId)
    }

    fun thisWeek(nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): AnalyticsRange {
        val date = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return fromDates(start, start.plusDays(6), "This week", zoneId)
    }

    fun thisMonth(nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): AnalyticsRange {
        val date = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        return fromDates(date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()), "This month", zoneId)
    }

    fun custom(
        startDate: LocalDate,
        endDateInclusive: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): AnalyticsRange {
        require(!endDateInclusive.isBefore(startDate)) { "End date cannot be before start date" }
        return fromDates(startDate, endDateInclusive, "Custom", zoneId)
    }

    private fun fromDates(
        startDate: LocalDate,
        endDateInclusive: LocalDate,
        label: String,
        zoneId: ZoneId,
    ): AnalyticsRange = AnalyticsRange(
        startMillis = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
        endExclusiveMillis = endDateInclusive.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
        label = label,
    )
}

object AnalyticsTime {
    fun loggedMillis(
        task: TaskItem,
        entries: List<TimeEntry>,
        range: AnalyticsRange,
        nowMillis: Long,
    ): Long {
        val closedSessions = entries.asSequence()
            .filter { it.taskId == task.id }
            .sumOf { overlapMillis(it.startedAt, it.endedAt, range) }
        val runningSession = task.timerStartedAt?.let { startedAt ->
            overlapMillis(startedAt, nowMillis.coerceAtLeast(startedAt), range)
        } ?: 0L
        return closedSessions + runningSession
    }

    internal fun overlapMillis(
        startedAt: Long,
        endedAt: Long,
        range: AnalyticsRange,
    ): Long {
        val overlapStart = maxOf(startedAt, range.startMillis)
        val overlapEnd = minOf(endedAt, range.endExclusiveMillis)
        return (overlapEnd - overlapStart).coerceAtLeast(0L)
    }
}
