package com.battor.freshmate.util

import android.content.res.Resources
import androidx.annotation.StringRes
import com.battor.freshmate.R
import java.time.Duration
import java.time.LocalDateTime

/**
 * 条目紧急度（需求-3 改为绝对时间六档，与主列表分桶同阈值），值越靠后越宽松。
 * 红(EXPIRED) → DUE_1D → DUE_3D → DUE_7D → DUE_14D → 绿(SAFE)。
 * 原桶头进度条刻度（progressWindowDays / windowProgress）已随信息下沉到卡片余量
 * 色层（specs/2026-09-26）而移除，枚举现在只承载状态标签资源。
 */
enum class ExpiryStatus(
    @StringRes val labelRes: Int,
) {
    EXPIRED(R.string.status_expired),
    DUE_1D(R.string.status_due_1d),
    DUE_3D(R.string.status_due_3d),
    DUE_7D(R.string.status_due_7d),
    DUE_14D(R.string.status_due_14d),
    SAFE(R.string.status_safe),
    ;
}

fun expiryStatus(expiry: LocalDateTime, now: LocalDateTime): ExpiryStatus {
    val remaining = Duration.between(now, expiry)
    if (remaining <= Duration.ZERO) return ExpiryStatus.EXPIRED
    return when {
        remaining <= Duration.ofDays(1) -> ExpiryStatus.DUE_1D
        remaining <= Duration.ofDays(3) -> ExpiryStatus.DUE_3D
        remaining <= Duration.ofDays(7) -> ExpiryStatus.DUE_7D
        remaining <= Duration.ofDays(14) -> ExpiryStatus.DUE_14D
        else -> ExpiryStatus.SAFE
    }
}

/** "还有 3 天 17 小时到期"里的时间段，精确到半小时（向下取整），按当前语言输出。 */
fun formatRemaining(res: Resources, remaining: Duration): String {
    val totalMinutes = remaining.toMinutes().coerceAtLeast(0)
    val days = totalMinutes / 1440
    val hours = totalMinutes % 1440 / 60
    val halfHour = totalMinutes % 60 >= 30
    val parts = buildList {
        if (days > 0) add(res.getString(R.string.duration_days, days))
        if (hours > 0) add(res.getString(R.string.duration_hours, hours))
        if (halfHour && days == 0L) add(res.getString(R.string.duration_half_hour))
    }
    return if (parts.isEmpty()) res.getString(R.string.duration_under_half_hour) else parts.joinToString(" ")
}

/**
 * 只显示完整天数（"更久到期"桶专用，走查反馈：超过 14 天不显示小时）。
 * 向下取整——14 天 1 小时说成 14 天：不夸大剩余，与原"天 + 小时"的天数部分一致。
 */
fun formatRemainingDaysOnly(res: Resources, remaining: Duration): String =
    res.getString(R.string.duration_days, remaining.toMinutes().coerceAtLeast(0) / 1440)

/** 已过期的时长文案："2 天" / "5 小时"，按当前语言输出。 */
fun formatExpired(res: Resources, overdue: Duration): String {
    val days = overdue.toDays()
    return if (days > 0) {
        res.getString(R.string.duration_days, days)
    } else {
        res.getString(R.string.duration_hours, overdue.toHours().coerceAtLeast(1))
    }
}
