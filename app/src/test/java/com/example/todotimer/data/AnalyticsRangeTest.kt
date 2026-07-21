package com.example.todotimer.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsRangeTest {
    private val task = TaskItem(
        id = 7L,
        objectiveId = 1L,
        parentTaskId = null,
        title = "Focused work",
        reminderAt = null,
        isDone = false,
        createdAt = 0L,
        completedAt = null,
        trackedMillis = 0L,
        timerStartedAt = 8_000L,
    )

    @Test
    fun `sessions are clipped to selected range and running time is included`() {
        val range = AnalyticsRange(5_000L, 10_000L, "Custom")
        val entries = listOf(
            TimeEntry(1L, task.id, 1_000L, 6_000L),
            TimeEntry(2L, task.id, 6_000L, 8_000L),
            TimeEntry(3L, 99L, 5_000L, 10_000L),
        )

        assertEquals(5_000L, AnalyticsTime.loggedMillis(task, entries, range, nowMillis = 12_000L))
    }

    @Test
    fun `custom range includes the full end date`() {
        val zone = ZoneId.of("UTC")
        val range = AnalyticsRanges.custom(
            startDate = LocalDate.of(2026, 7, 1),
            endDateInclusive = LocalDate.of(2026, 7, 3),
            zoneId = zone,
        )

        assertEquals(
            LocalDate.of(2026, 7, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            range.startMillis,
        )
        assertEquals(
            LocalDate.of(2026, 7, 4).atStartOfDay(zone).toInstant().toEpochMilli(),
            range.endExclusiveMillis,
        )
    }
}
