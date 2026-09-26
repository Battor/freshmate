package com.battor.freshmate.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.battor.freshmate.R
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.util.ExpiryStatus
import com.battor.freshmate.util.expiryDateTime
import com.battor.freshmate.util.formatExpired
import com.battor.freshmate.util.formatRemaining
import com.battor.freshmate.util.formatRemainingDaysOnly
import java.time.Duration
import java.time.LocalDateTime

/** 已过期状态与滑动删除背景共用的深红色。 */
val ExpiredRed = Color(0xFFC62828)

/** 历史页右滑还原背景（镜像主列表删除的 ExpiredRed）。 */
val RestoreGreen = Color(0xFF3EB04A)

/** 一档状态色：(容器色, 前景色)。accent：中性底上的强调色（默认取 on；EXPIRED 档 on 是白色需覆盖）。 */
data class StatusColors(
    val container: Color,
    val on: Color,
    val accent: Color = on,
)

/** 六档色板（红→绿），浅色用粉彩容器色，深色用低饱和容器 + 浅前景。 */
data class StatusPalette(
    val expired: StatusColors,
    val due1d: StatusColors,
    val due3d: StatusColors,
    val due7d: StatusColors,
    val due14d: StatusColors,
    val safe: StatusColors,
) {
    fun of(status: ExpiryStatus): StatusColors = when (status) {
        ExpiryStatus.EXPIRED -> expired
        ExpiryStatus.DUE_1D -> due1d
        ExpiryStatus.DUE_3D -> due3d
        ExpiryStatus.DUE_7D -> due7d
        ExpiryStatus.DUE_14D -> due14d
        ExpiryStatus.SAFE -> safe
    }
}

val LightStatusPalette = StatusPalette(
    // accent：容器深红——浅色中性底上白色 on 不可见（需求-6 走查点子：图标染状态色）
    expired = StatusColors(ExpiredRed, Color(0xFFFFFFFF), ExpiredRed),
    due1d = StatusColors(Color(0xFFFFCDD2), Color(0xFF8E1418)),
    due3d = StatusColors(Color(0xFFFFE0B2), Color(0xFF8C4A00)),
    due7d = StatusColors(Color(0xFFFFF3BF), Color(0xFF6B5A00)),
    due14d = StatusColors(Color(0xFFEDF5C0), Color(0xFF3F5327)),
    safe = StatusColors(Color(0xFFDCF5CE), Color(0xFF274F1B)),
)

val DarkStatusPalette = StatusPalette(
    // accent：浅红——深灰中性底上白色无色彩信号、深红容器对比不足
    expired = StatusColors(Color(0xFFB71C1C), Color(0xFFFFFFFF), Color(0xFFEF9A9A)),
    due1d = StatusColors(Color(0xFF5D2A30), Color(0xFFF6C4C8)),
    due3d = StatusColors(Color(0xFF54462E), Color(0xFFFCD9A6)),
    due7d = StatusColors(Color(0xFF565030), Color(0xFFF0E8A0)),
    due14d = StatusColors(Color(0xFF424D2B), Color(0xFFDCE8B0)),
    safe = StatusColors(Color(0xFF2F4A26), Color(0xFFC8E8B8)),
)

/** 由 Theme.kt 随深浅色提供；卡片/桶头/历史页统一读取。 */
val LocalStatusColors = staticCompositionLocalOf { LightStatusPalette }

/** 条目右侧状态文本：已过期 x / 还有 x 到期（按当前语言）。 */
@Composable
fun expiryText(item: FoodItem, now: LocalDateTime): String =
    expiryText(expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays), now)

/** 按到期时刻的重载：表单「当前操作项目」实时预览用（需求-5 走查反馈）。 */
@Composable
fun expiryText(expiry: LocalDateTime, now: LocalDateTime, daysOnly: Boolean = false): String {
    val remaining = Duration.between(now, expiry)
    val expired = remaining.isNegative || remaining.isZero
    val duration = when {
        expired -> formatExpired(LocalContext.current.resources, remaining.negated())
        daysOnly -> formatRemainingDaysOnly(LocalContext.current.resources, remaining)
        else -> formatRemaining(LocalContext.current.resources, remaining)
    }
    return if (expired) {
        stringResource(R.string.card_status_expired, duration)
    } else {
        stringResource(R.string.card_status_remaining, duration)
    }
}
