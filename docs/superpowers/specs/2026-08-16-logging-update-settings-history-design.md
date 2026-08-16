# 食刻 FreshMate — 日志 / 自动更新 / 设置页 / 历史页 设计文档

日期：2026-08-16
状态：已与需求方逐段确认
前置：`2026-08-15-freshmate-app-design.md`（v1 基线）、组容器模型（2026-08-16，见 §5.2/§5.4 更新）

## 1. 概述

v2 批次四项能力：

1. **本地日志**：记录数据库操作（内容与返回值）、系统操作、系统广播；App 内直接查看。
2. **自动更新**：启动时 + 手动检查自托管 JSON 版本清单，发现新版本则下载 APK 走系统安装器安装。
3. **设置页**：右上角设置图标进入，含「查看日志」「检查更新」「关于」。
4. **历史页**：右上角历史图标进入，展示已删除项（软删除）；仅当原组仍活跃时可还原。

## 2. 新增依赖

| 依赖 | 用途 |
|---|---|
| `com.jakewharton.timber:timber` | 日志分发（Debug 双写 logcat） |
| `com.squareup.okhttp3:okhttp` | 版本清单拉取 + APK 流式下载 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 版本清单解析 |
| `androidx.navigation:navigation-compose` | 页面导航 |

均经已配置的国内镜像下载。URL 常量经 `BuildConfig.UPDATE_MANIFEST_URL` 从 build.gradle 注入。

## 3. 包结构（增量）

```
com.battor.freshmate
├── logging           # [新] FileTree：按天写 filesDir/logs/，7 天清理，2MB 截断
├── update            # [新] UpdateChecker / ApkDownloader / Installer
├── data              # FoodItem +deletedAt；DAO 活跃/已删双查询；Repository 埋日志
├── ui
│   ├── history       # [新] 历史页
│   ├── settings      # [新] 设置页
│   ├── logviewer     # [新] 日志查看页
│   └── navigation    # [新] FreshMateNavGraph
```

依赖方向：`update`、`logging` 无内部依赖；`ui.*` → `data`/`update`；`notification` 埋日志（依赖 `logging` 的 Timber）。

## 4. 本地日志

### 4.1 记录范围

| 类别 | 埋点位置 | 示例 |
|---|---|---|
| 数据库操作 | Repository 每个方法包裹 DAO 调用（入参 + 返回值 + 耗时） | `DB insert → FoodItem(name=牛奶, days=7) ← id=3 (12ms)` |
| 系统操作 | ReminderScheduler.schedule/cancel、通知发送、权限申请结果 | `ALARM schedule itemId=3 at 2026-08-20T14:30` |
| 系统广播 | BootReceiver、ReminderBroadcastReceiver、MY_PACKAGE_REPLACED | `BCAST BOOT_COMPLETED → reschedule 12 items` |

三类日志双写：始终写文件，同时也输出到系统 logcat（Debug/Release 均种 DebugTree，便于日后 `adb logcat` 排查）。

DAO 层（Room 生成代码）不打日志；Repository 是全部 DAO 调用的必经之路。

### 4.2 FileTree

- Timber 种树位置：`FreshMateApp.onCreate`；Debug 构建加种 DebugTree（logcat）。
- 文件：`filesDir/logs/yyyy-MM-dd.txt`，追加写，`synchronized` 线程安全；跨天首写自动切新文件。
- 清理：种树时删除 7 天前的文件；单文件超 2MB 截断前半段。
- 格式：`MM-dd HH:mm:ss.SSS [tag] message`，纯文本一行一条。
- 日志含食品名称等数据：仅本地，卸载即失，不外发。

### 4.3 日志查看页

扫描 logs 目录，默认展示今天，顶部下拉切换日期；LazyColumn 等宽字体渲染。不做筛选/搜索/导出（后置）。

## 5. 自动更新

### 5.1 版本清单（自托管 JSON）

```json
{
  "versionCode": 2,
  "versionName": "1.1.0",
  "apkUrl": "https://host/freshmate-1.1.0.apk",
  "sha256": "<hex>",
  "notes": "新增历史页与日志查看"
}
```

### 5.2 检查流程

- **启动自动**：进入 main 后异步检查（不阻塞 UI）；有更新 → Snackbar“发现新版本 x.y.z”，点「查看」弹更新说明对话框。
- **手动**：设置页「检查更新」→ 转圈 → 已是最新 / 发现新版本（同上对话框）/ 失败提示。
- 判定：`manifest.versionCode > BuildConfig.VERSION_CODE`。

### 5.3 下载与安装

1. OkHttp 流式下载 APK 到 `cacheDir`，对话框进度条；
2. SHA-256 校验（清单提供），失败删文件报错；
3. FileProvider 暴露 APK，`ACTION_VIEW` 唤起系统安装器；
4. `REQUEST_INSTALL_PACKAGES`：检测未授权时跳系统“安装未知应用”设置页（Android 8+ 用户授权一次）。

清单新增权限：`INTERNET`、`REQUEST_INSTALL_PACKAGES`。

检查/下载/校验/安装全链路埋日志。

## 6. 设置页与导航

- 唯一 Activity；`FreshMateTheme { FreshMateNavGraph() }`；路由：`main`（起点）/ `history` / `settings` / `logviewer` / `about`。
- 主页面 TopAppBar 右上角两个动作图标：历史（左）、设置（右）。
- 设置页三项：「查看日志」→ logviewer；「检查更新」→ §5.2 手动流程；「关于」→ 应用名、版本号、简介。

## 7. 历史页与软删除

### 7.1 数据模型

`FoodItem` 新增 `@ColumnInfo(name = "deleted_at") val deletedAt: LocalDateTime? = null`；Room 版本 1→2，`Migration(1,2)` = `ALTER TABLE food_items ADD COLUMN deleted_at TEXT`（TypeConverter 将 LocalDateTime 存为字符串，列类型须与其一致）；现存数据全 null。

查询拆分：

- 主列表：`WHERE deleted_at IS NULL ORDER BY created_at DESC`（改造现有 observeAll）
- 历史页：`WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC`

### 7.2 删除/还原流转

| 动作 | 数据 | 闹钟 |
|---|---|---|
| 主列表左滑删除 | `deletedAt = now` | 取消 |
| 5 秒 Snackbar 撤销 | `deletedAt = null` | 重排 |
| 历史页右滑 + 弹框确认还原 | `deletedAt = null`（`createdAt` 原样，自动回原组） | 重排（已过期条目除外） |

组无 id：组 = `groupKey(createdAt)` 派生，还原天然归回原组。

**还原条件（仅组活跃）**：组键在主列表存在活跃条目 → 可还原；否则置灰、右滑无效、标注“原组已不存在”。派生布尔随主列表实时更新。

### 7.3 历史页 UI

复用组容器视觉：按**删除时间**分组，组头 = 删除时刻 + 可还原条数；条目卡片同主列表样式（状态色保留）；右滑露绿色“还原”背景 → AlertDialog「还原到原组？」确认 → 执行还原，成功后 Snackbar 提示。

历史页仅还原，无彻底删除（数据库只增不减，v2 明确不做）。

## 8. 错误处理

| 故障点 | 处理 |
|---|---|
| 日志 IO 失败 | FileTree 吞掉，绝不因日志崩溃；logcat 仍可见 |
| 更新检查失败/JSON 坏 | 启动静默；手动时 Snackbar 提示 |
| APK 下载中断 | 对话框报错可重试；残文件清理 |
| SHA-256 不匹配 | 删文件 + “安装包校验失败” |
| 未授权安装 | 跳系统授权页，返回后可继续 |
| Room 迁移失败 | 现有 destructive fallback 兜底（迁移脚本须先经 MigrationTestHelper 验证） |

## 9. 测试策略

- **FileTree**：临时目录单测——按天分文件、7 天清理、2MB 截断。
- **UpdateChecker**：版本比对（> = <）、JSON 解析容错、SHA-256 校验。
- **DAO**：Room in-memory——活跃/已删查询、软删、还原。
- **ViewModel**：删除→撤销→还原流转、还原重排闹钟、组活跃判定。
- **迁移**：`MigrationTestHelper` 1→2（老数据 deleted_at 为 null 且仍在主列表）。
- 现有 58 个 MainViewModel 测试适配：FakeRepository 加 deletedAt 语义。
- 下载与系统安装器交互：模拟器手动走查（装旧版→触发更新→走完安装）。

## 10. 实施顺序

1. 日志系统（Timber + FileTree + 埋点）
2. 软删除 + 迁移 + 历史页
3. Navigation + 设置页 + 日志查看页 + 关于页
4. 更新机制

## 11. 明确不做（v2）

- 日志导出/分享/筛选
- WorkManager 周期后台检查更新
- 历史页彻底删除与自动清理
- 过期项移入历史页（维持主列表深红显示）
- 增量更新 / 静默安装
