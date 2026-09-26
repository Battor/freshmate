package com.battor.freshmate.ui.main

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.battor.freshmate.R
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.inputmethod.InputMethodId
import com.battor.freshmate.ui.common.GuideKeys
import com.battor.freshmate.ui.common.OneShotSnackbar
import com.battor.freshmate.ui.common.UiText
import com.battor.freshmate.ui.common.asString
import com.battor.freshmate.ui.common.guideTarget
import com.battor.freshmate.util.ExpiryStatus
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MainContent 全部交互回调：主流程用匿名对象适配 ViewModel 与导航（编译器强制覆写全量，
 * 漏接即编译错误）；引导页用 [NoopMainActions]。
 * 方法名不带 on 前缀——避免与 MainScreen 的同名 lambda 参数在适配对象里递归遮蔽。
 */
interface MainActions {
    fun updateHintShown()
    fun openUpdate()
    fun openHistory()
    fun openSettings()
    fun startEdit(item: FoodItem)
    fun delete(item: FoodItem)
    fun undoDelete(item: FoodItem)
    fun disperse()
    fun startNew(method: InputMethodId)
    fun save()
    fun saveAndExit()
    fun backToMethodSelection()
    fun updateEditing(transform: (MainViewModel.EditingState) -> MainViewModel.EditingState)
    fun permissionRequested()
    fun errorShown()
    fun refreshNow()
    fun confirmPendingSave()
    fun cancelPendingSave()
}

/** 引导页用：纯展示「只看不摸」，空实现显式写全——换来主流程适配器漏接时的编译错误。 */
object NoopMainActions : MainActions {
    override fun updateHintShown() {}
    override fun openUpdate() {}
    override fun openHistory() {}
    override fun openSettings() {}
    override fun startEdit(item: FoodItem) {}
    override fun delete(item: FoodItem) {}
    override fun undoDelete(item: FoodItem) {}
    override fun disperse() {}
    override fun startNew(method: InputMethodId) {}
    override fun save() {}
    override fun saveAndExit() {}
    override fun backToMethodSelection() {}
    override fun updateEditing(transform: (MainViewModel.EditingState) -> MainViewModel.EditingState) {}
    override fun permissionRequested() {}
    override fun errorShown() {}
    override fun refreshNow() {}
    override fun confirmPendingSave() {}
    override fun cancelPendingSave() {}
}

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
    MainContent(
        state = state,
        errorEvent = errorEvent,
        updateHint = updateHint,
        actions = object : MainActions {
            override fun updateHintShown() = onUpdateHintShown()
            override fun openUpdate() = onOpenUpdate()
            override fun openHistory() = onOpenHistory()
            override fun openSettings() = onOpenSettings()
            override fun startEdit(item: FoodItem) = viewModel.startEdit(item)
            override fun delete(item: FoodItem) = viewModel.delete(item)
            override fun undoDelete(item: FoodItem) = viewModel.undoDelete(item)
            override fun disperse() = viewModel.disperseSession()
            override fun startNew(method: InputMethodId) = viewModel.startNew(method)
            override fun save() = viewModel.save()
            override fun saveAndExit() = viewModel.saveAndExit()
            override fun backToMethodSelection() = viewModel.backToMethodSelection()
            override fun updateEditing(transform: (MainViewModel.EditingState) -> MainViewModel.EditingState) =
                viewModel.updateEditing(transform)
            override fun permissionRequested() = viewModel.onPermissionRequested()
            override fun errorShown() = viewModel.onErrorShown()
            override fun refreshNow() = viewModel.refreshNow()
            override fun confirmPendingSave() = viewModel.confirmPendingSave()
            override fun cancelPendingSave() = viewModel.cancelPendingSave()
        },
    )
}

/**
 * 主页完整界面（Scaffold + 列表/表单 + 全部对话框）。
 * 从 MainScreen 抽出的纯展示层：不依赖 MainViewModel，全部交互走 [MainActions]——
 * 引导页（ui/guide）用 NoopMainActions + mock state 复用同一套界面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainContent(
    state: MainViewModel.UiState,
    errorEvent: UiText?,
    updateHint: UiText?,
    actions: MainActions,
    /** 引导模式聚光「第一张卡片」「第一个桶」：透传给列表分支第一个桶，主流程为 null 零影响 */
    guideFirstCardKey: String? = null,
    guideFirstBucketKey: String? = null,
    /** 引导页复用 MainContent 时置 false：系统返回拦截（退出编辑确认/双击退出）让位给
     *  GuideScreen 自己的 BackHandler——Compose 返回栈后组合者优先，不关会被反超。 */
    handleSystemBack: Boolean = true,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    OneShotSnackbar(
        message = errorEvent,
        snackbarHostState = snackbarHostState,
        onShown = actions::errorShown,
    )
    // 启动静默检查发现新版：Snackbar 一条 + 「查看」跳设置页（一次性，展示即清）
    OneShotSnackbar(
        message = updateHint,
        snackbarHostState = snackbarHostState,
        onShown = actions::updateHintShown,
        actionLabel = stringResource(R.string.view),
        onAction = actions::openUpdate,
    )

    NotificationPermissionEffect(
        request = state.requestNotificationPermission,
        onHandled = actions::permissionRequested,
    )

    // 回到前台刷新页面时刻：条目跨档（如滑入"已过期"）后桶及时迁移
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            actions.refreshNow()
        }
    }

    // 删除确认（需求-5 走查反馈）：软删除虽可撤销，仍先弹框防误触
    var pendingDelete by remember { mutableStateOf<FoodItem?>(null) }
    val onDeleteItem: (FoodItem) -> Unit = { item -> pendingDelete = item }

    // ← / 系统返回退出编辑（走查需求）：有未保存内容先弹「保存并退出」确认，无内容直接退
    var pendingDiscard by remember { mutableStateOf(false) }
    val requestExitEdit = {
        if (state.hasFormContent) pendingDiscard = true else actions.backToMethodSelection()
    }

    if (handleSystemBack) {
        // 编辑/新增态：系统返回与 ← 顶栏按钮同一逻辑（含未保存确认）
        BackHandler(enabled = state.isEditing, onBack = requestExitEdit)
        // 主页根（非编辑态）：2 秒内连按两次返回才真正退出 app
        var lastBackAt by remember { mutableStateOf(0L) }
        BackHandler(enabled = !state.isEditing) {
            val pressedAt = System.currentTimeMillis()
            if (pressedAt - lastBackAt < 2_000L) {
                (context as? Activity)?.finish()
            } else {
                lastBackAt = pressedAt
                Toast.makeText(context, context.getString(R.string.press_back_again), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 编辑态当前吸附段（提升到 Scaffold 外：顶栏与 pager 内容都要读写）。
    // key(isEditing)：每次进出编辑态回落 FORM，避免上次停留段的旧值在 pager 重建前
    // 先渲染一帧错误顶栏
    var editPage by remember(state.isEditing) { mutableStateOf(EditPageKind.FORM) }

    Scaffold(
        topBar = {
            // 需求-7 走查：编辑态滑到「已添加」段时顶栏回到默认样式（应用名 + 历史/设置），
            // 本次操作/编辑区两段仍是编辑操作栏
            if (state.isEditing && editPage != EditPageKind.EXISTING) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(
                                if (state.isAddForm) R.string.edit_title_add else R.string.edit_title_edit,
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = requestExitEdit) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.discard_back),
                            )
                        }
                    },
                    actions = {
                        if (state.hasFormContent) {
                            IconButton(onClick = actions::save) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = stringResource(
                                        if (state.isAddForm) R.string.stash_and_continue else R.string.save,
                                    ),
                                )
                            }
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_title)) },
                    actions = {
                        // 引导第 5 步聚光顶栏动作区（主流程 holder 为 null，guideTarget 原样返回）
                        Row(Modifier.guideTarget(GuideKeys.TOPBAR)) {
                            IconButton(onClick = actions::openHistory) {
                                Icon(Icons.Filled.History, contentDescription = stringResource(R.string.history))
                            }
                            IconButton(onClick = actions::openSettings) {
                                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                            }
                        }
                    },
                )
            }
        },
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
            )
        },
        floatingActionButton = {
            // 需求-7：编辑态操作上移顶栏，编辑态不再渲染 FAB；非编辑态保留 + 号新增入口
            if (!state.isEditing) {
                FabMenu(
                    onStartInput = actions::startNew,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                        .guideTarget(GuideKeys.FAB),
                )
            }
        },
    ) { padding ->
        // imePadding 不加在这里：键盘弹出会逐帧压缩 pager 视口，pager 反复重算吸附位置
        // 与输入框 bring-into-view 互相触发——正是「点数量框后页面上下抖动」的走查 BUG。
        // 改由各分支内部（非编辑 LazyColumn / EditingPager 每页）自行 imePadding。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            PermissionBanners()
            // 400ms 短暂亮灯给「散入各桶」一个可见反馈，不做假加载
            var refreshing by remember { mutableStateOf(false) }
            // 表单开合淡入淡出 + 高度过渡（走查反馈：两态切换生硬）。
            // target 只能取「是否编辑中」布尔：editing 状态对象每次键入都是新实例，
            // 拿它当 target 会每次键入触发一次转场、TextField 重建丢焦点（走查修复）。
            // 退场中的表单读 lastEditing 快照，不因 state.editing 已置空而中途消失
            var lastEditing by remember { mutableStateOf(state.editing) }
            state.editing?.let { lastEditing = it }
            AnimatedContent(
                targetState = state.isEditing,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(160))
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "form_vs_list",
            ) { isEditing ->
                val editing = if (isEditing) lastEditing else null
                if (editing != null) {
                    // 需求-7：编辑态三段式（本次操作 / 编辑区 / 已添加）VerticalPager 吸附切换，
                    // 交互细节（梯形跳段、段内滚动）收口在 EditingPager。
                    // 编辑期间隐藏正在编辑的条目（已是表单内虚线框）；空桶/空置顶区整体不渲染
                    val editingId = editing.editingItemId
                    // 记忆化保列表身份稳定：updateEditing 只 copy editing，buckets/pinned 引用
                    // 击键间不变——若每次重组新建列表（不稳定 List 身份），全部桶与卡片逐字符重组
                    val visiblePinned = remember(state.pinnedItems, editingId) {
                        state.pinnedItems.filterNot { it.id == editingId }
                    }
                    val visibleBuckets = remember(state.buckets, editingId) {
                        state.buckets
                            .map { it.copy(items = it.items.filterNot { item -> item.id == editingId }) }
                            .filter { it.items.isNotEmpty() }
                    }
                    EditingPager(
                        pinned = visiblePinned,
                        buckets = visibleBuckets,
                        editing = editing,
                        editingTarget = state.editingTarget,
                        now = state.now,
                        onStateChange = { newState -> actions.updateEditing { newState } },
                        onPlaceholderHint = { scope.launch { snackbarHostState.showSnackbar(it) } },
                        onStartEdit = actions::startEdit,
                        onDeleteItem = onDeleteItem,
                        onPageChanged = { editPage = it },
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().imePadding()) {
                        PullToRefreshBox(
                            isRefreshing = refreshing,
                            onRefresh = {
                                refreshing = true
                                actions.disperse()
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
                                            modifier = Modifier.animateItem(),
                                            header = stringResource(R.string.pinned_header),
                                            status = null,
                                            items = state.pinnedItems,
                                            now = state.now,
                                            cardsEnabled = true,
                                            onHeaderAction = actions::disperse,
                                            onStartEdit = actions::startEdit,
                                            onDeleteItem = onDeleteItem,
                                        )
                                    }
                                }
                                // 六个过期时间桶，空桶不渲染；桶头/组空白不可点击
                                state.buckets.forEachIndexed { bucketIndex, bucket ->
                                    item(key = "bucket_${bucket.status}") {
                                        BucketBox(
                                            modifier = Modifier.animateItem(),
                                            header = stringResource(bucket.status.labelRes),
                                            status = bucket.status,
                                            items = bucket.items,
                                            now = state.now,
                                            cardsEnabled = true,
                                            onStartEdit = actions::startEdit,
                                            onDeleteItem = onDeleteItem,
                                            // 引导第 3 步聚光首卡、第 4 步聚光首桶（含桶头讲分桶概念；
                                            // 整列表做镂空会几乎盖满全屏，遮罩形同虚设——走查反馈）
                                            guideFirstCardKey = if (bucketIndex == 0) guideFirstCardKey else null,
                                            guideBucketKey = if (bucketIndex == 0) guideFirstBucketKey else null,
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
    }

    state.pendingSave?.let { pending ->
        AlertDialog(
            onDismissRequest = actions::cancelPendingSave,
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
                TextButton(onClick = actions::confirmPendingSave) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = actions::cancelPendingSave) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // 退出编辑确认：保存并退出（经 save() 同一套校验/过期确认）/ 不保存直接放弃
    if (pendingDiscard) {
        AlertDialog(
            onDismissRequest = { pendingDiscard = false },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = { Text(stringResource(R.string.unsaved_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDiscard = false
                        actions.saveAndExit()
                    },
                ) { Text(stringResource(R.string.save_and_exit)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingDiscard = false
                        actions.backToMethodSelection()
                    },
                ) { Text(stringResource(R.string.discard_exit)) }
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
                        actions.delete(item)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                context.getString(R.string.deleted_snackbar, item.name),
                                actionLabel = context.getString(R.string.undo),
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                actions.undoDelete(item)
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
 * 桶容器（需求-3）：浅边框 + 状态色桶头 + 子条目卡片。
 * status = null 表示「本次添加」置顶区（主色 2dp 边框、组头用主色文本）。
 * 点卡片 = 编辑；组头与组空白不可点击（桶按过期时间聚合，无组级交互）。
 * animateContentSize：卡片增删时桶高度平滑伸缩（走查反馈），配合 LazyColumn 的
 * animateItem 让后续桶跟随滑动。
 */
@Composable
internal fun BucketBox(
    modifier: Modifier = Modifier,
    header: String,
    status: ExpiryStatus?,
    items: List<FoodItem>,
    now: LocalDateTime,
    cardsEnabled: Boolean,
    onHeaderAction: (() -> Unit)? = null,
    onStartEdit: (FoodItem) -> Unit,
    onDeleteItem: (FoodItem) -> Unit,
    /** 非空时把首张卡片/整个桶注册为引导聚光目标 */
    guideFirstCardKey: String? = null,
    guideBucketKey: String? = null,
) {
    // 走查反馈：缺口边框——整圈边框、标题嵌在顶部边框缺口里（类似输入框浮动标签）。
    // 边框线用状态色 accent 加透明度淡化；标题文字保持实色 accent 保证可读，
    // 「本次添加」桶用 primary（文字）+ 淡化线
    val statusColors = status?.let { LocalStatusColors.current.of(it) }
    val notchColor = statusColors?.accent ?: MaterialTheme.colorScheme.primary
    val lineColor = (statusColors?.accent ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.45f)
    // semantics 块非 Composable 上下文，提前解析
    val disperseLabel = stringResource(R.string.disperse_into_buckets)
    val bucketTarget = if (guideBucketKey != null) Modifier.guideTarget(guideBucketKey) else Modifier
    Box(modifier = modifier.then(bucketTarget).fillMaxWidth().animateContentSize()) {
        // 顶部预留 14dp ≈ 缺口标题半高：LazyColumn 的 item 只能在自身边界内绘制，
        // 标题的布局位置必须落在 Box 内（渲染时再上移半高），否则上半截被 item 裁切
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, lineColor),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        ) {
            Column(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEachIndexed { cardIndex, item ->
                // 卡片入场淡入（走查反馈）：跨桶迁移/散入各桶/撤销删除时新位置柔和不突兀。
                // key 按 item.id 圈住 remember 槽位，重排时复用入场状态——否则相邻卡
                // 会因槽位换主而重新播放入场动画。移除的收缩由桶 animateContentSize 承接
                key(item.id) {
                    AnimatedVisibility(
                        visibleState = remember {
                            MutableTransitionState(false).apply { targetState = true }
                        },
                        enter = fadeIn(tween(200)),
                    ) {
                        Box(
                            modifier = if (cardIndex == 0 && guideFirstCardKey != null) {
                                Modifier.guideTarget(guideFirstCardKey)
                            } else {
                                Modifier
                            },
                        ) {
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
        }
    }
        // 缺口标题（走查反馈改为靠右）：布局位置 = Surface 顶边（y=14dp），渲染时上移半高
        // 骑在边框上；背景与页面底色同色遮出缺口。「本次添加」桶在此承载散入动作的 TalkBack 语义
        Text(
            header,
            style = MaterialTheme.typography.labelLarge,
            color = notchColor,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-18).dp, y = 14.dp)
                .graphicsLayer { translationY = -size.height / 2f }
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 6.dp)
                .semantics {
                    if (status == null) {
                        onHeaderAction?.let {
                            customActions = listOf(
                                CustomAccessibilityAction(disperseLabel) { it(); true },
                            )
                        }
                    }
                },
        )
    }
}
