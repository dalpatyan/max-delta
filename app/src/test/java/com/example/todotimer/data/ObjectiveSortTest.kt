package com.example.todotimer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ObjectiveSortTest {
    private val objectives = listOf(
        Objective(1L, "Older", emptyList(), createdAt = 100L, updatedAt = 200L),
        Objective(2L, "Newer", emptyList(), createdAt = 300L, updatedAt = 300L),
        Objective(3L, "Worked", emptyList(), createdAt = 150L, updatedAt = 150L),
    )
    private val tasks = listOf(
        task(id = 10L, objectiveId = 1L, reminderAt = 5_000L, updatedAt = 400L),
        task(id = 20L, objectiveId = 2L, reminderAt = 9_000L, updatedAt = 500L),
        task(id = 30L, objectiveId = 3L, reminderAt = null, updatedAt = 2_000L),
    )

    @Test
    fun `recently added sorts by objective creation`() {
        assertEquals(
            listOf(2L, 3L, 1L),
            ObjectiveSorter.sort(objectives, tasks, ObjectiveSortOrder.RECENTLY_ADDED, 1_000L)
                .map { it.id },
        )
    }

    @Test
    fun `upcoming puts nearest future reminder first and unscheduled last`() {
        assertEquals(
            listOf(1L, 2L, 3L),
            ObjectiveSorter.sort(objectives, tasks, ObjectiveSortOrder.UPCOMING, 1_000L)
                .map { it.id },
        )
    }

    @Test
    fun `recently worked uses latest task or objective update`() {
        assertEquals(
            listOf(3L, 2L, 1L),
            ObjectiveSorter.sort(objectives, tasks, ObjectiveSortOrder.RECENTLY_WORKED, 1_000L)
                .map { it.id },
        )
    }

    private fun task(
        id: Long,
        objectiveId: Long,
        reminderAt: Long?,
        updatedAt: Long,
    ) = TaskItem(
        id = id,
        objectiveId = objectiveId,
        parentTaskId = null,
        title = "Task $id",
        reminderAt = reminderAt,
        isDone = false,
        createdAt = id,
        completedAt = null,
        trackedMillis = 0L,
        timerStartedAt = null,
        updatedAt = updatedAt,
    )
}
