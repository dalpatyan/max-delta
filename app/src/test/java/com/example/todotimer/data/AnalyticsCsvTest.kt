package com.example.todotimer.data

import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsCsvTest {
    @Test
    fun `export includes objective tags and current running time`() {
        val objective = Objective(
            id = 1L,
            title = "Launch, pilot",
            tags = listOf("Professional", "Q3"),
            createdAt = 1L,
        )
        val task = TaskItem(
            id = 10L,
            objectiveId = 1L,
            parentTaskId = null,
            title = "Review \"results\"",
            reminderAt = null,
            isDone = false,
            createdAt = 2L,
            completedAt = null,
            trackedMillis = 2_000L,
            timerStartedAt = 4_000L,
        )

        val csv = AnalyticsCsv.build(listOf(objective), listOf(task), nowMillis = 7_000L)

        assertTrue(csv.startsWith("\uFEFFObjective,"))
        assertTrue(csv.contains("\"Launch, pilot\""))
        assertTrue(csv.contains("\"Professional | Q3\""))
        assertTrue(csv.contains("\"Review \"\"results\"\"\""))
        assertTrue(csv.contains("\"Running\",5,\"00:00:05\""))
    }
}
