# 需求-5：主页编辑聚焦交互 + 桶头进度条（2026-08-22）

来源：用户口头需求（5 点界面调整）。分支 `feature/edit-focus-ui`（基于 `main`）。

## 背景与目标

1. 主页标题居中不符合习惯，改靠左；
2. 新增时填了保质期但生产日期为空，需要手动补当天日期，多余操作；
3. 编辑条目 A 时表单展开在列表顶部，A 仍留在原桶里，两处同时出现同一物品易混淆；
4. 桶头「x 天内到期」纯文字，紧急程度不直观；
5. 编辑时列表其余部分仍可看到/误触，浮动「← 返回」按钮不突出。

## 1. 标题靠左

`MainScreen` 顶栏 `CenterAlignedTopAppBar` → `TopAppBar`（M3 标准，标题默认靠左），右侧历史/设置图标不动。其它页面顶栏不动。

## 2. 生产日期自动填充（仅新增表单）

- 规则：`editingItemId == null` 且 `productionDate == null` 时，保质期字段一旦变为有效（手输数字或点快捷 chips）→ 自动填 `LocalDate.now()`。
- 编辑表单**永不**自动填：编辑无生产日期的旧条目时，起算点是当时的录入时刻（createdAt），自动填今天会大幅改变到期语义。
- 实现位置：`ItemForm` 两处状态变更（数字输入 onValueChange、快捷 chip onClick）构造新 state 时应用规则；与 `ExpiryPreview` 同级的纯 UI 行为。
- 用户手动清除（×）生产日期后，再次修改保质期会重新触发填充。

## 3. 编辑目标卡片上移 + 隐藏列表项

- `MainViewModel.withDerived` 新增派生态 `editingTarget: FoodItem?`：`editing?.editingItemId?.let { id -> items.firstOrNull { it.id == id } }`。
- 列表渲染（「本次添加」置顶区与六个桶）过滤掉 `editing.editingItemId` 对应条目——编辑期间列表里不再出现 A。
- `MainScreen` 在表单上方渲染 `editingTarget` 的 `FoodItemCard`：纯展示（不可点、不可滑删），沿用状态色与到期文案，作为「正在编辑」的指示。

## 4. 桶头进度条（桶级紧急度指示）

六个状态桶的组头改为 `Row { 进度条 + 原文字 }`：

- **刻度**：14 天封顶。progress = 桶阈值 / 14：DUE_1D→1/14、DUE_3D→3/14（≈1/5）、DUE_7D→1/2、DUE_14D→1、SAFE→1、EXPIRED→1（整条红）。
- **颜色（方案 B，M3 常规）**：剩余段（progress 填充）= 状态容器色实色（与子项卡片同色）；轨道 = 同色 30% 透明；EXPIRED = 深红实心（`expired.container`）。
- **宽度**：`TextMeasurer` 量出右侧文字（labelLarge）实际宽度 × 1.5，精确值不估算。
- **高度**：扁，默认 4dp 轨道厚度。
- 进度条是**桶级**指示（一桶一条），代表该桶的紧急度窗口，非组内单张卡片的剩余时间（卡片精确剩余时间在右侧文字）。
- 「本次添加」置顶区组头保持现状（无状态色、无进度条）。

## 5. 表单钉顶 + 遮罩（新增/编辑统一）

表单打开期间（新增与编辑同一套行为）：

- 「编辑目标卡片（仅编辑时）+ ItemForm」固定钉在内容区顶部，不随列表滚动；键盘弹出由该区域自行 `imePadding`。
- 其下的列表区覆盖半透明 scrim（`onSurface` 低透明度，随主题深浅适配）：scrim 吸收点击（防误触，不可点穿），下拉刷新随 scrim 禁用。
- 顶栏与 FAB 不盖——浮动「← 返回」按钮在变暗的列表上更突出。
- 删除原「表单打开滚到顶部 / 关闭恢复位置」的 LaunchedEffect（钉顶后不再需要）。
- 卡片 `cardsEnabled = editing == null` 门禁保留（scrim 已吸收点击，双保险）。

## 不动的部分

`FabMenu`、`FoodItemCard` 本体样式、分桶/提醒/DB/保存流程（暂存并继续、保存即退出）、本次添加会话逻辑全部不变。

## 测试与验证

- `MainViewModelTest`：新增 `editingTarget` 派生态用例（startEdit 后 editingTarget = 该条目、backToMethodSelection/save 后为 null）；列表过滤属渲染层（MainScreen 过滤），随 UI 走查。
- 进度条比例/宽度、遮罩、标题靠左为纯 UI，无单测，装机走查覆盖。
- `testDebugUnitTest + assembleDebug + lintDebug` 全绿 → 装机走查清单：
  1. 标题靠左，图标仍在右侧；
  2. 新增表单填保质期（手输/chip）→ 生产日期自动出现今天；清除后再改保质期会重新填；编辑旧条目（无生产日期）不自动填；
  3. 编辑 A：A 从列表消失、表单上方出现 A 的卡片（纯展示）；保存/放弃后 A 回到原位；
  4. 各桶进度条比例与颜色（DUE_3D 约 1/5 橙、EXPIRED 整条红、SAFE 满）、宽度约为文字 1.5 倍；
  5. 表单打开时列表变暗、点列表无响应、下拉刷新禁用；返回按钮突出；键盘弹出表单不被遮挡；
  6. 深浅两主题 + 三语言抽查（进度条文字、「还有 x 到期」等沿用既有资源，无新增文案）。
- 实现完成后 `mobile-android-design` + `styles` 双 skill 重审新增代码（延续需求-3/4 惯例）。

## 范围

- **修改**：`MainScreen.kt`（顶栏、钉顶容器、遮罩、列表过滤、桶头）、`ItemForm.kt`（自动填充规则）、`MainViewModel.kt`（editingTarget 派生态）、`BucketBox`（进度条组头）。
- **新增文案**：无（全部复用既有 string 资源）。
- **依赖**：无新增。
