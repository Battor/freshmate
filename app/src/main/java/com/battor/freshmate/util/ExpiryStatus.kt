package com.battor.freshmate.util

import java.time.Duration
import java.time.LocalDateTime

/**
 * 条目紧急度（需求-3 改为绝对时间六档，与主列表分桶同阈值），值越靠后越宽松。
 * 红(EXPIRED) → DUE_1D → DUE_3D → DUE_7D → DUE_14D → 绿(SAFE)。
 */
enum class ExpiryStatus(val label: String) {
    EXPIRED("已过期"),
    DUE_1D("1 天内到期"),
    DUE_3D("3 天内到期"),
    DUE_7D("7 天内到期"),
    DUE_14D("14 天内到期"),
    SAFE("更久到期"),
}

fun expiryStatus(expiry: LocalDateTime, now: LocalDateTime): ExpiryStatus {
    val remaining = Duration.between(now, expiry)
    if (!remaining.isNegative && remaining.isZero) return ExpiryStatus.EXPIRED
    if (remaining.isNegative) return ExpiryStatus.EXPIRED
    return when {
        remaining <= Duration.ofDays(1) -> ExpiryStatus.DUE_1D
        remaining <= Duration.ofDays(3) -> ExpiryStatus.DUE_3D
        remaining <= Duration.ofDays(7) -> ExpiryStatus.DUE_7D
        remaining <= Duration.ofDays(14) -> ExpiryStatus.DUE_14D
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
