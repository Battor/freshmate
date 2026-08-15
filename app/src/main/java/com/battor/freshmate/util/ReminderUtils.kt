package com.battor.freshmate.util

import java.time.Duration
import java.time.LocalDateTime

/** 向下取整到半小时（14:47→14:30，14:29→14:00），秒/纳秒清零。 */
fun roundDownToHalfHour(t: LocalDateTime): LocalDateTime =
    t.withMinute(if (t.minute >= 30) 30 else 0).withSecond(0).withNano(0)

/**
 * 3 个提醒时点：剩余 1/3、1/5、1/6 时（精确比例，再向下取整到半小时）。
 * 升序、去重、已取整到半小时。
 */
fun reminderTimes(expiry: LocalDateTime, shelfLifeDays: Int): List<LocalDateTime> {
    val total = Duration.ofDays(shelfLifeDays.toLong())
    return listOf(3L, 5L, 6L)
        .map { divisor -> roundDownToHalfHour(expiry.minus(total.dividedBy(divisor))) }
        .distinct()
        .sorted()
}

/** 只保留严格晚于 now 的提醒时点（<= now 视为已过去）。 */
fun futureReminderTimes(
    expiry: LocalDateTime,
    shelfLifeDays: Int,
    now: LocalDateTime,
): List<LocalDateTime> = reminderTimes(expiry, shelfLifeDays).filter { it > now }
