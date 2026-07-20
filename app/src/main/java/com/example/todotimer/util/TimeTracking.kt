package com.example.todotimer.util

import java.util.Locale
import kotlin.math.max

object TimeTracking {
    fun totalAt(
        trackedMillis: Long,
        timerStartedAt: Long?,
        nowMillis: Long,
    ): Long = trackedMillis + if (timerStartedAt == null) {
        0L
    } else {
        max(0L, nowMillis - timerStartedAt)
    }

    fun format(millis: Long): String {
        val totalSeconds = max(0L, millis) / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }
}
