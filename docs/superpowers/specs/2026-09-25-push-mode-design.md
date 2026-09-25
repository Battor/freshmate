# 推送方式设置：统一推送 / 逐个推送 设计

日期：2026-09-25
状态：已与用户逐段确认

## 背景与目标

当前每个物品按保存时的提醒快照逐条推送，物品多时通知泛滥。新增设置项「推送方式」：

- **统一推送（默认）**：每天在用户选定的时间点（默认 16:30，最多 2 个）推送摘要，按紧急度分两条：1 天内到期一条、3 天内到期一条；已过期和 3 天以上不推。
- **逐个推送**：现状行为，按每条物品的过期时间逐条提醒。

两模式互斥。切换不重写 DB——统一推送触发时实时查库计算，`reminderTimes` 快照原样保留。

附带确认：现状通知已满足「不自动消失」（渠道 `IMPORTANCE_DEFAULT` 无超时 + `setAutoCancel(true)`，仅点击/划走消失），无需改动。

## 架构决策

### 触发时判断模式（choke point），而非保存时

逐个推送的所有写路径（保存/编辑/删除/撤销/历史还原）**零改动**，物品闹钟照排照触发；`ReminderBroadcastReceiver` 触发时读当前模式，统一模式则不发通知直接返回。

- 优点：模式判断收敛到 receiver 一处；不往 MainViewModel/HistoryViewModel 注入模式依赖；现有测试零改动。
- 代价：统一模式下物品闹钟空转唤醒（可忽略）。
- 通知 id 现状不变：逐条通知 id 固定 `requestCode(itemId, 0)`，同物品后续提醒覆盖前一条。

### 摘要闹钟自续（不走 WorkManager）

`setExactAndAllowWhileIdle` 一次性触发，`DailyDigestReceiver` 触发后自排明天同时刻；开机/换版本/改设置/App 启动都重装。WorkManager 周期任务 ±15 分钟漂移，无法保证「选定时间点」，不采用。

### DB 不动

无结构变更、无迁移。切回逐个时对快照时点已全流逝但未过期的条目按还原语义重算（`scheduleRestored` 逻辑）。

## 组件设计

### 1. 设置存储（`data/SettingsRepository.kt` 扩展）

```kotlin
enum class PushMode { DIGEST, INDIVIDUAL }   // 默认 DIGEST
```

- 键 `push_mode`（stringPreferencesKey，存枚举名，解析失败回落 DIGEST）
- 键 `digest_times`（string，`"HH:mm"` 逗号分隔，最多 2 个，默认 `"16:30"`；解析失败回落默认）
- `SettingsRepository` 新增 `pushMode: Flow<PushMode>`、`setPushMode()`、`digestTimes: Flow<List<LocalTime>>`、`setDigestTimes()`

### 2. 摘要分档纯函数（新建 `util/DigestTiers.kt`）

```kotlin
data class DigestTiers(val dueWithin1d: List<FoodItem>, val dueWithin3d: List<FoodItem>)
fun digestTiers(items: List<FoodItem>, now: LocalDateTime): DigestTiers
```

按 `expiryStatus()`（复用主列表分桶阈值）分组：`DUE_1D` → 第一档，`DUE_3D` → 第二档，`EXPIRED`/`DUE_7D`/`DUE_14D`/`SAFE` 排除；档内按到期时间升序（最紧急在前）。

### 3. 摘要调度器（新建 `notification/DigestScheduler.kt`）

```kotlin
interface DigestScheduling {
    fun arm(times: List<LocalTime>)
    fun cancelAll()
}
```

- requestCode 用负数 `-1`/`-2`（物品闹钟 `itemId*10+index` 恒 ≥0，天然不冲突）
- `arm(times)`：每个时间点排**下一次出现时刻**（`nextOccurrence(time, now)`：今天未过→今天，已过→明天）的精确闹钟；槽位多于时间数时先取消多余槽位；`FLAG_UPDATE_CURRENT` 覆盖重排
- `cancelAll()`：取消两个槽位
- 精确性策略与 `ReminderScheduler` 一致（S+ 无精确权限回落 `setAndAllowWhileIdle`）

### 4. 摘要接收器（新建 `notification/DailyDigestReceiver.kt`，清单注册）

触发时（goAsync + IO 协程，模式沿用 ReminderBroadcastReceiver）：

1. 读 `pushMode`，逐个模式 → 不自续、直接返回（防切换后残留 PendingIntent 空转）
2. 查库取活跃条目（`deletedAt == null`），`digestTiers()` 分档
3. 每档发一条通知；**档为空则取消该档残留旧通知**（通知不自动消失，不取消会永远陈述过期信息）
4. 自排明天同时刻闹钟

通知形态：

- 通知 id 用档位槽位号（`-1`/`-2`），同档新通知替换旧的 → 通知栏最多 2 条摘要
- 标题：`1 天内到期（3 项）` / `3 天内到期（5 项）`（复用 `status_due_1d`/`status_due_3d`）
- 收起态正文：物品名顿号连接
- 展开态：InboxStyle 每行 `名称 · 还有 12 小时`（复用 `formatRemaining`）
- `setAutoCancel(true)`，点击进 App，渠道/图标同现状

### 5. 物品接收器（改 `notification/ReminderBroadcastReceiver.kt`）

`onReceive` 起始处读 `pushMode`：统一模式 → 记日志后返回（闹钟已触发，无需取消）。其余不变。

### 6. 开机重排（改 `notification/BootReceiver.kt`）

读模式分流：统一 → `DigestScheduler.arm(times)`（重启后闹钟清空，无需取消逐条闹钟）；逐个 → 现状 `rescheduleAll()`。

### 7. 启动兜底（改 `MainActivity.kt`）

`onCreate` 起协程按当前模式 arm/cancel 一次（幂等）。这是默认统一推送能工作的前提——新装/升级用户未进过设置页也要有摘要闹钟；也修复 receiver 自续偶发失败的积累误差。

### 8. 设置页（改 `ui/settings/SettingsViewModel.kt` + `SettingsScreen.kt` + `NavGraph.kt` factory）

两行，插在「主题」「语言」之间：

- **推送方式**：`SingleChoiceDialog` 二选一（统一推送/逐个推送）。选定后：切统一 → `digestScheduler.arm(times)`；切逐个 → `digestScheduler.cancelAll()` + `reminderScheduler.rescheduleAll()`（还原语义重排：快照全流逝但未过期的条目按当前时刻重算）
- **推送时间**：仅统一模式渲染（逐个模式整行隐藏，避免置灰态的语义困惑）；对话框列当前时间（每行可删）+ 添加（TimePicker，上限 2 个）；改动后 `arm` 重排

`SettingsViewModel` 注入 `DigestScheduling` + `ReminderScheduling`（NavGraph settings factory 构造 `DigestScheduler(context)` / `ReminderScheduler(context)`）。

## 边界行为

- **不补推**：重启/关机跨过时间点 → 排下一次，不回溯（与逐个推送重启丢弃已过时点一致）
- **两个时间点内容相同**：同一档一天最多推 2 次，每次都是全量重算，后者替换前者（同通知 id）
- **统一模式下的短保质期盲区**：摘要时刻之间到期的条目（如保质期 6 小时）可能在下次摘要前已过期——统一模式的固有取舍，不做特殊处理
- **模式与时间的解析容错**：DataStore 损坏值回落默认（DIGEST / 16:30），不崩溃

## 测试策略

- `digestTiers`：落档正确（1d/3d）、EXPIRED/7d/14d/safe 排除、档内到期升序
- `nextOccurrence`：今天未过→今天、已过→明天、跨午夜边界
- `SettingsViewModelTest` 扩展（Fake 调度器）：切统一触发 `arm`、切逐个触发 `cancelAll`+`rescheduleAll`、改时间触发 `arm`
- 设置存储解析回落：损坏字符串回落 DIGEST / 默认 16:30
- 装机走查：默认统一模式存 2 条临期食品到点收 2 条摘要；切逐个后老时点不再发、重排后按条收；改时间立即生效；重启后摘要闹钟恢复；空档位旧通知被清

## 字符串（×3 locale）

- 设置：推送方式、统一推送、逐个推送、推送时间、添加时间
- 通知：`%1$d 项`条数、档位标题拼接（复用 status_due_1d/3d）、正文名称连接符（中文「、」/英文「, 」）

## 不动的部分

- 主列表/表单/历史全部 UI 与 ViewModel 逻辑
- `computeReminderTimes` 快照算法、`ReminderScheduler` 排闹钟逻辑
- 通知渠道与权限流程、测试页、更新流程
