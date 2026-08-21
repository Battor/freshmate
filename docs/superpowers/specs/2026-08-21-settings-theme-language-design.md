# 需求-4：设置页主题切换 + 多语言（2026-08-21）

来源：用户口头需求（设置页新增暗黑模式切换与多语言）。分支 `feature/settings-theme-language`（基于 `main`）。

## 1. 设置页 UI

```
设置
├─ 🌓 主题            浅色 / 深色（trailing 显示当前生效值）
├─ 🌐 语言            跟随系统 / 简体中文 / 繁體中文 / English
├─ 📄 查看日志
├─ ⤓ 检查更新
└─ ℹ️ 关于
```

- 沿用现有 `ListItem` 模式（leading 图标 + headline + trailing 当前值 + `KeyboardArrowRight`）；「检查更新」移到「关于」前面（原顺序：检查更新/查看日志/关于）。
- **主题项**：点击弹 M3 `AlertDialog` 单选，仅**浅色 / 深色**两项，选择即时生效并写入存储，无「跟随系统」选项、无回归途径（用户明确确认）。trailing 当前值：未手动选择时 = 系统当前模式（进入设置页时经 `isSystemInDarkTheme()` 检测），手动选择后 = 所选值。
- **语言项**：点击弹 `AlertDialog` 单选四项，当前值显示在 trailing，选择后重建 Activity 生效。
- 图标：主题 `Icons.Outlined.DarkMode`，语言 `Icons.Outlined.Language`。

## 2. 暗黑模式机制（DataStore + Compose 状态）

- `ThemeMode` 枚举：`SYSTEM / LIGHT / DARK`，默认 `SYSTEM`。
- 新建 `SettingsRepository`（`androidx.datastore:datastore-preferences`，工程首个 DataStore 依赖）：键 `theme_mode`。
- `MainActivity.setContent` 内 `collectAsState(initial = SYSTEM)` 读取，传入
  `FreshMateTheme(darkTheme = when (mode) { SYSTEM -> isSystemInDarkTheme(); LIGHT -> false; DARK -> true })`。
  切换经 Compose 重组即时生效，无需重建 Activity。深浅 colorScheme 与六档状态色板已就绪，只换入参。
- 设置页 SYSTEM 态展示值用 `isSystemInDarkTheme()` 换算成「浅色/深色」。

## 3. 语言切换机制（官方 per-app language）

- 加 `androidx.appcompat:appcompat` 依赖；`MainActivity` 改继承 `AppCompatActivity`（与 Compose、`enableEdgeToEdge` 兼容）。
- manifest 注册 `AppLocalesMetadataHolderService` + `<meta-data android:name="autoStoreLocales" android:value="true"/>`：API < 33 由 appcompat 自动持久化；API 33+ 走系统 LocaleManager（系统设置出现「应用语言」项，随云备份）。
- 切换：`AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))`；「跟随系统」= 空列表。Activity 自动重建，资源整体换语言。
- 语言枚举：`SYSTEM(null) / SIMPLIFIED_CHINESE("zh-CN") / TRADITIONAL_CHINESE("zh-TW") / ENGLISH("en")`。
- 资源目录：`values/`（简体中文，兜底）、`values-zh-rTW/`、`values-en/`。系统语言不匹配三语时显示简体中文。
- **已知限制（记录不修）**：通知渠道名（Application onCreate 创建）与提醒通知正文在 API < 33 跟随系统语言；API 33+ 正常跟随 app 内选择。

## 4. 全量字符串抽取

当前工程无 `strings.xml`，全部文案硬编码中文。抽取范围：

| 区域 | 内容 |
|---|---|
| MainScreen | 标题、历史/设置 contentDescription、空态、删除 Snackbar、临近过期对话框 |
| ItemForm / FabMenu | 表单标签、提示、分类名、语音文案 |
| MainViewModel | 错误事件文案 |
| History 页 | 标题、还原对话框、Snackbar |
| Settings / About / LogViewer | 全部 |
| `ExpiryStatus` | `label` 改 `@StringRes` 资源（六档桶头文案） |
| 通知 | 渠道名、提醒通知标题/正文（NotificationFactory） |
| 更新模块 | 对话框/Snackbar 文案（UpdateViewModel notice） |

- 带参数文案用占位符（`已删除「%1$s」`）；「还有 x 到期」类用参数化字符串。
- 英文/繁中翻译由实现提供初稿、用户审校；品牌词「食刻」英文界面保留 FreshMate。
- 设置选项名（浅色/深色/跟随系统）三语齐全；**语言名永远用各自母语显示**（简体中文/繁體中文/English），不随界面语言翻译。

## 5. 范围

- **新增**：`ThemeMode`、`SettingsRepository`（DataStore）、`AppLanguage` 枚举、主题/语言两个设置项与对话框、`strings.xml` ×3。
- **修改**：`MainActivity`（AppCompatActivity + themeMode 接线）、`AndroidManifest.xml`（locale service）、`SettingsScreen.kt`、全部含硬编码文案的 UI/VM/通知文件、`ExpiryStatus.kt`。
- **不动**：`Theme.kt`（`darkTheme` 参数签名已支持，无需改）、分桶/提醒逻辑、DB、更新模块逻辑、语音录入逻辑。
- 依赖新增：`datastore-preferences`、`appcompat`（+ Robolectric 已有则不重加）。

## 6. 测试与验证

- 新增 `SettingsRepositoryTest`（Robolectric）：ThemeMode 读写与默认值。
- 既有单测中的中文文案断言改为资源 ID 断言或随资源走，保持全绿。
- `testDebugUnitTest + assembleDebug` 全绿 → 装机走查：主题切换即时生效（六档色板双主题）、语言切换全 app 生效、API 33+ 系统设置出现「应用语言」、英文/繁中文案无遗漏（硬编码残留用 lint/审查兜底）。
- 实现完成后 `mobile-android-design` + `styles` 重审新增代码。
