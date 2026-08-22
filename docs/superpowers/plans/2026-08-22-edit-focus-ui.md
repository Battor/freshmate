# 需求-5：主页编辑聚焦交互 + 桶头进度条 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 主页五点界面打磨——标题靠左、新增表单自动填生产日期、编辑目标卡片上移+列表隐藏、桶头进度条、表单钉顶+遮罩。

**Architecture:** 全部为 UI 层改动 + 两个可测的小逻辑点（`ExpiryStatus` 进度窗口天数、`UiState.editingTarget` 计算属性）。`editingTarget` 做成 `UiState` 计算属性而非 `withDerived` 派生态，因为 `startEdit`/`backToMethodSelection` 直接 `copy(editing=...)` 不经 `withDerived`，放那里会陈旧；计算属性与既有 `hasFormContent` 同模式。spec：`docs/superpowers/specs/2026-08-22-edit-focus-and-bucket-progress-design.md`。

**Tech Stack:** Kotlin + Jetpack Compose Material 3。gradle 命令一律带前缀 `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`。无新增依赖、无新增文案。

**分支：** `feature/edit-focus-ui`（基于 main；spec 已在 main 上提交 16c8516）。

---

### Task 0: 建分支

- [ ] **Step 1: 从 main 创建特性分支**

```bash
git checkout main && git checkout -b feature/edit-focus-ui
```

---

### Task 1: ExpiryStatus 桶头进度窗口天数（TDD）

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/util/ExpiryStatus.kt:13-20`
- Test: `app/src/test/java/com/battor/freshmate/util/ExpiryStatusTest.kt`

- [ ] **Step 1: 写失败测试**

在 `ExpiryStatusTest` 末尾（`过期文案 - 小时` 测试之后、类结尾 `}` 之前）追加：

```kotlin
    @Test fun `桶头进度窗口天数`() {
        // 14 天满刻度：DUE_1D→1/14、DUE_3D→3/14≈1/5、DUE_7D→7/14、DUE_14D 满；EXPIRED/SAFE 满
        assertEquals(14, ExpiryStatus.EXPIRED.progressWindowDays)
        assertEquals(1, ExpiryStatus.DUE_1D.progressWindowDays)
        assertEquals(3, ExpiryStatus.DUE_3D.progressWindowDays)
        assertEquals(7, ExpiryStatus.DUE_7D.progressWindowDays)
        assertEquals(14, ExpiryStatus.DUE_14D.progressWindowDays)
        assertEquals(14, ExpiryStatus.SAFE.progressWindowDays)
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.util.ExpiryStatusTest"`
Expected: 编译失败 `Unresolved reference: progressWindowDays`

- [ ] **Step 3: 实现**

`ExpiryStatus.kt` 枚举改为（加 `progressWindowDays` 参数与 KDoc）：

```kotlin
/**
 * 条目紧急度（需求-3 改为绝对时间六档，与主列表分桶同阈值），值越靠后越宽松。
 * 红(EXPIRED) → DUE_1D → DUE_3D → DUE_7D → DUE_14D → 绿(SAFE)。
 * progressWindowDays：桶头进度条刻度（14 天满；EXPIRED 整条红、SAFE 已超窗，都取满）。
 */
enum class ExpiryStatus(@StringRes val labelRes: Int, val progressWindowDays: Int) {
    EXPIRED(R.string.status_expired, 14),
    DUE_1D(R.string.status_due_1d, 1),
    DUE_3D(R.string.status_due_3d, 3),
    DUE_7D(R.string.status_due_7d, 7),
    DUE_14D(R.string.status_due_14d, 14),
    SAFE(R.string.status_safe, 14),
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.util.ExpiryStatusTest"`
Expected: PASS（全类绿）

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/battor/freshmate/util/ExpiryStatus.kt app/src/test/java/com/battor/freshmate/util/ExpiryStatusTest.kt
git commit -m "feat(ui): ExpiryStatus 桶头进度窗口天数"
```

---

### Task 2: UiState.editingTarget 派生态（TDD）

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/MainViewModel.kt:74-110`（UiState）
- Test: `app/src/test/java/com/battor/freshmate/ui/main/MainViewModelTest.kt`

- [ ] **Step 1: 写失败测试**

在 `MainViewModelTest` 的 `refreshNow刷新页面时刻` 测试之后追加：

```kotlin
    @Test fun `编辑时editingTarget指向目标条目`() = runTest(dispatcher) {
        saveNew()
        val item = repo.items.value[0]
        vm.startEdit(item)
        assertEquals(item.id, vm.uiState.value.editingTarget?.id)
        // 放弃表单即清空
        vm.backToMethodSelection()
        assertNull(vm.uiState.value.editingTarget)
    }

    @Test fun `新增表单无editingTarget`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        assertNull(vm.uiState.value.editingTarget)
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.ui.main.MainViewModelTest"`
Expected: 编译失败 `Unresolved reference: editingTarget`

- [ ] **Step 3: 实现**

`UiState` 里 `isAddForm` 之后、`hasFormContent` KDoc 之前插入（计算属性，非存储字段——`startEdit`/`backToMethodSelection` 不经 `withDerived`，存储态会陈旧）：

```kotlin
        /** 正在编辑的目标条目（表单上方展示的卡片）；新增表单/无表单时为 null。 */
        val editingTarget: FoodItem?
            get() = editing?.editingItemId?.let { id -> items.firstOrNull { it.id == id } }
```

- [ ] **Step 4: 跑测试确认通过**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.ui.main.MainViewModelTest"`
Expected: PASS（全类绿）

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/battor/freshmate/ui/main/MainViewModel.kt app/src/test/java/com/battor/freshmate/ui/main/MainViewModelTest.kt
git commit -m "feat(ui): UiState.editingTarget 派生态"
```

---

### Task 3: 主页标题靠左

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt:25,136`（import 与 topBar）

- [ ] **Step 1: 改 import 与顶栏组件**

import 改：删除 `import androidx.compose.material3.CenterAlignedTopAppBar`，新增 `import androidx.compose.material3.TopAppBar`（按字母序插在 Text 之后、TextButton 之前）。

`Scaffold` 的 `topBar` 里 `CenterAlignedTopAppBar(` 改为 `TopAppBar(`，其余参数不动：

```kotlin
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
```

- [ ] **Step 2: 编译验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt
git commit -m "feat(ui): 主页标题靠左"
```

---

### Task 4: 新增表单自动填当天生产日期

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/ItemForm.kt`（约 139-189 两处 + 新增私有函数；需补 `import java.time.LocalDate`）

- [ ] **Step 1: 新增私有扩展函数**

`ItemForm` 函数体之前（`QuickShelfLives` 定义之后）加：

```kotlin
/**
 * 需求-5：新增表单里保质期一旦有效而生产日期为空，自动填今天。
 * 编辑表单不适用：旧条目起算点是当时的录入时刻（createdAt），自动填今天会大幅改变到期语义。
 */
private fun EditingState.withAutoProductionDate(shelfLifeText: String): EditingState =
    if (editingItemId == null &&
        productionDate == null &&
        shelfLifeText.toIntOrNull()?.let { it > 0 } == true
    ) {
        copy(productionDate = LocalDate.now())
    } else {
        this
    }
```

（`EditingState` 与 ItemForm 同包 `ui.main`，无需 import；`LocalDate` 需新增 import。）

- [ ] **Step 2: 保质期数字输入接入**

`field_shelf_life` 的 `OutlinedTextField` `onValueChange` 改为：

```kotlin
                onValueChange = { text ->
                    val filtered = text.filter { it in '0'..'9' }.take(4)
                    onStateChange(
                        state.copy(shelfLifeValue = filtered, shelfLifeError = false)
                            .withAutoProductionDate(filtered),
                    )
                },
```

- [ ] **Step 3: 快捷保质期 chips 接入**

`QuickShelfLives.forEach` 里 `AssistChip` 的 `onClick` 改为：

```kotlin
                        onClick = {
                            onStateChange(
                                state.copy(
                                    shelfLifeValue = quick.value.toString(),
                                    shelfLifeUnit = quick.unit,
                                    shelfLifeError = false,
                                ).withAutoProductionDate(quick.value.toString()),
                            )
                        },
```

- [ ] **Step 4: 编译验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/battor/freshmate/ui/main/ItemForm.kt
git commit -m "feat(ui): 新增表单保质期有效时自动填当天生产日期"
```

---

### Task 5: 表单钉顶 + 编辑遮罩 + 目标卡片 + 列表隐藏

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt`（主体结构重排）

- [ ] **Step 1: 调整 import**

删除：`import androidx.compose.foundation.lazy.rememberLazyListState`（不再需要滚动控制）。
新增（按现有分组字母序插入）：

```kotlin
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
```

- [ ] **Step 2: 删除滚动控制状态**

删除 `val listState = rememberLazyListState()`（约 :84）与「表单打开滚到顶部」整个 `LaunchedEffect(state.editing != null)` 块及 `restoreIndex`（约 :115-124）。`LazyColumn` 的 `state = listState` 参数一并移除。

- [ ] **Step 3: 重排 Scaffold 内容**

`Scaffold { padding -> ... }` 内（`PermissionBanners()` 之后）整体替换为下述结构。要点：表单区钉顶（编辑时上方多一张目标卡片，纯展示 `enabled = false`）；列表退到下方 `Box(weight(1f))`；编辑期间 Box 内叠遮罩吸收点击；`imePadding` 从 LazyColumn 移到外层 Column：

```kotlin
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
            // 表单（+编辑目标卡片）钉在内容区顶部，不随列表滚动（需求-5）；
            // verticalScroll：表单高于可用空间（如键盘弹出）时自身可滚
            state.editing?.let { editing ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.editingTarget?.let { target ->
                        FoodItemCard(
                            item = target,
                            now = state.now,
                            onClick = {},
                            onDelete = {},
                            enabled = false, // 纯展示：正在编辑的条目指示，不可交互
                        )
                    }
                    ItemForm(
                        state = editing,
                        onStateChange = { newState -> viewModel.updateEditing { newState } },
                        onPlaceholderHint = { scope.launch { snackbarHostState.showSnackbar(it) } },
                    )
                }
            }
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
                        // 编辑期间从列表隐藏正在编辑的条目（已上移为表单上方卡片）
                        val editingId = state.editing?.editingItemId
                        // 本次添加置顶区（会话内存态；下拉刷新/冷启动散入各桶）
                        state.pinnedItems
                            .filterNot { it.id == editingId }
                            .takeIf { it.isNotEmpty() }
                            ?.let { pinned ->
                                item(key = "pinned") {
                                    BucketBox(
                                        header = stringResource(R.string.pinned_header),
                                        status = null,
                                        items = pinned,
                                        now = state.now,
                                        cardsEnabled = state.editing == null,
                                        onHeaderAction = { viewModel.disperseSession() },
                                        onStartEdit = { viewModel.startEdit(it) },
                                        onDeleteItem = { item ->
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
                                    )
                                }
                            }
                        // 六个过期时间桶，空桶不渲染；桶头/组空白不可点击
                        state.buckets.forEach { bucket ->
                            bucket.items
                                .filterNot { it.id == editingId }
                                .takeIf { it.isNotEmpty() }
                                ?.let { items ->
                                    item(key = "bucket_${bucket.status}") {
                                        BucketBox(
                                            header = stringResource(bucket.status.labelRes),
                                            status = bucket.status,
                                            items = items,
                                            now = state.now,
                                            cardsEnabled = state.editing == null,
                                            onStartEdit = { viewModel.startEdit(it) },
                                            onDeleteItem = { item ->
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
                                        )
                                    }
                                }
                        }
                        if (state.items.isEmpty() && state.editing == null) {
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
                // 编辑期间遮罩：列表压暗 + 吸收点击防误触（下拉刷新手势同被拦截）；
                // 顶栏与 FAB 不盖——浮动「← 返回」按钮在变暗的列表上更突出。
                // scrim 为黑：深色主题下 onSurface 是浅色，浅色蒙层会「发白」而非「变暗」
                if (state.editing != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f))
                            .pointerInput(Unit) { detectTapGestures { } },
                    )
                }
            }
        }
```

注意：原 LazyColumn 里的 `item(key = "form") { ItemForm(...) }` 整块删除（表单已上移钉顶）；`Modifier.imePadding()` 从 LazyColumn 移除（已在外层 Column）。

- [ ] **Step 4: 编译验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt
git commit -m "feat(ui): 表单钉顶与编辑遮罩、编辑目标卡片上移列表隐藏"
```

---

### Task 6: 桶头进度条

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt`（`BucketBox` 组头，约 :326-355；import）

- [ ] **Step 1: 调整 import**

新增（按字母序）：

```kotlin
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
```

- [ ] **Step 2: 替换状态桶组头**

`BucketBox` 内 `if (statusColors != null) { ... }` 分支（原组头 Text）整体替换为：

```kotlin
            if (statusColors != null) {
                // 进度条宽度 = 组头文字实际宽度 × 1.5（需求-5 用户指定，精确测量非估算）
                val textMeasurer = rememberTextMeasurer()
                val textStyle = MaterialTheme.typography.labelLarge
                val textWidth = textMeasurer.measure(header, textStyle).size.width
                val barWidth = with(LocalDensity.current) { textWidth.toDp() * 1.5f }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 桶级紧急度：剩余段=状态容器色（与子项卡片同色），轨道=同色 30% 透明；
                    // EXPIRED 窗口取满 → 整条深红实心
                    LinearProgressIndicator(
                        progress = { status!!.progressWindowDays / 14f },
                        modifier = Modifier.width(barWidth),
                        color = statusColors.container,
                        trackColor = statusColors.container.copy(alpha = 0.3f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(header, style = textStyle, color = statusColors.on)
                }
            } else {
```

（`else` 分支「本次添加」组头原样保留。）

- [ ] **Step 3: 编译验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt
git commit -m "feat(ui): 桶头进度条——状态色 14 天刻度紧急度指示"
```

---

### Task 7: 全量验证

- [ ] **Step 1: 测试链**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug lintDebug`
Expected: 全绿

- [ ] **Step 2: 装机走查**（用户执行）

1. 标题靠左，历史/设置图标仍在右侧；
2. 新增表单填保质期（手输/chip）→ 生产日期自动出现今天；× 清除后再改保质期会重新填；编辑无生产日期的旧条目不自动填；
3. 编辑 A：A 从列表消失、表单上方出现 A 的卡片（不可点/不可滑）；保存或放弃后 A 回到原位；
4. 各桶进度条：DUE_1D 细条、DUE_3D 约 1/5、DUE_7D 约一半、DUE_14D/SAFE 满、EXPIRED 整条深红；宽度约为文字 1.5 倍；「本次添加」组头不变；
5. 表单打开：列表变暗、点击/滑动列表无响应、下拉刷新不可触发；← / ✓ 按钮突出；键盘弹出表单不被遮挡、表单内可滚动；
6. 深浅两主题 + 三语言抽查（无新增文案，既有资源应正常）。

- [ ] **Step 3: 双 skill 重审**（延续需求-3/4 惯例，`mobile-android-design` + `styles` 并行只读审查新增代码，产出记录入 `docs/reviews/`）
