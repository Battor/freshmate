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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.battor.freshmate.R
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.ui.main.RestoreGreen
import com.battor.freshmate.ui.main.categoryIcon
import com.battor.freshmate.ui.main.expiryText
import com.battor.freshmate.ui.main.LocalStatusColors
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
                title = { Text(stringResource(R.string.history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.empty_history), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.groups.forEach { group ->
                    item(key = "del_${group.deletedAt}") {
                        DeletedGroupBox(group, state.now, viewModel::requestRestore)
                    }
                }
            }
        }
    }

    state.restoring?.let { item ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.restore_title)) },
            text = { Text(stringResource(R.string.restore_confirm, item.name)) },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text(stringResource(R.string.restore)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun DeletedGroupBox(
    group: DeletedGroup,
    now: LocalDateTime,
    onRequestRestore: (FoodItem) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.deleted_at, group.deletedAt.format(DeletedAtFormat)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            group.items.forEach { item ->
                HistoryItemCard(item, now, onRequestRestore)
            }
        }
    }
}

/**
 * 右滑（Start→End）露绿色还原背景。
 * confirmValueChange 返回 false：条目此刻仍在已删除列表（对话框待确认），
 * 让卡片立即回弹，由对话框驱动后续状态变化。
 */
@Composable
private fun HistoryItemCard(
    item: FoodItem,
    now: LocalDateTime,
    onRequestRestore: (FoodItem) -> Unit,
) {
    val expiry = remember(item) { expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays) }
    val (container, onColor) = LocalStatusColors.current.of(expiryStatus(expiry, now))

    val restoreLabel = stringResource(R.string.restore)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onRequestRestore(item)
            }
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        // 仅开放右滑还原，左滑不响应
        enableDismissFromEndToStart = false,
        enableDismissFromStartToEnd = true,
        // TalkBack 恒提供「还原」自定义动作（需求-3：随时可还原，无门禁）
        modifier = Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(restoreLabel) {
                    onRequestRestore(item)
                    true
                },
            )
        },
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(RestoreGreen, MaterialTheme.shapes.large)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Icon(Icons.Filled.Restore, contentDescription = restoreLabel, tint = Color.White) }
        },
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
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
                    contentDescription = stringResource(item.category.labelRes),
                    tint = onColor,
                    modifier = Modifier.size(28.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(item.name, color = onColor, style = MaterialTheme.typography.bodyLarge)
                }
                Text(expiryText(item, now), color = onColor, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
