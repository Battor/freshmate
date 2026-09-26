# FAB 直进手动 + 引导编辑态两步 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** FAB 点击直接打开手动输入表单（语音/图片入口暂时隐藏）；新手引导在 FAB 步后插入「编辑表单区」与「上下梯形（双洞聚光）」两步，5 步变 7 步。

**Architecture:** 引导层把单矩形聚光升级为多矩形（`targetKeys: List<String>`，EvenOdd 路径循环挖洞、洞数相同逐洞补间、说明卡按包围盒定位）；mock 数据用纯函数 `uiStateForStep(step)` 按步骤声明派生编辑态/非编辑态 UiState；聚光 key 走既有 `guideTarget` CompositionLocal 通道透传到 `EditingPager`。

**Tech Stack:** Kotlin、Jetpack Compose（Material3）、JUnit4 + Robolectric（既有单测配置）。

**Spec:** `docs/superpowers/specs/2026-09-26-fab-input-and-guide-design.md`

**项目惯例（执行者必读）：**
- 测试/构建命令统一带前缀：`JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew …`
- 提交信息结尾加 `Co-Authored-By: Claude <noreply@anthropic.com>`
- **禁止 `git add -A`**：未跟踪的 `.codegraph/`、`.superpowers/`、`resources/` 下文件绝不能入库
- 字符串改动必须同步三份：`app/src/main/res/values/strings.xml`、`values-en/`、`values-zh-rTW/`
- 项目未发布，无需 DB 迁移

---

### Task 1: FAB 直进手动输入

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/FabMenu.kt`

- [ ] **Step 1: 重写 FabMenu——删菜单与 expanded，FAB onClick 直发 MANUAL**

```kotlin
package com.battor.freshmate.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.battor.freshmate.R
import com.battor.freshmate.inputmethod.InputMethodId

/**
 * 新增入口：+ 号直接新开手动输入表单。
 * 语音/图片入口暂时移除（走查决定，2026-09-26）：InputMethods 框架与字符串保留，
 * 日后恢复只需把 DropdownMenu 菜单加回来遍历 InputMethods.all。
 * 编辑态的返回/保存按钮已上移顶栏（需求-7），编辑态不再渲染 FAB。
 */
@Composable
fun FabMenu(
    onStartInput: (InputMethodId) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 走查反馈：FAB 用品牌主色（浅色主题即 APP 图标的深绿）+ onPrimary 前景；
    // 深色主题沿用 primary = 亮绿变体，保持与深底的对比
    FloatingActionButton(
        onClick = { onStartInput(InputMethodId.MANUAL) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier,
    ) {
        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add))
    }
}
```

注意：原来 FAB 在 `Box(modifier)` 里、menu 与 FAB 同级；现 FAB 直接顶到根，`modifier` 移到 FAB 上（MainScreen 调用点 `Modifier.guideTarget(…)` 语义不变，仍量 FAB 本体）。

- [ ] **Step 2: 编译验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL（MainScreen 的 `FabMenu(onStartInput = actions::startNew, …)` 与 `NoopMainActions` 均零改动）

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/main/FabMenu.kt
git commit -m "feat(fab): + 号直进手动输入——语音/图片入口暂时隐藏（框架保留）"
```

---

### Task 2: 引导层多洞支持（GuideStep.targetKeys + 绘制/动画/定位）

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/guide/GuideOverlay.kt`

本文件同时承载步骤定义与绘制，多洞改造与步骤表更新在一起做（拆开会产生中间态编译不过）。

- [ ] **Step 1: GuideStep 与 GuideSteps 改多洞 + 声明编辑态标记 + 新两步**

`GuideStep` 定义替换为：

```kotlin
/** 引导步骤定义：targetKeys 空 = 无聚光（居中欢迎卡）。 */
data class GuideStep(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val targetKeys: List<String>,
    /** 本步追加通知权限提示（声明式标记，不耦合 targetKey 字符串）。 */
    val appendPermissionNote: Boolean = false,
    /** 本步 mock 显示编辑态（表单+双梯形三段式）；false = 非编辑主页。 */
    val showEditingMock: Boolean = false,
)

internal val GuideSteps = listOf(
    GuideStep(R.string.guide_welcome_title, R.string.guide_welcome_body, targetKeys = emptyList()),
    GuideStep(R.string.guide_fab_title, R.string.guide_fab_body, listOf(GuideKeys.FAB)),
    GuideStep(R.string.guide_form_title, R.string.guide_form_body, listOf(GuideKeys.FORM_AREA), showEditingMock = true),
    GuideStep(R.string.guide_trap_title, R.string.guide_trap_body, listOf(GuideKeys.TRAP_TOP, GuideKeys.TRAP_BOTTOM), showEditingMock = true),
    GuideStep(R.string.guide_card_title, R.string.guide_card_body, listOf(GuideKeys.FIRST_CARD)),
    GuideStep(R.string.guide_buckets_title, R.string.guide_buckets_body, listOf(GuideKeys.BUCKET_AREA), appendPermissionNote = true),
    GuideStep(R.string.guide_topbar_title, R.string.guide_topbar_body, listOf(GuideKeys.TOPBAR)),
)
```

- [ ] **Step 2: GuideOverlay 消费点改多洞**

`targetHole` 单 Rect 逻辑替换为多洞列表。删除原 `var animatedHole by remember { … }` 与其 `LaunchedEffect`，替换为：

```kotlin
val targetHoles = step.targetKeys
    .mapNotNull { key -> holder.targets[key] }
    .map { it.translate(-overlayOffset).inflate(holePaddingPx) }

// 镂空滑向新目标；洞数量与上一步相同 → 逐洞补间；数量变化/首帧（尺寸未测得）直接落位
var animatedHoles by remember { mutableStateOf<List<Rect>?>(null) }
LaunchedEffect(targetHoles, overlaySize) {
    if (targetHoles.isEmpty()) {
        // 目标已消失（onDispose 摘除注册）：清陈旧镂空，退回无聚光布局，
        // 否则聚光灯悬在幽灵位置、说明卡对着不存在的矩形定位
        animatedHoles = null
        return@LaunchedEffect
    }
    if (overlaySize == IntSize.Zero) return@LaunchedEffect
    val from = animatedHoles
    if (from == null || from.size != targetHoles.size) {
        animatedHoles = targetHoles
        return@LaunchedEffect
    }
    // 洞数相同：逐洞同步补间（每帧聚合各 Animatable 当前值）
    val anims = from.mapIndexed { i, rect -> Animatable(rect, Rect.VectorConverter) }
    anims.forEachIndexed { i, anim ->
        launch { anim.animateTo(targetHoles[i], tween(280)) }
    }
    while (anims.any { it.isRunning }) {
        animatedHoles = anims.map { it.value }
        kotlinx.coroutines.delay(16)
    }
    animatedHoles = targetHoles
}
val holes = animatedHoles ?: targetHoles.ifEmpty { null }
```

（`launch` 来自 `LaunchedEffect` 的 `CoroutineScope` 接收者；`import kotlinx.coroutines.delay` 加入。）

Canvas 绘制把单 `hole?.let { addRoundRect(…) }` 改为循环：

```kotlin
Canvas(Modifier.fillMaxSize()) {
    val corner = CornerRadius(14.dp.toPx())
    // EvenOdd 填充：整屏矩形 XOR 每个镂空圆角矩形 = 带洞遮罩（多洞天然支持）
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(Offset.Zero, size))
        holes.orEmpty().forEach { holeRect ->
            addRoundRect(RoundRect(rect = holeRect, cornerRadius = corner))
        }
    }
    drawPath(path, ScrimColor)
    holes.orEmpty().forEach { holeRect ->
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = holeRect.topLeft,
            size = holeRect.size,
            cornerRadius = corner,
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}
```

说明卡定位：`holes` 非空时取包围盒（洞的 union）传给既有 `cardOffsetY`（签名不变）：

```kotlin
} else {
    val gapPx = with(LocalDensity.current) { 24.dp.toPx() }
    // 说明卡按所有洞的包围盒定位：单洞=原行为；双洞（上下梯形）包围盒≈整屏 → 卡片居中
    val bounds = holes!!.reduce { acc, rect -> acc.union(rect) }
    val cardY = cardOffsetY(bounds, overlaySize.height, cardHeightPx, gapPx)
    // …… GuideCard(…) 原样，modifier 链不变
}
```

`Rect.union` 是 `androidx.compose.ui.geometry.Rect` 成员函数（返回包含两者的最小矩形），无需 import。`hole == null` 分支条件同步改为 `holes == null`。`import kotlinx.coroutines.delay` 加入。

- [ ] **Step 3: 编译验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin -q`
Expected: FAIL——`R.string.guide_form_title` 等未解析（Task 3 才加字符串）。这是预期的失败，直接进 Task 3，不要在本任务里加字符串。

---

### Task 3: 字符串（步骤文案 + mock 条目名）×3 locale

**Files:**
- Modify: `app/src/main/res/values/strings.xml`（在 `guide_mock_vegetable` 之后加）

- [ ] **Step 1: 三份 strings.xml 各加 7 条**

`values/strings.xml`：

```xml
<string name="guide_form_title">编辑界面</string>
<string name="guide_form_body">名称、分类、保质期在这里填写；保存后条目会进入「本次添加」。</string>
<string name="guide_trap_title">切换查看</string>
<string name="guide_trap_body">屏幕上下有梯形按钮，滑动可以同时查看其它界面：上面是本次添加，下面是已有的条目。</string>
<string name="guide_mock_form_name">草莓</string>
<string name="guide_mock_session_bread">面包</string>
<string name="guide_mock_session_egg">鸡蛋</string>
```

`values-en/strings.xml`：

```xml
<string name="guide_form_title">Edit screen</string>
<string name="guide_form_body">Fill in the name, category and shelf life here; saved items go to "Just added".</string>
<string name="guide_trap_title">Switch views</string>
<string name="guide_trap_body">Trapezoid buttons at the top and bottom edges — swipe to see the other views: items added this session above, existing items below.</string>
<string name="guide_mock_form_name">Strawberry</string>
<string name="guide_mock_session_bread">Bread</string>
<string name="guide_mock_session_egg">Egg</string>
```

`values-zh-rTW/strings.xml`：

```xml
<string name="guide_form_title">編輯介面</string>
<string name="guide_form_body">名稱、分類、保存期限在這裡填寫；儲存後條目會進入「本次新增」。</string>
<string name="guide_trap_title">切換檢視</string>
<string name="guide_trap_body">螢幕上下有梯形按鈕，滑動可以同時檢視其它介面：上面是本次新增，下面是已有的條目。</string>
<string name="guide_mock_form_name">草莓</string>
<string name="guide_mock_session_bread">麵包</string>
<string name="guide_mock_session_egg">雞蛋</string>
```

注意：先在 zh-rTW 里确认「本次添加」的既有译法（搜 `pinned_header`），guide_trap_body/form_body 措辞与其一致。

- [ ] **Step 2: 编译验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit（Task 2 + 3 一起）**

```bash
git add app/src/main/java/com/battor/freshmate/ui/guide/GuideOverlay.kt app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml app/src/main/res/values-zh-rTW/strings.xml
git commit -m "feat(guide): 引导层多洞聚光 + 新增编辑界面/上下梯形两步文案（7 步）"
```

---

### Task 4: mock 编辑态派生（TDD）

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/guide/GuideMockContent.kt`
- Test: `app/src/test/java/com/battor/freshmate/ui/guide/GuideMockContentTest.kt`

- [ ] **Step 1: 写失败测试（追加到既有 GuideMockContentTest）**

```kotlin
@Test
fun `编辑步返回预填表单与会话条目`() {
    val content = GuideMockContent(
        "牛奶", "酸奶", "蔬菜", now,
        formName = "草莓", breadName = "面包", eggName = "鸡蛋",
    )
    val state = content.uiStateForStep(isEditingStep = true) // 步 3/4 = 编辑态
    val editing = requireNotNull(state.editing)
    assertEquals("草莓", editing.name)
    assertEquals(Category.FRUITS_VEG, editing.category)
    assertEquals("3", editing.shelfLifeValue)
    assertEquals(now, editing.createdAt)
    // 会话区：面包+鸡蛋，新→旧（createdAt 倒序），不入桶
    assertEquals(listOf("鸡蛋", "面包"), state.pinnedItems.map { it.name })
    assertEquals(setOf(-4L, -5L), state.sessionItemIds)
    // 桶里仍是原 3 条 mock，无会话条目混入
    assertEquals(3, state.buckets.size)
    assertTrue(state.buckets.all { bucket -> bucket.items.all { it.id in -3L..-1L } })
    // 基础态未被污染（派生函数不修改原对象）
    assertNull(content.uiState.editing)
}

@Test
fun `非编辑步返回原主页状态`() {
    val content = GuideMockContent("牛奶", "酸奶", "蔬菜", now)
    val state = content.uiStateForStep(isEditingStep = false)
    assertNull(state.editing)
    assertTrue(state.pinnedItems.isEmpty())
    assertEquals(content.uiState, state)
}
```

文件头补 `import com.battor.freshmate.data.Category`。

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.battor.freshmate.ui.guide.GuideMockContentTest"`
Expected: FAIL——`uiStateForStep` 未定义

- [ ] **Step 3: 实现 uiStateForStep**

`GuideMockContent.kt` 追加（类内）：

```kotlin
/**
 * 按引导步骤派生界面（specs/2026-09-26-fab-input-and-guide）：isEditingStep = true
 * （对应 GuideSteps 中 showEditingMock = true 的两步，由调用方传入——本层不依赖
 * GuideOverlay/GuideSteps，保持纯数据无 UI 依赖）返回预填表单 + 会话条目的三段式，
 * 否则返回基础主页态。纯函数，不修改基础 uiState。
 * 叙事：刚录完面包和鸡蛋（本次添加），正在录草莓（表单预填）→ 预览卡有内容、
 * 顶栏 ✓ 出现、FORM 页恒有上下两个梯形。
 */
fun uiStateForStep(isEditingStep: Boolean): MainViewModel.UiState {
    if (!isEditingStep) return uiState
    val bread = FoodItem(
        id = -4L, name = breadName, category = Category.STAPLE,
        productionDate = null, shelfLifeDays = 5,
        quantity = null, createdAt = now.minusMinutes(8),
    )
    val egg = FoodItem(
        id = -5L, name = eggName, category = Category.MEAT_EGG,
        productionDate = null, shelfLifeDays = 15,
        quantity = null, createdAt = now.minusMinutes(3),
    )
    return uiState.copy(
        editing = MainViewModel.EditingState(
            name = formName,
            category = Category.FRUITS_VEG,
            shelfLifeValue = "3",
            createdAt = now,
        ),
        sessionItemIds = setOf(-4L, -5L),
        pinnedItems = listOf(egg, bread), // 新→旧（createdAt 倒序）
        buckets = uiState.buckets,        // 会话条目不重复入桶（mock 直接给派生值）
    )
}
```

类主构造加三个名称参数（放在既有 vegetableName 之后，全默认化不破坏旧调用）：

```kotlin
class GuideMockContent(
    milkName: String,
    yogurtName: String,
    vegetableName: String,
    now: LocalDateTime = LocalDateTime.now(),
    private val formName: String = "",
    private val breadName: String = "",
    private val eggName: String = "",
)
```

签名里 `isEditingStep: Boolean` 由调用方传 `GuideSteps[stepIndex].showEditingMock`——mock 层不 import GuideSteps/GuideOverlay，保持纯数据无 UI 依赖（与既有 KDoc 一致）。

- [ ] **Step 4: 跑测试确认通过**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.battor.freshmate.ui.guide.GuideMockContentTest"`
Expected: PASS（全部）

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/guide/GuideMockContent.kt app/src/test/java/com/battor/freshmate/ui/guide/GuideMockContentTest.kt
git commit -m "feat(guide): mock 编辑态纯函数派生——预填表单+会话两条目（TDD）"
```

---

### Task 5: 聚光 key 接线（GuideKeys → EditingPager → MainContent → GuideScreen）

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/common/GuideTargets.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/EditingPager.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/guide/GuideScreen.kt`

- [ ] **Step 1: GuideKeys 加三个 key（GuideTargets.kt）**

```kotlin
object GuideKeys {
    const val FAB = "fab"
    const val FIRST_CARD = "first_card"
    const val BUCKET_AREA = "bucket_area"
    const val TOPBAR = "topbar"
    const val FORM_AREA = "form_area"
    const val TRAP_TOP = "trap_top"
    const val TRAP_BOTTOM = "trap_bottom"
}
```

- [ ] **Step 2: EditingPager 加三个可选 key 并挂点（EditingPager.kt）**

签名追加（放 `onPageChanged` 之前，KDoc 说明同款通道）：

```kotlin
    /** 引导模式聚光 key：表单区 / 上下梯形；主流程为 null 零开销（guideFirstCardKey 同款通道） */
    guideFormKey: String? = null,
    guideTrapTopKey: String? = null,
    guideTrapBottomKey: String? = null,
```

FORM 分支把预览+表单包进带 guideTarget 的 Column：

```kotlin
EditPageKind.FORM -> {
    Column(Modifier.guideTarget(guideFormKey)) {
        // 预览在编辑区上方（走查反馈确认）、拉开距离：列间距 12 + 额外 12 = 24dp
        FormPreviewCard(state = editing, saved = editingTarget, now = now)
        Spacer(Modifier.height(12.dp))
        ItemForm(
            state = editing,
            onStateChange = onStateChange,
            onPlaceholderHint = onPlaceholderHint,
            now = now,
        )
    }
}
```

（内层 EditScrollColumn 已有 `spacedBy(12.dp)`，包一层 Column 不改变外观；guideTarget 为 null 时返回原 Modifier。`import com.battor.freshmate.ui.common.guideTarget`。）

TrapIndicator 加 `modifier: Modifier = Modifier` 参数，`Text(modifier = modifier.clip(…).background(…)…)`；两个调用点分别传 `Modifier.guideTarget(guideTrapTopKey)` / `Modifier.guideTarget(guideTrapBottomKey)`。

- [ ] **Step 3: MainContent 透传（MainScreen.kt）**

`MainContent` 签名在 `guideFirstBucketKey` 后追加：

```kotlin
    /** 引导模式聚光「编辑表单区/上下梯形」：透传给 EditingPager，主流程为 null 零影响 */
    guideFormKey: String? = null,
    guideTrapTopKey: String? = null,
    guideTrapBottomKey: String? = null,
```

`EditingPager(…)` 调用点在 `onPageChanged` 前追加：

```kotlin
                        guideFormKey = guideFormKey,
                        guideTrapTopKey = guideTrapTopKey,
                        guideTrapBottomKey = guideTrapBottomKey,
```

- [ ] **Step 4: GuideScreen 接线（GuideScreen.kt）**

mock 构造补三个新名称（`milkName`/`yogurtName`/`vegetableName` 的取法照旧）：

```kotlin
    val strawberryName = stringResource(R.string.guide_mock_form_name)
    val breadName = stringResource(R.string.guide_mock_session_bread)
    val eggName = stringResource(R.string.guide_mock_session_egg)
    val mock = remember(milkName, yogurtName, vegetableName, strawberryName, breadName, eggName) {
        GuideMockContent(
            milkName, yogurtName, vegetableName,
            formName = strawberryName, breadName = breadName, eggName = eggName,
        )
    }
```

`MainContent` 调用改为按步骤派生状态 + 传全部 key：

```kotlin
        CompositionLocalProvider(LocalGuideState provides holder) {
            MainContent(
                state = remember(mock, machine.current) {
                    mock.uiStateForStep(isEditingStep = GuideSteps[machine.current].showEditingMock)
                },
                errorEvent = null,
                updateHint = null,
                actions = NoopMainActions,
                guideFirstCardKey = GuideKeys.FIRST_CARD,
                guideFirstBucketKey = GuideKeys.BUCKET_AREA,
                guideFormKey = GuideKeys.FORM_AREA,
                guideTrapTopKey = GuideKeys.TRAP_TOP,
                guideTrapBottomKey = GuideKeys.TRAP_BOTTOM,
                handleSystemBack = false,
            )
        }
```

（key 全部恒传：目标控件不可见时无布局 → onDispose 自动摘注册，非编辑步无幽灵洞。）

- [ ] **Step 5: 全量验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug lintDebug`
Expected: BUILD SUCCESSFUL（既有 CardOffsetYTest 等不受影响）

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/common/GuideTargets.kt app/src/main/java/com/battor/freshmate/ui/main/EditingPager.kt app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt app/src/main/java/com/battor/freshmate/ui/guide/GuideScreen.kt
git commit -m "feat(guide): 编辑态聚光接线——表单区/上下梯形 guideTarget 通道透传"
```

---

## 验证（全计划完成后）

1. 全量：`JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug lintDebug` 全绿
2. 装机走查清单：
   - FAB 点击直接进手动表单（无菜单）；表单内无语音/图片占位图标行
   - 引导 7 步顺序：欢迎 → FAB → 编辑表单区（编辑态界面、表单预填"草莓"）→ 上下梯形（双洞同时高亮、文案含"滑动可以同时查看其它界面"）→ 滑动删除 → 到期分桶 → 历史与设置
   - 步 3→4 洞平滑动画；步 4→5 退回非编辑主页、梯形洞消失无残影
   - 深浅主题下双洞白边可辨；跳过/完成行为如旧
3. 最终提交后按惯例不 push（用户自行推 Gitee）

## 执行备注

- 计划批准后按惯例问完整/轻量模式（memory：轻量 = 主流程实现 + 合并审查）
- 不 `git add -A`；resources/ 下未跟踪文件与 .codegraph/.superpowers 不入库
