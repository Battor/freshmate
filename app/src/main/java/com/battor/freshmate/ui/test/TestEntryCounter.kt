package com.battor.freshmate.ui.test

/**
 * 测试页隐藏入口的连点计数（关于页 APP 名称连点 5 次解锁）：
 * 点击间隔 ≤ [windowMillis] 才累加（Android 开发者选项惯例窗口），超时视为新一轮的第 1 次；
 * 第 [HINT_START] 次起回调 [onHint]（剩余次数），第 [REQUIRED_TAPS] 次回调 [onUnlock] 并归零。
 * 时间由调用方注入（UI 传 System.currentTimeMillis()），纯逻辑可单测。
 */
class TestEntryCounter(
    private val windowMillis: Long = 1500,
    private val onHint: (remaining: Int) -> Unit,
    private val onUnlock: () -> Unit,
) {
    private var taps = 0
    private var lastTapAt = 0L

    /** 处理一次点击（[nowMillis] 为当前时刻）：窗口内累加，超时算新一轮第 1 次；达标解锁并归零。 */
    fun onTap(nowMillis: Long) {
        taps = if (nowMillis - lastTapAt <= windowMillis) taps + 1 else 1
        lastTapAt = nowMillis
        when {
            taps >= REQUIRED_TAPS -> {
                taps = 0
                onUnlock()
            }
            taps >= HINT_START -> onHint(REQUIRED_TAPS - taps)
        }
    }

    companion object {
        const val REQUIRED_TAPS = 5
        private const val HINT_START = 3
    }
}
