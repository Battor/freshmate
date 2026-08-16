package com.battor.freshmate.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.ui.main.RestoreGreen
import com.battor.freshmate.ui.main.categoryIcon
import com.battor.freshmate.ui.main.expiryText
import com.battor.freshmate.ui.main.statusColors
import com.battor.freshmate.util.expiryDateTime
import com.battor.freshmate.util.expiryStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DeletedAtFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无已删除条目", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.groups.forEach { group ->
                    item(key = "del_${group.deletedAt}") {
                        DeletedGroupBox(group, state::isRestorable, viewModel::requestRestore)
                    }
                }
            }
        }
    }

    state.restoring?.let { item ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("还原条目") },
            text = { Text("把「${item.name}」还原到原组吗？") },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text("还原") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("取消") } },
        )
    }
}

@Composable
private fun DeletedGroupBox(
    group: DeletedGroup,
    isRestorable: (FoodItem) -> Boolean,
    onRequestRestore: (FoodItem) -> Unit,
) {
    val restorableCount = group.items.count(isRestorable)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "删除于 ${group.deletedAt.format(DeletedAtFormat)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "  可还原 $restorableCount/${group.items.size}",
                    fontSize = 11.sp,
                    color = if (restorableCount > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            group.items.forEach { item ->
                HistoryItemCard(item, isRestorable(item), onRequestRestore)
            }
        }
    }
}

/**
 * 右滑（Start→End）露绿色还原背景；组不活跃时禁用滑动并整体变淡。
 * confirmValueChange 返回 false：条目此刻仍在已删除列表（对话框待确认），
 * 让卡片立即回弹，由对话框驱动后续状态变化。
 */
@Composable
private fun HistoryItemCard(
    item: FoodItem,
    restorable: Boolean,
    onRequestRestore: (FoodItem) -> Unit,
) {
    val now = remember { LocalDateTime.now() }
    val expiry = remember(item) { expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays) }
    val (container, onColor) = statusColors(expiryStatus(expiry, item.shelfLifeDays, now))

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (restorable && value == SwipeToDismissBoxValue.StartToEnd) {
                onRequestRestore(item)
            }
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        // 不可还原：两个方向都禁拖，不给误导性的绿色背景；可还原时仅开放右滑
        enableDismissFromEndToStart = false,
        enableDismissFromStartToEnd = restorable,
        // 组不活跃（不可还原）时整体变淡提示禁用；TalkBack 的「还原」动作同样仅可还原时提供
        modifier = Modifier
            .alpha(if (restorable) 1f else 0.4f)
            .then(
                if (restorable) {
                    Modifier.semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("还原") {
                                onRequestRestore(item)
                                true
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(RestoreGreen, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Icon(Icons.Filled.Restore, contentDescription = "还原", tint = Color.White) }
        },
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            // 钉同样的状态色：不活跃条目只变淡、不变 M3 禁用配色
            colors = CardDefaults.cardColors(
                containerColor = container, contentColor = onColor,
                disabledContainerColor = container, disabledContentColor = onColor,
            ),
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
                    if (!restorable) {
                        Text("原组已不存在", color = onColor, fontSize = 12.sp)
                    }
                }
                Text(expiryText(item, now), color = onColor, fontSize = 13.sp)
            }
        }
    }
}
