package com.example.todotimer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReusableTaskCodecTest {
    @Test
    fun `subtask titles round trip and blanks are removed`() {
        val titles = listOf("Draft", "  Review  ", "", "Publish")

        assertEquals(
            listOf("Draft", "Review", "Publish"),
            ReusableTaskCodec.decode(ReusableTaskCodec.encode(titles)),
        )
    }
}
