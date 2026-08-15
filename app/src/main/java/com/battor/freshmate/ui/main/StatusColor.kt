package com.battor.freshmate.ui.main

import androidx.compose.ui.graphics.Color
import com.battor.freshmate.util.ExpiryStatus

/** 返回 (背景容器色, 前景色)。绿 → 黄 → 橙红 → 深红 → 已过期。 */
fun statusColors(status: ExpiryStatus): Pair<Color, Color> = when (status) {
    ExpiryStatus.SAFE -> Color(0xFFDCF5CE) to Color(0xFF274F1B)
    ExpiryStatus.CAUTION -> Color(0xFFFFF3BF) to Color(0xFF6B5A00)
    ExpiryStatus.WARNING -> Color(0xFFFFE0B2) to Color(0xFF8C4A00)
    ExpiryStatus.CRITICAL -> Color(0xFFFFCDD2) to Color(0xFF8E1418)
    ExpiryStatus.EXPIRED -> Color(0xFFC62828) to Color(0xFFFFFFFF)
}
