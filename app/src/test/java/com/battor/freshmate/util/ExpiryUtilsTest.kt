package com.battor.freshmate.util

import org.junit.Assert.assertEquals
import org.junit.Test
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
}
