package com.battor.freshmate.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class ExpiryStatusTest {
    private val now = LocalDateTime.of(2026, 8, 15, 10, 0)

    private fun statusOf(remaining: Duration, totalDays: Long = 30): ExpiryStatus =
        expiryStatus(now.plus(remaining), totalDays.toInt(), now)

    @Test
    fun `已过期`() = assertEquals(ExpiryStatus.EXPIRED, statusOf(Duration.ofMinutes(-1)))

    @Test
    fun `恰好到期算过期`() = assertEquals(ExpiryStatus.EXPIRED, statusOf(Duration.ZERO))

    @Test
    fun testCriticalAtSixth() { assertEquals(ExpiryStatus.CRITICAL, statusOf(Duration.ofDays(5))) }

    @Test
    fun testWarningAtFifth() { assertEquals(ExpiryStatus.WARNING, statusOf(Duration.ofDays(6))) }

    @Test
    fun testCautionAtThird() { assertEquals(ExpiryStatus.CAUTION, statusOf(Duration.ofDays(10))) }

    @Test
    fun testSafeBeyondThird() { assertEquals(ExpiryStatus.SAFE, statusOf(Duration.ofDays(15))) }

    @Test
    fun `剩余文案 - 天加小时`() =
        assertEquals("3 天 17 小时", formatRemaining(Duration.ofMinutes(3 * 1440 + 17 * 60 + 29)))

    @Test
    fun `剩余文案 - 不足半小时`() =
        assertEquals("不足 30 分钟", formatRemaining(Duration.ofMinutes(20)))

    @Test
    fun `剩余文案 - 半小时`() =
        assertEquals("30 分钟", formatRemaining(Duration.ofMinutes(45)))

    @Test
    fun `过期文案 - 天`() =
        assertEquals("已过期 2 天", "已过期 " + formatExpired(Duration.ofDays(2)))

    @Test
    fun `过期文案 - 小时`() =
        assertEquals("已过期 5 小时", "已过期 " + formatExpired(Duration.ofHours(5)))
}
