package com.battor.freshmate.notification

import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NextOccurrenceTest {
    @Test
    fun `今天未过返回今天`() {
        val now = LocalDateTime.of(2026, 9, 25, 10, 0)
        assertEquals(
            LocalDateTime.of(2026, 9, 25, 16, 30),
            nextOccurrence(LocalTime.of(16, 30), now),
        )
    }

    @Test
    fun `已过返回明天`() {
        val now = LocalDateTime.of(2026, 9, 25, 17, 0)
        assertEquals(
            LocalDateTime.of(2026, 9, 26, 16, 30),
            nextOccurrence(LocalTime.of(16, 30), now),
        )
    }

    @Test
    fun `恰等于现在算已过排明天`() {
        val now = LocalDateTime.of(2026, 9, 25, 16, 30)
        assertEquals(
            LocalDateTime.of(2026, 9, 26, 16, 30),
            nextOccurrence(LocalTime.of(16, 30), now),
        )
    }
}
