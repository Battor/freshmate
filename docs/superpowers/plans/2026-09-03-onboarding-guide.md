# 新手引导（教练标记 + 内存 mock 数据）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 首次启动自动进入遮罩聚光引导（5 步，mock 数据、只看不摸），设置页可随时重看。

**Architecture:** `MainScreen` 的 Scaffold 内容抽成可复用的 `MainContent`（纯展示）；`ui/guide/` 包新增 mock 状态、自绘遮罩层、步骤状态机与引导页；完成标记存进现有 DataStore。引导层数据流：目标控件经 `guideTarget(key)` 上报 bounds → 全屏 Overlay 按 `GuideStep` 画遮罩/镂空/说明卡。

**Tech Stack:** Compose（Canvas 自绘、pointerInput）、DataStore Preferences、JUnit4。零新依赖。

**Spec:** `docs/superpowers/specs/2026-09-03-onboarding-guide-design.md`

**约定（全计划通用）：**
- 构建命令前缀 `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`，即：
  `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug`
- 提交信息末尾加 `Co-Authored-By: Claude <noreply@anthropic.com>`；**禁止 `git add -A`**（`.codegraph/`、`.superpowers/`、`resources/` 下有永不入库的文件）。
- 工作目录始终在仓库根 `C:\Users\Battor\source\repos\FreshMate`。

---

### Task 1: SettingsRepository 增加引导完成标记

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/data/SettingsRepository.kt`
- Modify: `app/src/test/java/com/battor/freshmate/ui/settings/SettingsViewModelTest.kt`（接口新增成员 → FakeRepo 必须补实现，否则编译失败）

- [ ] **Step 1: 扩展接口与实现**

`SettingsRepository.kt` 中，接口增加两个成员、实现类补上（key 与注释风格对齐现有 `theme_mode` 写法）：

```kotlin
interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    /** 新手引导是否已完成/跳过（首启自动弹一次的闸门）。 */
    val onboardingCompleted: Flow<Boolean>
    suspend fun setOnboardingCompleted()
}
```

`DataStoreSettingsRepository` 内（`companion object` 的 `Key` 旁加 `OnboardingKey`）：

```kotlin
    override val onboardingCompleted: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[OnboardingKey] ?: false }

    override suspend fun setOnboardingCompleted() {
        context.settingsDataStore.edit { it[OnboardingKey] = true }
    }

    private companion object {
        val Key = stringPreferencesKey("theme_mode")
        val OnboardingKey = booleanPreferencesKey("onboarding_completed")
    }
```

import 区补 `androidx.datastore.preferences.core.booleanPreferencesKey`。

- [ ] **Step 2: 修 FakeRepo 使现有测试可编译**

`SettingsViewModelTest.kt` 的 `FakeRepo` 补两个 override（`onboardingCompleted` 初值 `true`，本测试用不到）：

```kotlin
    private class FakeRepo(initial: ThemeMode) : SettingsRepository {
        override val themeMode = MutableStateFlow(initial)
        override val onboardingCompleted = MutableStateFlow(true)
        override suspend fun setThemeMode(mode: ThemeMode) {
            themeMode.value = mode
        }
        override suspend fun setOnboardingCompleted() {
            onboardingCompleted.value = true
        }
    }
```

注：`DataStoreSettingsRepository` 的真实读写无单测——`preferencesDataStore` 委托进程级单例，JVM 单测环境无法创建第二个实例（与现有 `theme_mode` 一致，无测试），由 Task 10 手动走查覆盖。

- [ ] **Step 3: 构建验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL，测试全绿。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/data/SettingsRepository.kt app/src/test/java/com/battor/freshmate/ui/settings/SettingsViewModelTest.kt
git commit -m "feat(data): SettingsRepository 新增新手引导完成标记"
```

---

### Task 2: MainScreen 拆出可复用的 MainContent

纯机械重构，行为零变化；GuideScreen（Task 7）靠它复用整个主页界面。

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt`

- [ ] **Step 1: 新增 MainContent，MainScreen 变薄壳**

在 `MainScreen.kt` 中：原 `MainScreen` 的 Scaffold 及其后的 `pendingSave`/`pendingDelete` 两个 AlertDialog 整体搬进新的 `internal fun MainContent(...)`；`MainScreen` 只留状态收集与回调转发。

新的 `MainScreen` 全文：

```kotlin
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
        onUpdateHintShown = onUpdateHintShown,
        onOpenUpdate = onOpenUpdate,
        onOpenHistory = onOpenHistory,
        onOpenSettings = onOpenSettings,
        onStartEdit = viewModel::startEdit,
        onDisperse = viewModel::disperseSession,
        onStartNew = viewModel::startNew,
        onSave = viewModel::save,
        onBackToMethodSelection = viewModel::backToMethodSelection,
        onUpdateEditing = { transform -> viewModel.updateEditing(transform) },
        onPermissionRequested = viewModel::onPermissionRequested,
        onErrorShown = viewModel::onErrorShown,
    )
}
```

新的 `MainContent` 签名（放 `MainScreen` 之后）：

```kotlin
/**
 * 主页完整界面（Scaffold + 列表/表单 + 全部对话框）。
 * 从 MainScreen 抽出的纯展示层：不依赖 MainViewModel，全部交互走回调——
 * 引导页（ui/guide）用 no-op 回调 + mock state 复用同一套界面。
 */
@Composable
internal fun MainContent(
    state: MainViewModel.UiState,
    errorEvent: UiText?,
    updateHint: UiText?,
    onUpdateHintShown: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartEdit: (FoodItem) -> Unit,
    onDisperse: () -> Unit,
    onStartNew: (InputMethodId) -> Unit,
    onSave: () -> Unit,
    onBackToMethodSelection: () -> Unit,
    onUpdateEditing: ((MainViewModel.EditingState) -> MainViewModel.EditingState) -> Unit,
    onPermissionRequested: () -> Unit,
    onErrorShown: () -> Unit,
) {
```

- [ ] **Step 2: MainContent 函数体 = 搬移 + 回调名替换**

搬移规则（函数体其余内容**逐字保留**，包括注释、动画、BucketBox、`pendingDelete`/`refreshing`/`lastEditing` 等局部状态——这些全部留在 MainContent 内）：

| 原引用（MainScreen 内） | 替换为 |
|---|---|
| `viewModel.onErrorShown()` | `onErrorShown()` |
| `{ viewModel.startEdit(it) }`（两处 BucketBox 的 onStartEdit） | `onStartEdit` |
| `viewModel.disperseSession()`（PullToRefreshBox onRefresh 内 + 置顶桶头 onHeaderAction） | `onDisperse()` |
| `{ viewModel.startNew(it) }`（FabMenu onStartInput） | `onStartNew` |
| `{ viewModel.save() }`（FabMenu onSave） | `onSave` |
| `{ viewModel.backToMethodSelection() }`（FabMenu onBack） | `onBackToMethodSelection` |
| `{ newState -> viewModel.updateEditing { newState } }`（ItemForm onStateChange） | `{ newState -> onUpdateEditing { newState } }` |
| `{ viewModel.onPermissionRequested() }`（NotificationPermissionEffect onHandled） | `onPermissionRequested` |
| `viewModel.delete(item)`（pendingDelete 确认按钮内） | 保留 `viewModel.delete` 所在行改为只调本地逻辑：该对话框是 UI 自有状态（`pendingDelete`），`viewModel.delete/undoDelete` **留在 MainContent 内直接引用形参**——见下方说明 |

关于删除链路的特别说明：`pendingDelete` 对话框的确认按钮里原本依次做 `viewModel.delete(item)` 与 Snackbar 撤销 `viewModel.undoDelete(item)`。为让引导模式零依赖，把这两个动作也抽成回调：签名再加两个参数

```kotlin
    onDelete: (FoodItem) -> Unit,
    onUndoDelete: (FoodItem) -> Unit,
```

（加在 `onStartEdit` 之后；上面 `MainScreen` 转发处补 `onDelete = viewModel::delete,` `onUndoDelete = viewModel::undoDelete,`。）对话框内对应替换为 `onDelete(item)` / `onUndoDelete(item)`。

- [ ] **Step 3: 清理 import**

`MainScreen.kt` 顶部 import 基本不变（`NotificationPermissionEffect`、`FabMenu`、`ItemForm`、`BucketBox` 等同包引用不受影响）；确认无未使用 import 警告即可。

- [ ] **Step 4: 构建验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL（无行为变化，纯重构）。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt
git commit -m "refactor(ui): 主页 Scaffold 内容抽成 MainContent 纯展示层——供引导页复用"
```

---

### Task 3: 引导文案字符串（三语言）

**Files:**
- Modify: `app/src/main/res/values/strings.xml`（简中，默认）
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`

- [ ] **Step 1: 三个 strings.xml 末尾（`</resources>` 前）追加同序资源**

`values/strings.xml`（简体中文）：

```xml
    <!-- 新手引导（教练标记五步 + 设置入口） -->
    <string name="settings_onboarding">新手引导</string>
    <string name="guide_skip">跳过引导</string>
    <string name="guide_next">下一步</string>
    <string name="guide_done">完成</string>
    <string name="guide_step_indicator">第 %1$d 步，共 %2$d 步</string>
    <string name="guide_welcome_title">欢迎使用食刻</string>
    <string name="guide_welcome_body">记下保质期，到期前提醒你。先花半分钟认识几个关键操作。</string>
    <string name="guide_fab_title">添加食品</string>
    <string name="guide_fab_body">点右下角「＋」，手动录入，填上保质期就行。</string>
    <string name="guide_card_title">滑动删除</string>
    <string name="guide_card_body">在卡片上向左滑删除（会先弹确认）。删错了也不怕，「历史」里可以还原。</string>
    <string name="guide_buckets_title">到期分桶</string>
    <string name="guide_buckets_body">列表按剩余时间自动分桶；下拉刷新可把「本次添加」散入各桶。</string>
    <string name="guide_permission_note">允许通知后，到期提醒才会送达。</string>
    <string name="guide_topbar_title">历史 与 设置</string>
    <string name="guide_topbar_body">右上角可打开历史（右滑还原）和设置（主题/语言，或重看本引导）。</string>
```

`values-zh-rTW/strings.xml`（繁体中文）：

```xml
    <!-- 新手引導（教練標記五步 + 設定入口） -->
    <string name="settings_onboarding">新手引導</string>
    <string name="guide_skip">跳過引導</string>
    <string name="guide_next">下一步</string>
    <string name="guide_done">完成</string>
    <string name="guide_step_indicator">第 %1$d 步，共 %2$d 步</string>
    <string name="guide_welcome_title">歡迎使用食刻</string>
    <string name="guide_welcome_body">記下保質期，到期前提醒你。先花半分鐘認識幾個關鍵操作。</string>
    <string name="guide_fab_title">新增食品</string>
    <string name="guide_fab_body">點右下角「＋」，手動輸入，填上保質期就行。</string>
    <string name="guide_card_title">滑動刪除</string>
    <string name="guide_card_body">在卡片上向左滑刪除（會先彈確認）。刪錯了也不怕，「歷史」裡可以還原。</string>
    <string name="guide_buckets_title">到期分桶</string>
    <string name="guide_buckets_body">列表按剩餘時間自動分桶；下拉重新整理可把「本次新增」散入各桶。</string>
    <string name="guide_permission_note">允許通知後，到期提醒才會送達。</string>
    <string name="guide_topbar_title">歷史 與 設定</string>
    <string name="guide_topbar_body">右上角可開啟歷史（右滑還原）和設定（主題/語言，或重看本引導）。</string>
```

`values-en/strings.xml`（英文）：

```xml
    <!-- Onboarding tour (5 coach-mark steps + settings entry) -->
    <string name="settings_onboarding">Getting-started tour</string>
    <string name="guide_skip">Skip tour</string>
    <string name="guide_next">Next</string>
    <string name="guide_done">Done</string>
    <string name="guide_step_indicator">Step %1$d of %2$d</string>
    <string name="guide_welcome_title">Welcome to FreshMate</string>
    <string name="guide_welcome_body">Track shelf life and get reminded before food expires. Take half a minute to see how it works.</string>
    <string name="guide_fab_title">Add an item</string>
    <string name="guide_fab_body">Tap “＋” in the corner and enter its shelf life — that’s all.</string>
    <string name="guide_card_title">Swipe to remove</string>
    <string name="guide_card_body">Swipe a card left to delete (a confirmation pops up first). Deleted items can be restored from History.</string>
    <string name="guide_buckets_title">Expiry buckets</string>
    <string name="guide_buckets_body">Items are grouped by remaining time. Pull down to scatter newly added items into their buckets.</string>
    <string name="guide_permission_note">Allow notifications so expiry reminders can reach you.</string>
    <string name="guide_topbar_title">History &amp; Settings</string>
    <string name="guide_topbar_body">Top-right opens History (swipe right to restore) and Settings (theme, language, or replay this tour).</string>
```

- [ ] **Step 2: 构建验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL（资源编译通过）。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh-rTW/strings.xml app/src/main/res/values-en/strings.xml
git commit -m "feat(res): 新手引导文案——简中/繁中/英文"
```

---

### Task 4: GuideStateMachine（TDD）

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/ui/guide/GuideStateMachine.kt`
- Test: `app/src/test/java/com/battor/freshmate/ui/guide/GuideStateMachineTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.battor.freshmate.ui.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideStateMachineTest {
    @Test
    fun `初始停在第一步`() {
        val machine = GuideStateMachine(stepCount = 5)
        assertEquals(0, machine.current)
        assertFalse(machine.isLast)
    }

    @Test
    fun `逐步推进到末步`() {
        val machine = GuideStateMachine(stepCount = 3)
        machine.next()
        assertEquals(1, machine.current)
        assertFalse(machine.isLast)
        machine.next()
        assertEquals(2, machine.current)
        assertTrue(machine.isLast)
    }

    @Test
    fun `末步再推进不越界`() {
        val machine = GuideStateMachine(stepCount = 2)
        machine.next()
        machine.next()
        machine.next()
        assertEquals(1, machine.current)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.ui.guide.GuideStateMachineTest"`
Expected: FAIL（`GuideStateMachine` 未定义，编译错误）。

- [ ] **Step 3: 最小实现**

```kotlin
package com.battor.freshmate.ui.guide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** 引导步骤推进（纯状态，UI 无关）：current 从 0 起，next() 在末步饱和。 */
class GuideStateMachine(private val stepCount: Int) {
    var current: Int by mutableIntStateOf(0)
        private set

    val isLast: Boolean get() = current == stepCount - 1

    fun next() {
        if (current < stepCount - 1) current++
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.ui.guide.GuideStateMachineTest"`
Expected: PASS（3 个测试）。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/guide/GuideStateMachine.kt app/src/test/java/com/battor/freshmate/ui/guide/GuideStateMachineTest.kt
git commit -m "feat(guide): 引导步骤状态机——推进/末步饱和"
```

---

### Task 5: GuideViewModel 内存 mock 数据（TDD）

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/ui/guide/GuideViewModel.kt`
- Test: `app/src/test/java/com/battor/freshmate/ui/guide/GuideViewModelTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.battor.freshmate.ui.guide

import com.battor.freshmate.util.ExpiryStatus
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideViewModelTest {
    private val now = LocalDateTime.of(2026, 9, 3, 12, 0)

    @Test
    fun `mock 条目落在三个预期桶`() {
        val state = GuideViewModel(now).uiState
        fun names(status: ExpiryStatus) =
            state.buckets.firstOrNull { it.status == status }?.items?.map { it.name }
        assertEquals(listOf("牛奶"), names(ExpiryStatus.EXPIRED))
        assertEquals(listOf("酸奶"), names(ExpiryStatus.DUE_1D))
        assertEquals(listOf("蔬菜"), names(ExpiryStatus.DUE_7D))
        assertEquals(3, state.buckets.size)
    }

    @Test
    fun `无置顶区无表单`() {
        val state = GuideViewModel(now).uiState
        assertTrue(state.pinnedItems.isEmpty())
        assertEquals(null, state.editing)
        assertTrue(state.buckets.all { bucket -> bucket.items.all { it.id < 0 } })
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.ui.guide.GuideViewModelTest"`
Expected: FAIL（`GuideViewModel` 未定义）。

- [ ] **Step 3: 最小实现**

```kotlin
package com.battor.freshmate.ui.guide

import com.battor.freshmate.data.Category
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.ui.main.MainViewModel
import com.battor.freshmate.ui.main.bucketItems
import java.time.LocalDateTime

/**
 * 引导模式数据源：纯内存 mock 三件条目（覆盖已过期/1 天内/7 天内三个色档），
 * 不持有 Repository、不写 Room、不碰提醒调度——用户真实数据全程不被触及，
 * 「引导结束还原原数据」因此自动成立。
 * id 取负数：与任何真实条目 id 空间隔离，排查日志一眼可辨。
 */
class GuideViewModel(now: LocalDateTime = LocalDateTime.now()) {

    val uiState: MainViewModel.UiState = run {
        // expiry = createdAt + shelfLifeDays（productionDate = null 时），倒推 createdAt 落桶
        fun mock(id: Long, name: String, category: Category, shelfLifeDays: Int, createdAt: LocalDateTime) =
            FoodItem(
                id = -id,
                name = name,
                category = category,
                productionDate = null,
                shelfLifeDays = shelfLifeDays,
                quantity = null,
                createdAt = createdAt,
            )

        val items = listOf(
            mock(1, "牛奶", Category.DAIRY, shelfLifeDays = 10, createdAt = now.minusDays(13)), // 到期 now-3d
            mock(2, "酸奶", Category.DAIRY, shelfLifeDays = 7, createdAt = now.plusHours(11).minusDays(7)), // 到期 now+11h
            mock(3, "蔬菜", Category.FRUITS_VEG, shelfLifeDays = 7, createdAt = now.minusDays(2)), // 到期 now+5d
        )
        MainViewModel.UiState(items = items, now = now, buckets = bucketItems(items, now))
    }
}
```

注：mock 名称固定中文（`FoodItem.name` 是纯字符串，VM 层拿不到资源；默认语言简中，后续有需要再经资源工厂本地化）。

- [ ] **Step 4: 跑测试确认通过**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.ui.guide.GuideViewModelTest"`
Expected: PASS（2 个测试）。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/guide/GuideViewModel.kt app/src/test/java/com/battor/freshmate/ui/guide/GuideViewModelTest.kt
git commit -m "feat(guide): 引导模式内存 mock 数据——三档三件，零真实数据触碰"
```

---

### Task 6: 引导层——目标注册 + 自绘遮罩 Overlay

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/ui/guide/GuideOverlay.kt`

（纯 UI，无单测；正确性由 Task 10 手动走查覆盖。）

- [ ] **Step 1: 写 GuideOverlay.kt 全文**

```kotlin
package com.battor.freshmate.ui.guide

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.battor.freshmate.R
import kotlin.math.roundToInt

/**
 * 聚光目标注册：目标控件挂 [guideTarget]，把自身 bounds（root 坐标）报进来。
 * 挂在 CompositionLocal 上——主流程里 LocalGuideState 为 null，guideTarget 原样返回，零开销。
 */
class GuideStateHolder {
    val targets = mutableStateMapOf<String, Rect>()
}

val LocalGuideState = compositionLocalOf<GuideStateHolder?> { null }

/** 引导步骤定义：targetKey = null 表示无聚光（居中欢迎卡）。 */
data class GuideStep(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val targetKey: String?,
)

internal val GuideSteps = listOf(
    GuideStep(R.string.guide_welcome_title, R.string.guide_welcome_body, null),
    GuideStep(R.string.guide_fab_title, R.string.guide_fab_body, "fab"),
    GuideStep(R.string.guide_card_title, R.string.guide_card_body, "first_card"),
    GuideStep(R.string.guide_buckets_title, R.string.guide_buckets_body, "bucket_area"),
    GuideStep(R.string.guide_topbar_title, R.string.guide_topbar_body, "topbar"),
)

/**
 * @Composable 修饰符工厂：引导态把 bounds 报到 holder，主流程（holder 为 null）原样返回。
 * 必须是 @Composable 才能读 CompositionLocal，避免 composed{} 的性能与限制问题。
 */
@Composable
fun Modifier.guideTarget(key: String): Modifier {
    val holder = LocalGuideState.current ?: return this
    return onGloballyPositioned { holder.targets[key] = it.boundsInRoot() }
}

private val ScrimColor = Color.Black.copy(alpha = 0.55f)

/**
 * 自绘引导层（spec 2026-09-03）：全屏遮罩 + 圆角镂空聚光 + 说明卡。
 * 覆盖在 MainContent 之上并消费全部点击（「只看不摸」）；GuideScreen 的 Box 直铺
 * 路由内容、与 MainContent 同一 root，boundsInRoot 减去自身偏移即本层局部坐标。
 */
@Composable
fun GuideOverlay(
    holder: GuideStateHolder,
    stepIndex: Int,
    showPermissionNote: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val step = GuideSteps[stepIndex]
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                overlayOffset = it.boundsInRoot().topLeft
                overlaySize = it.size
            }
            // 消费一切触摸：down 在本层被拦截，滚动/滑动不会落入下层界面（「只看不摸」）
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        val targetRect = step.targetKey?.let { holder.targets[it] }

        Canvas(Modifier.fillMaxSize()) {
            val corner = CornerRadius(14.dp.toPx())
            val hole = targetRect?.translate(-overlayOffset)?.inflate(8.dp.toPx())
            // EvenOdd 填充：整屏矩形 XOR 镂空圆角矩形 = 带洞遮罩
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                hole?.let { addRoundRect(RoundRect(rect = it, cornerRadius = corner)) }
            }
            drawPath(path, ScrimColor)
            hole?.let {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = it.topLeft,
                    size = it.size,
                    cornerRadius = corner,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }

        // 说明卡纵向定位：目标在屏上半 → 卡贴镂空下方；否则贴上方。
        // 首帧卡高未测得按 0 估，onSizeChanged 回填后下一帧自然校正。
        var cardHeightPx by remember { mutableStateOf(0) }
        if (targetRect == null) {
            // 无聚光（欢迎步）：卡片居中
            GuideCard(
                step = step,
                stepIndex = stepIndex,
                showPermissionNote = showPermissionNote,
                onNext = onNext,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            )
        } else {
            val gapPx = with(LocalDensity.current) { 24.dp.toPx() }
            val local = targetRect.translate(-overlayOffset)
            val cardY = if (local.center.y < overlaySize.height / 2f) {
                (local.bottom + gapPx).roundToInt()
            } else {
                (local.top - gapPx - cardHeightPx).roundToInt()
            }
            GuideCard(
                step = step,
                stepIndex = stepIndex,
                showPermissionNote = showPermissionNote,
                onNext = onNext,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, end = 24.dp)
                    .absoluteOffset { IntOffset(0, cardY) }
                    .onSizeChanged { cardHeightPx = it.height },
            )
        }

        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 8.dp, top = 4.dp),
        ) {
            Text(stringResource(R.string.guide_skip), color = Color.White)
        }
    }
}

/** 说明卡：步数指示 + 标题 + 正文（第 4 步按条件追加权限提示）+ 下一步/完成。 */
@Composable
private fun GuideCard(
    step: GuideStep,
    stepIndex: Int,
    showPermissionNote: Boolean,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.guide_step_indicator, stepIndex + 1, GuideSteps.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(step.titleRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                buildString {
                    append(stringResource(step.bodyRes))
                    if (showPermissionNote && step.targetKey == "bucket_area") {
                        append("\n\n")
                        append(stringResource(R.string.guide_permission_note))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = onNext,
                modifier = Modifier.align(Alignment.End).padding(top = 12.dp),
            ) {
                Text(
                    stringResource(
                        if (stepIndex == GuideSteps.lastIndex) R.string.guide_done else R.string.guide_next,
                    ),
                )
            }
        }
    }
}
```

- [ ] **Step 2: 构建验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/guide/GuideOverlay.kt
git commit -m "feat(guide): 自绘引导层——遮罩镂空聚光、说明卡定位、触摸全拦截"
```

---

### Task 7: GuideScreen 组装 + MainContent 引导态接线

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/ui/guide/GuideScreen.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt`（给 4 个聚光目标挂 `guideTarget`）

- [ ] **Step 1: GuideOverlay.kt 内给聚光目标挂点（已在本文件定义 guideTarget）——改在 MainScreen.kt 挂 4 处**

`MainScreen.kt` 的 `MainContent` 内四处追加 `guideTarget`（无引导态时 holder 为 null，原样返回零影响）：

1. FAB：`FabMenu(..., modifier = Modifier.windowInsetsPadding(...).guideTarget("fab"))`
2. 顶栏动作：`actions = { Row(Modifier.guideTarget("topbar")) { 两个 IconButton 原样 } }`（import `androidx.compose.foundation.layout.Row` 已有）
3. 首卡：`BucketBox` 增加透传参数 `guideFirstCardKey: String? = null`，列表分支第一个非空桶传入 `"first_card"`——`state.buckets.forEachIndexed { index, bucket -> BucketBox(..., guideFirstCardKey = if (index == 0) "first_card" else null, ...) }`；`BucketBox` 内卡片循环改为 `items.forEachIndexed { cardIndex, item ->`，`cardIndex == 0` 时给包卡片的 `Box` 挂 `Modifier.guideTarget(guideFirstCardKey!!)`（用局部 `val targetModifier = if (cardIndex == 0 && guideFirstCardKey != null) Modifier.guideTarget(guideFirstCardKey) else Modifier` 避免非空断言）。
4. 列表区：列表分支 `LazyColumn(modifier = Modifier.fillMaxSize().guideTarget("bucket_area"), ...)`

同时给 `MainContent` 增加可选透传参数 `guideFirstCardKey: String? = null`（默认 null，主流程零改动），表单分支的 `BucketBox` 调用不传。

- [ ] **Step 2: 写 GuideScreen.kt 全文**

```kotlin
package com.battor.freshmate.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import com.battor.freshmate.inputmethod.InputMethodId
import com.battor.freshmate.ui.main.MainContent
import com.battor.freshmate.ui.main.MainViewModel

/**
 * 引导页：mock 数据渲染完整主页界面 + 全屏引导层。
 * 所有交互回调 no-op：引导层已拦截触摸，回调是第二道保险。
 * 完成/跳过/返回键都走 onExit(markCompletedOnExit)。
 */
@Composable
fun GuideScreen(
    markCompletedOnExit: Boolean,
    onExit: (Boolean) -> Unit,
) {
    val guideViewModel = remember { GuideViewModel() }
    val holder = remember { GuideStateHolder() }
    val machine = remember { GuideStateMachine(GuideSteps.size) }
    val context = LocalContext.current
    // 条件文案：通知被关才在第 4 步追加权限说明（读真实系统权限，非用户数据）
    val showPermissionNote = remember {
        !NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val exit = { onExit(markCompletedOnExit) }

    BackHandler(onBack = exit)

    Box(Modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(LocalGuideState provides holder) {
            MainContent(
                state = guideViewModel.uiState,
                errorEvent = null,
                updateHint = null,
                onUpdateHintShown = {},
                onOpenUpdate = {},
                onOpenHistory = {},
                onOpenSettings = {},
                onStartEdit = {},
                onDelete = {},
                onUndoDelete = {},
                onDisperse = {},
                onStartNew = {},
                onSave = {},
                onBackToMethodSelection = {},
                onUpdateEditing = {},
                onPermissionRequested = {},
                onErrorShown = {},
                guideFirstCardKey = "first_card",
            )
        }
        GuideOverlay(
            holder = holder,
            stepIndex = machine.current,
            showPermissionNote = showPermissionNote,
            onNext = { if (machine.isLast) exit() else machine.next() },
            onSkip = exit,
        )
    }
}
```

（`InputMethodId` import 若未用到则删。）

- [ ] **Step 3: 构建验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/guide/GuideScreen.kt app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt
git commit -m "feat(guide): 引导页组装——mock 主页 + 聚光目标挂点 + 返回键即跳过"
```

---

### Task 8: NavGraph 接线——路由、首启自动进入、完成标记

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/navigation/NavGraph.kt`

- [ ] **Step 1: 路由与自动导航**

`NavGraph.kt` 修改（要点全部列出，其余原样）：

1. import 增补：
```kotlin
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.battor.freshmate.ui.guide.GuideScreen
```

2. 路由常量与 hoist（`FreshMateNavGraph` 开头）：
```kotlin
private object Routes {
    const val MAIN = "main"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val LOG_VIEWER = "logviewer"
    const val ABOUT = "about"
    const val GUIDE = "guide?first={first}"
}

@Composable
fun FreshMateNavGraph() {
    val navController = rememberNavController()
    val appContext = LocalContext.current.applicationContext
    // 全导航图共享一个 settings 仓库：首启标记收集（main）与设置页读写共用
    val settingsRepo = remember { DataStoreSettingsRepository(appContext) }
    // 协程作用域放导航图层级：main 与 guide 两个路由都要用（禁止放在单个路由 composable 内）
    val scope = rememberCoroutineScope()
    NavHost(navController = navController, startDestination = Routes.MAIN) {
```

3. MAIN 路由内（`MainScreen(...)` 调用之前）追加：
```kotlin
            // 首启未完成引导 → 自动进入（初值 true 防 DataStore 首帧误弹；
            // false 到达后 LaunchedEffect 重启触发导航，写完标记回来不再弹）
            val onboardingDone by settingsRepo.onboardingCompleted
                .collectAsStateWithLifecycle(initialValue = true)
            LaunchedEffect(onboardingDone) {
                if (!onboardingDone) navController.navigate("${Routes.GUIDE}?first=true")
            }
```

（`rememberCoroutineScope`、`LaunchedEffect` 若缺 import 则补。）

4. 新 GUIDE 路由（`composable(Routes.SETTINGS)` 块之前插入）：
```kotlin
        composable(
            Routes.GUIDE,
            arguments = listOf(navArgument("first") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            val first = entry.arguments?.getBoolean("first") ?: false
            GuideScreen(
                markCompletedOnExit = first,
                onExit = {
                    // 完成/跳过都落盘：首启路径写标记（设置重看不写，写了也无害但按 spec 区分）
                    if (first) {
                        scope.launch { settingsRepo.setOnboardingCompleted() }
                    }
                    navController.popBackStack()
                },
            )
        }
```

5. SETTINGS 路由里 `SettingsViewModel(DataStoreSettingsRepository(context))` 改为 `SettingsViewModel(settingsRepo)`（复用同一实例）。

- [ ] **Step 2: 构建验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/navigation/NavGraph.kt
git commit -m "feat(guide): 导航接线——guide 路由、首启自动进入、完成标记落盘"
```

---

### Task 9: 设置页入口

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/navigation/NavGraph.kt`（传 `onOpenGuide`）

- [ ] **Step 1: SettingsScreen 加参数与入口行**

签名加 `onOpenGuide: () -> Unit,`（放 `onOpenAbout` 之后）。`LazyColumn` 里 `item { LanguageSettingItem(viewModel) }` 之后插入：

```kotlin
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_onboarding)) },
                    leadingContent = { Icon(Icons.Filled.School, contentDescription = null) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onOpenGuide),
                )
            }
```

import 补 `androidx.compose.material.icons.filled.School`（material-icons-extended 已在依赖里，`Icons.Filled.School` 可用；若编译报缺 symbol，改用 `Icons.Filled.HelpOutline`）。

- [ ] **Step 2: NavGraph SETTINGS 路由接线**

`SettingsScreen(...)` 调用处补：

```kotlin
                onOpenGuide = { navController.navigate("${Routes.GUIDE}?first=false") },
```

- [ ] **Step 3: 构建验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug lintDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/settings/SettingsScreen.kt app/src/main/java/com/battor/freshmate/ui/navigation/NavGraph.kt
git commit -m "feat(guide): 设置页新增「新手引导」重看入口"
```

---

### Task 10: 全量验证 + 手动走查

- [ ] **Step 1: 全量构建**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug lintDebug`
Expected: 全绿。

- [ ] **Step 2: 装机走查清单（交给用户）**

1. 清数据冷启动 → 自动进引导，5 步顺序推进，mock 三件（牛奶红/酸奶粉/蔬菜黄绿）可见
2. 每步聚光位置正确（FAB / 首卡 / 列表区 / 顶栏），说明卡不出屏、不压镂空
3. 「跳过引导」/「完成」/返回键 → 回主页，真实数据显示；杀进程重启不再弹
4. 第 4 步文案：通知被关时含权限提示，开启后不含
5. 设置 → 新手引导 → 重看一遍 → 返回，期间真实数据无任何增删
6. 深浅两主题下遮罩、说明卡、镂空描边均清晰
7. TalkBack：跳过按钮可聚焦，五步顺序朗读
