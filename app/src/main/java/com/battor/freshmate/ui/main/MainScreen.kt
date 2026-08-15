package com.battor.freshmate.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.battor.freshmate.notification.ReminderIds
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val GroupHeaderFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorEvent by viewModel.errorEvent.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(errorEvent) {
        errorEvent?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    NotificationPermissionEffect(
        request = state.requestNotificationPermission,
        onHandled = { viewModel.onPermissionRequested() },
    )

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("食刻 FreshMate") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FabMenu(
                formOpen = state.isEditing,
                isBatchForm = state.isBatchForm,
                batchActive = state.batch != null,
                stagedCount = state.batchItems.size,
                onStartInput = { viewModel.startNew(it) },
                onSave = { viewModel.save() },
                onDiscard = { viewModel.discard() },
                onBackToSelection = { viewModel.backToMethodSelection() },
                onFinishBatch = { viewModel.finishBatch() },
            )
        },
    ) { padding ->
        val editId = state.editing?.editingItemId
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PermissionBanners()
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.editing?.let { editing ->
                    item(key = "editing_form") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GroupHeader(editing.createdAt)
                            ItemForm(
                                state = editing,
                                onStateChange = { newState ->
                                    viewModel.updateEditing { newState }
                                },
                                onPlaceholderHint = { hint ->
                                    scope.launch { snackbarHostState.showSnackbar(hint) }
                                },
                            )
                        }
                    }
                }
                val batchItemIds = state.batchItems.map { it.id }.toSet()
                if (batchItemIds.isNotEmpty()) {
                    item(key = "batch_section_header") { BatchHeader(state.batchItems.size) }
                    items(state.batchItems, key = { it.id }) { item ->
                        FoodItemCard(
                            item = item,
                            onClick = { viewModel.startEdit(item) },
                            onDelete = {
                                viewModel.delete(item)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        "已删除「${item.name}」",
                                        actionLabel = "撤销",
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoDelete(item)
                                    }
                                }
                            },
                            highlight = true,
                        )
                    }
                }
                // 先过滤被编辑条目和本次批量条目再渲染，避免编辑/批量时残留空组头
                val visibleGroups = state.groups
                    .map {
                        it.copy(
                            items = it.items.filterNot { item -> item.id == editId || item.id in batchItemIds },
                        )
                    }
                    .filter { it.items.isNotEmpty() }
                visibleGroups.forEach { group ->
                    item(key = "header_${group.createdAt}") {
                        GroupHeader(group.createdAt)
                    }
                    items(group.items, key = { it.id }) { item ->
                        FoodItemCard(
                            item = item,
                            onClick = { viewModel.startEdit(item) },
                            onDelete = {
                                viewModel.delete(item)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        "已删除「${item.name}」",
                                        actionLabel = "撤销",
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoDelete(item)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    state.pendingSave?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingSave() },
            title = { Text("该食品临近过期") },
            text = {
                Text(
                    "「${pending.editing.name}」已有 ${pending.skippedReminders} 个提醒时点过去，" +
                        "剩余提醒时点 ${ReminderIds.REMINDER_COUNT - pending.skippedReminders} 个。确认保存吗？",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmPendingSave() }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelPendingSave() }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun GroupHeader(createdAt: LocalDateTime) {
    Surface(color = Color(0xFF616161), shape = RoundedCornerShape(10.dp)) {
        Text(
            createdAt.format(GroupHeaderFormat),
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** 「本次添加」批量区块的标题，主色与灰色组头区分。 */
@Composable
private fun BatchHeader(count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            "本次添加 · $count 条",
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
