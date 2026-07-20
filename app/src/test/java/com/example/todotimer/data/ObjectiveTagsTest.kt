package com.example.todotimer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ObjectiveTagsTest {
    @Test
    fun `custom tags are trimmed and empty values removed`() {
        assertEquals(
            listOf("Personal", "Professional"),
            ObjectiveTags.fromInput(" Personal, , Professional "),
        )
    }

    @Test
    fun `duplicate tags are removed without changing first spelling`() {
        assertEquals(
            listOf("Personal", "Work"),
            ObjectiveTags.fromInput("Personal, personal, Work, WORK"),
        )
    }

    @Test
    fun `stored tags round trip`() {
        val tags = listOf("Personal", "Long term", "Health")
        assertEquals(tags, ObjectiveTags.decode(ObjectiveTags.encode(tags)))
    }
}
