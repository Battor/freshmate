# 配色改造实现计划（多巴胺配色落地）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落实 `docs/superpowers/specs/2026-09-26-color-scheme-design.md`——固定品牌配色、分类色系统、卡片余量色层、表单底随分类联动、梯形配色。

**Architecture:** 品牌色板从此是唯一来源（删动态取色分支）；新增 `CategoryColors.kt` 集中管理 8 分类 × 4 色值；卡片余量色层用"状态 on 色 18% alpha + 右缘渐隐"绘制在 `FoodItemCard` 背景上，桶头进度条整体删除；纯函数 `remainingFraction` 落在 `util/ExpiryUtils.kt` 并配单测。

**Tech Stack:** Jetpack Compose + Material 3、JUnit4。Gradle 一律加前缀 `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`。

**仓库纪律：** 永不 `git add -A`（未跟踪的 `.codegraph/`、`.superpowers/`、`resources/*` 不得入库）；提交信息结尾 `Co-Authored-By: Claude <noreply@anthropic.com>`；在 main 上直接开发（项目惯例）。

---

### Task 1: 主题层——删除动态取色

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/theme/Theme.kt`

- [ ] **Step 1: 替换 FreshMateTheme 与 imports**

删除 imports：`android.os.Build`、`androidx.compose.material3.dynamicDarkColorScheme`、`androidx.compose.material3.dynamicLightColorScheme`、`androidx.compose.ui.platform.LocalContext`。函数体替换为：

```kotlin
@Composable
fun FreshMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // 多巴胺配色落地（specs/2026-09-26）：品牌色板是唯一来源，不再被 Android 12+ 动态取色覆盖
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val palette = if (darkTheme) DarkStatusPalette else LightStatusPalette
    CompositionLocalProvider(LocalStatusColors provides palette) {
        MaterialTheme(colorScheme = colorScheme, shapes = AppShapes, content = content)
    }
}
```

调用方只有 `MainActivity.kt:51`（只传 `darkTheme`），删除 `dynamicColor` 参数无破坏。

- [ ] **Step 2: 编译验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/theme/Theme.kt
git commit -m "feat(theme): 固定品牌配色——删除 Android 12+ 动态取色分支"
```

### Task 2: 纯函数 remainingFraction（TDD）

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/util/ExpiryUtils.kt`
- Test: `app/src/test/java/com/battor/freshmate/util/ExpiryUtilsTest.kt`

- [ ] **Step 1: 写失败测试**

在 `ExpiryUtilsTest.kt` 追加（沿用该文件现有 JUnit4 风格）：

```kotlin
// ---- remainingFraction（卡片余量色层，specs/2026-09-26）----

@Test
fun `未填生产日期按录入日起算 剩余8天除以总10天为08`() {
    val createdAt = LocalDateTime.of(2026, 9, 1, 8, 0)
    val now = createdAt.plusDays(2)
    // expiry = createdAt + 10d；total = 10d；remaining = 8d
    assertEquals(0.8f, remainingFraction(null, createdAt, 10, now), 0.001f)
}

@Test
fun `填生产日期时到期日含当天 比例约05`() {
    val production = LocalDate.of(2026, 1, 1)
    val createdAt = LocalDateTime.of(2026, 1, 1, 10, 0)
    // expiry = 2026-01-11 23:59:59（生产日 + 10 天当天末尾）；total ≈ 11 天
    val now = LocalDateTime.of(2026, 1, 6, 12, 0)
    assertEquals(0.5f, remainingFraction(production, createdAt, 10, now), 0.01f)
}

@Test
fun `过期归零 恰好到期时刻也归零`() {
    val createdAt = LocalDateTime.of(2026, 9, 1, 8, 0)
    val expiry = createdAt.plusDays(10)
    assertEquals(0f, remainingFraction(null, createdAt, 10, expiry.plusSeconds(1)), 0.001f)
    assertEquals(0f, remainingFraction(null, createdAt, 10, expiry), 0.001f)
}

@Test
fun `now早于起点或总时长异常时 clamp 到1`() {
    val createdAt = LocalDateTime.of(2026, 9, 1, 8, 0)
    // now 早于录入时刻
    assertEquals(1f, remainingFraction(null, createdAt, 10, createdAt.minusDays(1)), 0.001f)
    // shelfLife = 0 → total 为零，防御性归 1
    assertEquals(1f, remainingFraction(null, createdAt, 0, createdAt), 0.001f)
}
```

- [ ] **Step 2: 运行确认失败**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.util.ExpiryUtilsTest"`
Expected: FAIL（`remainingFraction` 未定义）

- [ ] **Step 3: 实现**

`ExpiryUtils.kt` 追加（补 import `java.time.Duration`）：

```kotlin
/**
 * 余量比例（卡片余量色层，specs/2026-09-26）：剩余 ÷ 总保质期，油表语义——越接近 1 越新鲜。
 * 过期（含恰好到期）归 0；总时长 ≤ 0 的异常数据防御性归 1；结果 clamp [0,1]。
 * 起点 = 生产日期当天零点（未填则录入时刻），与 expiryDateTime 的口径一致。
 */
fun remainingFraction(
    productionDate: LocalDate?,
    createdAt: LocalDateTime,
    shelfLifeDays: Int,
    now: LocalDateTime,
): Float {
    val expiry = expiryDateTime(productionDate, createdAt, shelfLifeDays)
    val total = Duration.between(productionDate?.atStartOfDay() ?: createdAt, expiry)
    if (total.isZero || total.isNegative) return 1f
    val fraction = Duration.between(now, expiry).toNanos().toFloat() / total.toNanos()
    return fraction.coerceIn(0f, 1f)
}
```

- [ ] **Step 4: 运行确认通过**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.util.ExpiryUtilsTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/util/ExpiryUtils.kt app/src/test/java/com/battor/freshmate/util/ExpiryUtilsTest.kt
git commit -m "feat(util): remainingFraction 余量比例纯函数——油表语义+边界防御"
```

### Task 3: 分类色系统 CategoryColors + 图标接线

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/ui/theme/CategoryColors.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/FoodItemCard.kt`（Icon tint）
- Modify: `app/src/main/java/com/battor/freshmate/ui/history/HistoryScreen.kt:217-221`（Icon tint）
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/ItemForm.kt:255-291`（FormPreviewCard 图标 tint）

- [ ] **Step 1: 新建 CategoryColors.kt**

```kotlin
package com.battor.freshmate.ui.theme

import androidx.compose.ui.graphics.Color
import com.battor.freshmate.data.Category

/** 分类色系统（多巴胺配色，specs/2026-09-26）：图标按分类染色，表单底随分类联动。 */
private data class CategoryColors(
    val iconLight: Color,
    val iconDark: Color,
    val formLight: Color,
    val formDark: Color,
)

// 图标浅色 = 饱和正色（压粉彩卡底）；图标深色 = 同色相提亮降饱和（压深色卡底）；
// 表单底 = 极淡色调（深色版 = 图标深色 12% 混入 #242620，预计算）
private val categoryColorMap = mapOf(
    Category.FRUITS_VEG to CategoryColors(Color(0xFF2E7D32), Color(0xFF81C784), Color(0xFFE3F0DC), Color(0xFF2F392C)),
    Category.MEAT_EGG to CategoryColors(Color(0xFFC62828), Color(0xFFE57373), Color(0xFFFAE3DC), Color(0xFF3B2F2C)),
    Category.DAIRY to CategoryColors(Color(0xFF1E88E5), Color(0xFF64B5F6), Color(0xFFE1EDF9), Color(0xFF2C373A)),
    Category.DRINK to CategoryColors(Color(0xFF00ACC1), Color(0xFF4DD0E1), Color(0xFFDCF2F5), Color(0xFF293A37)),
    Category.SNACK to CategoryColors(Color(0xFFF57C00), Color(0xFFFFB74D), Color(0xFFFDE6DC), Color(0xFF3E3725)),
    Category.STAPLE to CategoryColors(Color(0xFF795548), Color(0xFFA1887F), Color(0xFFEFE8E4), Color(0xFF33322B)),
    Category.FROZEN to CategoryColors(Color(0xFF78909C), Color(0xFFB0BEC5), Color(0xFFDFF4F8), Color(0xFF353834)),
    Category.CONDIMENT to CategoryColors(Color(0xFFAFB42B), Color(0xFFDCE775), Color(0xFFF3F2D6), Color(0xFF3A3D2A)),
)

/** 分类图标色：全 app 一致表达"分类身份"，不再借用状态 on 色。 */
fun categoryIconColor(category: Category, darkTheme: Boolean): Color =
    if (darkTheme) categoryColorMap.getValue(category).iconDark
    else categoryColorMap.getValue(category).iconLight

/** 表单卡底色：随所选分类联动的极淡色调（新增默认果蔬淡绿；编辑按条目分类着色）。 */
fun categoryFormColor(category: Category, darkTheme: Boolean): Color =
    if (darkTheme) categoryColorMap.getValue(category).formDark
    else categoryColorMap.getValue(category).formLight
```

- [ ] **Step 2: FoodItemCard 图标 tint**

`FoodItemCard.kt`：`Icon(categoryIcon(...), tint = onColor, ...)` 改为

```kotlin
Icon(
    categoryIcon(item.category),
    contentDescription = stringResource(item.category.labelRes),
    tint = categoryIconColor(item.category, isSystemInDarkTheme()),
    modifier = Modifier.size(28.dp),
)
```

新增 imports：`androidx.compose.foundation.isSystemInDarkTheme`、`com.battor.freshmate.ui.theme.categoryIconColor`。

- [ ] **Step 3: HistoryScreen 图标 tint**

`HistoryScreen.kt:217-221` 同样把 `tint = onColor` 改为 `tint = categoryIconColor(item.category, isSystemInDarkTheme())`，新增同样两个 imports（`isSystemInDarkTheme` + `categoryIconColor`）。`onColor` 仍被名称/状态文字使用，不删。

- [ ] **Step 4: FormPreviewCard 图标 tint**

`ItemForm.kt` 中 FormPreviewCard：删除「需求-6 走查点子：图标按到期状态染色」的 `statusAccent` 计算块（`liveExpiry?.let { ... } ?: onColor`），Icon 改为

```kotlin
Icon(
    categoryIcon(state.category),
    contentDescription = null,
    tint = categoryIconColor(state.category, isSystemInDarkTheme()),
    modifier = Modifier.size(28.dp),
)
```

`liveExpiry` 仍被下方 `expiryDisplay` 使用，保留。新增 imports：`androidx.compose.foundation.isSystemInDarkTheme`、`com.battor.freshmate.ui.theme.categoryIconColor`。

- [ ] **Step 5: 编译 + 全量单测**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/theme/CategoryColors.kt app/src/main/java/com/battor/freshmate/ui/main/FoodItemCard.kt app/src/main/java/com/battor/freshmate/ui/history/HistoryScreen.kt app/src/main/java/com/battor/freshmate/ui/main/ItemForm.kt
git commit -m "feat(ui): 分类色系统——图标按分类染色(深浅双变体)，与状态色解耦"
```

### Task 4: 卡片余量色层 + 删桶头进度条

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/FoodItemCard.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt:103-106,555-587,629-652`

- [ ] **Step 1: FoodItemCard 叠色层**

文件顶部（imports 之后）加常量：

```kotlin
/** 卡片余量色层（specs/2026-09-26）：油表方向，on 色 18% alpha，右缘 12% 卡宽渐隐。 */
private const val CARD_FILL_ALPHA = 0.18f
private const val CARD_FILL_FADE_FRACTION = 0.12f
```

Card 内容（现 `Row(...)` 整块）外包 `Box`，色层用 `drawBehind` 画在内容底下；`val card` 内改为：

```kotlin
Card(
    onClick = onClick,
    enabled = enabled,
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(
        containerColor = container,
        contentColor = onColor,
        disabledContainerColor = container,
        disabledContentColor = onColor,
    ),
    modifier = Modifier.fillMaxWidth(),
) {
    // 余量色层：剩余越多覆盖越长（油表）；过期 fraction=0 无色层。on 色低 alpha 在
    // 浅色主题呈加深、深色主题呈提亮，文字对比度不受影响
    val fillFraction = remainingFraction(item.productionDate, item.createdAt, item.shelfLifeDays, now)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (fillFraction > 0f) {
                    val fillWidth = size.width * fillFraction
                    val fade = size.width * CARD_FILL_FADE_FRACTION
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to onColor.copy(alpha = CARD_FILL_ALPHA),
                            ((fillWidth - fade) / fillWidth).coerceIn(0f, 1f) to
                                onColor.copy(alpha = CARD_FILL_ALPHA),
                            1f to Color.Transparent,
                        ),
                        size = Size(fillWidth, size.height),
                    )
                }
            },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // …Row 内现有内容一字不改（Task 3 已换 icon tint）…
        }
    }
}
```

新增 imports：`androidx.compose.ui.draw.drawBehind`、`androidx.compose.ui.geometry.Size`、`androidx.compose.ui.graphics.Brush`、`com.battor.freshmate.util.remainingFraction`（`Color` 已有）。

- [ ] **Step 2: 删桶头进度条**

`MainScreen.kt`：

1. 删常量 `BUCKET_BAR_WIDTH_SCALE`（:103）与 `BUCKET_BAR_TRACK_ALPHA`（:106）
2. 删 `BucketProgressBar` 整个函数（:555-587）及其 KDoc
3. `BucketBox` 内 `if (status != null)` 分支：删 `rememberTextMeasurer`、`textStyle`、`textWidth`、`barWidth` 计算与整个 `Row`（进度条 + Spacer + Text），替换为：

```kotlin
if (status != null) {
    val statusColors = LocalStatusColors.current.of(status)
    Text(
        header,
        style = MaterialTheme.typography.labelLarge,
        color = statusColors.on,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.End,
    )
} else {
```

（桶头文字仍右对齐、仍用状态 on 色；进度条及其测量逻辑全部移除——信息下沉到卡片余量色层。）

4. 新增 import `androidx.compose.ui.text.style.TextAlign`；删除仅剩进度条在用的 imports——先 `grep -n "rememberTextMeasurer\|TextMeasurer\|Canvas\|RoundRect\|CornerRadius\|clipPath\|drawLine" app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt` 确认无其他引用，再删对应 import 行（Kotlin 未用 import 只是 warning，但保持文件干净）。

- [ ] **Step 3: 编译 + 全量单测**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/main/FoodItemCard.kt app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt
git commit -m "feat(ui): 卡片余量色层(油表方向)落地；桶头进度条删除——痛点1以删除解决"
```

### Task 5: 表单底随分类联动

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/ItemForm.kt:124-131`

- [ ] **Step 1: ItemForm 卡片底色**

`ItemForm` 的 Card（现注释「需求-6：编辑区中性化」）改为：

```kotlin
// 编辑区底色随分类联动（specs/2026-09-26）：表单本身成为"分类指示器"；
// 输入框维持 surfaceContainerLowest 中性填充，与任何分类底色相容
Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(
        containerColor = categoryFormColor(state.category, isSystemInDarkTheme()),
    ),
    modifier = Modifier.fillMaxWidth(),
) {
```

新增 imports：`com.battor.freshmate.ui.theme.categoryFormColor`（`isSystemInDarkTheme` Task 3 已加）。

- [ ] **Step 2: 编译**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/main/ItemForm.kt
git commit -m "feat(ui): 表单底色随分类联动——新增默认果蔬淡绿，编辑按条目分类着色"
```

### Task 6: 梯形配色（中绿 + 白字）

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/EditingPager.kt:220-235`

- [ ] **Step 1: Color.kt 加 TrapFill**

```kotlin
// 编辑态梯形跳段提示（specs/2026-09-26）：中绿实色深浅主题共用——小面积功能件要白字可读
val TrapFill = Color(0xFF4E8B54)
```

- [ ] **Step 2: TrapIndicator 换色**

`EditingPager.kt` 的 `TrapIndicator`：`color = MaterialTheme.colorScheme.onPrimaryContainer` 改为 `color = Color.White`；`.background(MaterialTheme.colorScheme.primaryContainer)` 改为 `.background(TrapFill)`。新增 imports：`androidx.compose.ui.graphics.Color`、`com.battor.freshmate.ui.theme.TrapFill`。`TrapezoidShape`（6dp 贝塞尔圆角）不动。

- [ ] **Step 3: 编译**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/theme/Color.kt app/src/main/java/com/battor/freshmate/ui/main/EditingPager.kt
git commit -m "feat(ui): 梯形改中绿实色+白字——深浅主题共用，圆角形状不变"
```

### Task 7: 全量验证

- [ ] **Step 1: 全量构建**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug lintDebug`
Expected: BUILD SUCCESSFUL（三任务全绿）

- [ ] **Step 2: 装机走查清单（交用户）**

- 主页桶头不再有进度条，仅右对齐文字；每张卡片背景有"油表"色层（剩得多覆盖长、过期无色层）
- 图标：果蔬绿/肉蛋红/乳品蓝/饮料青/零食橙/主食棕/冷冻蓝灰/调味黄绿，同分类不同桶颜色一致；深色模式为亮色变体
- 新增页底色 = 果蔬淡绿；切分类底色实时变；编辑既有条目按其分类着色；深色模式为暗调变体
- 编辑态梯形中绿底白字，四角圆角不变，深浅主题都可读
- 深浅主题各过一遍以上四项

## 不动的部分

六档状态色板数值、卡片/桶头结构与交互、滑动删除/还原背景、通知、导航、深浅主题切换机制。
