package com.battor.freshmate.util

import java.time.LocalDateTime

/** 向下取整到半小时（14:47→14:30，14:29→14:00），秒/纳秒清零。 */
fun roundDownToHalfHour(t: LocalDateTime): LocalDateTime =
    t.withMinute(if (t.minute >= 30) 30 else 0).withSecond(0).withNano(0)

/**
 * 3 个提醒时点：剩余 1/3、1/5、1/6 时（时长按整小时向下取整后再减）。
 * 升序、去重、已取整到半小时。
 */
fun reminderTimes(expiry: LocalDateTime, shelfLifeDays: Int): List<LocalDateTime> {
    val totalHours = shelfLifeDays.toLong() * 24
    return listOf(3L, 5L, 6L)
        .map { divisor -> roundDownToHalfHour(expiry.minusHours(totalHours / divisor)) }
        .distinct()
        .sorted()
}

/** 只保留严格晚于 now 的提醒时点（<= now 视为已过去）。 */
fun futureReminderTimes(
    expiry: LocalDateTime,
    shelfLifeDays: Int,
    now: LocalDateTime,
): List<LocalDateTime> = reminderTimes(expiry, shelfLifeDays).filter { it > now }
