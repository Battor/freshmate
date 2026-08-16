# v2 批次（日志 / 自动更新 / 设置页 / 历史页）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为食刻 FreshMate 增加本地日志（Timber + 自写按天文件树）、自托管 JSON 自动更新、设置页与历史页（软删除 + 右滑还原）。

**Architecture:** 四阶段递进——先日志（后续阶段全用得上）、再软删除数据层与历史页、再 Navigation Compose 导航与设置/日志/关于页、最后更新机制（入口在设置页）。单 Activity + NavHost；`data` 层加 `deletedAt` 列走 Room Migration 1→2。

**Tech Stack:** Kotlin 2.3.20 / Compose M3 / Room 2.8.4 / Timber 5.0.1 / OkHttp 4.12.0 / kotlinx-serialization-json 1.7.3（仅运行时解析，不加编译插件）/ navigation-compose 2.8.1。

**Spec:** `docs/superpowers/specs/2026-08-16-logging-update-settings-history-design.md`

**构建命令约定**（每个 gradle 步骤都用）：

```bash
cd "C:/Users/Battor/source/repos/FreshMate"
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew <task>
```

⚠️ 管道陷阱：`gradlew ... | tail` 永远退出码 0，构建失败也会继续走 `&&`。判定成败必须 grep 输出中的 `BUILD SUCCESSFUL` / `FAILED`。

---

## 文件结构总览

| 文件 | 职责 |
|---|---|
| `app/build.gradle.kts`（改） | 新依赖、buildConfig、androidTest schemas 资源 |
| `logging/DailyFileWriter.kt`（新） | 按天分文件、7 天清理、2MB 截断——纯 IO 类，可单测 |
| `logging/FileTree.kt`（新） | 薄壳：Timber 树格式化后委托 DailyFileWriter |
| `data/FoodItem.kt`（改） | +`deletedAt` |
| `data/FoodItemDao.kt`（改） | 活跃/已删双查询、`setDeletedAt` |
| `data/FoodItemDatabase.kt`（改） | version 2 + `MIGRATION_1_2` |
| `data/FoodItemRepository.kt`（改） | 软删除/还原接口 + DB 操作日志 |
| `notification/ScheduleExtensions.kt`（新） | `scheduleOrCancel` 扩展（Main/History 共用） |
| `ui/history/HistoryViewModel.kt`（新） | 已删分组、组活跃判定、还原流转 |
| `ui/history/HistoryScreen.kt`（新） | 历史页 UI（右滑还原 + 确认框） |
| `ui/navigation/NavGraph.kt`（新） | 全部路由 |
| `ui/settings/SettingsScreen.kt`（新） | 设置页 |
| `ui/settings/AboutScreen.kt`（新） | 关于页 |
| `ui/logviewer/LogViewerScreen.kt`（新） | 日志查看页 |
| `update/UpdateManifest.kt`（新） | 清单模型 + 解析 + 版本比对（纯函数可单测） |
| `update/UpdateChecker.kt`（新） | OkHttp 拉清单 |
| `update/ApkDownloader.kt`（新） | 流式下载 + SHA-256 |
| `update/ApkInstaller.kt`（新） | FileProvider + 系统安装器 + 授权引导 |
| `update/UpdateViewModel.kt`（新） | 检查/下载/安装状态机 |
| `MainActivity.kt`（改） | setContent 换 NavGraph |
| `res/xml/file_paths.xml`（新） | FileProvider 路径 |
| `AndroidManifest.xml`（改） | INTERNET / REQUEST_INSTALL_PACKAGES / provider |
| 测试 | `DailyFileWriterTest`、`MigrationTest`、`FoodItemDaoTest`、`HistoryViewModelTest`、`UpdateManifestTest`、`ApkDownloaderTest`（+共享 Fake，MainViewModelTest 适配） |

---

# 阶段 1：日志系统

### Task 1: 新依赖 + DailyFileWriter（TDD）

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/battor/freshmate/logging/DailyFileWriter.kt`
- Test: `app/src/test/java/com/battor/freshmate/logging/DailyFileWriterTest.kt`

- [ ] **Step 1: 加依赖**

`app/build.gradle.kts` dependencies 块末尾（Tooling 之前）加：

```kotlin
    // v2：日志 / 更新 / 导航
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.core:core:1.13.1")
```

- [ ] **Step 2: 写失败测试**

`DailyFileWriterTest.kt`：

```kotlin
package com.battor.freshmate.logging

import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DailyFileWriterTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = LocalDateTime.of(2026, 8, 16, 10, 0)

    @Test fun `写入内容落到当天的文件`() {
        val w = DailyFileWriter(tmp.newFolder(), nowProvider = { now })
        w.append("hello")
        assertEquals(listOf("hello"), w.read(LocalDate.of(2026, 8, 16)))
    }

    @Test fun `跨天自动切新文件`() {
        val w = DailyFileWriter(tmp.newFolder(), nowProvider = { now })
        w.append("day1")
        now = now.plusDays(1)
        w.append("day2")
        assertEquals(listOf("day1"), w.read(LocalDate.of(2026, 8, 16)))
        assertEquals(listOf("day2"), w.read(LocalDate.of(2026, 8, 17)))
        assertEquals(
            listOf(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 16)),
            w.availableDates(),
        )
    }

    @Test fun `超过保留期的文件被清理`() {
        val dir = tmp.newFolder()
        File(dir, "2026-08-01.txt").writeText("old\n") // 距 08-16 超 7 天
        val w = DailyFileWriter(dir, nowProvider = { now })
        w.append("today")
        assertFalse(File(dir, "2026-08-01.txt").exists())
    }

    @Test fun `保留期内的文件不清理`() {
        val dir = tmp.newFolder()
        File(dir, "2026-08-09.txt").writeText("keep\n") // today-7，仍在保留期
        val w = DailyFileWriter(dir, nowProvider = { now })
        w.append("today")
        assertTrue(File(dir, "2026-08-09.txt").exists())
    }

    @Test fun `单文件超上限截断保留后半段`() {
        val w = DailyFileWriter(tmp.newFolder(), maxFileBytes = 60, nowProvider = { now })
        repeat(10) { w.append("line-$it-0123456789") } // 每行约 18 字节，累计超 60 后触发截断
        val lines = w.read(LocalDate.of(2026, 8, 16))
        assertTrue(lines.size in 1..5)
        assertEquals("line-9-0123456789", lines.last())
    }
}
```

- [ ] **Step 3: 跑测试确认失败**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.logging.DailyFileWriterTest" 2>&1 | grep -E "BUILD|FAILED|error:"
```
Expected: `FAILED`（Unresolved reference: DailyFileWriter）

- [ ] **Step 4: 实现 DailyFileWriter**

```kotlin
package com.battor.freshmate.logging

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 按天写日志文件（设计文档 §4.2）：dir/yyyy-MM-dd.txt。
 * - 追加写，synchronized 线程安全；跨天首写切新文件并清理超期文件；
 * - 单文件达 [maxFileBytes] 时截断，保留后半段（约一半体积）。
 * 日期解析失败的文件不动（不是我们的日志）。
 */
class DailyFileWriter(
    private val dir: File,
    private val retentionDays: Long = 7,
    private val maxFileBytes: Long = 2L * 1024 * 1024,
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
) {
    private val lock = Any()
    private var currentDate: LocalDate? = null
    private var writer: BufferedWriter? = null

    init {
        dir.mkdirs()
    }

    fun append(line: String) = synchronized(lock) {
        val today = nowProvider().toLocalDate()
        if (currentDate != today) {
            writer?.close()
            currentDate = today
            cleanup(today)
            writer = newWriter(today)
        }
        val file = fileFor(today)
        if (file.length() >= maxFileBytes) {
            // 先关句柄再截断（append 模式的偏移量在外部重写后不可信）
            writer!!.close()
            truncate(file)
            writer = newWriter(today)
        }
        writer!!.write(line)
        writer!!.newLine()
        writer!!.flush()
    }

    /** 可查看的日志日期，新→旧。 */
    fun availableDates(): List<LocalDate> =
        dir.listFiles().orEmpty()
            .mapNotNull { runCatching { LocalDate.parse(it.name.removeSuffix(".txt")) }.getOrNull() }
            .sortedDescending()

    fun read(date: LocalDate): List<String> =
        fileFor(date).takeIf { it.exists() }?.readLines() ?: emptyList()

    private fun fileFor(date: LocalDate) = File(dir, "$date.txt")

    private fun newWriter(date: LocalDate): BufferedWriter = BufferedWriter(
        OutputStreamWriter(FileOutputStream(fileFor(date), true), Charsets.UTF_8),
    )

    private fun cleanup(today: LocalDate) {
        val cutoff = today.minusDays(retentionDays)
        dir.listFiles()?.forEach { f ->
            val date = runCatching { LocalDate.parse(f.name.removeSuffix(".txt")) }.getOrNull()
            if (date != null && date.isBefore(cutoff)) f.delete()
        }
    }

    private fun truncate(file: File) {
        val lines = file.readLines()
        val kept = ArrayList<String>()
        var size = 0L
        for (i in lines.indices.reversed()) {
            val lineBytes = lines[i].toByteArray().size + 1L
            if (size + lineBytes > maxFileBytes / 2) break
            kept.add(lines[i])
            size += lineBytes
        }
        kept.reverse()
        file.writeText(kept.joinToString(separator = "\n", postfix = "\n"))
    }
}
```

- [ ] **Step 5: 跑测试确认通过**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.logging.DailyFileWriterTest" 2>&1 | grep -E "BUILD|FAILED"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 提交**

```bash
git add app/build.gradle.kts app/src/main/java/com/battor/freshmate/logging/ app/src/test/java/com/battor/freshmate/logging/
git commit -m "feat(logging): DailyFileWriter 按天日志文件与轮转清理"
```

### Task 2: FileTree + 应用内种树

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/logging/FileTree.kt`
- Modify: `app/src/main/java/com/battor/freshmate/FreshMateApp.kt`、`app/build.gradle.kts`

- [ ] **Step 1: build.gradle.kts 开启 buildConfig**

`android { buildFeatures { compose = true } }` 改为：

```kotlin
    buildFeatures {
        compose = true
        buildConfig = true
    }
```

（更新机制的 `UPDATE_MANIFEST_URL` 在 Task 12 用，这里先打开开关。）

- [ ] **Step 2: 实现 FileTree**

> ⚠️ 2026-08-16 修订（审查发现原代码两缺陷）：必须继承 `Timber.DebugTree` 而非 `Timber.Tree`
> （基类不推断 tag，所有行会打成 `[null]`）；且不可手动追加堆栈
> （Timber 的 prepareLog 已把堆栈拼进 message，重复会写两遍）。override log 不调 super，不写 logcat。

```kotlin
package com.battor.freshmate.logging

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import timber.log.Timber

/**
 * 写文件的 Timber 树（设计文档 §4.2）：`MM-dd HH:mm:ss.SSS [tag] message`。
 * 继承 DebugTree 以获得调用类名的 tag 推断（基类 Tree 只认显式 Timber.tag()，否则为 null）；
 * 堆栈已由 Timber prepareLog 拼进 message，这里不再重复追加。
 * 一切失败静默——日志绝不能把 App 搞崩（logcat 由 FreshMateApp 另种的 DebugTree 承担）。
 */
class FileTree(private val writer: DailyFileWriter) : Timber.DebugTree() {
    private val timeFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        runCatching {
            val time = LocalDateTime.now().format(timeFormat)
            writer.append("$time [$tag] $message")
        }
    }
}
```

- [ ] **Step 3: FreshMateApp 种树（Debug/Release 双写 logcat）**

```kotlin
package com.battor.freshmate

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.battor.freshmate.logging.DailyFileWriter
import com.battor.freshmate.logging.FileTree
import com.battor.freshmate.notification.ReminderIds
import java.io.File
import timber.log.Timber

class FreshMateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(FileTree(DailyFileWriter(File(filesDir, "logs"))))
        Timber.plant(Timber.DebugTree())
        Timber.i("APP 启动 versionName=%s versionCode=%d", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        val channel = NotificationChannel(
            ReminderIds.CHANNEL_ID,
            "过期提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
```

- [ ] **Step 4: 全量构建 + 现有测试不破**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug 2>&1 | grep -E "BUILD|FAILED"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 提交**

```bash
git add app/build.gradle.kts app/src/main/java/com/battor/freshmate/
git commit -m "feat(logging): FileTree 接入 Timber，双写文件与 logcat"
```

### Task 3: Repository 数据库操作日志

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/data/FoodItemRepository.kt`

- [ ] **Step 1: 加埋点**

```kotlin
package com.battor.freshmate.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

interface FoodRepository {
    fun observeAll(): Flow<List<FoodItem>>
    suspend fun insert(item: FoodItem): Long
    suspend fun update(item: FoodItem)
    suspend fun delete(item: FoodItem)
    suspend fun getAll(): List<FoodItem>
}

class FoodItemRepository(private val dao: FoodItemDao) : FoodRepository {
    override fun observeAll(): Flow<List<FoodItem>> =
        dao.observeAll().onEach { Timber.i("DB observeAll ← %d items", it.size) }

    override suspend fun insert(item: FoodItem): Long =
        timed("insert(${item.summary()})") { dao.insert(item) }

    override suspend fun update(item: FoodItem) =
        timed("update(${item.summary()})") { dao.update(item) }

    override suspend fun delete(item: FoodItem) =
        timed("delete(id=${item.id} ${item.name})") { dao.delete(item) }

    override suspend fun getAll(): List<FoodItem> =
        timed("getAll") { dao.getAllOnce() }

    /** 设计文档 §4.1：记录查询内容、返回值与耗时；失败也记（不吞异常）。 */
    private inline fun <T> timed(label: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        val outcome = runCatching(block)
        val result = outcome.fold(
            { if (it == Unit) "OK" else it.toString() },
            { "失败: ${it.message}" },
        )
        Timber.i("DB %s ← %s (%dms)", label, result, System.currentTimeMillis() - start)
        return outcome.getOrThrow()
    }
}

private fun FoodItem.summary(): String = "name=$name, days=$shelfLifeDays"
```

（`delete` 在 Task 6 改为软删除接口，此处先保持现有签名。）

- [ ] **Step 2: 构建确认**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest 2>&1 | grep -E "BUILD|FAILED"
```
Expected: `BUILD SUCCESSFUL`（Timber 未种树时为 no-op，单测不受影响）

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/battor/freshmate/data/FoodItemRepository.kt
git commit -m "feat(logging): Repository 数据库操作日志（内容/返回值/耗时）"
```

### Task 4: 系统操作与广播日志

**Files:**
- Modify: `notification/ReminderScheduler.kt`、`notification/BootReceiver.kt`、`notification/ReminderBroadcastReceiver.kt`、`ui/main/PermissionEffects.kt`

- [ ] **Step 1: ReminderScheduler**

`schedule()` 在 `times.forEachIndexed` 之前加（`canExact` 计算之后）：

```kotlin
        Timber.i("ALARM schedule itemId=%d 到期=%s 时点数=%d 精确=%b", item.id, expiry, times.size, canExact)
```

`cancel()` 首行加：

```kotlin
        Timber.i("ALARM cancel itemId=%d", itemId)
```

`rescheduleAll()` 改为（加导入 `timber.log.Timber`）：

```kotlin
    override suspend fun rescheduleAll() = withContext(Dispatchers.IO) {
        val items = FoodItemDatabase.get(context).foodItemDao().getAllOnce()
        Timber.i("ALARM rescheduleAll ← %d items", items.size)
        items.forEach { item ->
            val expiry = expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
            if (expiry > LocalDateTime.now()) schedule(item) else cancel(item.id)
        }
    }
```

- [ ] **Step 2: 两个 Receiver**

`BootReceiver.onReceive`，在 action 校验通过后（`val pendingResult = goAsync()` 之前）加：

```kotlin
        Timber.i("BCAST %s", action)
```

成功分支 `.onFailure { ... }` 前补成功日志（goAsync 协程内 try 块末尾）：

```kotlin
                Timber.i("BCAST %s 重排完成", action)
```

`ReminderBroadcastReceiver.onReceive`，`if (itemId == -1L) return` 之后加：

```kotlin
        Timber.i("BCAST reminder itemId=%d", itemId)
```

`NotificationManagerCompat.from(context).notify(...)` 调用后加：

```kotlin
                        Timber.i("NOTIFY 提醒已发 itemId=%d", itemId)
```

- [ ] **Step 3: 通知权限结果**

`PermissionEffects.kt` 的 launcher 回调 `{ _ -> onHandled() }` 改为：

```kotlin
    ) {
        Timber.i(
            "PERM 通知权限 granted=%b",
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )
        onHandled()
    }
```

（各文件加 `import timber.log.Timber`。）

- [ ] **Step 4: 构建 + 提交**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug 2>&1 | grep -E "BUILD|FAILED"
git add -u app/src/main
git commit -m "feat(logging): 提醒调度/广播/权限结果的系统操作日志"
```

---

# 阶段 2：软删除 + 历史页

### Task 5: FoodItem.deletedAt + DAO + Migration 1→2

**Files:**
- Modify: `data/FoodItem.kt`、`data/FoodItemDao.kt`、`data/FoodItemDatabase.kt`、`app/build.gradle.kts`
- Test: `app/src/androidTest/java/com/battor/freshmate/data/MigrationTest.kt`、`FoodItemDaoTest.kt`

- [ ] **Step 1: androidTest 挂 schemas 资源**

`app/build.gradle.kts` 的 `android { }` 内加：

```kotlin
    sourceSets {
        getByName("androidTest").assets.srcDirs(files("$projectDir/schemas"))
    }
```

- [ ] **Step 2: 实体加列**

`FoodItem.kt` 最后一个字段后加：

```kotlin
    /** 软删除时刻（设计文档 §7.1）：null = 活跃；非 null = 已删除（历史页可见）。 */
    @ColumnInfo(name = "deleted_at") val deletedAt: LocalDateTime? = null,
```

- [ ] **Step 3: DAO 改造**

```kotlin
package com.battor.freshmate.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodItemDao {
    @Query("SELECT * FROM food_items WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun observeAll(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun observeDeleted(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE deleted_at IS NULL")
    suspend fun getAllOnce(): List<FoodItem>

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun getById(id: Long): FoodItem?

    @Insert
    suspend fun insert(item: FoodItem): Long

    @Update
    suspend fun update(item: FoodItem)

    @Delete
    suspend fun delete(item: FoodItem)

    /** 软删除/还原的唯一写入口：deletedAt 传 null 即还原。 */
    @Query("UPDATE food_items SET deleted_at = :deletedAt WHERE id = :id")
    suspend fun setDeletedAt(id: Long, deletedAt: LocalDateTime?)
}
```

（`getAllOnce` 只取活跃——重排提醒不应给已删条目排闹钟；`delete` 暂留给编译兼容，Task 6 移除。）

- [ ] **Step 4: 数据库 version 2 + Migration**

`FoodItemDatabase.kt`：

```kotlin
@Database(entities = [FoodItem::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class FoodItemDatabase : RoomDatabase() {
    abstract fun foodItemDao(): FoodItemDao

    companion object {
        @Volatile private var instance: FoodItemDatabase? = null

        /** deleted_at 为 TEXT：TypeConverter 把 LocalDateTime 存为字符串，迁移列类型须与其一致。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_items ADD COLUMN deleted_at TEXT")
            }
        }

        fun get(context: Context): FoodItemDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FoodItemDatabase::class.java,
                    "freshmate.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
```

（加导入 `androidx.room.migration.Migration`、`androidx.sqlite.db.SupportSQLiteDatabase`。）

- [ ] **Step 5: 写迁移测试（先跑确认通过 1→2 schema 校验）**

`app/src/androidTest/java/com/battor/freshmate/data/MigrationTest.kt`：

```kotlin
package com.battor.freshmate.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FoodItemDatabase::class.java,
    )

    @Test fun `迁移1到2老数据deletedAt为null`() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL(
                """INSERT INTO food_items (name, category, production_date, shelf_life_days, quantity, created_at)
                   VALUES ('牛奶', 'DAIRY', NULL, 7, NULL, '2026-08-15T10:00:00')""",
            )
            close()
        }
        helper.runMigrationsAndValidate(DB_NAME, 2, true, FoodItemDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT name, deleted_at FROM food_items").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("牛奶", c.getString(0))
                assertNull(c.getString(1))
            }
        }
    }

    companion object {
        private const val DB_NAME = "migration-test.db"
    }
}
```

- [ ] **Step 6: 写 DAO 测试**

`app/src/androidTest/java/com/battor/freshmate/data/FoodItemDaoTest.kt`：

```kotlin
package com.battor.freshmate.data

import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class FoodItemDaoTest {
    private lateinit var db: FoodItemDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FoodItemDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private fun item(name: String) = FoodItem(
        name = name, category = Category.DAIRY, productionDate = null,
        shelfLifeDays = 7, quantity = null,
        createdAt = LocalDateTime.of(2026, 8, 15, 10, 0),
    )

    @Test fun `活跃与已删查询互不重叠`() = runTest {
        val a = item("牛奶"); val b = item("面包")
        db.foodItemDao().insert(a); db.foodItemDao().insert(b)
        db.foodItemDao().setDeletedAt(b.id!!, LocalDateTime.of(2026, 8, 16, 9, 0))

        assertEquals(listOf("牛奶"), db.foodItemDao().observeAll().first().map { it.name })
        assertEquals(listOf("面包"), db.foodItemDao().observeDeleted().first().map { it.name })
        assertEquals(listOf("牛奶"), db.foodItemDao().getAllOnce().map { it.name })
    }

    @Test fun `setDeletedAt传null即还原`() = runTest {
        val a = item("牛奶")
        db.foodItemDao().insert(a)
        db.foodItemDao().setDeletedAt(a.id!!, LocalDateTime.of(2026, 8, 16, 9, 0))
        db.foodItemDao().setDeletedAt(a.id!!, null)
        assertEquals(listOf("牛奶"), db.foodItemDao().observeAll().first().map { it.name })
        assertEquals(0, db.foodItemDao().observeDeleted().first().size)
    }
}
```

注意：FoodItem 主键 `id = 0` 时 insert 返回新 id，但返回的实体本身 id 仍是 0——上面 `a.id!!` 拿到的是 0，会误更新 id=0。改为先拿返回值：

```kotlin
    @Test fun `活跃与已删查询互不重叠`() = runTest {
        val a = item("牛奶"); val b = item("面包")
        val idA = db.foodItemDao().insert(a)
        val idB = db.foodItemDao().insert(b)
        db.foodItemDao().setDeletedAt(idB, LocalDateTime.of(2026, 8, 16, 9, 0))
        ...
    }
```

（`setDeletedAt传null即还原` 同理用返回值；实现时以修正后的版本为准。）

- [ ] **Step 7: 跑 androidTest（需模拟器在线）**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew connectedDebugAndroidTest 2>&1 | grep -E "BUILD|FAILED|Tests on"
```
Expected: `BUILD SUCCESSFUL`，迁移与 DAO 测试全绿。

- [ ] **Step 8: 单测与构建不破 + 提交**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug 2>&1 | grep -E "BUILD|FAILED"
git add app/build.gradle.kts app/schemas app/src
git commit -m "feat(data): FoodItem 软删除列与 Migration 1→2"
```

### Task 6: Repository 软删除接口 + MainViewModel 适配

**Files:**
- Modify: `data/FoodItemRepository.kt`、`ui/main/MainViewModel.kt`
- Create: `notification/ScheduleExtensions.kt`
- Modify: `app/src/test/java/com/battor/freshmate/ui/main/MainViewModelTest.kt`；共享 Fake 移到 `app/src/test/java/com/battor/freshmate/Fakes.kt`

- [ ] **Step 1: 抽取共用的 scheduleOrCancel**

`notification/ScheduleExtensions.kt`：

```kotlin
package com.battor.freshmate.notification

import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.util.expiryDateTime
import java.time.LocalDateTime

/** 未过期排提醒，已过期取消——保持调度与数据状态对称（主列表与历史页还原共用）。 */
fun ReminderScheduling.scheduleOrCancel(item: FoodItem, now: LocalDateTime) {
    val expiry = expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
    if (expiry > now) schedule(item) else cancel(item.id)
}
```

- [ ] **Step 2: Repository 接口改软删除**

`FoodItemRepository.kt` 接口与实现改为（`delete` 删除，新增两条）：

```kotlin
interface FoodRepository {
    fun observeAll(): Flow<List<FoodItem>>
    fun observeDeleted(): Flow<List<FoodItem>>
    suspend fun insert(item: FoodItem): Long
    suspend fun update(item: FoodItem)
    suspend fun softDelete(item: FoodItem, deletedAt: LocalDateTime)
    suspend fun restore(item: FoodItem)
    suspend fun getAll(): List<FoodItem>
}
```

实现（删除原 `delete`）：

```kotlin
    override suspend fun softDelete(item: FoodItem, deletedAt: LocalDateTime) =
        timed("softDelete(id=${item.id} ${item.name})") { dao.setDeletedAt(item.id, deletedAt) }

    override suspend fun restore(item: FoodItem) =
        timed("restore(id=${item.id} ${item.name})") { dao.setDeletedAt(item.id, null) }
```

加导入 `java.time.LocalDateTime`；同时删掉 DAO 里已无调用者的 `@Delete delete`（保持 YAGNI）。

- [ ] **Step 3: MainViewModel 改删除/撤销语义**

`delete` 与 `undoDelete` 替换为（删除原 `scheduleOrCancel` 私有方法与 `expiryDateTime` 相关导入按编译器提示清理）：

```kotlin
    /** 软删除：条目移入历史页，取消提醒。 */
    fun delete(item: FoodItem) {
        viewModelScope.launch {
            try {
                repository.softDelete(item, nowProvider())
                scheduler.cancel(item.id)
            } catch (e: Exception) {
                _errorEvent.value = "删除失败，请重试"
            }
        }
    }

    /** 5 秒 Snackbar 撤销 = 立即还原（同一条目同 id，闹钟重排）。 */
    fun undoDelete(item: FoodItem) {
        viewModelScope.launch {
            try {
                repository.restore(item)
                scheduler.scheduleOrCancel(item, nowProvider())
            } catch (e: Exception) {
                _errorEvent.value = "恢复失败，请重试"
            }
        }
    }
```

`persist()` 里的 `scheduleOrCancel(saved)` 改为 `scheduler.scheduleOrCancel(saved, nowProvider())`；加导入 `com.battor.freshmate.notification.scheduleOrCancel`。

- [ ] **Step 4: 共享测试 Fake**

新建 `app/src/test/java/com/battor/freshmate/Fakes.kt`（从 MainViewModelTest 移过来并加软删除语义；MainViewModelTest 删除文件内两个类并加 `import com.battor.freshmate.FakeRepository` / `FakeScheduler`）：

```kotlin
package com.battor.freshmate

import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.data.FoodRepository
import com.battor.freshmate.notification.ReminderScheduling
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeRepository : FoodRepository {
    val items = MutableStateFlow<List<FoodItem>>(emptyList())
    var failNextInsert = false
    private var nextId = 1L

    override fun observeAll(): Flow<List<FoodItem>> = items.map { l -> l.filter { it.deletedAt == null } }
    override fun observeDeleted(): Flow<List<FoodItem>> =
        items.map { l -> l.filter { it.deletedAt != null } }

    override suspend fun insert(item: FoodItem): Long {
        if (failNextInsert) {
            failNextInsert = false
            throw RuntimeException("db error")
        }
        val id = nextId++
        items.value = items.value + item.copy(id = id)
        return id
    }

    override suspend fun update(item: FoodItem) {
        items.value = items.value.map { if (it.id == item.id) item else it }
    }

    override suspend fun softDelete(item: FoodItem, deletedAt: LocalDateTime) {
        items.value = items.value.map { if (it.id == item.id) it.copy(deletedAt = deletedAt) else it }
    }

    override suspend fun restore(item: FoodItem) {
        items.value = items.value.map { if (it.id == item.id) it.copy(deletedAt = null) else it }
    }

    override suspend fun getAll(): List<FoodItem> = items.value.filter { it.deletedAt == null }
}

class FakeScheduler : ReminderScheduling {
    val scheduled = mutableListOf<FoodItem>()
    val cancelled = mutableListOf<Long>()
    override fun schedule(item: FoodItem) { scheduled.add(item) }
    override fun cancel(itemId: Long) { cancelled.add(itemId) }
    override suspend fun rescheduleAll() {}
}
```

- [ ] **Step 5: 适配受影响的现有测试并补新测试**

`MainViewModelTest`：

1. `删除后可撤销恢复` 改为：

```kotlin
    @Test fun `删除后可撤销恢复`() = runTest(dispatcher) {
        saveNew()
        val item = repo.items.value[0]
        vm.delete(item)
        advanceUntilIdle()
        assertTrue(repo.items.value.all { it.deletedAt != null }) // 软删除：仍在库，标记已删
        assertEquals(item.id, scheduler.cancelled.singleOrNull())

        vm.undoDelete(item)
        advanceUntilIdle()
        assertEquals(1, repo.getAll().size) // 撤销后回到活跃
        assertEquals("牛奶", repo.items.value[0].name)
        assertEquals(1, scheduler.scheduled.size) // 提醒重排
    }
```

2. `撤销恢复保留录入时间` 中 `repo.items.value[0].createdAt` 断言不变（软删除不复制数据），无需改。
3. 新增：

```kotlin
    @Test fun `软删除条目不出现在主列表`() = runTest(dispatcher) {
        saveNew()
        vm.delete(repo.items.value[0])
        advanceUntilIdle()
        assertTrue(vm.uiState.value.items.isEmpty())
        assertTrue(vm.uiState.value.groups.isEmpty())
    }
```

- [ ] **Step 6: 跑单测 + 提交**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest 2>&1 | grep -E "BUILD|FAILED"
git add app/src
git commit -m "feat(data): 删除改软删除，撤销即还原同条目"
```
Expected: 全部通过（原 58 例 ± 适配）

### Task 7: HistoryViewModel（TDD）

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/ui/history/HistoryViewModel.kt`
- Test: `app/src/test/java/com/battor/freshmate/ui/history/HistoryViewModelTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.battor.freshmate.ui.history

import com.battor.freshmate.FakeRepository
import com.battor.freshmate.FakeScheduler
import com.battor.freshmate.data.Category
import com.battor.freshmate.data.FoodItem
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private var now = LocalDateTime.of(2026, 8, 16, 10, 0)
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeRepository
    private lateinit var scheduler: FakeScheduler
    private lateinit var vm: HistoryViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeRepository()
        scheduler = FakeScheduler()
        vm = HistoryViewModel(repo, scheduler) { now }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun active(name: String, createdAt: LocalDateTime) = FoodItem(
        name = name, category = Category.DAIRY, productionDate = null,
        shelfLifeDays = 7, quantity = null, createdAt = createdAt,
    )

    private suspend fun TestScope.deleteActive(item: FoodItem) {
        repo.items.value = repo.items.value.map {
            if (it.id == item.id) it.copy(deletedAt = now) else it
        }
        advanceUntilIdle()
    }

    @Test fun `已删除条目按删除时间倒序分组`() = runTest(dispatcher) {
        val g1 = LocalDateTime.of(2026, 8, 14, 9, 0)
        val a = active("牛奶", g1).let { repo.insert(it).let { id -> it.copy(id = id) } }
        val b = active("面包", g1).let { repo.insert(it).let { id -> it.copy(id = id) } }
        advanceUntilIdle()
        now = LocalDateTime.of(2026, 8, 16, 9, 0)
        deleteActive(a)
        now = LocalDateTime.of(2026, 8, 16, 10, 0)
        deleteActive(b)
        val groups = vm.uiState.value.groups
        assertEquals(2, groups.size)
        assertEquals(LocalDateTime.of(2026, 8, 16, 10, 0), groups[0].deletedAt)
        assertEquals(listOf("面包"), groups[0].items.map { it.name })
    }

    @Test fun `组活跃时可还原组不存在时不可还原`() = runTest(dispatcher) {
        val g = LocalDateTime.of(2026, 8, 14, 9, 0)
        val a = active("牛奶", g).let { repo.insert(it).let { id -> it.copy(id = id) } }
        val b = active("面包", g).let { repo.insert(it).let { id -> it.copy(id = id) } }
        advanceUntilIdle()
        deleteActive(a) // g 组仍有活跃的面包
        assertTrue(vm.uiState.value.isRestorable(vm.uiState.value.groups[0].items[0]))

        deleteActive(b) // g 组全部删除
        assertFalse(vm.uiState.value.isRestorable(vm.uiState.value.groups[0].items[0]))
    }

    @Test fun `确认还原清空deletedAt并重排提醒`() = runTest(dispatcher) {
        val g = LocalDateTime.of(2026, 8, 14, 9, 0)
        val a = active("牛奶", g).let { repo.insert(it).let { id -> it.copy(id = id) } }
        val b = active("面包", g).let { repo.insert(it).let { id -> it.copy(id = id) } }
        advanceUntilIdle()
        deleteActive(a)

        val deleted = vm.uiState.value.groups[0].items[0]
        vm.requestRestore(deleted)
        assertEquals(deleted, vm.uiState.value.restoring)
        vm.cancelRestore()
        assertNull(vm.uiState.value.restoring)

        vm.requestRestore(deleted)
        vm.confirmRestore()
        advanceUntilIdle()
        assertEquals(2, repo.getAll().size)
        assertEquals(listOf(a.id), scheduler.scheduled.map { it.id })
        assertNull(vm.uiState.value.restoring)
        assertEquals("已还原「牛奶」到原组", vm.message.value)
    }

    @Test fun `已过期条目还原不排提醒只取消`() = runTest(dispatcher) {
        val expired = FoodItem(
            name = "酸奶", category = Category.DAIRY,
            productionDate = LocalDate.of(2026, 8, 1), shelfLifeDays = 7,
            quantity = null, createdAt = LocalDateTime.of(2026, 8, 14, 9, 0),
        ).let { repo.insert(it).let { id -> it.copy(id = id) } }
        val other = active("面包", expired.createdAt)
            .let { repo.insert(it).let { id -> it.copy(id = id) } }
        advanceUntilIdle()
        deleteActive(expired)

        vm.confirmRestoreFor(vm.uiState.value.groups[0].items[0])
        advanceUntilIdle()
        assertEquals(2, repo.getAll().size)
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(listOf(expired.id), scheduler.cancelled)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.ui.history.HistoryViewModelTest" 2>&1 | grep -E "BUILD|FAILED"
```
Expected: `FAILED`（Unresolved reference: HistoryViewModel）

- [ ] **Step 3: 实现 HistoryViewModel**

```kotlin
package com.battor.freshmate.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.data.FoodRepository
import com.battor.freshmate.notification.ReminderScheduling
import com.battor.freshmate.notification.scheduleOrCancel
import com.battor.freshmate.ui.main.groupKey
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 已删除条目按删除时刻（截断到分钟）分组，新→旧。 */
data class DeletedGroup(val deletedAt: LocalDateTime, val items: List<FoodItem>)

class HistoryViewModel(
    private val repository: FoodRepository,
    private val scheduler: ReminderScheduling,
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {

    data class UiState(
        val groups: List<DeletedGroup> = emptyList(),
        /** 主列表活跃条目的组键集合——还原条件（设计文档 §7.2）。 */
        val activeGroupKeys: Set<LocalDateTime> = emptySet(),
        val restoring: FoodItem? = null,
    ) {
        fun isRestorable(item: FoodItem): Boolean = groupKey(item.createdAt) in activeGroupKeys
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 一次性提示（Snackbar），展示后 UI 调 clearMessage()。 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    init {
        viewModelScope.launch {
            repository.observeAll().collect { active ->
                _uiState.update {
                    it.copy(activeGroupKeys = active.map { i -> groupKey(i.createdAt) }.toSet())
                }
            }
        }
        viewModelScope.launch {
            repository.observeDeleted().collect { deleted ->
                _uiState.update {
                    it.copy(groups = deleted.groupBy { d -> groupKey(d.deletedAt!!) }
                        .map { (at, list) -> DeletedGroup(at, list) }
                        .sortedByDescending { g -> g.deletedAt })
                }
            }
        }
    }

    fun requestRestore(item: FoodItem) {
        if (uiState.value.isRestorable(item)) _uiState.update { it.copy(restoring = item) }
    }

    /** 测试捷径：跳过 requestRestore 直接确认（不可还原条目仍会被拒绝）。 */
    fun confirmRestoreFor(item: FoodItem) {
        _uiState.update { it.copy(restoring = item) }
        confirmRestore()
    }

    fun cancelRestore() {
        _uiState.update { it.copy(restoring = null) }
    }

    fun confirmRestore() {
        val item = _uiState.value.restoring ?: return
        if (!uiState.value.isRestorable(item)) {
            _uiState.update { it.copy(restoring = null) }
            return
        }
        _uiState.update { it.copy(restoring = null) }
        viewModelScope.launch {
            try {
                repository.restore(item)
                scheduler.scheduleOrCancel(item, nowProvider())
                _message.value = "已还原「${item.name}」到原组"
            } catch (e: Exception) {
                _message.value = "还原失败，请重试"
            }
        }
    }
}
```

- [ ] **Step 4: 跑测试确认通过 + 提交**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest 2>&1 | grep -E "BUILD|FAILED"
git add app/src
git commit -m "feat(history): HistoryViewModel 已删分组/组活跃判定/还原流转"
```

### Task 8: HistoryScreen（右滑还原 + 确认框）

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/ui/history/HistoryScreen.kt`
- Modify: `ui/main/StatusColor.kt`（加 RestoreGreen）

- [ ] **Step 1: StatusColor.kt 加还原绿**

```kotlin
/** 历史页右滑还原背景（镜像主列表删除的 ExpiredRed）。 */
val RestoreGreen = Color(0xFF3EB04A)
```

- [ ] **Step 2: HistoryScreen**

```kotlin
package com.battor.freshmate.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.ui.main.categoryIcon
import com.battor.freshmate.ui.main.statusColors
import com.battor.freshmate.ui.main.RestoreGreen
import com.battor.freshmate.ui.main.expiryText
import com.battor.freshmate.util.expiryDateTime
import com.battor.freshmate.util.expiryStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DeletedAtFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无已删除条目", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.groups.forEach { group ->
                    item(key = "del_${group.deletedAt}") { DeletedGroupBox(group, state::isRestorable, viewModel::requestRestore) }
                }
            }
        }
    }

    state.restoring?.let { item ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("还原条目") },
            text = { Text("把「${item.name}」还原到原组吗？") },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text("还原") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("取消") } },
        )
    }
}

@Composable
private fun DeletedGroupBox(
    group: DeletedGroup,
    isRestorable: (FoodItem) -> Boolean,
    onRequestRestore: (FoodItem) -> Unit,
) {
    val restorableCount = group.items.count(isRestorable)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "删除于 ${group.deletedAt.format(DeletedAtFormat)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "  可还原 $restorableCount/${group.items.size}",
                    fontSize = 11.sp,
                    color = if (restorableCount > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            group.items.forEach { item ->
                HistoryItemCard(item, isRestorable(item), onRequestRestore)
            }
        }
    }
}

/** 右滑（Start→End）露绿色还原背景；组不活跃时禁用滑动并整体变淡。 */
@Composable
private fun HistoryItemCard(
    item: FoodItem,
    restorable: Boolean,
    onRequestRestore: (FoodItem) -> Unit,
) {
    val now = remember { LocalDateTime.now() }
    val expiry = remember(item) { expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays) }
    val (container, onColor) = statusColors(expiryStatus(expiry, item.shelfLifeDays, now))

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (restorable && value == SwipeToDismissBoxValue.StartToEnd) {
                onRequestRestore(item)
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false,
        modifier = Modifier.alpha(if (restorable) 1f else 0.4f),
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(RestoreGreen, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Icon(Icons.Filled.Restore, contentDescription = "还原", tint = Color.White) }
        },
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = container, contentColor = onColor,
                disabledContainerColor = container, disabledContentColor = onColor,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(categoryIcon(item.category), contentDescription = item.category.label, tint = onColor, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, color = onColor, fontSize = 16.sp)
                    if (!restorable) {
                        Text("原组已不存在", color = onColor, fontSize = 12.sp)
                    }
                }
                Text(expiryText(item, now), color = onColor, fontSize = 13.sp)
            }
        }
    }
}
```

**实现注意**：`expiryText` 主列表已有同构逻辑但内联在 FoodItemCard（`"已过期 x"/"还有 x 到期"`）。为 DRY，在 `ui/main/StatusColor.kt`（或 `FoodItemCard.kt` 顶部）提取公共函数并在 FoodItemCard 改用：

```kotlin
/** 条目右侧状态文本：已过期 x / 还有 x 到期。 */
fun expiryText(item: FoodItem, now: LocalDateTime): String {
    val expiry = expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
    val remaining = java.time.Duration.between(now, expiry)
    return if (remaining.isNegative || remaining.isZero) {
        "已过期 ${formatExpired(remaining.negated())}"
    } else {
        "还有 ${formatRemaining(remaining)} 到期"
    }
}
```

FoodItemCard 内删除等价内联代码，改调 `expiryText(item, now)`。

- [ ] **Step 3: 构建 + 提交**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug 2>&1 | grep -E "BUILD|FAILED"
git add app/src
git commit -m "feat(history): 历史页 UI——右滑还原+确认框、组不活跃置灰"
```

---

# 阶段 3：导航 + 设置 / 日志 / 关于页

### Task 9: Navigation Compose 接入 + 主页顶栏入口

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/ui/navigation/NavGraph.kt`
- Modify: `MainActivity.kt`、`ui/main/MainScreen.kt`

- [ ] **Step 1: NavGraph（先只含 main / history，其余路由后续任务加）**

```kotlin
package com.battor.freshmate.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.battor.freshmate.data.FoodItemDatabase
import com.battor.freshmate.data.FoodItemRepository
import com.battor.freshmate.notification.ReminderScheduler
import com.battor.freshmate.ui.history.HistoryScreen
import com.battor.freshmate.ui.history.HistoryViewModel
import com.battor.freshmate.ui.main.MainScreen
import com.battor.freshmate.ui.main.MainViewModel

@Composable
fun FreshMateNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            val context = LocalContext.current.applicationContext
            val viewModel: MainViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    MainViewModel(
                        repository = FoodItemRepository(
                            FoodItemDatabase.get(context).foodItemDao(),
                        ),
                        scheduler = ReminderScheduler(context),
                    )
                }
            })
            MainScreen(
                viewModel = viewModel,
                onOpenHistory = { navController.navigate("history") },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable("history") {
            val context = LocalContext.current.applicationContext
            val viewModel: HistoryViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    HistoryViewModel(
                        repository = FoodItemRepository(
                            FoodItemDatabase.get(context).foodItemDao(),
                        ),
                        scheduler = ReminderScheduler(context),
                    )
                }
            })
            HistoryScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
```

（`settings` 路由在 Task 11 加入——本任务先保留 `onOpenSettings` 回调占位为空实现 `{}`，Task 11 替换；MainScreen 的 settings 按钮在 Task 11 才显示，见下。）

- [ ] **Step 2: MainActivity 换 NavGraph**

```kotlin
package com.battor.freshmate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.battor.freshmate.ui.navigation.FreshMateNavGraph
import com.battor.freshmate.ui.theme.FreshMateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FreshMateTheme { FreshMateNavGraph() }
        }
    }
}
```

（原 companion factory 移除——各路由自建 ViewModel。）

- [ ] **Step 3: MainScreen 顶栏加历史/设置图标**

签名加两个回调：

```kotlin
fun MainScreen(viewModel: MainViewModel, onOpenHistory: () -> Unit, onOpenSettings: () -> Unit)
```

Scaffold topBar 改为：

```kotlin
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
```

本任务先只显示历史按钮（设置路由未存在）：

```kotlin
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = "历史")
                    }
                },
```

Task 11 把设置按钮补回。导入 `Icons.Filled.History`、`IconButton`。

- [ ] **Step 4: 构建 + 手动走查 + 提交**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug 2>&1 | grep -E "BUILD|FAILED"
```

装机走查：顶栏历史图标 → 历史页（空态文案）；系统返回键/左上箭头回主页；主页删除一条 → 历史页出现；右滑弹框确认 → 还原回原组。

```bash
git add app/src
git commit -m "feat(ui): Navigation Compose 接入，主页顶栏历史入口"
```

### Task 10: LogViewerScreen

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/ui/logviewer/LogViewerScreen.kt`

- [ ] **Step 1: 实现**

```kotlin
package com.battor.freshmate.ui.logviewer

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.battor.freshmate.logging.DailyFileWriter
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(logsDir: File, onBack: () -> Unit) {
    val writer = remember { DailyFileWriter(logsDir) }
    var dates by remember { mutableStateOf(writer.availableDates()) }
    var selected by remember { mutableStateOf(dates.firstOrNull()) }
    var lines by remember { mutableStateOf(selected?.let { writer.read(it) } ?: emptyList()) }
    var menuOpen by remember { mutableStateOf(false) }

    fun select(date: LocalDate) {
        selected = date
        lines = writer.read(date)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志 ${selected ?: ""}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "选择日期")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        dates.forEach { date ->
                            DropdownMenuItem(
                                text = { Text(date.toString()) },
                                onClick = { menuOpen = false; select(date) },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(lines) { line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}
```

- [ ] **Step 2: 构建 + 提交**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug 2>&1 | grep -E "BUILD|FAILED"
git add app/src
git commit -m "feat(ui): 日志查看页（按天切换、等宽渲染）"
```
（本任务尚无入口——Task 11 接入设置页与路由。）

### Task 11: 设置页 + 关于页 + 顶栏设置入口

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/ui/settings/SettingsScreen.kt`、`app/src/main/java/com/battor/freshmate/ui/settings/AboutScreen.kt`
- Modify: `ui/navigation/NavGraph.kt`、`ui/main/MainScreen.kt`

- [ ] **Step 1: SettingsScreen（本阶段两项；「检查更新」Task 14 加）**

```kotlin
package com.battor.freshmate.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxWidth().padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("查看日志") },
                    leadingContent = { Icon(Icons.Filled.Description, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenLogs),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("关于") },
                    leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenAbout),
                )
            }
        }
    }
}
```

- [ ] **Step 2: AboutScreen**

```kotlin
package com.battor.freshmate.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.battor.freshmate.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("食刻 FreshMate", style = MaterialTheme.typography.titleLarge)
            Text("版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium)
            Text(
                "记录食品保质期，临期提醒不浪费。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 3: NavGraph 加 settings/logviewer/about 路由**

`FreshMateNavGraph` 的 NavHost 内加：

```kotlin
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenLogs = { navController.navigate("logviewer") },
                onOpenAbout = { navController.navigate("about") },
            )
        }
        composable("logviewer") {
            val context = LocalContext.current.applicationContext
            LogViewerScreen(
                logsDir = java.io.File(context.filesDir, "logs"),
                onBack = { navController.popBackStack() },
            )
        }
        composable("about") { AboutScreen(onBack = { navController.popBackStack() }) }
```

- [ ] **Step 4: MainScreen 补设置按钮**

Task 9 步骤 3 中预留处，actions 改回两项：

```kotlin
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = "历史")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
```

- [ ] **Step 5: 构建 + 手动走查 + 提交**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug 2>&1 | grep -E "BUILD|FAILED"
```

走查：设置 → 查看日志能看到今天的 DB/ALARM 日志行；关于显示版本号；返回链正常。

```bash
git add app/src
git commit -m "feat(ui): 设置页/关于页/日志查看路由与主页设置入口"
```

---

# 阶段 4：自动更新

### Task 12: 版本清单解析与检查（TDD）

**Files:**
- Modify: `app/build.gradle.kts`（URL 常量）
- Create: `app/src/main/java/com/battor/freshmate/update/UpdateManifest.kt`、`update/UpdateChecker.kt`
- Test: `app/src/test/java/com/battor/freshmate/update/UpdateManifestTest.kt`

- [ ] **Step 1: buildConfig 注入清单 URL**

`defaultConfig { }` 内加：

```kotlin
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"https://example.com/freshmate/manifest.json\"")
```

（占位 URL——用户自有服务器地址就绪后只改这一处。）

- [ ] **Step 2: 写失败测试**

```kotlin
package com.battor.freshmate.update

import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {
    private val json = """
        {"versionCode": 2, "versionName": "1.1.0",
         "apkUrl": "https://host/apk-1.1.0.apk", "sha256": "AbCd12", "notes": "修复与历史页"}
    """.trimIndent()

    @Test fun `解析完整清单`() {
        val m = ManifestParser.parse(json)
        assertEquals(2, m.versionCode)
        assertEquals("1.1.0", m.versionName)
        assertEquals("https://host/apk-1.1.0.apk", m.apkUrl)
        assertEquals("AbCd12", m.sha256)
        assertEquals("修复与历史页", m.notes)
    }

    @Test fun `sha256与notes可省略`() {
        val m = ManifestParser.parse("""{"versionCode":1,"versionName":"1.0","apkUrl":"u"}""")
        assertNull(m.sha256)
        assertNull(m.notes)
    }

    @Test fun `坏JSON抛异常`() {
        assertFailsWith<Exception> { ManifestParser.parse("not json") }
    }

    @Test fun `版本比对只在versionCode更大时有更新`() {
        val m = ManifestParser.parse(json)
        assertTrue(isUpdateAvailable(m, 1))
        assertFalse(isUpdateAvailable(m, 2))
        assertFalse(isUpdateAvailable(m, 3))
    }
}
```

- [ ] **Step 3: 跑测试确认失败**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.update.UpdateManifestTest" 2>&1 | grep -E "BUILD|FAILED"
```
Expected: `FAILED`

- [ ] **Step 4: 实现**

`UpdateManifest.kt`：

```kotlin
package com.battor.freshmate.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String?,
    val notes: String?,
)

/** 纯函数解析（设计文档 §5.1）；仅用运行时库，无需编译插件。 */
object ManifestParser {
    fun parse(json: String): UpdateManifest {
        val obj = Json.parseToJsonElement(json).jsonObject
        fun str(key: String) = obj.getValue(key).jsonPrimitive.content
        return UpdateManifest(
            versionCode = obj.getValue("versionCode").jsonPrimitive.int,
            versionName = str("versionName"),
            apkUrl = str("apkUrl"),
            sha256 = obj["sha256"]?.jsonPrimitive?.contentOrNull,
            notes = obj["notes"]?.jsonPrimitive?.contentOrNull,
        )
    }
}

fun isUpdateAvailable(manifest: UpdateManifest, currentVersionCode: Int): Boolean =
    manifest.versionCode > currentVersionCode
```

（加导入 `kotlinx.serialization.json.contentOrNull`。）

`UpdateChecker.kt`：

```kotlin
package com.battor.freshmate.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

class UpdateChecker(
    private val manifestUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    /** 结果从不抛异常——失败走 Result（设计文档 §8：启动静默/手动提示）。 */
    suspend fun fetch(): Result<UpdateManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.newCall(Request.Builder().url(manifestUrl).build()).execute()
            response.use {
                if (!it.isSuccessful) error("HTTP ${it.code}")
                ManifestParser.parse(it.body!!.string())
            }
        }.onFailure { Timber.i(it, "UPDATE 检查失败") }
            .onSuccess { Timber.i("UPDATE 检查 ← versionCode=%d versionName=%s", it.versionCode, it.versionName) }
    }
}
```

- [ ] **Step 5: 跑测试确认通过 + 提交**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest 2>&1 | grep -E "BUILD|FAILED"
git add app/build.gradle.kts app/src
git commit -m "feat(update): 版本清单解析与版本比对"
```

### Task 13: 下载器 + 安装器 + FileProvider + 权限

**Files:**
- Create: `update/ApkDownloader.kt`、`update/ApkInstaller.kt`、`app/src/main/res/xml/file_paths.xml`
- Modify: `AndroidManifest.xml`
- Test: `app/src/test/java/com/battor/freshmate/update/ApkDownloaderTest.kt`

- [ ] **Step 1: 写失败测试（sha256）**

```kotlin
package com.battor.freshmate.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `sha256与已知值一致`() {
        val f = tmp.newFile().apply { writeText("hello") }
        // sha256("hello") 的公认值
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            ApkDownloader().sha256(f),
        )
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.update.ApkDownloaderTest" 2>&1 | grep -E "BUILD|FAILED"
```
Expected: `FAILED`

- [ ] **Step 3: 实现 ApkDownloader**

```kotlin
package com.battor.freshmate.update

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

class ApkDownloader(private val client: OkHttpClient = OkHttpClient()) {

    /** 流式下载到 dest；onProgress(已下载字节, 总字节[未知为 -1])。 */
    suspend fun download(
        url: String,
        dest: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Unit = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("空响应体")
            val total = body.contentLength()
            dest.outputStream().use { out ->
                val input = body.byteStream()
                val buf = ByteArray(64 * 1024)
                var copied = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    copied += n
                    onProgress(copied, total)
                }
            }
        }
        Timber.i("UPDATE 下载完成 %s (%d bytes)", dest.name, dest.length())
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
```

- [ ] **Step 4: 实现 ApkInstaller + 清单/资源**

`update/ApkInstaller.kt`：

```kotlin
package com.battor.freshmate.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File

class ApkInstaller(private val context: Context) {

    /** Android 8+ 需用户在系统设置授予"安装未知应用"。 */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun launchInstall(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        Timber.i("UPDATE 发起安装 intent")
        context.startActivity(intent)
    }

    fun launchPermissionSettings() {
        Timber.i("UPDATE 引导安装未知应用授权")
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
```

`app/src/main/res/xml/file_paths.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="updates" path="updates/" />
</paths>
```

`AndroidManifest.xml`：`<uses-permission>` 区加

```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

`<application>` 内（receiver 之后）加：

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 5: 测试通过 + 构建 + 提交**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug 2>&1 | grep -E "BUILD|FAILED"
git add app/src/main
git commit -m "feat(update): APK 流式下载/SHA-256 校验与系统安装器接入"
```

### Task 14: UpdateViewModel + 设置页检查更新 + 启动静默检查

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/update/UpdateViewModel.kt`
- Modify: `ui/settings/SettingsScreen.kt`、`ui/navigation/NavGraph.kt`、`ui/main/MainScreen.kt`

- [ ] **Step 1: UpdateViewModel**

```kotlin
package com.battor.freshmate.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battor.freshmate.BuildConfig
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class UpdateViewModel(
    appContext: Context,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
) : ViewModel() {

    data class UiState(
        val checking: Boolean = false,
        /** 发现的新版本（对话框展示；null = 无）。 */
        val manifest: UpdateManifest? = null,
        /** 启动静默检查发现的新版本（主页 Snackbar 用，一次性）。 */
        val silentFound: UpdateManifest? = null,
        val downloading: Boolean = false,
        val progress: Float = 0f,
        val apkReady: Boolean = false,
        /** 手动检查的一次性结果提示（null = 不展示）。 */
        val notice: String? = null,
    ) {
        val busy: Boolean get() = checking || downloading
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val checker = UpdateChecker(BuildConfig.UPDATE_MANIFEST_URL)
    private val downloader = ApkDownloader()
    private val installer = ApkInstaller(appContext)
    private val apkFile = File(appContext.cacheDir, "updates/freshmate-update.apk")

    fun clearNotice() = _uiState.update { it.copy(notice = null) }
    fun dismissManifest() = _uiState.update { it.copy(manifest = null, apkReady = false) }

    /** manual=true：结果都提示用户；false：仅发现新版时提示（设计文档 §5.2）。 */
    fun check(manual: Boolean) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(checking = true) }
        viewModelScope.launch {
            checker.fetch().fold(
                onSuccess = { m ->
                    if (isUpdateAvailable(m, currentVersionCode)) {
                        _uiState.update {
                            it.copy(checking = false, manifest = if (manual) m else it.manifest, silentFound = if (manual) null else m)
                        }
                    } else {
                        _uiState.update { it.copy(checking = false, notice = if (manual) "已是最新版本" else null) }
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(checking = false, notice = if (manual) "检查更新失败，请稍后重试" else null) }
                },
            )
        }
    }

    fun download() {
        val m = _uiState.value.manifest ?: return
        if (_uiState.value.busy) return
        _uiState.update { it.copy(downloading = true, progress = 0f) }
        viewModelScope.launch {
            var error: String? = null
            runCatching {
                downloader.download(m.apkUrl, apkFile) { copied, total ->
                    _uiState.update { s ->
                        s.copy(progress = if (total > 0) copied.toFloat() / total else 0f)
                    }
                }
            }.onFailure { e ->
                Timber.i(e, "UPDATE 下载失败")
                apkFile.delete()
                error = "下载失败：${e.message}"
            }.onSuccess {
                m.sha256?.let { expected ->
                    val actual = downloader.sha256(apkFile)
                    if (!actual.equals(expected, ignoreCase = true)) {
                        Timber.i("UPDATE 校验失败 expected=%s actual=%s", expected, actual)
                        apkFile.delete()
                        error = "安装包校验失败"
                    }
                }
            }
            _uiState.update {
                if (error != null) it.copy(downloading = false, notice = error)
                else it.copy(downloading = false, apkReady = true)
            }
        }
    }

    /** 未授权时跳系统授权页；用户授权返回后可再点安装。 */
    fun install() {
        if (!_uiState.value.apkReady) return
        if (!installer.canInstall()) {
            installer.launchPermissionSettings()
            return
        }
        installer.launchInstall(apkFile)
    }
}
```

- [ ] **Step 2: SettingsScreen 加「检查更新」行与更新 UI**

SettingsScreen 签名加：

```kotlin
    updateState: com.battor.freshmate.update.UpdateViewModel.UiState,
    onCheckUpdate: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismissNotice: () -> Unit,
```

LazyColumn 首项前加「检查更新」ListItem（图标 `Icons.Filled.SystemUpdate`（extended 图标集），trailing 为 checking 时的 `CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)`）；再加更新对话框与提示：

```kotlin
    updateState.notice?.let { msg ->
        LaunchedEffect(msg) { /* 见下：snackbar */ }
    }
```

完整实现（放在 SettingsScreen 函数体内 Scaffold 之后）：

```kotlin
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(updateState.notice) {
        updateState.notice?.let {
            snackbarHostState.showSnackbar(it)
            onDismissNotice()
        }
    }
    // Scaffold snackbarHost 参数接 { SnackbarHost(snackbarHostState) }

    updateState.manifest?.let { m ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("发现新版本 ${m.versionName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    m.notes?.let { Text(it) }
                    if (updateState.downloading) {
                        LinearProgressIndicator(
                            progress = { updateState.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                when {
                    updateState.downloading -> TextButton(onClick = {}) { Text("下载中 ${（updateState.progress * 100).toInt()}%") }
                    updateState.apkReady -> TextButton(onClick = onInstall) { Text("安装") }
                    else -> TextButton(onClick = onDownload) { Text("下载更新") }
                }
            },
            dismissButton = {
                if (!updateState.downloading) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
            },
        )
    }
```

（`onDismiss` = `updateViewModel::dismissManifest`；onInstall 若未授权会先跳设置，用户回来再点一次。）

- [ ] **Step 3: NavGraph settings 路由接 UpdateViewModel**

```kotlin
        composable("settings") {
            val context = LocalContext.current.applicationContext
            val updateViewModel: UpdateViewModel = viewModel(
                key = "update",
                factory = viewModelFactory { initializer { UpdateViewModel(context) } },
            )
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenLogs = { navController.navigate("logviewer") },
                onOpenAbout = { navController.navigate("about") },
                updateState = updateViewModel.uiState.collectAsStateWithLifecycle().value,
                onCheckUpdate = { updateViewModel.check(manual = true) },
                onDownload = updateViewModel::download,
                onInstall = updateViewModel::install,
                onDismissNotice = updateViewModel::clearNotice,
                onDismiss = updateViewModel::dismissManifest,
            )
        }
```

- [ ] **Step 4: 主页启动静默检查**

NavGraph `composable("main")` 内，MainScreen 调用后追加副作用：

```kotlin
            val context = LocalContext.current.applicationContext
            val updateViewModel: UpdateViewModel = viewModel(
                key = "update", // 与 settings 路由同一实例（Activity 作用域默认即同源，key 保持一致）
                factory = viewModelFactory { initializer { UpdateViewModel(context) } },
            )
            LaunchedEffect(Unit) { updateViewModel.check(manual = false) }
            val silent by updateViewModel.uiState.collectAsStateWithLifecycle()
            silent.silentFound?.let { m ->
                LaunchedEffect(m) {
                    val scope = rememberCoroutineScope() // 简化：直接用 MainScreen 之外的 SnackbarHost
                }
            }
```

简化实现——把静默提示做进 MainScreen（它已有 SnackbarHost）：MainScreen 加可选参数：

```kotlin
fun MainScreen(
    viewModel: MainViewModel,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    updateHint: String? = null,          // 例："发现新版本 1.1.0"
    onUpdateHintShown: () -> Unit = {},
    onOpenUpdate: () -> Unit = {},       // Snackbar「查看」动作 → 跳设置页
)
```

`LaunchedEffect(updateHint) { updateHint?.let { snackbarHostState.showSnackbar(it, actionLabel = "查看") ; onUpdateHintShown() } }`（返回值等于 `SnackbarResult.ActionPerformed` 时调 `onOpenUpdate()`）。NavGraph main 路由传：

```kotlin
            updateHint = silent.silentFound?.let { "发现新版本 ${it.versionName}" },
            onUpdateHintShown = { updateViewModel.clearSilentFound() },
            onOpenUpdate = { navController.navigate("settings") },
```

`UpdateViewModel` 加：

```kotlin
    fun clearSilentFound() = _uiState.update { it.copy(silentFound = null) }
```

- [ ] **Step 5: 构建 + 提交**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug 2>&1 | grep -E "BUILD|FAILED"
git add app/src
git commit -m "feat(update): 检查/下载/安装状态机与设置页、启动静默检查接入"
```

### Task 15: 模拟器全流程走查 + 收尾

- [ ] **Step 1: 全量构建 + 全部测试**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest connectedDebugAndroidTest assembleDebug 2>&1 | grep -E "BUILD|FAILED"
```

- [ ] **Step 2: 安装并走查功能路径**

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

走查清单：
1. 主页添加 → 暂存多条成组；删除一条 → 5 秒撤销生效；
2. 历史页：右滑弹框 → 还原回原组；组全删后该组已删条目置灰不可滑；
3. 设置 → 查看日志：看到 `DB insert`、`ALARM schedule` 行；切日期正常；
4. 设置 → 关于：版本号正确；
5. 更新失败路径：默认占位 URL → 启动无提示（静默）；手动检查 → "检查更新失败"提示。

- [ ] **Step 3: 本地 HTTP 服务全流程验证更新（可选，需用户确认后执行）**

```bash
# 宿主机起服务（目录含 manifest.json 与 app-debug.apk，manifest 写 versionCode: 2）
python -m http.server 8000 --directory <服务目录>
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" reverse tcp:8000 tcp:8000
```

`manifest.json` 的 `apkUrl` 写 `http://127.0.0.1:8000/app-debug.apk`；临时把 `UPDATE_MANIFEST_URL` 改为 `http://127.0.0.1:8000/manifest.json` 构建安装 → 启动应弹"发现新版本"→ 下载进度 → 安装（需先授权"安装未知应用"）。验证后**还原 URL 常量再提交**。

- [ ] **Step 4: 提交收尾**

```bash
git add app docs
git commit -m "chore: v2 批次收尾——全流程走查通过"
```

---

## Self-Review 记录

- **Spec 覆盖**：§4 日志（Task 1-4）、§5 更新（Task 12-14）、§6 设置/导航（Task 9-11）、§7 软删除/历史（Task 5-8）、§8 错误处理（各任务内）、§9 测试（各任务 + Task 15）、§10 顺序一致。
- **类型一致性**：`scheduleOrCancel`（Task 6 定义，Task 7 复用）；`FoodRepository.softDelete/restore/observeDeleted`（Task 6 定义，Task 7 Fake 已同步）；`DailyFileWriter.read/availableDates`（Task 1 定义，Task 10 复用）；`ManifestParser.parse`/`isUpdateAvailable`（Task 12）。
- **已知风险**：① FoodItemDaoTest Step 6 初稿的 `a.id!!` 拿到的是 0（insert 不回写 id），正文已内联修正说明；② Task 14 Step 2/4 的 SettingsScreen/MainScreen 片段是"增量补丁"性质，执行时以现有文件结构为准插入，编译器是最终裁判；③ Task 9 的 `onOpenSettings` 暂传 `{}`、Task 11 替换——避免死按钮出现在用户可见构建里（Task 9 只显示历史按钮）。
