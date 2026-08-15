package com.battor.freshmate.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ReminderUtilsTest {
    private val expiry = LocalDateTime.of(2026, 1, 31, 18, 0)

    @Test
    fun `取整到半小时 - 分钟大于等于30`() =
        assertEquals(
            LocalDateTime.of(2026, 1, 1, 14, 30),
            roundDownToHalfHour(LocalDateTime.of(2026, 1, 1, 14, 47, 12)),
        )

    @Test
    fun `取整到半小时 - 分钟小于30`() =
        assertEquals(
            LocalDateTime.of(2026, 1, 1, 14, 0),
            roundDownToHalfHour(LocalDateTime.of(2026, 1, 1, 14, 29, 59, 999_999_999)),
        )

    @Test
    fun `取整到半小时 - 整点不变`() =
        assertEquals(
            LocalDateTime.of(2026, 1, 1, 14, 30),
            roundDownToHalfHour(LocalDateTime.of(2026, 1, 1, 14, 30, 0, 1)),
        )

    @Test
    fun `30天保质期的三个提醒时点`() {
        // 总 720h：1/3=240h → 01-21；1/5=144h → 01-25；1/6=120h → 01-26
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 1, 21, 18, 0),
                LocalDateTime.of(2026, 1, 25, 18, 0),
                LocalDateTime.of(2026, 1, 26, 18, 0),
            ),
            reminderTimes(expiry, 30),
        )
    }

    @Test
    fun `时点取整到半小时`() {
        // 到期 01-04 18:47，总 72h：1/3=24h → 01-03 18:47 → 18:30；
        // 1/5=14h24m → 01-04 04:23 → 04:00；1/6=12h → 01-04 06:47 → 06:30
        val e = LocalDateTime.of(2026, 1, 4, 18, 47)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 1, 3, 18, 30),
                LocalDateTime.of(2026, 1, 4, 4, 0),
                LocalDateTime.of(2026, 1, 4, 6, 30),
            ),
            reminderTimes(e, 3),
        )
    }

    @Test
    fun `过滤已过去的时点`() {
        val now = LocalDateTime.of(2026, 1, 25, 12, 0)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 1, 25, 18, 0),
                LocalDateTime.of(2026, 1, 26, 18, 0),
            ),
            futureReminderTimes(expiry, 30, now),
        )
    }
}
