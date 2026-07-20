package com.example.todotimer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ObjectiveFilterTest {
    private val objectives = listOf(
        Objective(1L, "Prepare promotion case", listOf("Professional", "Career"), 1L),
        Objective(2L, "Run a half marathon", listOf("Personal", "Health"), 2L),
        Objective(3L, "Plan family holiday", listOf("Personal"), 3L),
    )

    @Test
    fun `search matches objective title case insensitively`() {
        assertEquals(
            listOf(1L),
            ObjectiveFilter.filter(objectives, "PROMOTION", null).map { it.id },
        )
    }

    @Test
    fun `tag and search filters are combined`() {
        assertEquals(
            listOf(3L),
            ObjectiveFilter.filter(objectives, "plan", "personal").map { it.id },
        )
    }
}
