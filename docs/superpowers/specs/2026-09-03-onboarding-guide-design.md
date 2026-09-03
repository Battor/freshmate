# 新手引导（教练标记 + 内存 mock 数据）设计

日期：2026-09-03
状态：已与用户逐节确认

## 目的

新用户首次打开 FreshMate 时，用遮罩聚光（coach mark）在**真实界面**上依次讲解核心操作（添加、滑动删除、下拉散入、历史还原）；讲解期间界面展示**纯内存 mock 数据**，用户真实数据全程不被触碰。引导可从设置页随时重看。

用户已确认的关键决策：

- 形态：**B · 首页教练标记**（遮罩聚光真实控件，非轮播/气泡/空状态页）。
- 交互性：**只看不摸**——每步高亮控件 + 文字说明，点「下一步」推进；引导层拦截一切触摸，底层界面不可交互。
- 数据：**内存 mock**——引导模式不经过 Repository、不写 Room、不触发提醒调度。「引导结束还原原数据」因此自动成立（原数据从未被触及），无需任何还原代码。
- 遮罩实现：**自绘引导层**（Canvas 遮罩 + 镂空 + 说明卡），零新依赖。

## 触发时机与完成状态

- `SettingsRepository` 新增：
  - `onboardingCompleted: Flow<Boolean>`（DataStore key `onboarding_completed`，缺省 `false`）
  - `suspend fun setOnboardingCompleted()`
- **首次启动**：`NavGraph` 的 MAIN 路由收集该标记，为 `false` 时自动 `navigate(GUIDE)`。
- 完成或中途跳过（含返回键）都写入 `true`——跳过的用户不被二次打扰。
- **设置入口**：设置页「语言」与「查看日志」之间新增「新手引导」行，点击进入引导模式。从设置进入走完**不写标记**（此时标记已无意义），其余行为与首次完全一致。

## 引导步骤

共 5 步；第 1 步无聚光（居中欢迎卡），其余聚光真实控件：

| # | 聚光目标 | 文案要点 |
|---|---|---|
| 1 | 无 | 一句价值主张 +「带你认识界面」 |
| 2 | FAB「＋」 | 点它添加食品，录保质期即可 |
| 3 | 第一张卡片 | 左滑删除（会弹确认，删错可去历史还原） |
| 4 | 列表桶区域 | 下拉刷新，把「本次添加」散入各桶 |
| 5 | 顶栏历史 + 设置图标 | 历史右滑还原；设置可改主题/语言/重看本引导 |

条件文案：若通知权限 banner 正在显示，第 4 步文案追加「允许通知后，到期提醒才会送达」。

## mock 数据

3 件条目覆盖三个色档（其余桶为空不渲染）：

| 名称 | 桶 | 状态色 |
|---|---|---|
| 牛奶 | 已过期 3 天 | 红 |
| 酸奶 | 1 天内到期 | 粉 |
| 蔬菜 | 7 天内到期 | 安全档绿 |

`GuideViewModel` 直接构造 `FoodItem` 实例（负数 id）拼出 `MainUiState`；权限 banner 的显示与否**读真实系统通知权限**（非用户数据，且支撑上表条件文案）。

## 架构

```
ui/guide/
  GuideViewModel.kt    — 构造 mock MainUiState（不持有 Repository，编译期杜绝碰真数据）
  GuideOverlay.kt      — 自绘遮罩层 + Modifier.guideTarget(key) + GuideStateHolder
  GuideStateMachine.kt — 步骤推进纯状态类（currentStep / next() / skip()）
  GuideScreen.kt       — 组合 mock MainContent + GuideOverlay，上报完成/跳过
```

改造的现有文件：

- `MainScreen.kt`：Scaffold 内容抽成内部 `MainContent(state, callbacks…)` 纯展示函数；`MainScreen` 变为收集 MainViewModel 状态的薄壳。GuideScreen 复用 `MainContent`（callbacks 全 no-op）。
- `NavGraph.kt`：新路由 `GUIDE`；MAIN 收集完成标记自动导航；接线完成/跳过回调（首次 → 写标记）。
- `SettingsScreen.kt`：新增 `onOpenGuide` 参数与「新手引导」ListItem。
- `SettingsRepository.kt`：完成标记读写。
- `strings.xml`（默认 + 繁中 + 英文）：五步文案与入口标题。

### 引导层机制

- 目标控件挂 `Modifier.guideTarget(key)`，经 `onGloballyPositioned` 把 layout bounds 报到 `GuideStateHolder`（随重组自动更新，天然适配旋转/深浅主题）。
- `GuideOverlay` 全屏覆盖（含顶栏），Canvas 画半透明遮罩 + 目标圆角矩形镂空 + 说明卡（自动选择目标上方/下方避免出屏）；消费全部点击，「下一步/跳过」以外区域不透传。
- 返回键等价跳过。

## 错误与边界

- 引导中按 Home 返回：引导状态随路由栈保留，回前台继续。
- mock 界面无任何可触发动作：Overlay 拦截触摸 + callbacks no-op 双保险。
- 完成标记写入失败不阻塞退出（尽力而为，下次启动最多再看一次引导）。

## 测试

- `GuideStateMachine`：逐步推进、中途跳过、末步完成（纯单测）。
- `GuideViewModel`：3 件 mock 条目落在预期桶（已过期 / 1 天内 / 7 天内）。
- `SettingsRepository`：标记默认 `false`，写入后读回 `true`。
- 手动走查：首启自动进入 → 跳过不再弹 → 设置重看 → 深浅主题 → TalkBack 顺序朗读五步。
- 每步实现后跑 `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug` 保持全绿。
