package com.battor.freshmate.ui.guide

import com.battor.freshmate.data.Category
import com.battor.freshmate.util.ExpiryStatus
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideMockContentTest {
    private val now = LocalDateTime.of(2026, 9, 3, 12, 0)

    @Test
    fun `mock 条目落在三个预期桶`() {
        val state = GuideMockContent("牛奶", "酸奶", "蔬菜", now).uiState
        fun names(status: ExpiryStatus) =
            state.buckets.firstOrNull { it.status == status }?.items?.map { it.name }
        assertEquals(listOf("牛奶"), names(ExpiryStatus.EXPIRED))
        assertEquals(listOf("酸奶"), names(ExpiryStatus.DUE_1D))
        assertEquals(listOf("蔬菜"), names(ExpiryStatus.DUE_7D))
        assertEquals(3, state.buckets.size)
    }

    @Test
    fun `无置顶区无表单`() {
        val state = GuideMockContent("牛奶", "酸奶", "蔬菜", now).uiState
        assertTrue(state.pinnedItems.isEmpty())
        assertNull(state.editing)
        assertTrue(state.buckets.all { bucket -> bucket.items.all { it.id < 0 } })
    }

    @Test
    fun `编辑步返回预填表单与会话条目`() {
        val content = GuideMockContent(
            "牛奶", "酸奶", "蔬菜", now,
            formName = "草莓", breadName = "面包", eggName = "鸡蛋",
        )
        val state = content.uiStateForStep(isEditingStep = true)
        val editing = requireNotNull(state.editing)
        assertEquals("草莓", editing.name)
        assertEquals(Category.FRUITS_VEG, editing.category)
        assertEquals("3", editing.shelfLifeValue)
        assertEquals(now, editing.createdAt)
        // 会话区：面包+鸡蛋，新→旧（createdAt 倒序），不入桶
        assertEquals(listOf("鸡蛋", "面包"), state.pinnedItems.map { it.name })
        assertEquals(setOf(-4L, -5L), state.sessionItemIds)
        // 桶里仍是原 3 条 mock，无会话条目混入
        assertEquals(3, state.buckets.size)
        assertTrue(state.buckets.all { bucket -> bucket.items.all { it.id in -3L..-1L } })
        // 基础态未被污染（派生函数不修改原对象）
        assertNull(content.uiState.editing)
    }

    @Test
    fun `非编辑步返回原主页状态`() {
        val content = GuideMockContent("牛奶", "酸奶", "蔬菜", now)
        val state = content.uiStateForStep(isEditingStep = false)
        assertNull(state.editing)
        assertTrue(state.pinnedItems.isEmpty())
        assertEquals(content.uiState, state)
    }
}
