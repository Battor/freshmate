package com.battor.freshmate.util

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 到期时间 = 起点 + shelfLifeDays 天。
 * 填了生产日期：到期日（生产日 + N 天）当天 23:59:59——到期日含当天（食品标签惯例），
 * 提醒时点倒推也落在清醒时段而非凌晨；未填（null）：录入时刻 + N 天（精确到分钟）。
 */
fun expiryDateTime(
    productionDate: LocalDate?,
    createdAt: LocalDateTime,
    shelfLifeDays: Int,
): LocalDateTime =
    if (productionDate != null) {
        // 生产日 + N 天 + 1 天的 00:00 减 1 秒 = （生产日 + N 天）的 23:59:59
        productionDate.plusDays(shelfLifeDays.toLong() + 1).atStartOfDay().minusSeconds(1)
    } else {
        createdAt.plusDays(shelfLifeDays.toLong())
    }
