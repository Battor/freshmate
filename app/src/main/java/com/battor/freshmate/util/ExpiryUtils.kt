package com.battor.freshmate.util

import java.time.Duration
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

/**
 * 余量比例（卡片余量色层，specs/2026-09-26）：剩余 ÷ 总保质期，油表语义——越接近 1 越新鲜。
 * 过期（含恰好到期）归 0；总时长 ≤ 0 的异常数据防御性归 1；结果 clamp [0,1]。
 * 起点 = 生产日期当天零点（未填则录入时刻），与 expiryDateTime 的口径一致。
 */
fun remainingFraction(
    productionDate: LocalDate?,
    createdAt: LocalDateTime,
    shelfLifeDays: Int,
    now: LocalDateTime,
): Float {
    val expiry = expiryDateTime(productionDate, createdAt, shelfLifeDays)
    val total = Duration.between(productionDate?.atStartOfDay() ?: createdAt, expiry)
    if (total.isZero || total.isNegative) return 1f
    val fraction = Duration.between(now, expiry).toNanos().toFloat() / total.toNanos()
    return fraction.coerceIn(0f, 1f)
}
