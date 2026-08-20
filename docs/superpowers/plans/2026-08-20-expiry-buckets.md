# 过期时间分桶列表 + 提醒时点快照 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 主列表按绝对过期时间六档分桶（红→绿），提醒时点改为录入/编辑时算好快照存库（比例 1/3、1/5、1/6 + 绝对 7/3/1 天，≤24h 合并留较早，空则保底到期时刻）。

**Architecture:** 方案 A——`FoodItem` 新增 `reminderTimes: List<LocalDateTime>`（TEXT 列）保存时算好；分桶是 `MainViewModel` 的纯派生态（`bucketItems(items, now)`）+ 会话内存集合 `sessionItemIds` 驱动"本次添加"置顶区（下拉刷新/冷启动散入各桶）。`ExpiryStatus` 从比例式重定义为绝对时间六档，色板六档 × 双主题经 `LocalStatusColors` 全应用统一。

**Tech Stack:** Kotlin + Jetpack Compose M3（BOM 2024.09.02，material3 1.3.0 含 `PullToRefreshBox`）、Room（version 2→3，无迁移，`fallbackToDestructiveMigration` 兜底）、AlarmManager。

**Spec:** `docs/superpowers/specs/2026-08-20-expiry-buckets-design.md`。分支 `feature/expiry-buckets`（已建好，勿再新建）。

**测试命令**（Windows，bash）：`JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "<类全名>"`

---

## File Structure

| 文件 | 动作 | 职责 |
|---|---|---|
| `app/src/main/java/com/battor/freshmate/util/ReminderUtils.kt` | 重写 | 快照时点计算（合并/保底/过滤） |
| `app/src/main/java/com/battor/freshmate/util/ExpiryStatus.kt` | 重写 | 绝对时间六档 + label + 时长文案 |
| `app/src/main/java/com/battor/freshmate/ui/main/StatusColor.kt` | 重写 | 六档 × 双主题色板 + `LocalStatusColors` |
| `app/src/main/java/com/battor/freshmate/data/FoodItem.kt` | 修改 | +`reminderTimes` |
| `app/src/main/java/com/battor/freshmate/data/FoodItemDatabase.kt` | 修改 | Converters + version 3 |
| `app/src/main/java/com/battor/freshmate/ui/main/MainViewModel.kt` | 重写 | 分桶 + 会话置顶区 + 快照写入 |
| `app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt` | 重写 | 表单置顶/本次添加区/六桶/空态/下拉刷新 |
| `app/src/main/java/com/battor/freshmate/ui/main/FoodItemCard.kt` | 修改 | now 外部传入、新色板 |
| `app/src/main/java/com/battor/freshmate/notification/ReminderIds.kt`、`ReminderScheduler.kt` | 修改 | COUNT=6、读快照设闹钟 |
| `app/src/main/java/com/battor/freshmate/ui/theme/Theme.kt` | 修改 | 提供 `LocalStatusColors` |
| `app/src/main/java/com/battor/freshmate/ui/history/HistoryViewModel.kt`、`HistoryScreen.kt` | 修改 | 还原简化（随时可还原）+ 新色板 |
| `app/src/main/java/com/battor/freshmate/MainActivity.kt`、`AndroidManifest.xml` | 修改 | enableEdgeToEdge + adjustResize（表单在列表内，键盘适配必须） |
| `app/src/test/.../ReminderUtilsTest.kt`、`ExpiryStatusTest.kt`、`MainViewModelTest.kt`、`HistoryViewModelTest.kt` | 重写/修改 | 对应新逻辑 |

不动：`ItemForm.kt`、`FabMenu.kt`（无组参数，无需改）、语音输入、更新模块、设置页、`NavGraph.kt`（MainScreen 签名不变）。

---

### Task 1: ReminderUtils 重写——合并规则与保底

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/util/ReminderUtils.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/MainViewModel.kt:181-182`（save 的 skipped 统计切换新 API）
- Rewrite: `app/src/test/java/com/battor/freshmate/util/ReminderUtilsTest.kt`

- [ ] **Step 1: 重写测试（整文件替换）**

```kotlin
package com.battor.freshmate.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ReminderUtilsTest {
    // 参考场景：到期 2026-09-18 10:00，now 2026-08-20 10:00
    private val expiry = LocalDateTime.of(2026, 9, 18, 10, 0)
    private val now = LocalDateTime.of(2026, 8, 20, 10, 0)

    @Test
    fun `取整到半小时 - 分钟大于等于30`() =
        assertEquals(
            LocalDateTime.of(2026, 1, 1, 14, 30),
            roundDownToHalfHour(LocalDateTime.of(2026, 1, 1, 14, 47, 12)),
        )

    @Test
    fun `取整到半小时 - 分钟小于30`() =
        assertEquals(
            LocalDateTime.of(2026, 1, 1, 14, 0),
            roundDownToHalfHour(LocalDateTime.of(2026, 1, 1, 14, 29, 59, 999_999_999)),
        )

    @Test
    fun `30天保质期合并后五个时点`() {
        // 候选：1/3→09-08、7天→09-11、1/5→09-12、1/6→09-13、3天→09-15、1天→09-17
        // 09-12 与 09-11 间隔恰 24h（含）→ 舍 09-12；09-13 与 09-11 间隔 2 天 → 保留
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 9, 8, 10, 0),
                LocalDateTime.of(2026, 9, 11, 10, 0),
                LocalDateTime.of(2026, 9, 13, 10, 0),
                LocalDateTime.of(2026, 9, 15, 10, 0),
                LocalDateTime.of(2026, 9, 17, 10, 0),
            ),
            computeReminderTimes(expiry, 30, now),
        )
    }

    @Test
    fun `短保质期各档全部落入24h窗口链式合并为单个时点`() {
        // 到期 08-22 10:00，保质期 2 天：比例档 08-21 18:00 / 08-21 00:24→00:00 / 08-21 02:00，
        // 绝对档 7/3 天已过去、1 天→08-21 10:00。00:00 起整条链间隔均 ≤24h → 只剩最早者
        val e = LocalDateTime.of(2026, 8, 22, 10, 0)
        assertEquals(
            listOf(LocalDateTime.of(2026, 8, 21, 0, 0)),
            computeReminderTimes(e, 2, now),
        )
    }

    @Test
    fun `候选全部已过但未到期时保底追加到期时刻`() {
        // 到期 08-20 12:00，保质期 1 天，now 10:00：所有候选（1/3、1/5、1/6、1 天前）均 ≤ now
        val e = LocalDateTime.of(2026, 8, 20, 12, 0)
        assertEquals(
            listOf(LocalDateTime.of(2026, 8, 20, 12, 0)),
            computeReminderTimes(e, 1, now),
        )
    }

    @Test
    fun `保底时点同样取整到半小时`() {
        val e = LocalDateTime.of(2026, 8, 20, 12, 47)
        assertEquals(
            listOf(LocalDateTime.of(2026, 8, 20, 12, 30)),
            computeReminderTimes(e, 1, now),
        )
    }

    @Test
    fun `已过期条目返回空列表`() {
        val e = LocalDateTime.of(2026, 8, 19, 10, 0)
        assertEquals(emptyList<LocalDateTime>(), computeReminderTimes(e, 30, now))
    }

    @Test
    fun `等于now的时点视为已过去`() {
        // 候选 09-08 之外全部过去、09-08 也等于 now → 全过滤，未过期 → 保底
        val n = LocalDateTime.of(2026, 9, 8, 10, 0)
        assertEquals(
            listOf(LocalDateTime.of(2026, 9, 18, 10, 0)),
            computeReminderTimes(expiry, 30, n),
        )
    }
}
```

- [ ] **Step 2: 跑测试确认编译失败**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.util.ReminderUtilsTest"`
Expected: 编译错误 `unresolved reference: computeReminderTimes`

- [ ] **Step 3: 重写实现（整文件替换）**

```kotlin
package com.battor.freshmate.util

import java.time.Duration
import java.time.LocalDateTime

/** 向下取整到半小时（14:47→14:30，14:29→14:00），秒/纳秒清零。 */
fun roundDownToHalfHour(t: LocalDateTime): LocalDateTime =
    t.withMinute(if (t.minute >= 30) 30 else 0).withSecond(0).withNano(0)

/** 合并窗口：相邻候选时点间隔 ≤24 小时（含）视为相近，保留较早者。 */
private val MergeWindow = Duration.ofHours(24)

/**
 * 全部候选提醒时点（不滤 now）：比例档（剩 1/3、1/5、1/6 保质期）∪ 绝对档（到期前 7/3/1 天）。
 * 逐个取整到半小时、排序去重，再按 ≤24h 窗口合并（链式：每个时点与"最后保留者"比间隔）。
 */
fun mergedReminderTimes(expiry: LocalDateTime, shelfLifeDays: Int): List<LocalDateTime> {
    val total = Duration.ofDays(shelfLifeDays.toLong())
    val candidates = listOf(3L, 5L, 6L).map { expiry.minus(total.dividedBy(it)) } +
        listOf(7L, 3L, 1L).map { expiry.minusDays(it) }
    val merged = mutableListOf<LocalDateTime>()
    candidates.map(::roundDownToHalfHour).sorted().distinct().forEach { t ->
        val last = merged.lastOrNull()
        if (last == null || Duration.between(last, t) > MergeWindow) merged.add(t)
    }
    return merged
}

/**
 * 录入/编辑保存时的提醒快照：merged 只保留严格晚于 now 的时点；
 * 全部已过但条目未过期（expiry > now）时保底追加到期时刻本身；已过期返回空列表。
 */
fun computeReminderTimes(
    expiry: LocalDateTime,
    shelfLifeDays: Int,
    now: LocalDateTime,
): List<LocalDateTime> {
    val future = mergedReminderTimes(expiry, shelfLifeDays).filter { it > now }
    return if (future.isEmpty() && expiry > now) listOf(roundDownToHalfHour(expiry)) else future
}
```

- [ ] **Step 4: MainViewModel.save 切换到新 API（保持编译绿）**

`MainViewModel.kt` save() 内（原 `reminderTimes(expiry, days).count { it <= nowProvider() }` 一行）：

```kotlin
val expiry = expiryDateTime(editing.productionDate, editing.createdAt, days)
val skipped = mergedReminderTimes(expiry, days).count { it <= nowProvider() }
```

（import 从 `com.battor.freshmate.util.reminderTimes` 改为 `mergedReminderTimes`。）

- [ ] **Step 5: 跑测试确认通过**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.util.ReminderUtilsTest"`
Expected: PASS（7 个用例）

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/battor/freshmate/util/ReminderUtils.kt app/src/main/java/com/battor/freshmate/ui/main/MainViewModel.kt app/src/test/java/com/battor/freshmate/util/ReminderUtilsTest.kt
git commit -m "feat(reminder): 提醒时点改比例+绝对双档并按24h窗口合并、空则保底到期时刻"
```

---

### Task 2: FoodItem 新增 reminderTimes 字段（DB version 3）

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/data/FoodItem.kt`
- Modify: `app/src/main/java/com/battor/freshmate/data/FoodItemDatabase.kt`

- [ ] **Step 1: FoodItem 加字段（默认值保证既有构造点不破）**

`FoodItem.kt` 在 `deletedAt` 之前加：

```kotlin
    /** 提醒时点快照（需求-3）：新增/编辑保存时算好，调度器只读不算。 */
    @ColumnInfo(name = "reminder_times")
    val reminderTimes: List<LocalDateTime> = emptyList(),
```

- [ ] **Step 2: Converters 加 List 转换器**

`FoodItemDatabase.kt` 的 `Converters` 类追加：

```kotlin
    @TypeConverter
    fun localDateTimeListToString(v: List<LocalDateTime>?): String? =
        v?.takeIf { it.isNotEmpty() }?.joinToString(",") { it.toString() }

    @TypeConverter
    fun stringToLocalDateTimeList(v: String?): List<LocalDateTime>? =
        v?.takeIf { it.isNotBlank() }?.split(",")?.map { LocalDateTime.parse(it) }
```

- [ ] **Step 3: DB version 2→3**

`@Database(entities = [FoodItem::class], version = 3, exportSchema = true)`。
`addMigrations(MIGRATION_1_2)` 保持不变；不写 2→3 迁移——开发阶段数据可弃，`fallbackToDestructiveMigration()` 自动清库重建（在 `addMigrations` 下一行的注释处补一句"2→3 同理靠 destructive 兜底"）。

- [ ] **Step 4: 编译 + 单测全绿（确认无迁移崩溃面）**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL；`app/schemas/` 下生成 `3.json`

- [ ] **Step 5: 提交（含 schema 文件）**

```bash
git add app/src/main/java/com/battor/freshmate/data/FoodItem.kt app/src/main/java/com/battor/freshmate/data/FoodItemDatabase.kt app/schemas/com.battor.freshmate.data.FoodItemDatabase/3.json
git commit -m "feat(data): FoodItem 增加提醒时点快照列，DB version 3（开发期清库重建）"
```

（若 3.json 路径不同，以 `git status` 实际未跟踪的 schema 文件为准。）

---

### Task 3: 保存路径写入快照 + PendingSave 携带剩余数

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/MainViewModel.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt:189-206`（对话框文案）
- Test: `app/src/test/java/com/battor/freshmate/ui/main/MainViewModelTest.kt`

- [ ] **Step 1: 先写失败测试（MainViewModelTest 追加）**

```kotlin
    @Test fun `保存时算好提醒快照落库`() = runTest(dispatcher) {
        saveNew() // now 8-15 10:00 录入，保质期 7 天 → 到期 8-22 10:00
        val saved = repo.items.value.single()
        val expected = computeReminderTimes(
            LocalDateTime.of(2026, 8, 22, 10, 0), 7, now,
        )
        assertEquals(expected, saved.reminderTimes)
        assertFalse(saved.reminderTimes.isEmpty()) // 至少保底一个
    }

    @Test fun `编辑改保质期后快照重算`() = runTest(dispatcher) {
        saveNew()
        vm.startEdit(repo.items.value[0])
        vm.updateEditing { it.copy(shelfLifeValue = "30") }
        vm.save()
        advanceUntilIdle()
        val saved = repo.items.value.single()
        assertEquals(
            computeReminderTimes(LocalDateTime.of(2026, 9, 14, 10, 0), 30, now),
            saved.reminderTimes,
        )
    }
```

（文件头 import 追加 `com.battor.freshmate.util.computeReminderTimes`。）

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.ui.main.MainViewModelTest"`
Expected: 两个新用例 FAIL（`reminderTimes` 为空列表）

- [ ] **Step 3: 实现**

`MainViewModel.kt`：

1. `PendingSave` 增加剩余数字段：

```kotlin
    /** 有提醒时点已过、等待用户确认的保存（设计文档 §6；需求-3 改按快照统计）。 */
    data class PendingSave(
        val editing: EditingState,
        val days: Int,
        val skippedReminders: Int,
        val remainingReminders: Int,
    )
```

2. save() 中构造 pendingSave 处：

```kotlin
        if (skipped > 0) {
            _uiState.update {
                it.copy(
                    pendingSave = PendingSave(
                        editing.copy(name = name),
                        days,
                        skippedReminders = skipped,
                        remainingReminders = computeReminderTimes(expiry, days, nowProvider()).size,
                    ),
                )
            }
            return
        }
```

3. persist() 中构造 FoodItem 处加快照：

```kotlin
        viewModelScope.launch {
            val expiry = expiryDateTime(editing.productionDate, editing.createdAt, days)
            val item = FoodItem(
                id = editing.editingItemId ?: 0,
                name = editing.name.trim(),
                category = editing.category,
                productionDate = editing.productionDate,
                shelfLifeDays = days,
                quantity = editing.quantity.trim().ifEmpty { null },
                createdAt = editing.createdAt,
                reminderTimes = computeReminderTimes(expiry, days, nowProvider()),
                // 编辑保存回到活跃态（编辑入口只对活跃条目开放）
            )
```

（注意：`createdAt = groupKey(editing.createdAt)` 暂保持原样，Task 6 再去掉 groupKey。）

- [ ] **Step 4: MainScreen 对话框文案改用剩余数**

`MainScreen.kt` pendingSave 对话框 text 改为（并删除 `import com.battor.freshmate.notification.ReminderIds`）：

```kotlin
                Text(
                    "「${pending.editing.name}」已有 ${pending.skippedReminders} 个提醒时点过去，" +
                        "剩余提醒时点 ${pending.remainingReminders} 个。确认保存吗？",
                )
```

- [ ] **Step 5: 跑测试确认通过**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.ui.main.MainViewModelTest"`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/battor/freshmate/ui/main/MainViewModel.kt app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt app/src/test/java/com/battor/freshmate/ui/main/MainViewModelTest.kt
git commit -m "feat(reminder): 保存路径写入提醒快照，临近过期确认框按快照统计剩余数"
```

---

### Task 4: 调度器改读快照，REMINDER_COUNT 3→6

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/notification/ReminderIds.kt`
- Modify: `app/src/main/java/com/battor/freshmate/notification/ReminderScheduler.kt`

- [ ] **Step 1: ReminderIds**

```kotlin
object ReminderIds {
    const val CHANNEL_ID = "expiry_reminders"

    /** 快照时点合并后最多 6 个（比例 3 + 绝对 3）。 */
    const val REMINDER_COUNT = 6

    /** requestCode = itemId * 10 + index（index 0..5），同一通知 id 也用它。 */
    fun requestCode(itemId: Long, index: Int): Int = (itemId * 10 + index).toInt()
}
```

- [ ] **Step 2: ReminderScheduler.schedule 读快照**

schedule() 整体替换（删除内部 expiryDateTime/futureReminderTimes 计算；移除 import `com.battor.freshmate.util.expiryDateTime` 与 `com.battor.freshmate.util.futureReminderTimes`，`LocalDateTime` 仍被 rescheduleAll 使用保留）：

```kotlin
    override fun schedule(item: FoodItem) {
        cancel(item.id)
        // 提醒时点来自保存时的快照（需求-3），调度器只读不算
        val times = item.reminderTimes
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        Timber.i("ALARM schedule itemId=%d 时点数=%d 精确=%b", item.id, times.size, canExact)
        times.forEachIndexed { index, time ->
            val pi = broadcast(item.id, index)
            val atMillis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        }
    }
```

`cancel()`/`broadcast()`/`rescheduleAll()` 不变（cancel 循环自动覆盖 0..5）。

- [ ] **Step 3: 编译 + 全部单测**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/battor/freshmate/notification/ReminderIds.kt app/src/main/java/com/battor/freshmate/notification/ReminderScheduler.kt
git commit -m "refactor(reminder): 调度器改读快照时点设闹钟，REMINDER_COUNT 扩到 6"
```

---

### Task 5: ExpiryStatus 绝对六档 + 六档双主题色板

**Files:**
- Rewrite: `app/src/main/java/com/battor/freshmate/util/ExpiryStatus.kt`
- Rewrite: `app/src/main/java/com/battor/freshmate/ui/main/StatusColor.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/FoodItemCard.kt:47-50`
- Modify: `app/src/main/java/com/battor/freshmate/ui/history/HistoryScreen.kt:164-166`
- Rewrite: `app/src/test/java/com/battor/freshmate/util/ExpiryStatusTest.kt`

- [ ] **Step 1: 重写 ExpiryStatusTest（整文件替换）**

```kotlin
package com.battor.freshmate.util

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpiryStatusTest {
    private val now = LocalDateTime.of(2026, 8, 20, 10, 0)

    @Test fun `过期即EXPIRED`() {
        val e = now.minusHours(1)
        assertEquals(ExpiryStatus.EXPIRED, expiryStatus(e, now))
    }

    @Test fun `恰好到期即EXPIRED`() {
        assertEquals(ExpiryStatus.EXPIRED, expiryStatus(now, now))
    }

    @Test fun `恰在24h边界归DUE_1D`() =
        assertEquals(ExpiryStatus.DUE_1D, expiryStatus(now.plusHours(24), now))

    @Test fun `恰在72h边界归DUE_3D`() =
        assertEquals(ExpiryStatus.DUE_3D, expiryStatus(now.plusHours(72), now))

    @Test fun `恰在168h边界归DUE_7D`() =
        assertEquals(ExpiryStatus.DUE_7D, expiryStatus(now.plusHours(168), now))

    @Test fun `恰在336h边界归DUE_14D`() =
        assertEquals(ExpiryStatus.DUE_14D, expiryStatus(now.plusHours(336), now))

    @Test fun `超过14天SAFE`() =
        assertEquals(ExpiryStatus.SAFE, expiryStatus(now.plusHours(337), now))

    @Test fun `剩余2天归DUE_3D而非按旧比例`() {
        // 旧比例式：保质期 30 天剩 2 天已是 CRITICAL；新绝对式：2 天 = DUE_3D
        assertEquals(ExpiryStatus.DUE_3D, expiryStatus(now.plusDays(2), now))
    }
}
```

- [ ] **Step 2: 跑测试确认编译失败**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.util.ExpiryStatusTest"`
Expected: 编译错误 `too many arguments / unresolved DUE_1D` 等

- [ ] **Step 3: 重写 ExpiryStatus.kt（整文件替换；formatRemaining/formatExpired 原样保留）**

```kotlin
package com.battor.freshmate.util

import java.time.Duration
import java.time.LocalDateTime

/**
 * 条目紧急度（需求-3 改为绝对时间六档，与主列表分桶同阈值），值越靠后越宽松。
 * 红(EXPIRED) → DUE_1D → DUE_3D → DUE_7D → DUE_14D → 绿(SAFE)。
 */
enum class ExpiryStatus(val label: String) {
    EXPIRED("已过期"),
    DUE_1D("1 天内到期"),
    DUE_3D("3 天内到期"),
    DUE_7D("7 天内到期"),
    DUE_14D("14 天内到期"),
    SAFE("更久到期"),
}

fun expiryStatus(expiry: LocalDateTime, now: LocalDateTime): ExpiryStatus {
    val remaining = Duration.between(now, expiry)
    if (!remaining.isNegative && remaining.isZero) return ExpiryStatus.EXPIRED
    if (remaining.isNegative) return ExpiryStatus.EXPIRED
    return when {
        remaining <= Duration.ofDays(1) -> ExpiryStatus.DUE_1D
        remaining <= Duration.ofDays(3) -> ExpiryStatus.DUE_3D
        remaining <= Duration.ofDays(7) -> ExpiryStatus.DUE_7D
        remaining <= Duration.ofDays(14) -> ExpiryStatus.DUE_14D
        else -> ExpiryStatus.SAFE
    }
}

// formatRemaining / formatExpired 两个函数原样保留，勿动
```

- [ ] **Step 4: 重写 StatusColor.kt（整文件替换）**

```kotlin
package com.battor.freshmate.ui.main

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.util.ExpiryStatus
import com.battor.freshmate.util.expiryDateTime
import com.battor.freshmate.util.formatExpired
import com.battor.freshmate.util.formatRemaining
import java.time.Duration
import java.time.LocalDateTime

/** 已过期状态与滑动删除背景共用的深红色。 */
val ExpiredRed = Color(0xFFC62828)

/** 历史页右滑还原背景（镜像主列表删除的 ExpiredRed）。 */
val RestoreGreen = Color(0xFF3EB04A)

/** 一档状态色：(容器色, 前景色)。 */
data class StatusColors(val container: Color, val on: Color)

/** 六档色板（红→绿），浅色用粉彩容器色，深色用低饱和容器 + 浅前景。 */
data class StatusPalette(
    val expired: StatusColors,
    val due1d: StatusColors,
    val due3d: StatusColors,
    val due7d: StatusColors,
    val due14d: StatusColors,
    val safe: StatusColors,
) {
    fun of(status: ExpiryStatus): StatusColors = when (status) {
        ExpiryStatus.EXPIRED -> expired
        ExpiryStatus.DUE_1D -> due1d
        ExpiryStatus.DUE_3D -> due3d
        ExpiryStatus.DUE_7D -> due7d
        ExpiryStatus.DUE_14D -> due14d
        ExpiryStatus.SAFE -> safe
    }
}

val LightStatusPalette = StatusPalette(
    expired = StatusColors(Color(0xFFC62828), Color(0xFFFFFFFF)),
    due1d = StatusColors(Color(0xFFFFCDD2), Color(0xFF8E1418)),
    due3d = StatusColors(Color(0xFFFFE0B2), Color(0xFF8C4A00)),
    due7d = StatusColors(Color(0xFFFFF3BF), Color(0xFF6B5A00)),
    due14d = StatusColors(Color(0xFFEDF5C0), Color(0xFF3F5327)),
    safe = StatusColors(Color(0xFFDCF5CE), Color(0xFF274F1B)),
)

val DarkStatusPalette = StatusPalette(
    expired = StatusColors(Color(0xFFB71C1C), Color(0xFFFFFFFF)),
    due1d = StatusColors(Color(0xFF5D2A30), Color(0xFFF6C4C8)),
    due3d = StatusColors(Color(0xFF54462E), Color(0xFFFCD9A6)),
    due7d = StatusColors(Color(0xFF565030), Color(0xFFF0E8A0)),
    due14d = StatusColors(Color(0xFF424D2B), Color(0xFFDCE8B0)),
    safe = StatusColors(Color(0xFF2F4A26), Color(0xFFC8E8B8)),
)

/** 由 Theme.kt 随深浅色提供；卡片/桶头/历史页统一读取。 */
val LocalStatusColors = staticCompositionLocalOf { LightStatusPalette }

/** 条目右侧状态文本：已过期 x / 还有 x 到期。 */
fun expiryText(item: FoodItem, now: LocalDateTime): String {
    val expiry = expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
    val remaining = Duration.between(now, expiry)
    return if (remaining.isNegative || remaining.isZero) {
        "已过期 ${formatExpired(remaining.negated())}"
    } else {
        "还有 ${formatRemaining(remaining)} 到期"
    }
}
```

（色值为本轮选定的起点，装机走查时可微调，改动只落在此文件。）

- [ ] **Step 5: Theme.kt 提供色板**

`FreshMateTheme` 末尾的 `MaterialTheme(...)` 包上 CompositionLocalProvider（新增 import `androidx.compose.runtime.CompositionLocalProvider`、`com.battor.freshmate.ui.main.DarkStatusPalette`、`com.battor.freshmate.ui.main.LightStatusPalette`、`com.battor.freshmate.ui.main.LocalStatusColors`）：

```kotlin
    val palette = if (darkTheme) DarkStatusPalette else LightStatusPalette
    CompositionLocalProvider(LocalStatusColors provides palette) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
```

- [ ] **Step 6: FoodItemCard / HistoryScreen 消费点切换（保持编译绿）**

`FoodItemCard.kt:47-50` 改为（`expiryStatus` 新签名不再收 shelfLifeDays）：

```kotlin
    val now = remember { LocalDateTime.now() }
    val expiry = remember(item) { expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays) }
    val (container, onColor) = LocalStatusColors.current.of(expiryStatus(expiry, now))
```

（import 改 `com.battor.freshmate.ui.main.LocalStatusColors`，删 `statusColors` 引用；`now` 仍暂为 remember，Task 6 改外部传入。）

`HistoryScreen.kt:164-166` 同理：

```kotlin
    val now = remember { LocalDateTime.now() }
    val expiry = remember(item) { expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays) }
    val (container, onColor) = LocalStatusColors.current.of(expiryStatus(expiry, now))
```

（import 调整同上；`statusColors` 函数已删，全文件不得再引用。）

- [ ] **Step 7: 编译 + 全部单测**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL（ExpiryStatusTest 8 个用例 PASS）

- [ ] **Step 8: 提交**

```bash
git add app/src/main/java/com/battor/freshmate/util/ExpiryStatus.kt app/src/main/java/com/battor/freshmate/ui/main/StatusColor.kt app/src/main/java/com/battor/freshmate/ui/theme/Theme.kt app/src/main/java/com/battor/freshmate/ui/main/FoodItemCard.kt app/src/main/java/com/battor/freshmate/ui/history/HistoryScreen.kt app/src/test/java/com/battor/freshmate/util/ExpiryStatusTest.kt
git commit -m "feat(ui): 过期状态改绝对时间六档，六档双主题色板经 LocalStatusColors 统一"
```

---

### Task 6: 主列表重写——分桶、本次添加区、下拉刷新、顶部内嵌表单

**Files:**
- Rewrite: `app/src/main/java/com/battor/freshmate/ui/main/MainViewModel.kt`（分组部分）
- Rewrite: `app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/FoodItemCard.kt`（now 外部传入、去 dimmed）
- Modify: `app/src/main/java/com/battor/freshmate/MainActivity.kt`、`app/src/main/AndroidManifest.xml`（edge-to-edge，键盘适配）
- Rewrite（部分）: `app/src/test/java/com/battor/freshmate/ui/main/MainViewModelTest.kt`

- [ ] **Step 1: 重写 MainViewModel 分组为分桶 + 会话集合（先改测试再改实现）**

MainViewModelTest **删除**以下旧用例（组模型专属）：`按录入时间倒序分组`、`点组续加复用该组的录入时间`、`暂存成功后徽标转移到目标组`、`点组续加未暂存返回时徽标不动`、`加菜单每次新开一组`、`录入时刻截断到分钟保证表单与分组键一致`、`编辑含秒的旧数据条目也对齐分组键`、`返回放弃表单时徽标不动`。

**改写**以下用例：

```kotlin
    @Test fun `合法输入保存后入库并排提醒`() = runTest(dispatcher) {
        saveNew()
        assertEquals(1, repo.items.value.size)
        assertEquals("牛奶", repo.items.value[0].name)
        assertEquals(7, repo.items.value[0].shelfLifeDays)
        assertEquals(1, scheduler.scheduled.size)
        // 暂存后表单清空继续，条目进入「本次添加」置顶区
        val editing = vm.uiState.value.editing
        assertNotNull(editing)
        assertTrue(editing!!.name.isEmpty())
        assertEquals(setOf(repo.items.value[0].id), vm.uiState.value.sessionItemIds)
        assertEquals(1, vm.uiState.value.pinnedItems.size)
    }

    @Test fun `放弃新表单返回时置顶区不受影响`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        vm.backToMethodSelection()
        assertNull(vm.uiState.value.editing)
        assertTrue(vm.uiState.value.pinnedItems.isEmpty())
        assertTrue(repo.items.value.isEmpty())
    }

    @Test fun `软删除条目不出现在主列表`() = runTest(dispatcher) {
        saveNew()
        vm.delete(repo.items.value[0])
        advanceUntilIdle()
        assertTrue(vm.uiState.value.items.isEmpty())
        assertTrue(vm.uiState.value.buckets.isEmpty())
    }

    @Test fun `同会话多次暂存都在置顶区`() = runTest(dispatcher) {
        saveNew()
        saveNew(name = "面包")
        assertEquals(2, repo.items.value.size)
        assertEquals(2, vm.uiState.value.pinnedItems.size)
        assertTrue(vm.uiState.value.buckets.isEmpty()) // 置顶区条目不重复出现在桶中
    }
```

**新增**分桶与会话用例（import 追加 `com.battor.freshmate.util.ExpiryStatus`）：

```kotlin
    @Test fun `按绝对过期时间分桶且桶序为紧急度`() {
        val now = LocalDateTime.of(2026, 8, 20, 10, 0)
        fun of(name: String, hours: Long) = FoodItem(
            name = name, category = Category.DAIRY, productionDate = null,
            shelfLifeDays = 30, quantity = null, createdAt = now.minusHours(hours),
        )
        // 到期 = createdAt + 30 天，相对 now 的剩余 = 720h - hours
        val buckets = bucketItems(
            listOf(
                of("远", 100),   // 剩 620h ≈ 25.8 天 → SAFE
                of("一", 696),   // 剩 24h → DUE_1D
                of("过", 744),   // 剩 -24h → EXPIRED
                of("三", 648),   // 剩 72h → DUE_3D
                of("七", 552),   // 剩 168h → DUE_7D
                of("十四", 384), // 剩 336h → DUE_14D
            ),
            now,
        )
        assertEquals(
            listOf(
                ExpiryStatus.EXPIRED, ExpiryStatus.DUE_1D, ExpiryStatus.DUE_3D,
                ExpiryStatus.DUE_7D, ExpiryStatus.DUE_14D, ExpiryStatus.SAFE,
            ),
            buckets.map { it.status },
        )
    }

    @Test fun `桶内按到期时间升序最紧急在前`() {
        val now = LocalDateTime.of(2026, 8, 20, 10, 0)
        fun of(name: String, hours: Long) = FoodItem(
            name = name, category = Category.DAIRY, productionDate = null,
            shelfLifeDays = 30, quantity = null, createdAt = now.minusHours(hours),
        )
        val buckets = bucketItems(listOf(of("晚到期", 600), of("早到期", 690)), now)
        assertEquals(1, buckets.size)
        assertEquals(listOf("早到期", "晚到期"), buckets[0].items.map { it.name })
    }

    @Test fun `空桶不渲染`() {
        val now = LocalDateTime.of(2026, 8, 20, 10, 0)
        val only = FoodItem(
            name = "a", category = Category.DAIRY, productionDate = null,
            shelfLifeDays = 30, quantity = null, createdAt = now,
        )
        assertEquals(1, bucketItems(listOf(only), now).size)
    }

    @Test fun `下拉刷新清空会话置顶区条目散入各桶`() = runTest(dispatcher) {
        saveNew() // 到期 8-22 10:00 → DUE_1D（还剩 2 天）
        assertTrue(vm.uiState.value.pinnedItems.isNotEmpty())
        vm.disperseSession()
        assertTrue(vm.uiState.value.pinnedItems.isEmpty())
        assertEquals(listOf(ExpiryStatus.DUE_1D), vm.uiState.value.buckets.map { it.status })
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.ui.main.MainViewModelTest"`
Expected: 编译错误（`bucketItems`/`disperseSession`/`pinnedItems` 未定义）

- [ ] **Step 3: 重写 MainViewModel**

文件头部（替换 `FoodItemGroup`/`groupItems`/`groupKey` 三者；`groupKey` 本任务先保留原位，Task 7 删）：

```kotlin
/**
 * 组键：录入时刻截断到分钟——仅历史页删除分组仍在用（需求-3 后主列表不再分组），
 * 随 Task 7 历史重构移除。
 */
fun groupKey(time: LocalDateTime): LocalDateTime = time.truncatedTo(ChronoUnit.MINUTES)

/** 列表分桶（需求-3）：桶序 = 紧急度（EXPIRED→SAFE），桶内按到期时间升序（最紧急在前）。 */
data class ExpiryBucket(val status: ExpiryStatus, val items: List<FoodItem>)

fun bucketItems(items: List<FoodItem>, now: LocalDateTime): List<ExpiryBucket> =
    ExpiryStatus.entries.mapNotNull { status ->
        items
            .filter { expiryStatus(expiryDateTime(it.productionDate, it.createdAt, it.shelfLifeDays), now) == status }
            .takeIf { it.isNotEmpty() }
            ?.let { list ->
                ExpiryBucket(
                    status,
                    list.sortedBy { expiryDateTime(it.productionDate, it.createdAt, it.shelfLifeDays) },
                )
            }
    }
```

UiState 替换为（`activeGroup`/`groups`/`activeGroupItems` 删除）：

```kotlin
    data class UiState(
        val items: List<FoodItem> = emptyList(),
        /** 页面级统一时刻：分桶与卡片状态文本共用（卡片不再各自 remember）。 */
        val now: LocalDateTime = LocalDateTime.MIN,
        /** 本次会话新录入条目 id：驱动「本次添加」置顶区；下拉刷新/冷启动清空后散入各桶。 */
        val sessionItemIds: Set<Long> = emptySet(),
        val editing: EditingState? = null,
        val pendingSave: PendingSave? = null,
        val requestNotificationPermission: Boolean = false,
    ) {
        val isEditing: Boolean get() = editing != null
        val isAddForm: Boolean get() = editing != null && editing.editingItemId == null

        /** 「本次添加」置顶区条目（新→旧）。 */
        val pinnedItems: List<FoodItem>
            get() = items.filter { it.id in sessionItemIds }.sortedByDescending { it.createdAt }

        /** 过期时间分桶；置顶区条目不重复入桶。 */
        val buckets: List<ExpiryBucket>
            get() = bucketItems(items.filter { it.id !in sessionItemIds }, now)

        // hasFormContent 原样保留，勿动
    }
```

init 的 collect 简化为：

```kotlin
    init {
        viewModelScope.launch {
            repository.observeAll().collect { items ->
                _uiState.update { it.copy(items = items, now = nowProvider()) }
            }
        }
    }
```

方法改动：

```kotlin
    /** + 菜单录入：新开表单（新时间戳，作保质期无生产日期时的起算点）。 */
    fun startNew(method: InputMethodId) {
        if (saving) return
        _uiState.update {
            it.copy(editing = EditingState(inputMethod = method, createdAt = nowProvider()))
        }
    }

    /** 下拉刷新：本次添加区散入各桶（清空会话集合，纯内存态）。 */
    fun disperseSession() {
        _uiState.update { it.copy(sessionItemIds = emptySet()) }
    }
```

- **删除** `startAddTo` 整个方法。
- `startEdit`：`createdAt = groupKey(item.createdAt)` → `createdAt = item.createdAt`。
- `persist`：`createdAt = groupKey(editing.createdAt)` → `createdAt = editing.createdAt`；保存成功后的状态更新改为：

```kotlin
            _uiState.update {
                // 新条目暂存后表单清空继续，并进入「本次添加」置顶区；
                // 编辑已有条目仍是保存即退出，会话集合不动
                if (editing.editingItemId == null) {
                    it.copy(editing = clearedForm(editing), sessionItemIds = it.sessionItemIds + itemId)
                } else {
                    it.copy(editing = null)
                }
            }
```

- import 清理：`groupItems` 删除后无引用的 import 移除；新增 `com.battor.freshmate.util.ExpiryStatus`、`com.battor.freshmate.util.expiryStatus`。

- [ ] **Step 4: 跑 MainViewModelTest 确认通过**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.ui.main.MainViewModelTest"`
Expected: PASS（此时 MainScreen 编译失败没关系——下一步重写它；若 gradle 因 MainScreen 编译失败而无法跑测试，直接进 Step 5 再回来跑）

- [ ] **Step 5: FoodItemCard 改 now 外部传入、去 dimmed**

签名与开头改为（`dimmed` 参数删除、`remember { LocalDateTime.now() }` 删除、`alpha` import 若无他处使用一并删）：

```kotlin
@Composable
fun FoodItemCard(
    item: FoodItem,
    now: LocalDateTime,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean = true,
) {
    val expiry = remember(item) { expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays) }
    val (container, onColor) = LocalStatusColors.current.of(expiryStatus(expiry, now))
```

SwipeToDismissBox 的 modifier 中删除 `.alpha(if (dimmed) 0.4f else 1f)`（保留 semantics customActions）。

- [ ] **Step 6: 重写 MainScreen（整文件替换）**

```kotlin
package com.battor.freshmate.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pullrefresh.PullToRefreshBox
import androidx.compose.material3.pullrefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.util.ExpiryStatus
import java.time.LocalDateTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    updateHint: String? = null,
    onUpdateHintShown: () -> Unit = {},
    onOpenUpdate: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorEvent by viewModel.errorEvent.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(errorEvent) {
        errorEvent?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    // 启动静默检查发现新版：Snackbar 一条 + 「查看」跳设置页（一次性，展示即清）
    LaunchedEffect(updateHint) {
        if (updateHint == null) return@LaunchedEffect
        try {
            val result = snackbarHostState.showSnackbar(updateHint, actionLabel = "查看")
            onUpdateHintShown()
            if (result == SnackbarResult.ActionPerformed) onOpenUpdate()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 展示中途离开本页（Snackbar 协程被取消）：也清掉一次性提示，防回来重复弹
            onUpdateHintShown()
            throw e
        }
    }

    NotificationPermissionEffect(
        request = state.requestNotificationPermission,
        onHandled = { viewModel.onPermissionRequested() },
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("食刻 FreshMate") },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = "历史")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FabMenu(
                formOpen = state.isEditing,
                isAddForm = state.isAddForm,
                showSave = state.hasFormContent,
                onStartInput = { viewModel.startNew(it) },
                onSave = { viewModel.save() },
                onBack = { viewModel.backToMethodSelection() },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PermissionBanners()
            PullToRefreshBox(
                isRefreshing = false,
                onRefresh = { viewModel.disperseSession() },
                state = rememberPullToRefreshState(),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 表单固定渲染在列表顶部（需求-3：组不再承载表单）
                    state.editing?.let { editing ->
                        item(key = "form") {
                            ItemForm(
                                state = editing,
                                onStateChange = { newState -> viewModel.updateEditing { newState } },
                                onPlaceholderHint = { scope.launch { snackbarHostState.showSnackbar(it) } },
                            )
                        }
                    }
                    // 本次添加置顶区（会话内存态；下拉刷新/冷启动散入各桶）
                    if (state.pinnedItems.isNotEmpty()) {
                        item(key = "pinned") {
                            BucketBox(
                                header = "本次添加",
                                status = null,
                                items = state.pinnedItems,
                                now = state.now,
                                cardsEnabled = state.editing == null,
                                onStartEdit = { viewModel.startEdit(it) },
                                onDeleteItem = { item ->
                                    viewModel.delete(item)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            "已删除「${item.name}」",
                                            actionLabel = "撤销",
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.undoDelete(item)
                                        }
                                    }
                                },
                            )
                        }
                    }
                    // 六个过期时间桶，空桶不渲染；桶头/组空白不可点击
                    state.buckets.forEach { bucket ->
                        item(key = "bucket_${bucket.status}") {
                            BucketBox(
                                header = bucket.status.label,
                                status = bucket.status,
                                items = bucket.items,
                                now = state.now,
                                cardsEnabled = state.editing == null,
                                onStartEdit = { viewModel.startEdit(it) },
                                onDeleteItem = { item ->
                                    viewModel.delete(item)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            "已删除「${item.name}」",
                                            actionLabel = "撤销",
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.undoDelete(item)
                                        }
                                    }
                                },
                            )
                        }
                    }
                    if (state.items.isEmpty() && state.editing == null) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("暂无食品，点 + 添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    state.pendingSave?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingSave() },
            title = { Text("该食品临近过期") },
            text = {
                Text(
                    "「${pending.editing.name}」已有 ${pending.skippedReminders} 个提醒时点过去，" +
                        "剩余提醒时点 ${pending.remainingReminders} 个。确认保存吗？",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmPendingSave() }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelPendingSave() }) { Text("取消") }
            },
        )
    }
}

/**
 * 桶容器（需求-3）：浅边框 + 状态色桶头 + 子条目卡片。
 * status = null 表示「本次添加」置顶区（主色 2dp 边框、组头用主色文本）。
 * 点卡片 = 编辑；组头与组空白不可点击（桶按过期时间聚合，无组级交互）。
 */
@Composable
private fun BucketBox(
    header: String,
    status: ExpiryStatus?,
    items: List<FoodItem>,
    now: LocalDateTime,
    cardsEnabled: Boolean,
    onStartEdit: (FoodItem) -> Unit,
    onDeleteItem: (FoodItem) -> Unit,
) {
    val border = if (status == null) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = border,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val statusColors = status?.let { LocalStatusColors.current.of(it) }
            if (statusColors != null) {
                Text(
                    header,
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColors.on,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColors.container)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            } else {
                Text(
                    header,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items.forEach { item ->
                FoodItemCard(
                    item = item,
                    now = now,
                    onClick = { onStartEdit(item) },
                    onDelete = { onDeleteItem(item) },
                    enabled = cardsEnabled,
                )
            }
        }
    }
}
```

- [ ] **Step 7: edge-to-edge（表单在列表内，键盘适配必须）**

1. `MainActivity.kt`：`setContent` 之前加 `enableEdgeToEdge()`（import `androidx.activity.enableEdgeToEdge`；activity-compose 1.9.2 已含该 API，无需新依赖）。
2. `AndroidManifest.xml` MainActivity 节点加 `android:windowSoftInputMode="adjustResize"`。
3. LazyColumn 已带 `.imePadding()`（Step 6 代码中）。

- [ ] **Step 8: 编译 + 全部单测**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: 提交**

```bash
git add app/src/main/java/com/battor/freshmate/ui/main/MainViewModel.kt app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt app/src/main/java/com/battor/freshmate/ui/main/FoodItemCard.kt app/src/main/java/com/battor/freshmate/MainActivity.kt app/src/main/AndroidManifest.xml app/src/test/java/com/battor/freshmate/ui/main/MainViewModelTest.kt
git commit -m "feat(ui): 主列表按绝对过期时间六档分桶，本次添加置顶区+下拉刷新散入，表单改顶部内嵌"
```

（MainActivity 实际路径若为 `ui/MainActivity.kt` 之类，以 `git ls-files | grep MainActivity` 输出为准。）

---

### Task 7: 历史页还原简化

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/history/HistoryViewModel.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/history/HistoryScreen.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/MainViewModel.kt`（删 groupKey）
- Modify: `app/src/test/java/com/battor/freshmate/ui/history/HistoryViewModelTest.kt`

- [ ] **Step 1: 先改测试**

**删除**用例：`组活跃时可还原组不存在时不可还原`、`组在确认前失效时还原被拒绝`。

**改写** `确认还原清空deletedAt并重排提醒`（去掉保组活跃依赖与文案）：

```kotlin
    @Test fun `确认还原清空deletedAt并重排提醒`() = runTest(dispatcher) {
        val g = LocalDateTime.of(2026, 8, 14, 9, 0)
        val a = insertActive("牛奶", g)
        advanceUntilIdle()
        deleteActive(a)

        val deleted = vm.uiState.value.groups[0].items.single { it.name == "牛奶" }
        vm.requestRestore(deleted)
        assertEquals(deleted, vm.uiState.value.restoring)
        vm.cancelRestore()
        assertNull(vm.uiState.value.restoring)

        vm.requestRestore(deleted)
        vm.confirmRestore()
        advanceUntilIdle()
        assertEquals(1, repo.getAll().size)
        assertTrue(scheduler.scheduled.any { it.id == a.id })
        assertNull(vm.uiState.value.restoring)
        assertEquals("已还原「牛奶」", vm.message.value)
    }
```

**改写** `已过期条目还原不排提醒只取消`（去掉保组活跃）：

```kotlin
    @Test fun `已过期条目还原不排提醒只取消`() = runTest(dispatcher) {
        val expired = FoodItem(
            name = "酸奶", category = Category.DAIRY,
            productionDate = LocalDate.of(2026, 8, 1), shelfLifeDays = 7,
            quantity = null, createdAt = LocalDateTime.of(2026, 8, 14, 9, 0),
        ).let { item ->
            val id = repo.insert(item)
            repo.items.value.first { it.id == id }
        }
        advanceUntilIdle()
        deleteActive(expired)

        val deleted = vm.uiState.value.groups[0].items.single { it.name == "酸奶" }
        vm.requestRestore(deleted) // 需求-3：随时可还原，无组门禁
        vm.confirmRestore()
        advanceUntilIdle()
        assertEquals(1, repo.getAll().size)
        assertTrue(scheduler.scheduled.isEmpty())
        assertTrue(scheduler.cancelled.contains(expired.id))
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.ui.history.HistoryViewModelTest"`
Expected: FAIL（`isRestorable` 仍存在导致文案/行为不符）

- [ ] **Step 3: HistoryViewModel 简化**

1. 删除 `activeGroupKeys`、`isRestorable`、init 中 `observeAll` 的 collect、`import com.battor.freshmate.ui.main.groupKey`、`import com.battor.freshmate.data.FoodRepository` 保留其余。
2. UiState 改为：

```kotlin
    data class UiState(
        val groups: List<DeletedGroup> = emptyList(),
        val restoring: FoodItem? = null,
    )
```

3. 删除分组用的 groupKey 依赖——把删除分组改为内联截断（文件内新增私有函数）：

```kotlin
/** 删除时刻截断到分钟：同分钟删除的条目归为一组。 */
private fun deletedAtKey(time: LocalDateTime): LocalDateTime = time.truncatedTo(ChronoUnit.MINUTES)
```

init 中 observeDeleted 的 collect 改用 `deletedAtKey(requireNotNull(d.deletedAt))`（import `java.time.temporal.ChronoUnit`）。

4. `requestRestore`/`confirmRestore` 去掉 isRestorable 门禁：

```kotlin
    /** 右滑触发：随时可还原（需求-3：组模型取消，无门禁），进入待确认状态。 */
    fun requestRestore(item: FoodItem) {
        _uiState.update { it.copy(restoring = item) }
    }

    fun confirmRestore() {
        val item = _uiState.value.restoring ?: return
        _uiState.update { it.copy(restoring = null) }
        viewModelScope.launch {
            try {
                repository.restore(item)
                scheduler.scheduleOrCancel(item, nowProvider())
                _message.value = "已还原「${item.name}」"
            } catch (e: CancellationException) {
                throw e // 取消照常上抛，不按失败处理
            } catch (e: Exception) {
                _message.value = "还原失败，请重试"
            }
        }
    }
```

- [ ] **Step 4: HistoryScreen 简化**

1. 对话框文案：`"把「${item.name}」还原吗？还原后将回到对应的到期分组。"`
2. `DeletedGroupBox` 调用与签名去掉 `isRestorable` 参数；组头"可还原 x/y"文本行整行删除。
3. `HistoryItemCard`：`restorable` 参数删除——`alpha` 恒 1（modifier 里 `.alpha(...)` 与条件 semantics 简化为恒提供「还原」customActions）、`enableDismissFromStartToEnd = true`、`if (!restorable) Text("原组已不存在"...)` 分支删除。

- [ ] **Step 5: MainViewModel 删 groupKey**

Task 6 保留的 `groupKey` 函数及其注释整块删除（`ChronoUnit` import 若无他处使用一并删）。

- [ ] **Step 6: 编译 + 全部单测**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/battor/freshmate/ui/history/HistoryViewModel.kt app/src/main/java/com/battor/freshmate/ui/history/HistoryScreen.kt app/src/main/java/com/battor/freshmate/ui/main/MainViewModel.kt app/src/test/java/com/battor/freshmate/ui/history/HistoryViewModelTest.kt
git commit -m "refactor(history): 还原去掉组门禁随时可还原，删除分组键内聚到历史页"
```

---

### Task 8: 全量验证与重审

- [ ] **Step 1: 全量构建**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL，0 failures

- [ ] **Step 2: 装机走查清单（用户执行或模拟器）**

1. 录入保质期 30 天条目 → 日志 `ALARM schedule 时点数=5`；桶头"更久到期"绿色。
2. 录入保质期 1 天、生产日期昨天 → 临近过期确认框"剩余提醒时点 X 个"；确认后条目进"已过期"或"1 天内到期"桶。
3. 新增条目出现在顶部"本次添加"区；下拉刷新后散入所属桶。
4. 点卡片 → 表单顶部打开预填；保存即退出。点桶头/桶空白无反应。
5. 空库首启 → "暂无食品，点 + 添加"空态。
6. 表单中点击名称/保质期字段 → 键盘不遮输入框（imePadding + adjustResize 生效）。
7. 深色模式 → 六档色板为低饱和深色变体；历史页卡片同色板。
8. 历史页任意已删除条目右滑 → 可还原，无"原组已不存在"。

- [ ] **Step 3: 四 skill 重审（对重写后的新代码）**

按用户要求，实现完成后重新调用 `mobile-android-design` / `edge-to-edge` / `android-intent-security` / `styles` 四个 skill 对新代码做只读审查（旧清单 `docs/reviews/2026-08-20-skill-audit-findings.md` 仅作备用参照，其中 MainScreen/分组相关条目已随重写失效）。审查发现的新问题呈报用户决定是否本轮修。

- [ ] **Step 4: 收尾提交（如有走查修正）**

```bash
git add <修正文件>
git commit -m "fix(ui): 装机走查修正"
```

---

## Self-Review 记录

- **Spec 覆盖**：§1 快照计算/写入时机/调度器 → Task 1-4；§2 分桶/置顶区/下拉刷新/表单宿主/历史还原 → Task 6-7；§3 六档色板/LocalStatusColors/统一消费 → Task 5；§4 TODO 搜索 → 不需任务（文档已记）；§5 测试/验证 → 各任务 + Task 8。无缺口。
- **类型一致性**：`computeReminderTimes`/`mergedReminderTimes`（Task 1 定义，3/4/6 引用一致）；`PendingSave.remainingReminders`（Task 3 定义，MainScreen Task 6 重写后沿用）；`ExpiryBucket(status, items)`、`pinnedItems`/`sessionItemIds`/`disperseSession()`（Task 6 内自洽）；`ExpiryStatus.label`（Task 5 定义，Task 6 桶头使用）。
- **编译序**：每个 Task 结束时编译绿——Task 1 顺带改 save() 调用点；Task 5 顺带改 FoodItemCard/HistoryScreen 调用点；Task 6 内 VM 与 Screen 同任务完成（Step 4 注明若 Screen 未改完可跳过先跑不了测试的顺序）。
