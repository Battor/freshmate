package com.battor.freshmate.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.util.expiryDateTime
import com.battor.freshmate.util.expiryStatus
import com.battor.freshmate.util.formatExpired
import com.battor.freshmate.util.formatRemaining
import java.time.Duration
import java.time.LocalDateTime

@Composable
fun FoodItemCard(
    item: FoodItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    highlight: Boolean = false,
) {
    val now = remember { LocalDateTime.now() }
    val expiry = remember(item) { expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays) }
    val status = expiryStatus(expiry, item.shelfLifeDays, now)
    val (container, onColor) = statusColors(status)
    val remaining = Duration.between(now, expiry)
    val statusText = if (remaining.isNegative || remaining.isZero) {
        "已过期 ${formatExpired(remaining.negated())}"
    } else {
        "还有 ${formatRemaining(remaining)} 到期"
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        // TalkBack 用户无法滑动删除，提供自定义无障碍删除动作
        modifier = Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction("删除") {
                    onDelete()
                    true
                },
            )
        },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ExpiredRed, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color.White) }
        },
    ) {
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = container),
            // 「本次添加」批量条目用主色描边区分（状态色保留）
            border = if (highlight) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    categoryIcon(item.category),
                    contentDescription = item.category.label,
                    tint = onColor,
                    modifier = Modifier.size(28.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(item.name, color = onColor, fontSize = 16.sp)
                    item.quantity?.let { Text("数量：$it", color = onColor, fontSize = 12.sp) }
                }
                Text(statusText, color = onColor, fontSize = 13.sp)
            }
        }
    }
}
