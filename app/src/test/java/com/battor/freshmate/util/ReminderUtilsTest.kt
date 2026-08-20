package com.battor.freshmate.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ReminderUtilsTest {
    // 参考场景：到期 2026-09-18 10:00，now 2026-08-20 10:00
    private val expiry = LocalDateTime.of(2026, 9, 18, 10, 0)
    private val now = LocalDateTime.of(2026, 8, 20, 10, 0)

    @Test
    fun `取整到半小时 - 分钟大于等于30`() =
        assertEquals(
            LocalDateTime.of(2026, 1, 1, 14, 30),
            roundDownToHalfHour(LocalDateTime.of(2026, 1, 1, 14, 47, 12)),
        )

    @Test
    fun `取整到半小时 - 分钟小于30`() =
        assertEquals(
            LocalDateTime.of(2026, 1, 1, 14, 0),
            roundDownToHalfHour(LocalDateTime.of(2026, 1, 1, 14, 29, 59, 999_999_999)),
        )

    @Test
    fun `30天保质期合并后五个时点`() {
        // 候选：1/3→09-08、7天→09-11、1/5→09-12、1/6→09-13、3天→09-15、1天→09-17
        // 09-12 与 09-11 间隔恰 24h（含）→ 舍 09-12；09-13 与 09-11 间隔 2 天 → 保留
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 9, 8, 10, 0),
                LocalDateTime.of(2026, 9, 11, 10, 0),
                LocalDateTime.of(2026, 9, 13, 10, 0),
                LocalDateTime.of(2026, 9, 15, 10, 0),
                LocalDateTime.of(2026, 9, 17, 10, 0),
            ),
            computeReminderTimes(expiry, 30, now),
        )
    }

    @Test
    fun `短保质期各档全部落入24h窗口链式合并为单个时点`() {
        // 到期 08-22 10:00，保质期 2 天（总 48h）：比例档 1/3→08-21 18:00、1/5=9.6h→08-22 00:24→00:00、
        // 1/6=8h→08-22 02:00，绝对档 7/3 天已过去、1 天→08-21 10:00。
        // 08-21 10:00 起整条链间隔均 ≤24h → 只剩最早者
        val e = LocalDateTime.of(2026, 8, 22, 10, 0)
        assertEquals(
            listOf(LocalDateTime.of(2026, 8, 21, 10, 0)),
            computeReminderTimes(e, 2, now),
        )
    }

    @Test
    fun `过去候选不吞掉未来的提醒`() {
        // 到期 08-22 10:00、保质期 7 天、now = 08-19 20:00：
        // 若先合并后过滤，8-19 10:00（已过去）会作链锚吞掉 8-20 02:00——错误；
        // spec 顺序（先滤后并）：[8-20 02:00, 8-21 00:00, 8-21 06:00, 8-21 10:00]
        // → 8-20 02:00 留、8-21 00:00（22h）吞、8-21 06:00（28h）留、8-21 10:00 吞
        val e = LocalDateTime.of(2026, 8, 22, 10, 0)
        val n = LocalDateTime.of(2026, 8, 19, 20, 0)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 8, 20, 2, 0),
                LocalDateTime.of(2026, 8, 21, 6, 0),
            ),
            computeReminderTimes(e, 7, n),
        )
    }

    @Test
    fun `候选全部已过但未到期时保底追加到期时刻`() {
        // 到期 08-20 12:00，保质期 1 天，now 10:00：所有候选（1/3、1/5、1/6、1 天前）均 ≤ now
        val e = LocalDateTime.of(2026, 8, 20, 12, 0)
        assertEquals(
            listOf(LocalDateTime.of(2026, 8, 20, 12, 0)),
            computeReminderTimes(e, 1, now),
        )
    }

    @Test
    fun `保底时点同样取整到半小时`() {
        val e = LocalDateTime.of(2026, 8, 20, 12, 47)
        assertEquals(
            listOf(LocalDateTime.of(2026, 8, 20, 12, 30)),
            computeReminderTimes(e, 1, now),
        )
    }

    @Test
    fun `已过期条目返回空列表`() {
        val e = LocalDateTime.of(2026, 8, 19, 10, 0)
        assertEquals(emptyList<LocalDateTime>(), computeReminderTimes(e, 30, now))
    }

    @Test
    fun `等于now的时点视为已过去`() {
        // 合并后候选 09-08/09-11/09-13/09-15/09-17 全部 ≤ now（09-17 恰等于 now → 视为过去）
        // → 全过滤，条目未过期 → 保底到期时刻
        val n = LocalDateTime.of(2026, 9, 17, 10, 0)
        assertEquals(
            listOf(LocalDateTime.of(2026, 9, 18, 10, 0)),
            computeReminderTimes(expiry, 30, n),
        )
    }
}
