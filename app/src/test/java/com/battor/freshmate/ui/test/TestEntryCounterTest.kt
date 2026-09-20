package com.battor.freshmate.ui.test

import org.junit.Assert.assertEquals
import org.junit.Test

class TestEntryCounterTest {
    private val hints = mutableListOf<Int>()
    private val unlocks = mutableListOf<Unit>()
    private val counter = TestEntryCounter(
        onHint = { hints.add(it) },
        onUnlock = { unlocks.add(Unit) },
    )

    // 时间源由参数注入：t1, t2, ... 每次 +1000ms（窗口 1500ms 内）
    private fun t(n: Long) = n * 1000

    @Test fun `窗口内连点 5 次触发解锁`() {
        repeat(5) { counter.onTap(t(it.toLong())) }
        assertEquals(1, unlocks.size)
    }

    @Test fun `第 3、4 次提示剩余次数`() {
        repeat(4) { counter.onTap(t(it.toLong())) }
        assertEquals(listOf(2, 1), hints)
    }

    @Test fun `超时点击重置计数`() {
        counter.onTap(t(0)); counter.onTap(t(1)) // 计数 2
        counter.onTap(t(10)) // 距上次 9 秒 → 重置为 1
        repeat(3) { counter.onTap(t(11 + it.toLong())) } // 2,3,4 —— 不应解锁
        assertEquals(0, unlocks.size)
        assertEquals(listOf(2, 1), hints) // 第 3、4 次各提示一次
    }

    @Test fun `解锁后计数归零需重新连点 5 次`() {
        repeat(5) { counter.onTap(t(it.toLong())) }
        repeat(4) { counter.onTap(t(10 + it.toLong())) } // 解锁后从零再来 4 次不够
        assertEquals(1, unlocks.size)
    }
}
