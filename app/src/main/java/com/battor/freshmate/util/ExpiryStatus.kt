package com.battor.freshmate.util

import java.time.Duration
import java.time.LocalDateTime

/** 条目紧急度，值越靠后越紧急。 */
enum class ExpiryStatus { SAFE, CAUTION, WARNING, CRITICAL, EXPIRED }

fun expiryStatus(expiry: LocalDateTime, shelfLifeDays: Int, now: LocalDateTime): ExpiryStatus {
    val remaining = Duration.between(now, expiry)
    if (!remaining.isNegative && remaining.isZero) return ExpiryStatus.EXPIRED
    if (remaining.isNegative) return ExpiryStatus.EXPIRED
    val total = Duration.ofDays(shelfLifeDays.toLong())
    val ratio = remaining.toMillis().toDouble() / total.toMillis()
    return when {
        ratio <= 1.0 / 6 -> ExpiryStatus.CRITICAL
        ratio <= 1.0 / 5 -> ExpiryStatus.WARNING
        ratio <= 1.0 / 3 -> ExpiryStatus.CAUTION
        else -> ExpiryStatus.SAFE
    }
}

/** "还有 3 天 17 小时到期"里的时间段，精确到半小时（向下取整）。 */
fun formatRemaining(remaining: Duration): String {
    val totalMinutes = remaining.toMinutes().coerceAtLeast(0)
    val days = totalMinutes / 1440
    val hours = totalMinutes % 1440 / 60
    val halfHour = totalMinutes % 60 >= 30
    val parts = buildList {
        if (days > 0) add("${days} 天")
        if (hours > 0) add("${hours} 小时")
        if (halfHour && days == 0L) add("30 分钟")
    }
    return if (parts.isEmpty()) "不足 30 分钟" else parts.joinToString(" ")
}

/** 已过期的时长文案："2 天" / "5 小时"。 */
fun formatExpired(overdue: Duration): String {
    val days = overdue.toDays()
    return if (days > 0) "$days 天" else "${overdue.toHours().coerceAtLeast(1)} 小时"
}
