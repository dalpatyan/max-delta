package com.example.todotimer.util

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TimeTrackingTest {
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `inactive timer returns stored duration`() {
        assertEquals(12_500L, TimeTracking.totalAt(12_500L, null, 100_000L))
    }

    @Test
    fun `active timer adds current session`() {
        assertEquals(42_500L, TimeTracking.totalAt(12_500L, 70_000L, 100_000L))
    }

    @Test
    fun `clock moving backwards never subtracts tracked time`() {
        assertEquals(12_500L, TimeTracking.totalAt(12_500L, 110_000L, 100_000L))
    }

    @Test
    fun `duration under an hour uses minutes and seconds`() {
        assertEquals("05:09", TimeTracking.format(309_000L))
    }

    @Test
    fun `duration over an hour includes hours`() {
        assertEquals("02:05:09", TimeTracking.format(7_509_000L))
    }
}
