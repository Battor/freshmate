package com.battor.freshmate.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ExpiryUtilsTest {
    private val created = LocalDateTime.of(2026, 8, 15, 10, 0)

    @Test
    fun `未填生产日期时从录入时刻起算`() =
        assertEquals(
            LocalDateTime.of(2026, 8, 22, 10, 0),
            expiryDateTime(null, created, 7),
        )

    @Test
    fun `填了生产日期时到期日当天结束`() =
        assertEquals(
            LocalDateTime.of(2026, 8, 15, 23, 59, 59),
            expiryDateTime(java.time.LocalDate.of(2026, 8, 10), created, 5),
        )

    @Test
    fun `生产日期当天的当天到期`() =
        assertEquals(
            LocalDateTime.of(2026, 8, 15, 23, 59, 59),
            expiryDateTime(java.time.LocalDate.of(2026, 8, 15), created, 0),
        )

    // ---- remainingFraction（卡片余量色层，specs/2026-09-26）----

    @Test
    fun `未填生产日期按录入日起算 剩余8天除以总10天为08`() {
        val createdAt = LocalDateTime.of(2026, 9, 1, 8, 0)
        val now = createdAt.plusDays(2)
        // expiry = createdAt + 10d；total = 10d；remaining = 8d
        assertEquals(0.8f, remainingFraction(null, createdAt, 10, now), 0.001f)
    }

    @Test
    fun `填生产日期时到期日含当天 比例约05`() {
        val production = LocalDate.of(2026, 1, 1)
        val createdAt = LocalDateTime.of(2026, 1, 1, 10, 0)
        // expiry = 2026-01-11 23:59:59（生产日 + 10 天当天末尾）；total ≈ 11 天
        val now = LocalDateTime.of(2026, 1, 6, 12, 0)
        assertEquals(0.5f, remainingFraction(production, createdAt, 10, now), 0.01f)
    }

    @Test
    fun `过期归零 恰好到期时刻也归零`() {
        val createdAt = LocalDateTime.of(2026, 9, 1, 8, 0)
        val expiry = createdAt.plusDays(10)
        assertEquals(0f, remainingFraction(null, createdAt, 10, expiry.plusSeconds(1)), 0.001f)
        assertEquals(0f, remainingFraction(null, createdAt, 10, expiry), 0.001f)
    }

    @Test
    fun `now早于起点或总时长异常时 clamp 到1`() {
        val createdAt = LocalDateTime.of(2026, 9, 1, 8, 0)
        // now 早于录入时刻
        assertEquals(1f, remainingFraction(null, createdAt, 10, createdAt.minusDays(1)), 0.001f)
        // shelfLife = 0 → total 为零，防御性归 1
        assertEquals(1f, remainingFraction(null, createdAt, 0, createdAt), 0.001f)
    }
}
