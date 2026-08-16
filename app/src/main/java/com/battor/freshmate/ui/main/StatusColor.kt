package com.battor.freshmate.ui.main

import androidx.compose.ui.graphics.Color
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.util.ExpiryStatus
import com.battor.freshmate.util.expiryDateTime
import com.battor.freshmate.util.formatExpired
import com.battor.freshmate.util.formatRemaining
import java.time.Duration
import java.time.LocalDateTime

/** 已过期状态与滑动删除背景共用的深红色。 */
val ExpiredRed = Color(0xFFC62828)

/** 历史页右滑还原背景（镜像主列表删除的 ExpiredRed）。 */
val RestoreGreen = Color(0xFF3EB04A)

/** 条目右侧状态文本：已过期 x / 还有 x 到期。 */
fun expiryText(item: FoodItem, now: LocalDateTime): String {
    val expiry = expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
    val remaining = Duration.between(now, expiry)
    return if (remaining.isNegative || remaining.isZero) {
        "已过期 ${formatExpired(remaining.negated())}"
    } else {
        "还有 ${formatRemaining(remaining)} 到期"
    }
}

/** 返回 (背景容器色, 前景色)。绿 → 黄 → 橙红 → 深红 → 已过期。 */
fun statusColors(status: ExpiryStatus): Pair<Color, Color> = when (status) {
    ExpiryStatus.SAFE -> Color(0xFFDCF5CE) to Color(0xFF274F1B)
    ExpiryStatus.CAUTION -> Color(0xFFFFF3BF) to Color(0xFF6B5A00)
    ExpiryStatus.WARNING -> Color(0xFFFFE0B2) to Color(0xFF8C4A00)
    ExpiryStatus.CRITICAL -> Color(0xFFFFCDD2) to Color(0xFF8E1418)
    ExpiryStatus.EXPIRED -> ExpiredRed to Color(0xFFFFFFFF)
}
