# 功能测试页设计（隐藏入口 + 待通知列表 + 测试通知 + 快照日志）

日期：2026-09-20

## 背景与目标

装机走查时无法直接核对提醒链路：闹钟排在系统里看不见、通知计算结果只能靠猜。
本设计加一个**隐藏测试页**，提供三件事：

1. 关于页连点 APP 名称 5 次进入测试页（第 3 次起提示剩余次数）
2. 查看所有待通知的提醒时点
3. 选延迟档位，到点收到一条与过期提醒同方式的测试通知

另加一条独立改动：**新加 item 后，日志记录计算出的通知时点**。

已确认的决策：

- 测试通知定时方式：**延迟时长档位**（15 秒 / 1 分钟 / 5 分钟），不做绝对时刻选择
- 构建限定：**所有构建都保留**（用户装机走查用 release 包，debug-only 会测不到；隐藏入口本身就是门槛）
- 测试通知实现：**独立闹钟接收器** `TestAlarmReceiver`（完整验证「精确闹钟权限 → Doze 唤醒 → 通知送达」链路，测试代码与生产 receiver 隔离）

## 1. 入口：关于页连点 5 次

**改动：`AboutScreen.kt`、`NavGraph.kt`、`strings.xml` ×3**

- APP 名称 `Text` 加 `clickable`；点击逻辑抽成纯类 `TestEntryCounter`（`ui/test/TestEntryCounter.kt`）：
  - 计数从 0 起，**点击间隔 ≤ 1.5 秒才累加**（Android 开发者选项惯例窗口），超时重置为 1
  - 第 3、4 次点击回调 `onHint(remaining)`；第 5 次回调 `onUnlock()`
- 页面用现有 `OneShotSnackbar` 显示 `test_tap_hint`（「再点 %1$d 次进入测试页」）
- `AboutScreen` 新增参数 `onOpenTest: () -> Unit`；`NavGraph` 注册 `Routes.TEST` 路由并 `navigate`
- 计数状态在 `AboutScreen` 内 `remember`——离开页面即重置

## 2. 待通知列表

**新增：`ui/test/TestScreen.kt`、`ui/test/TestViewModel.kt`**

- 数据源：`repository.observeAll()`（活跃条目）→ 展平各条 `item.reminderTimes` 中 **> now** 的时点为 `(时点, 条目名)` 对，升序
- 说明：`AlarmManager` 无法枚举已排闹钟；正常路径下「快照时点 = 已排闹钟」，此列表即闹钟队列的可视化。重启重排等防御路径与快照的差异，正是测试页要暴露的内容
- UI：Scaffold + TopAppBar（`test_title`「测试页」）+ LazyColumn；每行时点（`yyyy-MM-dd HH:mm`）+ 条目名 + 距今时长（复用 `formatRemaining`）；空态 `test_empty`
- `now` 取进入页面时的快照，不做周期刷新（YAGNI）
- 通知权限未开时顶部复用 `banner_notifications_off` 横幅提示

## 3. 测试通知：独立闹钟接收器

**新增：`notification/TestAlarmReceiver.kt`**

- `TestScreen` 三个档位按钮（15 秒 / 1 分钟 / 5 分钟）→ `TestAlarmReceiver.schedule(context, atMillis)`：
  - `setExactAndAllowWhileIdle`；精确闹钟不可用回落 `setAndAllowWhileIdle`（与 `ReminderScheduler` 同策略）
  - requestCode 固定 0（独立 component，与 item 的 `itemId*10+index` 编码不冲突）；`FLAG_UPDATE_CURRENT` 使重复点按自然覆盖重排
  - 排上后测试页**状态区**显示本次测试的生命周期（`TestAlarmTracker` 进程内状态流，无历史、只看本次）：
    - `已排，HH:mm:ss 触发` → 到点后变 `已送达（HH:mm:ss）`
    - 通知权限未开：`未送达：通知权限未开（HH:mm:ss）`
    - 排了闹钟但到点 +10 秒宽限仍未触发：`闹钟未触发（可能被系统省电拦截）`——点档位和触发之间断掉只能出现在这一环
    - 状态在进程内存活（receiver 与页面同进程）；进程被杀则回落 Idle，属可接受边界
  - `Timber.i("ALARM test schedule …")` 照记，与状态区互补
- `onReceive` 构造通知与过期提醒同渠道同外观：`ReminderIds.CHANNEL_ID`、`ic_reminder`、标题 `notification_title`、正文 `test_notification_body`（「测试通知：提醒链路正常」）、`setAutoCancel(true)`、点击打开 app（照抄 `ReminderBroadcastReceiver` 的写法）
- 到点触发时 `Timber.i("ALARM test fired")`，便于日志页核对链路

## 4. 新加 item 的快照日志

**改动：`MainViewModel.persist()` 一行**

```kotlin
Timber.i("REMINDER 快照 name=%s 时点=%s", item.name, item.reminderTimes)
```

- `computeReminderTimes(...)` 算完后记录；Timber 同出 logcat 与应用内日志文件（LogViewer 可见，装机走查无需连电脑）
- `ReminderScheduler.schedule` 现有 `ALARM schedule` 日志不动（记「实际排几个」vs 本条记「算出来是几点」，互补）

## 文件清单

| 文件 | 动作 |
|---|---|
| `app/src/main/java/com/battor/freshmate/ui/test/TestScreen.kt` | 新增——测试页 UI |
| `app/src/main/java/com/battor/freshmate/ui/test/TestEntryCounter.kt` | 新增——连点计数纯类 |
| `app/src/main/java/com/battor/freshmate/ui/test/TestViewModel.kt` | 新增——派生待通知列表 |
| `app/src/main/java/com/battor/freshmate/notification/TestAlarmReceiver.kt` | 新增——schedule + 发通知 |
| `app/src/main/java/com/battor/freshmate/ui/settings/AboutScreen.kt` | 修改——入口 |
| `app/src/main/java/com/battor/freshmate/ui/navigation/NavGraph.kt` | 修改——TEST 路由 |
| `app/src/main/java/com/battor/freshmate/ui/main/MainViewModel.kt` | 修改——快照日志 |
| `app/src/main/res/values{,-zh-rTW,-en}/strings.xml` | 修改——新增 11 条字符串：test_title、test_tap_hint、test_empty、test_notification_body、test_delay_15s、test_delay_1m、test_delay_5m、test_status_scheduled、test_status_delivered、test_status_failed_perm、test_status_not_fired |

## 错误处理与边界

- 精确闹钟权限关闭：测试通知回落非精确闹钟（照常排，时间可能小幅偏差）；主功能已有横幅，测试页不重复拦截
- 通知权限关闭：测试页顶部横幅提醒；点档位仍排闹钟（到点 receiver 里 `areNotificationsEnabled()` 检查，未开则记日志、状态区显示失败原因，不发通知，与主链路行为一致）
- 连点中途离开关于页：计数随状态销毁重置，无残留

## 测试策略

- `TestEntryCounterTest`：窗口内累加 / 超时重置为 1 / 第 3、4 次回调 hint / 第 5 次回调 unlock
- `TestViewModelTest`（用现有 `Fakes.kt` 假仓库）：过滤已过时点、多条目展平、升序排序、空列表
- `persist` 快照日志：随 `MainViewModelTest` 现有保存用例断言（FakeScheduler/日志钩子酌情，不为日志单独造框架）
- UI 与 receiver 链路：装机走查（15 秒档位最快闭环）
- 全量验证：`JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug lintDebug`
