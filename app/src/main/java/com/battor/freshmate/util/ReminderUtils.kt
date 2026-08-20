package com.battor.freshmate.util

import java.time.Duration
import java.time.LocalDateTime

/** 向下取整到半小时（14:47→14:30，14:29→14:00），秒/纳秒清零。 */
fun roundDownToHalfHour(t: LocalDateTime): LocalDateTime =
    t.withMinute(if (t.minute >= 30) 30 else 0).withSecond(0).withNano(0)

/** 合并窗口：相邻时点间隔 ≤24 小时（含）视为相近，保留较早者。 */
private val MergeWindow = Duration.ofHours(24)

/** 候选提醒时点（比例档 1/3、1/5、1/6 保质期 ∪ 绝对档 7/3/1 天），取整到半小时、升序去重。 */
private fun candidateTimes(expiry: LocalDateTime, shelfLifeDays: Int): List<LocalDateTime> {
    val total = Duration.ofDays(shelfLifeDays.toLong())
    return (listOf(3L, 5L, 6L).map { expiry.minus(total.dividedBy(it)) } +
        listOf(7L, 3L, 1L).map { expiry.minusDays(it) })
        .map(::roundDownToHalfHour)
        .sorted()
        .distinct()
}

/** 链式合并：每个时点与"最后保留者"比间隔，≤24h 即吞掉（保留较早者）。 */
private fun mergeChained(times: List<LocalDateTime>): List<LocalDateTime> {
    val merged = mutableListOf<LocalDateTime>()
    times.forEach { t ->
        val last = merged.lastOrNull()
        if (last == null || Duration.between(last, t) > MergeWindow) merged.add(t)
    }
    return merged
}

/**
 * 全量候选的合并结果（含已过去时点）——仅用于保存时统计"已错过的提醒数"。
 */
fun mergedReminderTimes(expiry: LocalDateTime, shelfLifeDays: Int): List<LocalDateTime> =
    mergeChained(candidateTimes(expiry, shelfLifeDays))

/**
 * 录入/编辑保存时的提醒快照（spec 顺序：先滤 now 再合并）：
 * 只对未来的候选做 ≤24h 窗口合并，过去候选不会吞掉本应触发的未来提醒；
 * 结果为空但条目未过期（expiry > now）时保底追加到期时刻本身（取整到半小时，
 * 取整后可能 ≤ now——到期就在本半小时内——届时闹钟立即触发一次，属预期行为）；
 * 已过期条目返回空列表（不提醒）。
 */
fun computeReminderTimes(
    expiry: LocalDateTime,
    shelfLifeDays: Int,
    now: LocalDateTime,
): List<LocalDateTime> {
    val future = mergeChained(candidateTimes(expiry, shelfLifeDays).filter { it > now })
    return if (future.isEmpty() && expiry > now) listOf(roundDownToHalfHour(expiry)) else future
}
