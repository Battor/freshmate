# 需求-6：编辑区配色中性化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 编辑区（表单 Card + 当前操作项目虚线框 + 输入框 + 到期预览）从 `primaryContainer` 色系换成中性 surface 色阶，保留全局动态取色。

**Architecture:** 纯换 token，全部改动在 `ItemForm.kt` 单文件：表单 Card → `surfaceContainerHigh`，虚线框底 → `surfaceContainerHighest`、描边/文字 → `onSurfaceVariant`，四个 OutlinedTextField 统一近白填充（`surfaceContainerLowest`）经一个 `@Composable` 私有 helper 提供，`ExpiryPreview` 文字 → `onSurfaceVariant`。无逻辑改动、无新单测，验证靠构建链 + 装机走查。

**Tech Stack:** Jetpack Compose Material 3（`MaterialTheme.colorScheme` surface 色阶、`OutlinedTextFieldDefaults.colors`）。

**Spec:** `docs/superpowers/specs/2026-08-22-editing-area-neutral-colors-design.md`

---

### Task 1: 表单 Card、虚线框、到期预览换中性 token

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/ItemForm.kt:120-126`（ItemForm 的 Card colors）
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/ItemForm.kt:241-252`（EditingTargetCard 的 onColor 与 background）
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/ItemForm.kt:320`（ExpiryPreview 文字色）

- [ ] **Step 1: 表单 Card 容器色换 surfaceContainerHigh**

`ItemForm` 内的 Card（约 120 行）：

```kotlin
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
```

（只改 `containerColor` 一行的 token，其余不动。）

- [ ] **Step 2: 虚线框底色与描边/文字色换中性**

`EditingTargetCard`（约 241 行）开头取色：

```kotlin
    val onColor = MaterialTheme.colorScheme.onSurfaceVariant
```

（原为 `onPrimaryContainer`；函数内所有 `onColor` 引用不动。）

其 `Row` modifier 中的背景（约 252 行）：

```kotlin
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
```

（原为 `primaryContainer`；与表单底 `surfaceContainerHigh` 拉开一档。）

- [ ] **Step 3: 到期预览文字换 onSurfaceVariant**

`ExpiryPreview` 末行（约 320 行）：

```kotlin
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
```

（原为 `onPrimaryContainer`。）

- [ ] **Step 4: 编译验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/main/ItemForm.kt
git commit -m "feat(ui): 编辑区容器与虚线框换中性 surface 色阶"
```

### Task 2: 四个输入框统一近白填充

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/ItemForm.kt`（新增 helper；ItemForm 内名称/保质期/数量三处 OutlinedTextField + ProductionDateField 内日期一处）

- [ ] **Step 1: 新增 neutralFieldColors helper 与 import**

文件 import 区补：

```kotlin
import androidx.compose.material3.OutlinedTextFieldDefaults
```

（按字母序插入到既有 `androidx.compose.material3` import 组内。）

文件顶部非 composable 区之后（`ItemForm` 函数之前）新增：

```kotlin
/** 需求-6：编辑区中性化——输入框统一近白填充，不再透出卡片底色。 */
@Composable
private fun neutralFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
)
```

- [ ] **Step 2: 四处 OutlinedTextField 挂 colors**

每处的 `OutlinedTextField(...)` 参数列表里加一行 `colors = neutralFieldColors(),`（放在 `singleLine = true,` 之前、`label`/`supportingText` 之后均可，与邻居风格一致即可）。四处位置：

1. `ItemForm` 名称字段（约 137 行，`label = { Text(stringResource(R.string.field_name)) }` 那个）；
2. `ItemForm` 保质期字段（约 170 行，`keyboardOptions = ...` 那个）；
3. `ItemForm` 数量字段（约 222 行，`label = { Text(stringResource(R.string.field_quantity)) }` 那个）；
4. `ProductionDateField` 日期字段（约 338 行，`readOnly = true` 那个）。

示例（名称字段）：

```kotlin
            OutlinedTextField(
                value = state.name,
                onValueChange = { onStateChange(state.copy(name = it, nameError = false)) },
                label = { Text(stringResource(R.string.field_name)) },
                isError = state.nameError,
                supportingText = {
                    if (state.nameError) {
                        Text(
                            stringResource(R.string.error_name_required),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                colors = neutralFieldColors(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
```

（error 时 M3 自动换 errorContainer 填充，无需处理。）

- [ ] **Step 3: 编译验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/main/ItemForm.kt
git commit -m "feat(ui): 表单输入框改近白填充不透底色"
```

### Task 3: 全量验证链

**Files:** 无新改动。

- [ ] **Step 1: 测试 + 构建 + lint**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug lintDebug`
Expected: `BUILD SUCCESSFUL`（无新增单测——纯换色无新逻辑，spec §测试与验证）

- [ ] **Step 2: 装机走查清单（交付用户）**

1. 浅色（动态取色）：编辑区灰白中性底、虚线框深一档可辨、输入框近白、到期预览文字可读；
2. 深色（动态取色）：编辑区抬高深灰、虚线与文字在深底上可见；
3. 品牌回落（Android 10–11 或关动态色）：同上结构成立；
4. 顶栏/FAB/列表桶颜色与改动前一致（仍跟壁纸）。
