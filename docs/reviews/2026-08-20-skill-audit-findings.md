# FreshMate 待修复项清单（2026-08-20 四项 skill 审查）

来源：`android-intent-security` / `mobile-android-design` / `edge-to-edge` / `styles` 四个 skill 的只读审查报告，去重合并后按优先级排序。文件路径均相对 `app/src/main/`（java/com/battor/freshmate/ 简写为 `~/`）。

统计：P0 × 4 项 · P1 × 12 项 · P2 × 10 项 · 决策项 × 2 项

---

## P0 — 高优先（功能缺陷 / 已复现的 bug / 安全）

### 1. edge-to-edge 三连修（Gboard 覆盖问题的根因，三处叠加）

上次模拟器走查发现的「Gboard 覆盖在界面上」由以下三因叠加，全在主表单链路：

| 位置 | 问题 | 修法 |
|---|---|---|
| `MainActivity.kt:10-15` | 未调用 `enableEdgeToEdge()`（全项目 inset 处理零命中） | `setContent` 前加一行；activity-compose 1.9.2 已带 API，无需加依赖 |
| `AndroidManifest.xml:17-19` | MainActivity 未声明 `android:windowSoftInputMode` | 加 `android:windowSoftInputMode="adjustResize"` |
| `~/ui/main/MainScreen.kt:120-123` | 表单所在 LazyColumn 无 IME padding，键盘盖住「保质期/数量」字段 | modifier 加 `.imePadding()`（已确认无双重 padding 风险） |

**附带**：保存按钮是 Scaffold FAB（`MainScreen.kt:107-116`），键盘弹出时 FAB 仍会被盖住——键盘可见时隐藏 FAB，或给它加 `imePadding()`。

修 `enableEdgeToEdge()` 同时解决：**系统栏图标颜色脱节**（浅色主题 + Android 15+ 强制 e2e 下状态栏浅色图标叠浅色顶栏不可见；默认样式自动随深浅色切换，与 FreshMateTheme 判据一致）。

### 2. 安装器无兜底会崩溃（安全-建议1）

`~/update/ApkInstaller.kt:21-25` — 极简 ROM / 受管设备可能没有处理 `application/vnd.android.package-archive` 的 Activity，`startActivity` 直接抛 `ActivityNotFoundException`。
修法：启动前 `resolveActivity(packageManager)` 判空，或 `runCatching` 包裹 + 回退 `Intent.createChooser`（chooser 继承 grant flags）。

### 3. 更新通道完整性校验可被绕过（安全-建议2）

- `~/update/UpdateChecker.kt:22` — 清单 URL 本身无 https 显式校验（目前靠编译期常量 + targetSdk 36 禁明文流量隐性保障）。修：构造时 `require(manifestUrl.startsWith("https://"))`。
- `~/update/UpdateViewModel.kt:109-111` — 清单里 `sha256` 是可选字段，缺失时直接放行下载安装。修：`ManifestParser` 将 `sha256` 定为必填（缺失即 parse 失败），或至少 UI 提示「该安装包未经完整性校验」。

### 4. 过期状态色无深色模式变体（UI-Critical；与决策项 1 绑定）

`~/ui/main/StatusColor.kt:13-16, 30-36` — 绿/黄/橙/深红/ExpiredRed/RestoreGreen 全部硬编码单套亮色粉彩，深色主题下主列表 + 历史页渲染大片亮色块贴深底。
修法：双套配色随 `isSystemInDarkTheme()` 切换（深色用低饱和容器色 + 浅前景），或自定义 `LocalStatusColors` 在 Theme.kt 随 colorScheme 提供，参考 GreenDark/GreenLight 做法。
（styles 审查认为深浅对比度成立、若属设计意图可保留——见决策项 1。）

---

## P1 — 应修（明显的体验 / 规范 / 性能问题）

### 安全类

5. **`allowBackup="true"` 无备份规则**（`AndroidManifest.xml:15`）— 数据库与 `files/logs/` 日志可被 adb backup / 云备份导出出沙箱。修：`allowBackup="false"` 或 `dataExtractionRules` 排除 `logs/` 与数据库。
6. **异常消息带完整 URL 落盘**（`~/update/ApkDownloader.kt:19`）— 校验失败消息含原始 URL，经 Timber 写入持久化日志；未来 URL 带 query token 即泄露。修：错误消息只输出 `Uri.parse(url).host`。

### UI / 交互类

7. **每张卡片各自 `remember { LocalDateTime.now() }`**（`~/ui/main/FoodItemCard.kt:47, 49`、`~/ui/history/HistoryScreen.kt:164-166`）— 停留久了状态文本与真实过期进度脱节，卡片间时刻不一致。修：页面级统一传入（或 MainViewModel 的 nowProvider）。
8. **表单击键引发全列表重组**（`~/ui/main/MainScreen.kt:61` + `:141, :166-181`）— `editing` 混在 UiState 单流，每次击键 + 每次重组新 lambda 实例，整个 LazyColumn 全部组项重组。修：表单状态拆独立 StateFlow，或回调 `remember` + 方法引用稳定化。
9. **下载中「假按钮」**（`~/ui/settings/SettingsScreen.kt:138-141`）— confirmButton `onClick = {}` 可点无响应。修：`TextButton(enabled = false)` 或改纯文本。
10. **busy 时「检查更新」无禁用视觉**（`~/ui/settings/SettingsScreen.kt:85-88`）— 只 `enabled=false` 前景不变淡。修：busy 时 headline 套 `onSurfaceVariant`/alpha。
11. **主列表无空态**（`~/ui/main/MainScreen.kt:120-185`）— 首次安装整页空白只剩 FAB。修：仿 `HistoryScreen.kt:89-92` 补「暂无食品，点 + 添加」。
12. **SmallFloatingActionButton 40dp < 48dp 触摸目标**（`~/ui/main/FabMenu.kt:46, 52`）— 修：`Modifier.minimumInteractiveComponentSize()` 或换标准 56dp FAB。
13. **日志读取在主线程组合期间执行**（`~/ui/logviewer/LogViewerScreen.kt:42, 47`）— 文件 I/O 直接在组合里跑。修：挪 `LaunchedEffect`/VM 协程，`selected` 用 `rememberSaveable`。

### 主题体系类（styles 审查「值得做」清单）

14. **抽取 FoodItemCard 与 HistoryItemCard 公共卡片组件** — 两文件近乎逐行复制（shape 16dp、padding 12dp、icon 28dp、字号 16/12/13sp 全一致），抽公共 shell 净删 40-50 行，后续修改只做一次。
15. **裸 fontSize → MaterialTheme.typography**（约 10 处）— 16sp→`bodyLarge`、12sp→`bodySmall`、11sp→`labelSmall`、13sp 统一 `bodyMedium` 或明确保留。顺带获得默认行高字距。
16. **形状集中** — `RoundedCornerShape(16.dp)` ×7、`12.dp` ×1 全部内联魔法数（恰好等于 M3 默认 large/medium）。修：Theme.kt 显式传 `shapes = Shapes(...)` 或抽顶层 `CardShape` 常量，顺带消除 8 处重组期对象分配。

---

## P2 — 吹毛求疵（可选）

17. `~/notification/ReminderIds.kt:8` — `(itemId * 10 + index).toInt()` Long→Int 截断，理论可碰撞致闹钟覆盖（当前自增量级无实际风险）。可改 `"$itemId-$index".hashCode()` 或文档化。
18. `~/ui/main/MainScreen.kt:127` — 新组判断 `items.none{...}` 每次重组 O(n)，可移入 VM 派生态。
19. `~/ui/main/ItemForm.kt:212, 274` — `showPicker`/`recording` 用 `remember` 旋转即丢，换 `rememberSaveable`。
20. `~/ui/main/ItemForm.kt:221-239` — 只读 TextField 弹日历对 TalkBack 不友好（双击可能不触发 PressInteraction.Release），补日历 trailingIcon IconButton 显式入口。
21. `~/ui/logviewer/LogViewerScreen.kt:85` — 行项无 key，行多时可加行号策略 key。
22. `~/ui/main/PermissionEffects.kt:70-92` — `PermissionBanners` 直读系统权限状态，严格单向数据流应上提。（已有 ON_RESUME 刷新兜底）
23. `~/update/ApkDownloader.kt:15` + `~/update/UpdateViewModel.kt:40` — 每次 new `ApkDownloader()` 都新建 OkHttpClient，无连接复用。可注入单例 client。
24. 边框写法 `BorderStroke(1.dp, outlineVariant.copy(0.4f))` 在 `MainScreen.kt:236` 与 `HistoryScreen.kt:129` 逐字重复，可抽 `GroupBorder` 常量。
25. `AndroidManifest.xml:13-14` — 图标/主题用系统资源 `@android:drawable/sym_def_app_icon`，发布前应替换自有资源。
26. edge-to-edge 打磨：四处列表 `Modifier.padding(padding)` 包容器而非传 `contentPadding`（内容无法滚到系统栏后面，功能不丢内容，属贯穿效果打磨）；`window.isNavigationBarContrastEnforced = false`（三按钮导航 scrim，一行）。
27. `Color.kt:6-11` — `Green/Peach/Yellow/Sky/Pink` 五个色值全项目零引用，死代码可删；`StatusColor.kt:31` 的 SAFE 底色与 `Color.kt:12` 的 `LightGreenContainer` 数值重复未复用。

---

## 决策项（需用户拍板，不是纯技术修复）

1. **状态色深色模式表现**（对应 P0-4）：深色主题下仍用浅色粉彩底是否设计意图？是 → 在 StatusColor.kt 加注释固化；否 → 按 P0-4 做双套配色。
2. **`dynamicColor = true` 恒开**（`~/ui/theme/Theme.kt:52`）：Android 12+ 品牌绿配色永不生效（壁纸取色覆盖）。若有意为之加注释；否则考虑设置页加开关。

---

## 审查外的既有遗留（此前交接项，非本轮发现）

- `UPDATE_MANIFEST_URL` 仍为占位地址（`app/build.gradle.kts:18`），服务器就绪后替换
- 发布时 bump versionCode 2 / versionName 0.2.0
- 首次滑动删除偶发失灵（T9 报告过一次、未能复现，待真机手动验证）
- feature/freshmate-v1 分支待合并；模拟器中仍有种子数据（SeedA/SeedB/SeedDel）

---

## 已确认通过项（无需处理）

- 组件导出最小化、PendingIntent 全部 IMMUTABLE + 显式组件 + 唯一 requestCode、FileProvider 仅暴露 updates/ 子目录、六个权限逐一对应功能、无动态 receiver/sticky/嵌套 Intent 反模式（安全审查全过）
- 无障碍：全部交互图标有 contentDescription、SwipeToDismiss customActions 辅助动作、LazyColumn key 稳定、一次性事件流防重复弹出、下载中防误关对话框（UI 审查亮点）
- 颜色 token 纪律：屏幕代码零散落硬编码，双主题对称，常量顶层化（styles 审查结论：上游水平）
