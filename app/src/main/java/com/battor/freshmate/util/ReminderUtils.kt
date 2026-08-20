package com.battor.freshmate.util

import java.time.Duration
import java.time.LocalDateTime

/** 向下取整到半小时（14:47→14:30，14:29→14:00），秒/纳秒清零。 */
fun roundDownToHalfHour(t: LocalDateTime): LocalDateTime =
    t.withMinute(if (t.minute >= 30) 30 else 0).withSecond(0).withNano(0)

/** 合并窗口：相邻候选时点间隔 ≤24 小时（含）视为相近，保留较早者。 */
private val MergeWindow = Duration.ofHours(24)

/**
 * 全部候选提醒时点（不滤 now）：比例档（剩 1/3、1/5、1/6 保质期）∪ 绝对档（到期前 7/3/1 天）。
 * 逐个取整到半小时、排序去重，再按 ≤24h 窗口合并（链式：每个时点与"最后保留者"比间隔）。
 */
fun mergedReminderTimes(expiry: LocalDateTime, shelfLifeDays: Int): List<LocalDateTime> {
    val total = Duration.ofDays(shelfLifeDays.toLong())
    val candidates = listOf(3L, 5L, 6L).map { expiry.minus(total.dividedBy(it)) } +
        listOf(7L, 3L, 1L).map { expiry.minusDays(it) }
    val merged = mutableListOf<LocalDateTime>()
    candidates.map(::roundDownToHalfHour).sorted().distinct().forEach { t ->
        val last = merged.lastOrNull()
        if (last == null || Duration.between(last, t) > MergeWindow) merged.add(t)
    }
    return merged
}

/**
 * 录入/编辑保存时的提醒快照：merged 只保留严格晚于 now 的时点；
 * 全部已过但条目未过期（expiry > now）时保底追加到期时刻本身；已过期返回空列表。
 * 保底时点取整后可能 ≤ now（到期就在本半小时内），届时闹钟立即触发一次，属预期行为。
 */
fun computeReminderTimes(
    expiry: LocalDateTime,
    shelfLifeDays: Int,
    now: LocalDateTime,
): List<LocalDateTime> {
    val future = mergedReminderTimes(expiry, shelfLifeDays).filter { it > now }
    return if (future.isEmpty() && expiry > now) listOf(roundDownToHalfHour(expiry)) else future
}
