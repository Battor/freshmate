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
    fun `填了生产日期时从当天零点起算`() =
        assertEquals(
            LocalDateTime.of(2026, 8, 15, 0, 0),
            expiryDateTime(java.time.LocalDate.of(2026, 8, 10), created, 5),
        )
}
