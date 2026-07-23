package com.example.todotimer.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineTest {
    @Test
    fun `tasks can be reordered explicitly`() {
        assertEquals(
            listOf(3L, 1L, 2L),
            RoutineOrdering.move(listOf(1L, 2L, 3L), fromIndex = 2, toIndex = 0),
        )
        assertEquals(
            listOf(1L, 2L, 3L),
            RoutineOrdering.move(listOf(1L, 2L, 3L), fromIndex = 0, toIndex = -1),
        )
    }

    @Test
    fun `monthly frequency keeps local scheduled time`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val scheduled = LocalDateTime.of(2026, 7, 31, 7, 30)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val next = RoutineFrequency.MONTHLY.nextOccurrence(scheduled, zoneId = zone)

        assertEquals(
            LocalDateTime.of(2026, 8, 31, 7, 30),
            java.time.Instant.ofEpochMilli(next).atZone(zone).toLocalDateTime(),
        )
    }

    @Test
    fun `custom frequency advances to the next selected weekday`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val monday = LocalDateTime.of(2026, 7, 20, 9, 15)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val next = RoutineFrequency.CUSTOM.nextOccurrence(
            scheduledAt = monday,
            customDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            zoneId = zone,
        )

        assertEquals(
            LocalDateTime.of(2026, 7, 22, 9, 15),
            Instant.ofEpochMilli(next).atZone(zone).toLocalDateTime(),
        )
    }

    @Test
    fun `custom frequency aligns first run and round trips selected days`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val tuesday = LocalDateTime.of(2026, 7, 21, 7, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val selected = setOf(DayOfWeek.SUNDAY, DayOfWeek.THURSDAY)

        val aligned = RoutineFrequency.CUSTOM.alignFirstOccurrence(
            scheduledAt = tuesday,
            customDays = selected,
            zoneId = zone,
        )

        assertEquals(
            LocalDateTime.of(2026, 7, 23, 7, 0),
            Instant.ofEpochMilli(aligned).atZone(zone).toLocalDateTime(),
        )
        assertEquals(selected, RoutineDays.decode(RoutineDays.encode(selected)))
    }
}
