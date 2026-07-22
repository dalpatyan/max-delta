package com.example.todotimer.data

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

        val next = RoutineFrequency.MONTHLY.nextOccurrence(scheduled, zone)

        assertEquals(
            LocalDateTime.of(2026, 8, 31, 7, 30),
            java.time.Instant.ofEpochMilli(next).atZone(zone).toLocalDateTime(),
        )
    }
}
