# 需求-4 实现后 skill 重审记录（2026-08-22）

审查基线：`git diff main..HEAD`（feature/settings-theme-language，需求-4：设置页主题切换 + 多语言 + 全量文案资源化）。
审查方式：两阶段轻量审查（Phase A: 计划 Task 1-4，opus 合并审查；Phase B: Task 5-8，opus 合并审查）+ 实现完成后 `mobile-android-design` / `styles` 双 skill 只读并行重审。

## Phase A 审查修复（提交 8fd5c7a）

| # | 严重度 | 问题 | 修法 |
|---|---|---|---|
| 1 | Critical | SettingsViewModel 缓存 `language` val，NavBackStackEntry VMStore 跨 Activity 重建保留 → 切语言后当前值陈旧 | 改 `currentLanguage()` 每次现读 |
| 2 | Important | `collectAsState(initial=SYSTEM)` 首帧闪错主题（固定深/浅色用户） | onCreate `runBlocking { first() }` 同步初值（小文件读，注释记录取舍） |
| 3 | Minor | 单选对话框 Row.clickable + RadioButton 双语义节点；Text top=14dp 布局 hack | 整行 `selectable(role=Role.RadioButton)` + `RadioButton(onClick=null)` |
| 4 | Minor | `collectAsState` 与工程 lifecycle 惯例不一致 | `collectAsStateWithLifecycle` |
| 5 | Minor | SettingsRepositoryTest 两方法共享 DataStore 单例，有执行顺序耦合 | 合并为单方法先默认后写读 |
| 6 | Minor | zh-Hant（script 型繁中标签）被映射为简中 | fromLocales 加 `script == "Hant"` 分支 |

## Phase B 审查修复（提交 45255c6）

| # | 严重度 | 问题 | 修法 |
|---|---|---|---|
| 1 | Critical | 加 values-en 后 Robolectric 默认 en-US locale 解析英文资源，ExpiryStatusTest 5 个时长断言失败（Task 8 只跑了 assembleDebug+lint 漏测） | `@Config(qualifiers = "zh-rCN")` 钉回落 values/ |
| 2 | Minor | MainScreen 一处缩进错乱；四个文件 FQN `com.battor.freshmate.R` 与 import 混用 | 修正 |
| 3 | Minor | en `quick_shelf_months` "3m" 歧义 | 改 "3 mo" |

## 双 skill 重审修复（提交 879b589）

| # | 来源 | 严重度 | 问题 | 修法 |
|---|---|---|---|---|
| 1 | mobile-android-design | P1 | 强制深色 + 系统浅色时 `enableEdgeToEdge` 按系统模式定状态栏图标对比度 → 图标不可见 | `SideEffect` 随 darkTheme 同步 `isAppearanceLightStatusBars/NavigationBars` |
| 2 | 双审一致 | P1 | `expiryText` 拼接 `"${status_expired} $duration"` 破坏英文语序；卡片复用表单预览资源 | 新增 `card_status_expired`（含 "Expired %1$s ago"）/ `card_status_remaining` |
| 3 | styles | P1 | 新测试中文名缺反引号（不合工程惯例） | 补齐 |
| 4 | mobile-android-design | P2 | `ThemeMode.valueOf` 遇未知存储值抛异常 → 启动循环崩溃 | `entries.firstOrNull ?: SYSTEM` |
| 5 | styles | P2 | ThemeMode→darkTheme when 两处重复 | 枚举方法 `resolvesDark(systemDark)` |
| 6 | mobile-android-design | P2 | 对话框行视觉拥挤（无垂直 padding）；trailing 当前值应 onSurfaceVariant | 补 padding/weight + onSurfaceVariant |
| 7 | styles | P2 | `AppLanguage.apply` 遮蔽 kotlin `apply` | 改名 `applyToApp` |
| 8 | styles | P2 | 下载失败空消息悬挂冒号 | `e.message ?: e.javaClass.simpleName` |

## 遗留（记录在案，不阻塞）

- **plurals**：`near_expiry_text` 英文用 "time(s)" 拼写法（中文不区分单复数）——后续加语言时再迁 `<plurals>`
- **runBlocking 启动读**：主线程一次小文件读的防闪烁取舍；启动项变多时换 splash `keepOnScreenCondition`
- **DataStoreSettingsRepository 双处构造**（Activity + NavGraph）：`preferencesDataStore` 委托本身单例，仅构造逻辑重复；引入 DI 时再收敛
- **log_title 空日期尾随空格**：既有行为，重写日志页时顺手处理
- **API < 33 通知文案跟随系统语言**：spec §3 已记录的 per-app locale 限制
- LogViewer `11.sp` 等宽日志视图：保留（终端风格视图的合理特例）

## 通过面摘要

- 三语资源 109 键完全一致（lint MissingTranslation 通过）；格式参数/转义核对无误
- Compose i18n 纪律：所有 `stringResource` 提升出非 Composable 上下文（semantics/onClick/LaunchedEffect 共 8 处 hoist）
- UiText 数据类等值断言在 VM 测试中成立；FabMenu 改数据驱动消重
- 验证链 `testDebugUnitTest + assembleDebug + lintDebug` 全绿
