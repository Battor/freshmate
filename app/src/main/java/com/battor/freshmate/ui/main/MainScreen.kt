package com.battor.freshmate.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.util.ExpiryStatus
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    updateHint: String? = null,
    onUpdateHintShown: () -> Unit = {},
    onOpenUpdate: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorEvent by viewModel.errorEvent.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(errorEvent) {
        errorEvent?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    // 启动静默检查发现新版：Snackbar 一条 + 「查看」跳设置页（一次性，展示即清）
    LaunchedEffect(updateHint) {
        if (updateHint == null) return@LaunchedEffect
        try {
            val result = snackbarHostState.showSnackbar(updateHint, actionLabel = "查看")
            onUpdateHintShown()
            if (result == SnackbarResult.ActionPerformed) onOpenUpdate()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 展示中途离开本页（Snackbar 协程被取消）：也清掉一次性提示，防回来重复弹
            onUpdateHintShown()
            throw e
        }
    }

    NotificationPermissionEffect(
        request = state.requestNotificationPermission,
        onHandled = { viewModel.onPermissionRequested() },
    )

    // 表单打开滚到顶部；关闭（保存/放弃）回到打开前的位置
    var restoreIndex by remember { mutableStateOf(0) }
    LaunchedEffect(state.editing != null) {
        if (state.editing != null) {
            restoreIndex = listState.firstVisibleItemIndex
            listState.animateScrollToItem(0)
        } else {
            listState.scrollToItem(restoreIndex)
        }
    }

    // 回到前台刷新页面时刻：条目跨档（如滑入"已过期"）后桶及时迁移
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshNow()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("食刻 FreshMate") },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = "历史")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
            )
        },
        floatingActionButton = {
            FabMenu(
                formOpen = state.isEditing,
                isAddForm = state.isAddForm,
                showSave = state.hasFormContent,
                onStartInput = { viewModel.startNew(it) },
                onSave = { viewModel.save() },
                onBack = { viewModel.backToMethodSelection() },
                // edge-to-edge 下键盘弹出时 FAB 随 IME 抬升，不被遮挡；
                // exclude navigationBars：Scaffold 已消费的导航栏 inset 不重复计入
                modifier = Modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            PermissionBanners()
            // 400ms 短暂亮灯给「散入各桶」一个可见反馈，不做假加载
            var refreshing by remember { mutableStateOf(false) }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    viewModel.disperseSession()
                    scope.launch {
                        delay(400)
                        refreshing = false
                    }
                },
                state = rememberPullToRefreshState(),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 表单固定渲染在列表顶部（需求-3：组不再承载表单）
                    state.editing?.let { editing ->
                        item(key = "form") {
                            ItemForm(
                                state = editing,
                                onStateChange = { newState -> viewModel.updateEditing { newState } },
                                onPlaceholderHint = { scope.launch { snackbarHostState.showSnackbar(it) } },
                            )
                        }
                    }
                    // 本次添加置顶区（会话内存态；下拉刷新/冷启动散入各桶）
                    if (state.pinnedItems.isNotEmpty()) {
                        item(key = "pinned") {
                            BucketBox(
                                header = "本次添加",
                                status = null,
                                items = state.pinnedItems,
                                now = state.now,
                                cardsEnabled = state.editing == null,
                                onHeaderAction = { viewModel.disperseSession() },
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
                            )
                        }
                    }
                    // 六个过期时间桶，空桶不渲染；桶头/组空白不可点击
                    state.buckets.forEach { bucket ->
                        item(key = "bucket_${bucket.status}") {
                            BucketBox(
                                header = bucket.status.label,
                                status = bucket.status,
                                items = bucket.items,
                                now = state.now,
                                cardsEnabled = state.editing == null,
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
                            )
                        }
                    }
                    if (state.items.isEmpty() && state.editing == null) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("暂无食品，点 + 添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
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
                        "剩余提醒时点 ${pending.remainingReminders} 个。确认保存吗？",
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
 * 桶容器（需求-3）：浅边框 + 状态色桶头 + 子条目卡片。
 * status = null 表示「本次添加」置顶区（主色 2dp 边框、组头用主色文本）。
 * 点卡片 = 编辑；组头与组空白不可点击（桶按过期时间聚合，无组级交互）。
 */
@Composable
private fun BucketBox(
    header: String,
    status: ExpiryStatus?,
    items: List<FoodItem>,
    now: LocalDateTime,
    cardsEnabled: Boolean,
    onHeaderAction: (() -> Unit)? = null,
    onStartEdit: (FoodItem) -> Unit,
    onDeleteItem: (FoodItem) -> Unit,
) {
    val border = if (status == null) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = border,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val statusColors = status?.let { LocalStatusColors.current.of(it) }
            if (statusColors != null) {
                Text(
                    header,
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColors.on,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(statusColors.container)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            } else {
                // 「本次添加」组头：浅主色背景 chip 让散入动作可发现；TalkBack 提供自定义动作
                Text(
                    header,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .semantics {
                            onHeaderAction?.let {
                                customActions = listOf(
                                    CustomAccessibilityAction("散入各桶") { it(); true },
                                )
                            }
                        },
                )
            }
            items.forEach { item ->
                FoodItemCard(
                    item = item,
                    now = now,
                    onClick = { onStartEdit(item) },
                    onDelete = { onDeleteItem(item) },
                    enabled = cardsEnabled,
                )
            }
        }
    }
}
