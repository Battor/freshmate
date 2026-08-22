package com.battor.freshmate.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.battor.freshmate.R
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.ui.common.UiText
import com.battor.freshmate.ui.common.asString
import com.battor.freshmate.util.ExpiryStatus
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 桶头进度条宽度 = 组头文字宽度 × 2（需求-5 走查反馈：1.5 基础上再加宽）。 */
private const val BUCKET_BAR_WIDTH_SCALE = 2f

/** 桶头进度条轨道与条纹底色的透明度（同色淡底）。 */
private const val BUCKET_BAR_TRACK_ALPHA = 0.3f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    updateHint: UiText? = null,
    onUpdateHintShown: () -> Unit = {},
    onOpenUpdate: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorEvent by viewModel.errorEvent.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val hintText = updateHint?.asString()
    val viewLabel = stringResource(R.string.view)
    val errorText = errorEvent?.asString()
    LaunchedEffect(errorEvent) {
        errorText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    // 启动静默检查发现新版：Snackbar 一条 + 「查看」跳设置页（一次性，展示即清）
    LaunchedEffect(updateHint) {
        if (hintText == null) return@LaunchedEffect
        try {
            val result = snackbarHostState.showSnackbar(hintText, actionLabel = viewLabel)
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

    // 回到前台刷新页面时刻：条目跨档（如滑入"已过期"）后桶及时迁移
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshNow()
        }
    }

    // 删除确认（需求-5 走查反馈）：软删除虽可撤销，仍先弹框防误触
    var pendingDelete by remember { mutableStateOf<FoodItem?>(null) }
    val onDeleteItem: (FoodItem) -> Unit = { item -> pendingDelete = item }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = stringResource(R.string.history))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            PermissionBanners()
            // 400ms 短暂亮灯给「散入各桶」一个可见反馈，不做假加载
            var refreshing by remember { mutableStateOf(false) }
            val editing = state.editing
            if (editing != null) {
                // 表单打开：整页一块滚动（需求-5 走查反馈）——表单 + 列表内容直接铺开，
                // 滚过表单即见列表。列表用非 lazy 直铺：个人食材规模，
                // 且 LazyColumn 嵌 verticalScroll 会因无限高约束崩溃。
                // 编辑期间隐藏正在编辑的条目（已是表单内虚线框）；空桶/空置顶区整体不渲染
                val editingId = editing.editingItemId
                val visiblePinned = state.pinnedItems.filterNot { it.id == editingId }
                val visibleBuckets = state.buckets
                    .map { it.copy(items = it.items.filterNot { item -> item.id == editingId }) }
                    .filter { it.items.isNotEmpty() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ItemForm(
                        state = editing,
                        onStateChange = { newState -> viewModel.updateEditing { newState } },
                        onPlaceholderHint = { scope.launch { snackbarHostState.showSnackbar(it) } },
                        // 当前操作项目融入表单（需求-5 走查反馈）：表单内虚线框同底色块
                        editingTarget = state.editingTarget,
                        now = state.now,
                    )
                    // 本次添加置顶区（编辑期间卡片禁交互、无散入动作）
                    if (visiblePinned.isNotEmpty()) {
                        BucketBox(
                            header = stringResource(R.string.pinned_header),
                            status = null,
                            items = visiblePinned,
                            now = state.now,
                            cardsEnabled = false,
                            onHeaderAction = null,
                            onStartEdit = { viewModel.startEdit(it) },
                            onDeleteItem = onDeleteItem,
                        )
                    }
                    // 六个过期时间桶，空桶不渲染
                    visibleBuckets.forEach { bucket ->
                        BucketBox(
                            header = stringResource(bucket.status.labelRes),
                            status = bucket.status,
                            items = bucket.items,
                            now = state.now,
                            cardsEnabled = false,
                            onStartEdit = { viewModel.startEdit(it) },
                            onDeleteItem = onDeleteItem,
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // 本次添加置顶区（会话内存态；下拉刷新/冷启动散入各桶）
                            if (state.pinnedItems.isNotEmpty()) {
                                item(key = "pinned") {
                                    BucketBox(
                                        header = stringResource(R.string.pinned_header),
                                        status = null,
                                        items = state.pinnedItems,
                                        now = state.now,
                                        cardsEnabled = true,
                                        onHeaderAction = { viewModel.disperseSession() },
                                        onStartEdit = { viewModel.startEdit(it) },
                                        onDeleteItem = onDeleteItem,
                                    )
                                }
                            }
                            // 六个过期时间桶，空桶不渲染；桶头/组空白不可点击
                            state.buckets.forEach { bucket ->
                                item(key = "bucket_${bucket.status}") {
                                    BucketBox(
                                        header = stringResource(bucket.status.labelRes),
                                        status = bucket.status,
                                        items = bucket.items,
                                        now = state.now,
                                        cardsEnabled = true,
                                        onStartEdit = { viewModel.startEdit(it) },
                                        onDeleteItem = onDeleteItem,
                                    )
                                }
                            }
                            if (state.items.isEmpty()) {
                                item(key = "empty") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(stringResource(R.string.empty_list), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
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
            title = { Text(stringResource(R.string.near_expiry_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.near_expiry_text,
                        pending.editing.name,
                        pending.skippedReminders,
                        pending.remainingReminders,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmPendingSave() }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelPendingSave() }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_text, item.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        viewModel.delete(item)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                context.getString(R.string.deleted_snackbar, item.name),
                                actionLabel = context.getString(R.string.undo),
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.undoDelete(item)
                            }
                        }
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/**
 * 桶头进度条（需求-5 走查反馈第 4 轮）：药丸形轨道 + 状态容器色斜条纹填充段。
 * LinearProgressIndicator 不支持条纹，自绘 Canvas；无语义节点（避免 TalkBack 朗读
 * 对「窗口刻度」无意义的百分比，组头语义由右侧文字承载）。
 */
@Composable
private fun BucketProgressBar(
    progress: Float,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.height(6.dp)) {
        val corner = CornerRadius(size.height / 2f)
        drawRoundRect(color = fillColor.copy(alpha = BUCKET_BAR_TRACK_ALPHA), cornerRadius = corner)
        val fillWidth = size.width * progress.coerceIn(0f, 1f)
        if (fillWidth <= 0f) return@Canvas
        // 填充段 = 同色淡底 + 45° 实色斜纹：同色两调，深浅主题都成立
        clipPath(
            Path().apply {
                addRoundRect(RoundRect(rect = Rect(0f, 0f, fillWidth, size.height), cornerRadius = corner))
            },
        ) {
            drawRect(fillColor.copy(alpha = BUCKET_BAR_TRACK_ALPHA))
            val stripeWidth = 3.5.dp.toPx()
            val period = 7.dp.toPx()
            var x = -size.height
            while (x < fillWidth + size.height) {
                drawLine(
                    color = fillColor,
                    start = Offset(x, size.height),
                    end = Offset(x + size.height, 0f),
                    strokeWidth = stripeWidth,
                )
                x += period
            }
        }
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
    // semantics 块非 Composable 上下文，提前解析
    val disperseLabel = stringResource(R.string.disperse_into_buckets)
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
            if (status != null) {
                val statusColors = LocalStatusColors.current.of(status)
                // 进度条宽度 = 组头文字实际宽度 × 2（需求-5 走查反馈，精确测量非估算）
                val textMeasurer = rememberTextMeasurer()
                val textStyle = MaterialTheme.typography.labelLarge
                val textWidth = remember(header, textStyle) {
                    textMeasurer.measure(header, textStyle).size.width
                }
                val barWidth = with(LocalDensity.current) { textWidth.toDp() * BUCKET_BAR_WIDTH_SCALE }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // 桶级紧急度：填充段 = 状态容器色（与组内卡片同色）斜条纹；
                    // 条纹纹理的明暗差弥补粉彩容器色叠浅底的低对比（无需再借深色 on）
                    BucketProgressBar(
                        progress = status.windowProgress,
                        fillColor = statusColors.container,
                        modifier = Modifier.width(barWidth),
                    )
                    // 文字右对齐到组右缘（需求-5 走查反馈）
                    Spacer(Modifier.weight(1f))
                    Text(header, style = textStyle, color = statusColors.on)
                }
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
                                    CustomAccessibilityAction(disperseLabel) { it(); true },
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
