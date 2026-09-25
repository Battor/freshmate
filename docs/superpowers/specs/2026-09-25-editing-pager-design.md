# 需求-7 设计：编辑态三段式吸附布局 + 标题栏编辑态

日期：2026-09-25 ｜ 状态：已与用户逐节确认

## 背景与目标

装机走查反馈：编辑/新增状态下「本次添加」置顶区应在表单**上方**，整页自由滚动缺乏结构感。目标形态（编辑态从上到下三段）：

1. **本次操作**——本次会话新添加的条目
2. **编辑区**——表单，进入编辑态默认停这段
3. **已添加**——既有六桶列表

段间**吸附式**切换：段内自由滚动，滚到边缘继续拖、松手过阈值跳到相邻段开头（VerticalPager 原生 snap，零自定义手势）。屏幕上下边缘有**梯形色块**提示相邻段、可点击跳转。

同时：编辑态操作按钮从右下角 FAB 上移标题栏（左上返回 ←、右上保存 ✓），逻辑不变；编辑态 FAB 隐藏。

## 已确认决策

| 决策点 | 结论 |
|---|---|
| 进入编辑态默认段 | 编辑区 |
| 保存成功后 | 停在编辑区，不自动跳段 |
| 空段处理 | 无会话条目顶部段隐藏；无既有条目底部段隐藏 |
| 实现方案 | A：VerticalPager（官方 snap 手势物理） |
| 梯形样式 | ≈22dp 扁、水平居中、宽度随文案自适应、斜边各收 ~10dp、primaryContainer、滑入弹出带回弹、点击等效滑动、无相邻段隐藏 |
| 顶栏（编辑态） | ←（backToMethodSelection）/ 标题按 isAddForm 显「新增」「编辑」/ ✓ 仅 hasFormContent 时出现（save()）；contentDescription 沿用 discard_back、stash_and_continue（新增）/save（编辑） |
| 不变 | 非编辑顶栏与 FAB、下拉刷新散入、引导页、ItemForm/BucketBox 内部、全部 ViewModel 逻辑 |

梯形文案（上梯形 / 下梯形按当前段）：

| 当前段 | 上梯形 | 下梯形 |
|---|---|---|
| 本次操作 | 隐藏 | 下滑继续编辑 |
| 编辑区 | 已添加 %1$d 条（0 条整个隐藏） | 下滑查看已有（无既有项隐藏） |
| 已添加 | 上滑继续编辑 | 隐藏 |

## 结构

- **`ui/main/EditPages.kt`**（纯逻辑，无 Compose）：`EditPageKind{SESSION,FORM,EXISTING}`、`editingPages(hasSession, hasExisting)`（空段不生成页，默认索引指向 FORM）、`TrapLabel`（ContinueEditDown/ContinueEditUp/SessionCount(n)/ViewExisting）、`trapLabels(current, sessionCount, hasExisting)`
- **`ui/main/EditingPager.kt`**：`VerticalPager`（每页 `Column.verticalScroll`，内容复用 BucketBox / ItemForm）+ 梯形 overlay（`AnimatedVisibility` slideIn + spring 回弹；`TrapezoidShape` = GenericShape 两种朝向）+ 点击 `animateScrollToPage(±1)`
- **pagerState 重建时机**：AnimatedContent target 为 isEditing 布尔——编辑分支进出必重建（新开表单必经 false→true：编辑态 FAB/卡片都不可点）；保存后仍在编辑态不重建、停在当前页
- **`MainScreen.kt`**：编辑分支整块 Column 换 `EditingPager`（visiblePinned/visibleBuckets 记忆化照搬）；topBar 按编辑态分支；`floatingActionButton` 非编辑态才渲染
- **`FabMenu.kt`**：删编辑态两按钮分支与相关参数，只留 + 号菜单

## 字符串（×3 locale：中/英/繁）

`trap_session_to_form` 下滑继续编辑、`trap_form_count` 已添加 %1$d 条、`trap_form_to_existing` 下滑查看已有、`trap_existing_to_form` 上滑继续编辑、`edit_title_add` 新增、`edit_title_edit` 编辑。

## 测试

- 单测（`EditPagesTest`）：editingPages 四情形（页列表 + 默认索引）；trapLabels 六组合（含会话 0 条、无既有项的 null 边界）
- 手势/动画/主题对比 → 装机走查（清单见实现计划）
