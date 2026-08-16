package com.battor.freshmate.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.notification.ReminderIds
import com.battor.freshmate.ui.main.MainViewModel.EditingState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val GroupHeaderFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onOpenHistory: () -> Unit, onOpenSettings: () -> Unit) {
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
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("食刻 FreshMate") },
                actions = {
                    // 设置按钮 Task 11 加入；本任务只放历史入口，避免死按钮
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = "历史")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FabMenu(
                formOpen = state.isEditing,
                isAddForm = state.isAddForm,
                showSave = state.hasFormContent,
                onStartInput = { viewModel.startNew(it) },
                onSave = { viewModel.save() },
                onBack = { viewModel.backToMethodSelection() },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PermissionBanners()
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val editing = state.editing
                // 新组（表单目标组尚无条目）：顶部渲染仅含组头 + 表单的框，不重排现有组
                if (editing != null && state.items.none { groupKey(it.createdAt) == editing.createdAt }) {
                    item(key = "new_group_form") {
                        GroupBox(
                            createdAt = editing.createdAt,
                            items = emptyList(),
                            active = false,
                            form = editing,
                            editingItemId = null,
                            cardsEnabled = false,
                            cardsDimmed = false,
                            // 空表单点框内空白 = 放弃返回；有内容时不响应，防误触丢失
                            boxClick = if (state.hasFormContent) null else { { viewModel.backToMethodSelection() } },
                            onStartEdit = {},
                            onDeleteItem = {},
                            onStateChange = { newState -> viewModel.updateEditing { newState } },
                            onPlaceholderHint = { scope.launch { snackbarHostState.showSnackbar(it) } },
                        )
                    }
                }
                // 每组一个组容器；表单目标组在框内原位渲染表单
                state.groups.forEach { group ->
                    item(key = "group_${group.createdAt}") {
                        val formHost = editing != null && editing.createdAt == group.createdAt
                        GroupBox(
                            createdAt = group.createdAt,
                            items = group.items,
                            active = group.createdAt == state.activeGroup,
                            form = editing?.takeIf { it.createdAt == group.createdAt },
                            editingItemId = editing?.editingItemId,
                            cardsEnabled = editing == null,
                            // 编辑期间：表单所在组的子项保持原色（仅禁点），其它组变淡
                            cardsDimmed = editing != null && !formHost,
                            boxClick = when {
                                editing == null -> { { viewModel.startAddTo(group.createdAt) } }
                                formHost && !state.hasFormContent -> {
                                    { viewModel.backToMethodSelection() }
                                }
                                else -> null
                            },
                            onStartEdit = { viewModel.startEdit(it) },
                            onDeleteItem = { item ->
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
                            onStateChange = { newState -> viewModel.updateEditing { newState } },
                            onPlaceholderHint = { scope.launch { snackbarHostState.showSnackbar(it) } },
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

/**
 * 组容器（2026-08-16 组容器模型）：边框把组头与子条目框成一组。
 * - 普通：1dp 极浅描边；「本次添加」与正在编辑（表单挂本组）：2dp 主色描边。
 *   不加背景晕染——组内子项的状态色（绿/黄/橙/红）原样保留，编辑态的醒目
 *   由主色边框 + 框内 primaryContainer 表单卡片承担。
 * - 点击组头或框内空白区域（boxClick 非 null 时）：无表单 = 续加；本组空表单 = 放弃返回。
 * - 表单挂到本组时在框内渲染：编辑 = 被编辑条目原位置替换为表单（其余子项不动）；
 *   新增 = 渲染在组尾。列表次序始终不变。
 */
@Composable
private fun GroupBox(
    createdAt: LocalDateTime,
    items: List<FoodItem>,
    active: Boolean,
    form: EditingState?,
    editingItemId: Long?,
    cardsEnabled: Boolean,
    cardsDimmed: Boolean,
    boxClick: (() -> Unit)?,
    onStartEdit: (FoodItem) -> Unit,
    onDeleteItem: (FoodItem) -> Unit,
    onStateChange: (EditingState) -> Unit,
    onPlaceholderHint: (String) -> Unit,
) {
    val border = if (active || form != null) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
    // 整框可点（组头 + 空白区域）；去掉波纹避免干扰框内卡片与表单
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = boxClick != null,
            ) { boxClick?.invoke() },
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    createdAt.format(GroupHeaderFormat),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (active) {
                    Text(
                        " 本次添加",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            items.forEach { item ->
                if (item.id == editingItemId) {
                    // 编辑：表单渲染在被编辑条目的原位置，其余子项不动
                    form?.let { GroupForm(it, onStateChange, onPlaceholderHint) }
                } else {
                    FoodItemCard(
                        item = item,
                        onClick = { onStartEdit(item) },
                        onDelete = { onDeleteItem(item) },
                        enabled = cardsEnabled,
                        dimmed = cardsDimmed,
                    )
                }
            }
            // 新增（点组续加 / + 菜单）：表单渲染在组尾
            if (form != null && editingItemId == null) {
                GroupForm(form, onStateChange, onPlaceholderHint)
            }
        }
    }
}

@Composable
private fun GroupForm(
    form: EditingState,
    onStateChange: (EditingState) -> Unit,
    onPlaceholderHint: (String) -> Unit,
) {
    ItemForm(
        state = form,
        onStateChange = onStateChange,
        onPlaceholderHint = onPlaceholderHint,
    )
}
