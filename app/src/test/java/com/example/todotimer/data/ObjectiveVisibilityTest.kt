package com.example.todotimer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectiveVisibilityTest {
    private val objectives = listOf(
        Objective(1L, "Open objective", emptyList(), 1L),
        Objective(2L, "Closed objective", emptyList(), 2L),
        Objective(3L, "Empty objective", emptyList(), 3L),
    )
    private val tasks = listOf(
        task(id = 10L, objectiveId = 1L, isDone = false),
        task(id = 11L, objectiveId = 1L, isDone = true),
        task(id = 20L, objectiveId = 2L, isDone = true),
        task(id = 21L, objectiveId = 2L, parentTaskId = 20L, isDone = true),
    )

    @Test
    fun `objective closes only when it has tasks and every top-level task is done`() {
        assertFalse(ObjectiveVisibility.isClosed(1L, tasks))
        assertTrue(ObjectiveVisibility.isClosed(2L, tasks))
        assertFalse(ObjectiveVisibility.isClosed(3L, tasks))
    }

    @Test
    fun `closed objectives are hidden until requested`() {
        assertEquals(
            listOf(1L, 3L),
            ObjectiveVisibility.visibleObjectives(objectives, tasks, showClosed = false).map { it.id },
        )
        assertEquals(
            listOf(1L, 2L, 3L),
            ObjectiveVisibility.visibleObjectives(objectives, tasks, showClosed = true).map { it.id },
        )
    }

    @Test
    fun `completed tasks and subtasks are hidden until requested`() {
        assertEquals(
            listOf(10L),
            ObjectiveVisibility.visibleActions(tasks, showClosed = false).map { it.id },
        )
        assertEquals(tasks.map { it.id }, ObjectiveVisibility.visibleActions(tasks, true).map { it.id })
    }

    private fun task(
        id: Long,
        objectiveId: Long,
        parentTaskId: Long? = null,
        isDone: Boolean,
    ) = TaskItem(
        id = id,
        objectiveId = objectiveId,
        parentTaskId = parentTaskId,
        title = "Task $id",
        reminderAt = null,
        isDone = isDone,
        createdAt = id,
        completedAt = if (isDone) id else null,
        trackedMillis = 0L,
        timerStartedAt = null,
    )
}
