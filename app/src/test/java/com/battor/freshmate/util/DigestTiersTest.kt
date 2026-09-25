package com.battor.freshmate.util

import com.battor.freshmate.data.Category
import com.battor.freshmate.data.FoodItem
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestTiersTest {
    private val now = LocalDateTime.of(2026, 9, 25, 16, 30)

    /** hoursToExpiry = 距到期的整小时数（负数 = 已过期）。 */
    private fun of(name: String, hoursToExpiry: Long) = FoodItem(
        name = name, category = Category.DAIRY, productionDate = null,
        shelfLifeDays = 30, quantity = null,
        createdAt = now.plusHours(hoursToExpiry).minusDays(30),
    )

    @Test
    fun `一档收1天内二档收1到3天其余排除`() {
        val tiers = digestTiers(
            listOf(
                of("半天", 12),   // 剩 12h → DUE_1D
                of("两天", 48),   // 剩 48h → DUE_3D
                of("过", -5),     // EXPIRED：不推
                of("五天", 120),  // DUE_7D：不推
                of("十天", 240),  // DUE_14D：不推
                of("远", 720),    // SAFE：不推
            ),
            now,
        )
        assertEquals(listOf("半天"), tiers.dueWithin1d.map { it.name })
        assertEquals(listOf("两天"), tiers.dueWithin3d.map { it.name })
    }

    @Test
    fun `档内按到期升序最紧急在前`() {
        // 20h 与 6h 都在 1 天档内，6h 更紧急排前
        val tiers = digestTiers(listOf(of("晚", 20), of("早", 6)), now)
        assertEquals(listOf("早", "晚"), tiers.dueWithin1d.map { it.name })
    }

    @Test
    fun `空输入两档皆空`() {
        val tiers = digestTiers(emptyList(), now)
        assertTrue(tiers.dueWithin1d.isEmpty())
        assertTrue(tiers.dueWithin3d.isEmpty())
    }
}
