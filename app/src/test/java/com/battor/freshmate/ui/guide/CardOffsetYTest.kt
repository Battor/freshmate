package com.battor.freshmate.ui.guide

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

/** 说明卡纵向落点边界：上次第 4 步卡片被顶出屏裁切正是这片逻辑，纯函数化后护住。 */
class CardOffsetYTest {
    private val gap = 20f

    @Test
    fun `目标在上半屏卡贴镂空下方`() {
        val hole = Rect(0f, 100f, 400f, 200f) // 中心 y=150 < 500
        assertEquals(220f.toInt(), cardOffsetY(hole, overlayHeight = 1000, cardHeight = 150, gapPx = gap))
    }

    @Test
    fun `目标在下半屏卡贴镂空上方`() {
        val hole = Rect(0f, 700f, 400f, 800f) // 中心 y=750 ≥ 500
        assertEquals(530, cardOffsetY(hole, overlayHeight = 1000, cardHeight = 150, gapPx = gap))
    }

    @Test
    fun `上方放不下时贴顶不裁切`() {
        val hole = Rect(0f, 100f, 400f, 900f) // 中心 y=500 走上方：100 - 20 - 150 = -70 → 钳到 0
        assertEquals(0, cardOffsetY(hole, overlayHeight = 1000, cardHeight = 150, gapPx = gap))
    }

    @Test
    fun `下方放不下时贴底不裁切`() {
        val hole = Rect(0f, 0f, 400f, 999f) // 中心 y=499.5 < 500 走下方，999+20=1019 越界 → 钳到 850
        assertEquals(850, cardOffsetY(hole, overlayHeight = 1000, cardHeight = 150, gapPx = gap))
    }

    @Test
    fun `卡比屏高时贴顶`() {
        // 可用高度 coerceAtLeast(0)：coerceIn(0, 0) = 0，不抛异常
        val hole = Rect(0f, 400f, 400f, 500f)
        assertEquals(0, cardOffsetY(hole, overlayHeight = 1000, cardHeight = 1200, gapPx = gap))
    }

    @Test
    fun `首帧卡高为零时等价于无卡高钳制`() {
        val hole = Rect(0f, 700f, 400f, 800f)
        assertEquals(680, cardOffsetY(hole, overlayHeight = 1000, cardHeight = 0, gapPx = gap)) // 700-20-0
    }
}
