# FAB 直进手动输入 + 引导编辑态两步（双洞聚光）

日期：2026-09-26
状态：已与用户逐项确认（终端问答）

## 背景与需求

1. **暂时移除语音/图片输入**：FAB 点击不再弹菜单，直接打开手动输入表单。
   语音/图片是"暂时"下架——框架与字符串保留，日后恢复只需把菜单加回来。
2. **新手引导加两步**：在现有第 2 步（FAB）之后插入
   （a）聚焦整个编辑表单区域；（b）聚焦上下两个梯形，文案提示
   "滑动可以同时查看其它界面"。引导从 5 步变 7 步。

## 决策（逐项经用户确认）

| # | 问题 | 决策 |
|---|---|---|
| 1 | 移除深度 | **只藏入口**：`InputMethods` 框架、语音/图片代码与字符串保留（暂无入口可达），FAB 直接 `startNew(MANUAL)` |
| 2 | 上下梯形怎么聚焦 | **一步双洞**：引导层一步镂空多个矩形，上下梯形同时高亮、一张说明卡 |
| 3 | 引导 mock 编辑态摆什么 | **预填表单**：会话区 2 条假条目 + 表单预填名称/分类/保质期，三段式与双梯形齐全 |
| 4 | 编辑态切换机制 | **纯函数派生**：步骤声明所需界面，`uiStateForStep(step)` 从 mock 基础数据 copy，可单测 |
| 5 | 多洞动画 | 洞数量相同→逐洞补间；数量变化→直接落位（装饰性过渡不值匹配算法） |

## 设计

### 1. FAB 只藏入口（`FabMenu.kt`）

删 `DropdownMenu` 整块与 expanded 状态，FAB `onClick = { onStartInput(InputMethodId.MANUAL) }`。
`onStartInput: (InputMethodId) -> Unit` 签名不动（`MainViewModel.startNew`、`NoopMainActions`
零改动）。KDoc 注明语音/图片入口暂时移除、框架保留。

`InputMethods`、ItemForm 的 `extraAction` 占位行（手动态 `extraAction = null` 不渲染）、
相关字符串全部不动——暂无入口可达，日后恢复 = 菜单加回来。

### 2. 引导步骤序列（`GuideOverlay.kt` 的 `GuideSteps`）

| # | 步骤 | 聚光目标 | mock 界面 |
|---|---|---|---|
| 1 | 欢迎 | 无（居中卡） | 非编辑主页 |
| 2 | FAB | `fab` | 非编辑主页 |
| **3** | **编辑表单区（新）** | `form_area` | **编辑态** |
| **4** | **上下梯形（新）** | `trap_top` + `trap_bottom` 双洞 | **编辑态** |
| 5 | 第一张卡片 | `first_card` | 非编辑主页 |
| 6 | 桶区 | `bucket_area` | 非编辑主页 |
| 7 | 顶栏 | `topbar` | 非编辑主页 |

`GuideStep` 增加声明式标记 `showEditingMock: Boolean = false`（步 3、4 为 true），
`targetKey: String?` → `targetKeys: List<String>`（欢迎步空列表）。

### 3. mock 编辑态（`GuideMockContent` 纯函数）

```kotlin
/** isEditingStep 由调用方按 GuideSteps[step].showEditingMock 传入（mock 层不依赖引导步骤定义）。 */
fun uiStateForStep(isEditingStep: Boolean): UiState
```

编辑步从基础 `uiState` copy：`editing = EditingState(name = 草莓, category = FRUITS_VEG,
shelfLifeValue = "3", createdAt = now)`、`sessionItemIds = {-4L, -5L}`、
`pinnedItems = 面包/鸡蛋两条假条目`（createdAt 各回退几分钟）、`buckets = 原 3 条`
（会话条目不重复入桶，mock 直接构造派生字段）。非编辑步原样返回基础 `uiState`。

叙事：刚录完面包和鸡蛋（本次添加区），正在录草莓（表单预填）→ 预览卡有内容、
顶栏 ✓ 出现、FORM 页恒有顶梯形"已添加 2 条"+ 底梯形"下滑查看已有"。

`GuideScreen` 以 `remember(mock, machine.current)` 取值传给 `MainContent`。

新字符串 ×3 locale：`guide_mock_form_name`（草莓）、`guide_mock_session_bread`（面包）、
`guide_mock_session_egg`（鸡蛋），及步 3/4 的标题与正文（步 4 正文含
"滑动可以同时查看其它界面"）。

### 4. GuideOverlay 多洞

- 绘制：EvenOdd 路径对每个洞 `addRoundRect`（循环即可，机制本就支持）
- 动画：洞数量与上一步相同 → 逐洞补间；数量变化 → 直接落位
- 说明卡定位：取所有洞的**包围盒**传给现有 `cardOffsetY`（签名不变）——
  梯形步包围盒≈整屏，卡片自然居中
- 空 key 列表 / keys 全部未注册 → 无聚光居中卡（沿用现逻辑）；部分注册则只画已注册的洞

### 5. 聚光目标接线

`GuideKeys` 新增 `FORM_AREA` / `TRAP_TOP` / `TRAP_BOTTOM`。`EditingPager` 加三个可选
key 参数（默认 null），分别挂 FORM 页容器、上下两个 `AnimatedVisibility` 内的梯形——
走 `guideFirstCardKey` 同款通道：`MainContent` 透传、`GuideScreen` 提供、主流程 null
零开销。梯形不可见时无布局 → `onDispose` 自动摘注册，非编辑步无幽灵洞。

## 不动的部分

`InputMethods` 框架与语音/图片字符串、`MainViewModel` 全部逻辑（`startNew` 签名、
EditingState）、EditingPager 交互（吸附/梯形点击/动画）、非编辑引导步文案、
`cardOffsetY` 签名与既有单测。

## 测试

- 单测：`uiStateForStep` —— 编辑步（`editing != null`、会话集合 = {-4,-5}、
  pinned 2 条、buckets 3 条且不含会话条目）；非编辑步返回原主页态且基础态不被污染
- 全量：`testDebugUnitTest assembleDebug lintDebug`
- 装机走查：FAB 直进手动表单；7 步顺序、步 3/4 界面切换、双洞高亮、步 4 文案、
  深浅主题下双洞边框可辨
