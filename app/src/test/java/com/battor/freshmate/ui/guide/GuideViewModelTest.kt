package com.battor.freshmate.ui.guide

import com.battor.freshmate.util.ExpiryStatus
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideViewModelTest {
    private val now = LocalDateTime.of(2026, 9, 3, 12, 0)

    @Test
    fun `mock 条目落在三个预期桶`() {
        val state = GuideViewModel(now).uiState
        fun names(status: ExpiryStatus) =
            state.buckets.firstOrNull { it.status == status }?.items?.map { it.name }
        assertEquals(listOf("牛奶"), names(ExpiryStatus.EXPIRED))
        assertEquals(listOf("酸奶"), names(ExpiryStatus.DUE_1D))
        assertEquals(listOf("蔬菜"), names(ExpiryStatus.DUE_7D))
        assertEquals(3, state.buckets.size)
    }

    @Test
    fun `无置顶区无表单`() {
        val state = GuideViewModel(now).uiState
        assertTrue(state.pinnedItems.isEmpty())
        assertNull(state.editing)
        assertTrue(state.buckets.all { bucket -> bucket.items.all { it.id < 0 } })
    }
}
