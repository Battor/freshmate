package com.battor.freshmate.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.battor.freshmate.R
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.ui.theme.categoryIconColor
import com.battor.freshmate.util.expiryDateTime
import com.battor.freshmate.util.expiryStatus
import com.battor.freshmate.util.remainingFraction
import java.time.LocalDateTime

/** 卡片余量色层（specs/2026-09-26）：油表方向，on 色 18% alpha，右缘 12% 卡宽渐隐。 */
private const val CARD_FILL_ALPHA = 0.18f
private const val CARD_FILL_FADE_FRACTION = 0.12f

@Composable
fun FoodItemCard(
    item: FoodItem,
    now: LocalDateTime,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean = true,
) {
    val expiry = remember(item) { expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays) }
    val (container, onColor) = LocalStatusColors.current.of(expiryStatus(expiry, now))
    val deleteLabel = stringResource(R.string.delete)

    // 防御：rememberSwipeToDismissBoxState 只在状态对象首建时捕获 confirmValueChange
    // 闭包（与历史页同款坑，见 HistoryScreen），经 rememberUpdatedState 取最新回调与门禁
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentEnabled by rememberUpdatedState(enabled)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            // 滑到位只触发确认弹框（需求-5 走查反馈），卡片回弹；
            // 真正的删除在用户确认后发生。表单打开期间同样不触发。
            if (currentEnabled && value == SwipeToDismissBoxValue.EndToStart) {
                currentOnDelete()
            }
            false
        },
    )

    val card: @Composable () -> Unit = {
        Card(
            onClick = onClick,
            enabled = enabled,
            shape = MaterialTheme.shapes.large,
            // 禁用态钉同样的状态色：编辑期间卡片只禁交互、不变色
            colors = CardDefaults.cardColors(
                containerColor = container,
                contentColor = onColor,
                disabledContainerColor = container,
                disabledContentColor = onColor,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 余量色层：剩余越多覆盖越长（油表）；过期 fraction=0 无色层。on 色低 alpha 在
            // 浅色主题呈加深、深色主题呈提亮，文字对比度不受影响
            val fillFraction = remainingFraction(item.productionDate, item.createdAt, item.shelfLifeDays, now)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        if (fillFraction > 0f) {
                            val fillWidth = size.width * fillFraction
                            val fade = size.width * CARD_FILL_FADE_FRACTION
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to onColor.copy(alpha = CARD_FILL_ALPHA),
                                    ((fillWidth - fade) / fillWidth).coerceIn(0f, 1f) to
                                        onColor.copy(alpha = CARD_FILL_ALPHA),
                                    1f to Color.Transparent,
                                ),
                                size = Size(fillWidth, size.height),
                            )
                        }
                    },
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                Icon(
                    categoryIcon(item.category),
                    contentDescription = stringResource(item.category.labelRes),
                    tint = categoryIconColor(item.category),
                    modifier = Modifier.size(28.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(item.name, color = onColor, style = MaterialTheme.typography.bodyLarge)
                    item.quantity?.let {
                        Text(
                            stringResource(R.string.quantity_label, it),
                            color = onColor,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                    Text(expiryText(item, now), color = onColor, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (enabled) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            modifier = Modifier
                .semantics {
                // TalkBack 自定义删除动作与滑动手势同受 enabled 门禁：表单打开期间不暴露
                customActions = listOf(
                    CustomAccessibilityAction(deleteLabel) {
                        onDelete()
                        true
                    },
                )
            },
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 删除背景用色板已过期档容器色：深色主题下同步变深
                        .background(
                            LocalStatusColors.current.expired.container,
                            MaterialTheme.shapes.large,
                        )
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) { Icon(Icons.Filled.Delete, contentDescription = deleteLabel, tint = Color.White) }
            },
        ) { card() }
    } else {
        // 禁用态直接渲染卡片：SwipeToDismissBox 的拖动手势不受 enabled 门禁，会拖出再回弹
        card()
    }
}
