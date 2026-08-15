package com.battor.freshmate.util

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 到期时间 = 起点 + shelfLifeDays 天。
 * 起点：productionDate 的 00:00；未填（null）时为 createdAt 的精确时刻。
 */
fun expiryDateTime(
    productionDate: LocalDate?,
    createdAt: LocalDateTime,
    shelfLifeDays: Int,
): LocalDateTime {
    val start = productionDate?.atStartOfDay() ?: createdAt
    return start.plusDays(shelfLifeDays.toLong())
}
