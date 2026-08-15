# 食刻 FreshMate 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现设计文档 `docs/superpowers/specs/2026-08-15-freshmate-app-design.md` 定义的食刻 FreshMate v1 —— Android 单机食品保质期记录与过期提醒应用。

**Architecture:** 单 `app` 模块，MVVM。`util`（纯函数日期计算）← `data`（Room）← `ui`（Compose + ViewModel）；`notification`（AlarmManager 提醒）；`inputmethod`（输入方式接口，语音/图片 v1 占位）。派生数据（到期时间、剩余时长）不落库，全部由 `util` 计算。

**Tech Stack:** Kotlin 2.0.20、Jetpack Compose（BOM 2024.09.02）、Material 3、Room 2.6.1（KSP）、AlarmManager、minSdk 29 / targetSdk 35、AGP 8.5.2、Gradle 8.9、JDK 17。

**重要约束（来自用户）：** 需要安装任何东西（JDK、Android SDK、Gradle 等）时，必须提示用户由用户自行安装，不要自动安装。

---

## 文件结构总览

```
FreshMate/
├── settings.gradle.kts                     # Task 1
├── build.gradle.kts                        # Task 1（根）
├── .gitignore                              # Task 1
├── gradle/wrapper/*                        # Task 1（由 gradle wrapper 生成）
└── app/
    ├── build.gradle.kts                    # Task 1
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml         # Task 1（Task 9 补权限与接收器）
        │   ├── java/com/battor/freshmate/
        │   │   ├── FreshMateApp.kt         # Task 9（Application，通知渠道）
        │   │   ├── MainActivity.kt         # Task 1（空壳）/ Task 13（组装）
        │   │   ├── data/
        │   │   │   ├── Category.kt         # Task 6
        │   │   │   ├── FoodItem.kt         # Task 6
        │   │   │   ├── FoodItemDao.kt      # Task 6
        │   │   │   ├── FoodItemDatabase.kt # Task 6
        │   │   │   └── FoodItemRepository.kt # Task 6
        │   │   ├── inputmethod/
        │   │   │   └── InputMethods.kt     # Task 8（接口 + 3 实现）
        │   │   ├── notification/
        │   │   │   ├── ReminderIds.kt      # Task 9
        │   │   │   ├── ReminderScheduler.kt # Task 9
        │   │   │   ├── ReminderBroadcastReceiver.kt # Task 9
        │   │   │   └── BootReceiver.kt     # Task 9
        │   │   ├── ui/
        │   │   │   ├── main/
        │   │   │   │   ├── MainViewModel.kt      # Task 10
        │   │   │   │   ├── MainScreen.kt         # Task 11、13
        │   │   │   │   ├── FoodItemCard.kt       # Task 11
        │   │   │   │   ├── CategoryIcon.kt       # Task 11
        │   │   │   │   ├── StatusColor.kt        # Task 11
        │   │   │   │   ├── ItemForm.kt           # Task 12
        │   │   │   │   └── FabMenu.kt            # Task 13
        │   │   │   └── theme/
        │   │   │       ├── Color.kt        # Task 7
        │   │   │       └── Theme.kt        # Task 7
        │   │   └── util/
        │   │       ├── ShelfLifeUnit.kt    # Task 2
        │   │       ├── ExpiryUtils.kt      # Task 3
        │   │       ├── ReminderUtils.kt    # Task 4
        │   │       └── ExpiryStatus.kt     # Task 5
        │   └── res/drawable/ic_reminder.xml # Task 9（通知小图标）
        └── test/java/com/battor/freshmate/
            ├── util/                        # Task 2-5 的单测
            ├── inputmethod/InputMethodsTest.kt # Task 8
            └── ui/main/MainViewModelTest.kt # Task 10
```

**依赖方向**：`util` 不依赖任何其他包；`data` 只依赖 `util`；`inputmethod` 只依赖 Compose 图标；`notification` 依赖 `data` + `util`；`ui` 依赖全部。

---

### Task 1: 环境检查与项目脚手架

**Files:**
- Create: `settings.gradle.kts`、`build.gradle.kts`、`.gitignore`、`app/build.gradle.kts`、`app/src/main/AndroidManifest.xml`、`app/src/main/java/com/battor/freshmate/MainActivity.kt`、`app/proguard-rules.pro`

- [ ] **Step 1: 环境检查（缺什么就停下来请用户安装，不要自动安装）**

```bash
java -version          # 需要 17.x；没有 → 请用户安装 JDK 17（或 Android Studio 自带的 JBR）
gradle -v              # 没有可用 gradle → 请用户安装 Gradle 8.9+ 或改用 Android Studio 创建项目骨架
echo $ANDROID_HOME     # 为空则查 ls "$LOCALAPPDATA/Android/Sdk"；都没有 → 请用户安装 Android Studio / cmdline-tools
```

任一缺失时：**停止本任务，向用户说明缺什么、推荐怎么装，等用户确认装好后再继续。**

- [ ] **Step 2: 创建根构建文件**

`settings.gradle.kts`：

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "FreshMate"
include(":app")
```

根 `build.gradle.kts`：

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("com.google.devtools.ksp") version "2.0.20-1.0.25" apply false
}
```

`.gitignore`：

```
.gradle/
build/
local.properties
.idea/
*.iml
.cxx/
captures/
.DS_Store
```

- [ ] **Step 3: 创建 app 模块**

`app/build.gradle.kts`：

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.battor.freshmate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.battor.freshmate"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

`app/proguard-rules.pro`：空文件（写一行注释即可）。

`app/src/main/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="食刻 FreshMate"
        android:icon="@mipmap/ic_launcher"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

（`@mipmap/ic_launcher` 由 AGP 的默认资源提供不需要；若构建报缺图标，则改用 `android:icon="@android:drawable/sym_def_app_icon"`。）

`app/src/main/java/com/battor/freshmate/MainActivity.kt`：

```kotlin
package com.battor.freshmate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("食刻 FreshMate") }
    }
}
```

- [ ] **Step 4: 生成 Gradle Wrapper 并构建验证**

```bash
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
```

预期：`BUILD SUCCESSFUL`。失败则根据报错修正（常见：SDK 路径 → 在项目根创建 `local.properties` 写入 `sdk.dir=...`；JDK 版本 → `org.gradle.java.home`）。

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore: 项目脚手架（Gradle + Compose 空壳应用）"
```

---

### Task 2: 保质期单位与换算（util）

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/util/ShelfLifeUnit.kt`
- Test: `app/src/test/java/com/battor/freshmate/util/ShelfLifeUnitTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.battor.freshmate.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfLifeUnitTest {
    @Test
    fun `换算天`() = assertEquals(7, shelfLifeToDays(7, ShelfLifeUnit.DAY))

    @Test
    fun `换算周`() = assertEquals(14, shelfLifeToDays(2, ShelfLifeUnit.WEEK))

    @Test
    fun `换算月`() = assertEquals(540, shelfLifeToDays(18, ShelfLifeUnit.MONTH))

    @Test
    fun `换算年`() = assertEquals(365, shelfLifeToDays(1, ShelfLifeUnit.YEAR))

    @Test
    fun `单位标签`() {
        assertEquals("天", ShelfLifeUnit.DAY.label)
        assertEquals("年", ShelfLifeUnit.YEAR.label)
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `./gradlew testDebugUnitTest --tests "com.battor.freshmate.util.ShelfLifeUnitTest"`
预期：编译失败，`unresolved reference: shelfLifeToDays`

- [ ] **Step 3: 最小实现**

```kotlin
package com.battor.freshmate.util

/** 保质期单位。设计文档约定：1 个月 = 30 天，1 年 = 365 天。 */
enum class ShelfLifeUnit(val label: String, val days: Int) {
    DAY("天", 1),
    WEEK("周", 7),
    MONTH("月", 30),
    YEAR("年", 365),
}

fun shelfLifeToDays(value: Int, unit: ShelfLifeUnit): Int = value * unit.days
```

- [ ] **Step 4: 运行验证通过**

Run: `./gradlew testDebugUnitTest --tests "com.battor.freshmate.util.ShelfLifeUnitTest"`
预期：5 个测试全部 PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/util/ShelfLifeUnit.kt app/src/test/java/com/battor/freshmate/util/ShelfLifeUnitTest.kt
git commit -m "feat(util): 保质期单位与天数换算"
```

---

### Task 3: 到期时间计算（util）

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/util/ExpiryUtils.kt`
- Test: `app/src/test/java/com/battor/freshmate/util/ExpiryUtilsTest.kt`

约定（设计文档 §4）：起点 = `productionDate` 当天 00:00；未填生产日期时 = `createdAt` 的精确时刻。到期时间 = 起点 + `shelfLifeDays` 天。

- [ ] **Step 1: 写失败测试**

```kotlin
package com.battor.freshmate.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ExpiryUtilsTest {
    private val created = LocalDateTime.of(2026, 8, 15, 10, 0)

    @Test
    fun `未填生产日期时从录入时刻起算`() =
        assertEquals(
            LocalDateTime.of(2026, 8, 22, 10, 0),
            expiryDateTime(null, created, 7),
        )

    @Test
    fun `填了生产日期时从当天零点起算`() =
        assertEquals(
            LocalDateTime.of(2026, 8, 15, 0, 0),
            expiryDateTime(java.time.LocalDate.of(2026, 8, 10), created, 5),
        )
}
```

- [ ] **Step 2: 运行验证失败**

Run: `./gradlew testDebugUnitTest --tests "com.battor.freshmate.util.ExpiryUtilsTest"`
预期：编译失败，`unresolved reference: expiryDateTime`

- [ ] **Step 3: 最小实现**

```kotlin
package com.battor.freshmate.util

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 到期时间 = 起点 + shelfLifeDays 天。
 * 起点：productionDate 的 00:00；未填（null）时为 createdAt 的精确时刻。
 */
fun expiryDateTime(
    productionDate: LocalDate?,
    createdAt: LocalDateTime,
    shelfLifeDays: Int,
): LocalDateTime {
    val start = productionDate?.atStartOfDay() ?: createdAt
    return start.plusDays(shelfLifeDays.toLong())
}
```

- [ ] **Step 4: 运行验证通过**

Run: `./gradlew testDebugUnitTest --tests "com.battor.freshmate.util.ExpiryUtilsTest"`
预期：2 个测试 PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/util/ExpiryUtils.kt app/src/test/java/com/battor/freshmate/util/ExpiryUtilsTest.kt
git commit -m "feat(util): 到期时间计算"
```

---

### Task 4: 提醒时点计算（util）

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/util/ReminderUtils.kt`
- Test: `app/src/test/java/com/battor/freshmate/util/ReminderUtilsTest.kt`

规则（设计文档 §6）：3 个时点 = 到期时刻往前倒 `总保质期 × 1/3、1/5、1/6`；每个时点向下取整到半小时；按时间升序去重；`futureReminderTimes` 过滤掉已过去的时点（`<= now` 算过去）。

- [ ] **Step 1: 写失败测试**

```kotlin
package com.battor.freshmate.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ReminderUtilsTest {
    private val expiry = LocalDateTime.of(2026, 1, 31, 18, 0)

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
    fun `取整到半小时 - 整点不变`() =
        assertEquals(
            LocalDateTime.of(2026, 1, 1, 14, 30),
            roundDownToHalfHour(LocalDateTime.of(2026, 1, 1, 14, 30, 0, 1)),
        )

    @Test
    fun `30天保质期的三个提醒时点`() {
        // 总 720h：1/3=240h → 01-21；1/5=144h → 01-25；1/6=120h → 01-26
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 1, 21, 18, 0),
                LocalDateTime.of(2026, 1, 25, 18, 0),
                LocalDateTime.of(2026, 1, 26, 18, 0),
            ),
            reminderTimes(expiry, 30),
        )
    }

    @Test
    fun `时点取整到半小时`() {
        // 到期 01-04 18:47，总 72h：1/3=24h → 01-03 18:47 → 18:30；
        // 1/5=33h → 01-04 04:47 → 04:30；1/6=12h → 01-04 06:47 → 06:30
        val e = LocalDateTime.of(2026, 1, 4, 18, 47)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 1, 3, 18, 30),
                LocalDateTime.of(2026, 1, 4, 4, 30),
                LocalDateTime.of(2026, 1, 4, 6, 30),
            ),
            reminderTimes(e, 3),
        )
    }

    @Test
    fun `过滤已过去的时点`() {
        val now = LocalDateTime.of(2026, 1, 25, 12, 0)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 1, 25, 18, 0),
                LocalDateTime.of(2026, 1, 26, 18, 0),
            ),
            futureReminderTimes(expiry, 30, now),
        )
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `./gradlew testDebugUnitTest --tests "com.battor.freshmate.util.ReminderUtilsTest"`
预期：编译失败，`unresolved reference: roundDownToHalfHour`

- [ ] **Step 3: 最小实现**

```kotlin
package com.battor.freshmate.util

import java.time.Duration
import java.time.LocalDateTime

/** 向下取整到半小时（14:47→14:30，14:29→14:00），秒/纳秒清零。 */
fun roundDownToHalfHour(t: LocalDateTime): LocalDateTime =
    t.withMinute(if (t.minute >= 30) 30 else 0).withSecond(0).withNano(0)

/**
 * 3 个提醒时点：剩余 1/3、1/5、1/6 时。升序、去重、已取整到半小时。
 */
fun reminderTimes(expiry: LocalDateTime, shelfLifeDays: Int): List<LocalDateTime> {
    val total = Duration.ofDays(shelfLifeDays.toLong())
    return listOf(3L, 5L, 6L)
        .map { divisor -> roundDownToHalfHour(expiry.minus(total.dividedBy(divisor))) }
        .distinct()
        .sorted()
}

/** 只保留严格晚于 now 的提醒时点（<= now 视为已过去）。 */
fun futureReminderTimes(
    expiry: LocalDateTime,
    shelfLifeDays: Int,
    now: LocalDateTime,
): List<LocalDateTime> = reminderTimes(expiry, shelfLifeDays).filter { it > now }
```

- [ ] **Step 4: 运行验证通过**

Run: `./gradlew testDebugUnitTest --tests "com.battor.freshmate.util.ReminderUtilsTest"`
预期：6 个测试 PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/util/ReminderUtils.kt app/src/test/java/com/battor/freshmate/util/ReminderUtilsTest.kt
git commit -m "feat(util): 剩余1/3、1/5、1/6提醒时点计算与半小时取整"
```

---

### Task 5: 紧急度分档与文案（util）

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/util/ExpiryStatus.kt`
- Test: `app/src/test/java/com/battor/freshmate/util/ExpiryStatusTest.kt`

分档规则（设计文档 §5.1，比值 = 剩余时长 / 总保质期）：`<= 0` EXPIRED；`<= 1/6` CRITICAL；`<= 1/5` WARNING；`<= 1/3` CAUTION；否则 SAFE。

- [ ] **Step 1: 写失败测试**

```kotlin
package com.battor.freshmate.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class ExpiryStatusTest {
    private val now = LocalDateTime.of(2026, 8, 15, 10, 0)

    private fun statusOf(remaining: Duration, totalDays: Long = 30): ExpiryStatus =
        expiryStatus(now.plus(remaining), totalDays.toInt(), now)

    @Test
    fun `已过期`() = assertEquals(ExpiryStatus.EXPIRED, statusOf(Duration.ofMinutes(-1)))

    @Test
    fun `恰好到期算过期`() = assertEquals(ExpiryStatus.EXPIRED, statusOf(Duration.ZERO))

    @Test
    fun `剩余恰好1/6是紧急`() = assertEquals(ExpiryStatus.CRITICAL, statusOf(Duration.ofDays(5)))

    @Test
    fun `剩余恰好1/5是警告`() = assertEquals(ExpiryStatus.WARNING, statusOf(Duration.ofDays(6)))

    @Test
    fun `剩余恰好1/3是注意`() = assertEquals(ExpiryStatus.CAUTION, statusOf(Duration.ofDays(10)))

    @Test
    fun `剩余超过1/3是安全`() = assertEquals(ExpiryStatus.SAFE, statusOf(Duration.ofDays(15)))

    @Test
    fun `剩余文案 - 天加小时`() =
        assertEquals("3 天 17 小时", formatRemaining(Duration.ofMinutes(3 * 1440 + 17 * 60 + 29)))

    @Test
    fun `剩余文案 - 不足半小时`() =
        assertEquals("不足 30 分钟", formatRemaining(Duration.ofMinutes(20)))

    @Test
    fun `剩余文案 - 半小时`() =
        assertEquals("30 分钟", formatRemaining(Duration.ofMinutes(45)))

    @Test
    fun `过期文案 - 天`() =
        assertEquals("已过期 2 天", "已过期 " + formatExpired(Duration.ofDays(2)))

    @Test
    fun `过期文案 - 小时`() =
        assertEquals("已过期 5 小时", "已过期 " + formatExpired(Duration.ofHours(5)))
}
```

- [ ] **Step 2: 运行验证失败**

Run: `./gradlew testDebugUnitTest --tests "com.battor.freshmate.util.ExpiryStatusTest"`
预期：编译失败，`unresolved reference: ExpiryStatus`

- [ ] **Step 3: 最小实现**

```kotlin
package com.battor.freshmate.util

import java.time.Duration
import java.time.LocalDateTime

/** 条目紧急度，值越靠后越紧急。 */
enum class ExpiryStatus { SAFE, CAUTION, WARNING, CRITICAL, EXPIRED }

fun expiryStatus(expiry: LocalDateTime, shelfLifeDays: Int, now: LocalDateTime): ExpiryStatus {
    val remaining = Duration.between(now, expiry)
    if (!remaining.isNegative && remaining.isZero) return ExpiryStatus.EXPIRED
    if (remaining.isNegative) return ExpiryStatus.EXPIRED
    val total = Duration.ofDays(shelfLifeDays.toLong())
    val ratio = remaining.toMillis().toDouble() / total.toMillis()
    return when {
        ratio <= 1.0 / 6 -> ExpiryStatus.CRITICAL
        ratio <= 1.0 / 5 -> ExpiryStatus.WARNING
        ratio <= 1.0 / 3 -> ExpiryStatus.CAUTION
        else -> ExpiryStatus.SAFE
    }
}

/** “还有 3 天 17 小时到期”里的时间段，精确到半小时（向下取整）。 */
fun formatRemaining(remaining: Duration): String {
    val totalMinutes = remaining.toMinutes().coerceAtLeast(0)
    val days = totalMinutes / 1440
    val hours = totalMinutes % 1440 / 60
    val halfHour = totalMinutes % 60 >= 30
    val parts = buildList {
        if (days > 0) add("${days} 天")
        if (hours > 0) add("${hours} 小时")
        if (halfHour && days == 0L) add("30 分钟")
    }
    return if (parts.isEmpty()) "不足 30 分钟" else parts.joinToString(" ")
}

/** 已过期的时长文案：“2 天” / “5 小时”。 */
fun formatExpired(overdue: Duration): String {
    val days = overdue.toDays()
    return if (days > 0) "$days 天" else "${overdue.toHours().coerceAtLeast(1)} 小时"
}
```

- [ ] **Step 4: 运行验证通过**

Run: `./gradlew testDebugUnitTest --tests "com.battor.freshmate.util.ExpiryStatusTest"`
预期：11 个测试 PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/util/ExpiryStatus.kt app/src/test/java/com/battor/freshmate/util/ExpiryStatusTest.kt
git commit -m "feat(util): 紧急度分档与剩余/过期时长文案"
```

---

### Task 6: 数据层（Room）

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/data/Category.kt`、`FoodItem.kt`、`FoodItemDao.kt`、`FoodItemDatabase.kt`、`FoodItemRepository.kt`
- Test: `app/src/androidTest/java/com/battor/freshmate/data/FoodItemDaoTest.kt`（在 Task 15 执行，需设备）

注意：`data` 包不依赖 Compose（图标映射在 Task 11 的 UI 层）。DAO 行为测试需设备/模拟器，放到 Task 15（可选）；本任务以编译通过为验证。Room 自动把枚举存为 String。

- [ ] **Step 1: 创建 Category 与 FoodItem**

`Category.kt`：

```kotlin
package com.battor.freshmate.data

/** 商品分类（设计文档 §4，共 9 类）。 */
enum class Category(val label: String) {
    FRUITS_VEG("果蔬"),
    MEAT_EGG("肉蛋"),
    DAIRY("乳品"),
    DRINK("饮料"),
    SNACK("零食"),
    STAPLE("主食"),
    FROZEN("冷冻"),
    CONDIMENT("调味"),
    OTHER("其他"),
}
```

`FoodItem.kt`：

```kotlin
package com.battor.freshmate.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "food_items")
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: Category,
    @ColumnInfo(name = "production_date") val productionDate: LocalDate?,
    @ColumnInfo(name = "shelf_life_days") val shelfLifeDays: Int,
    val quantity: String?,
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime,
)
```

- [ ] **Step 2: 创建 DAO 与 Database**

`FoodItemDao.kt`：

```kotlin
package com.battor.freshmate.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodItemDao {
    @Query("SELECT * FROM food_items ORDER BY created_at DESC")
    fun observeAll(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items")
    suspend fun getAllOnce(): List<FoodItem>

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun getById(id: Long): FoodItem?

    @Insert
    suspend fun insert(item: FoodItem): Long

    @Update
    suspend fun update(item: FoodItem)

    @Delete
    suspend fun delete(item: FoodItem)
}
```

`FoodItemDatabase.kt`：

```kotlin
package com.battor.freshmate.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate
import java.time.LocalDateTime

class Converters {
    @TypeConverter fun localDateToString(v: LocalDate?): String? = v?.toString()
    @TypeConverter fun stringToLocalDate(v: String?): LocalDate? = v?.let(LocalDate::parse)
    @TypeConverter fun localDateTimeToString(v: LocalDateTime?): String? = v?.toString()
    @TypeConverter fun stringToLocalDateTime(v: String?): LocalDateTime? =
        v?.let(LocalDateTime::parse)
}

@Database(entities = [FoodItem::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class FoodItemDatabase : RoomDatabase() {
    abstract fun foodItemDao(): FoodItemDao

    companion object {
        @Volatile private var instance: FoodItemDatabase? = null

        fun get(context: Context): FoodItemDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FoodItemDatabase::class.java,
                    "freshmate.db",
                ).build().also { instance = it }
            }
    }
}
```

- [ ] **Step 3: 创建 Repository（接口 + 实现，接口供 ViewModel 测试用 fake）**

`FoodItemRepository.kt`：

```kotlin
package com.battor.freshmate.data

import kotlinx.coroutines.flow.Flow

interface FoodRepository {
    fun observeAll(): Flow<List<FoodItem>>
    suspend fun insert(item: FoodItem): Long
    suspend fun update(item: FoodItem)
    suspend fun delete(item: FoodItem)
    suspend fun getAll(): List<FoodItem>
}

class FoodItemRepository(private val dao: FoodItemDao) : FoodRepository {
    override fun observeAll(): Flow<List<FoodItem>> = dao.observeAll()
    override suspend fun insert(item: FoodItem): Long = dao.insert(item)
    override suspend fun update(item: FoodItem) = dao.update(item)
    override suspend fun delete(item: FoodItem) = dao.delete(item)
    override suspend fun getAll(): List<FoodItem> = dao.getAllOnce()
}
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew assembleDebug`
预期：`BUILD SUCCESSFUL`（KSP 生成 Room 实现）

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/data
git commit -m "feat(data): FoodItem 实体、DAO、Room 数据库与 Repository"
```

---

### Task 7: Material 3 主题

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/ui/theme/Color.kt`、`Theme.kt`

无逻辑，编译验证即可。Android 12+ 动态取色；Android 10–11 用明快（多巴胺）配色。

- [ ] **Step 1: Color.kt**

```kotlin
package com.battor.freshmate.ui.theme

import androidx.compose.ui.graphics.Color

// Android 10–11 的明快“多巴胺”配色（Android 12+ 使用动态取色，见 Theme.kt）
val Green = Color(0xFF3EB04A)
val GreenDark = Color(0xFF1E6B2A)
val Peach = Color(0xFFFF7B54)
val Yellow = Color(0xFFFFC94D)
val Sky = Color(0xFF4DA8DA)
val Pink = Color(0xFFFF6B9D)
val LightGreenContainer = Color(0xFFDCF5CE)
val LightBackground = Color(0xFFFDFBF3)
val DarkBackground = Color(0xFF1A1C18)
```

- [ ] **Step 2: Theme.kt**

```kotlin
package com.battor.freshmate.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = LightGreenContainer,
    secondary = Sky,
    tertiary = Peach,
    background = LightBackground,
)

private val DarkColors = darkColorScheme(
    primary = Green,
    secondary = Sky,
    tertiary = Peach,
    background = DarkBackground,
)

@Composable
fun FreshMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

- [ ] **Step 3: 编译验证并提交**

Run: `./gradlew assembleDebug` → 预期 `BUILD SUCCESSFUL`

```bash
git add app/src/main/java/com/battor/freshmate/ui/theme
git commit -m "feat(ui): Material 3 主题（动态取色 + 明快预设配色）"
```

---

### Task 8: InputMethod 接口与实现

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/inputmethod/InputMethods.kt`
- Test: `app/src/test/java/com/battor/freshmate/inputmethod/InputMethodsTest.kt`

v1 语音/图片为占位：接口带 `extraAction`（图标+说明），实际占位行为由 UI 按 `InputMethodId` 分发（Task 12）。后续接入识别时只改这个包和 UI 的分发处。

- [ ] **Step 1: 写失败测试**

```kotlin
package com.battor.freshmate.inputmethod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputMethodsTest {
    @Test
    fun `手动输入没有附加操作`() {
        assertNull(ManualInputMethod.extraAction)
        assertEquals("手动输入", ManualInputMethod.menuLabel)
    }

    @Test
    fun `语音输入有附加操作`() {
        assertEquals("长按说话", VoiceInputMethod.extraAction?.label)
    }

    @Test
    fun `图片输入有附加操作`() {
        assertEquals("选择图片", ImageInputMethod.extraAction?.label)
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `./gradlew testDebugUnitTest --tests "com.battor.freshmate.inputmethod.InputMethodsTest"`
预期：编译失败，`unresolved reference: ManualInputMethod`

- [ ] **Step 3: 实现**

```kotlin
package com.battor.freshmate.inputmethod

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.ui.graphics.vector.ImageVector

enum class InputMethodId { MANUAL, VOICE, IMAGE }

/** 输入方式的附加操作（语音/图片各多一个图标）；手动输入为 null。 */
data class ExtraAction(val icon: ImageVector, val label: String)

/** 输入方式策略接口（设计文档 §5.3）。v1 语音/图片的 extraAction 行为由 UI 层占位实现。 */
interface InputMethod {
    val id: InputMethodId
    val menuIcon: ImageVector
    val menuLabel: String
    val extraAction: ExtraAction?
}

object ManualInputMethod : InputMethod {
    override val id = InputMethodId.MANUAL
    override val menuIcon = Icons.Filled.Edit
    override val menuLabel = "手动输入"
    override val extraAction: ExtraAction? = null
}

object VoiceInputMethod : InputMethod {
    override val id = InputMethodId.VOICE
    override val menuIcon = Icons.Filled.KeyboardVoice
    override val menuLabel = "语音输入"
    override val extraAction = ExtraAction(Icons.Filled.KeyboardVoice, "长按说话")
}

object ImageInputMethod : InputMethod {
    override val id = InputMethodId.IMAGE
    override val menuIcon = Icons.Filled.Image
    override val menuLabel = "图片输入"
    override val extraAction = ExtraAction(Icons.Filled.Image, "选择图片")
}

object InputMethods {
    val all: List<InputMethod> = listOf(ManualInputMethod, VoiceInputMethod, ImageInputMethod)
    fun byId(id: InputMethodId): InputMethod = all.first { it.id == id }
}
```

- [ ] **Step 4: 运行验证通过**

Run: `./gradlew testDebugUnitTest --tests "com.battor.freshmate.inputmethod.InputMethodsTest"`
预期：3 个测试 PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/inputmethod app/src/test/java/com/battor/freshmate/inputmethod
git commit -m "feat(inputmethod): 输入方式策略接口与手动/语音/图片实现"
```

---

### Task 9: 提醒调度与通知

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/notification/ReminderIds.kt`、`ReminderScheduler.kt`、`ReminderBroadcastReceiver.kt`、`BootReceiver.kt`
- Create: `app/src/main/java/com/battor/freshmate/FreshMateApp.kt`、`app/src/main/res/drawable/ic_reminder.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/battor/freshmate/notification/ReminderIdsTest.kt`

纯逻辑（requestCode 计算）走 TDD；闹钟/接收器/通知以编译 + 构建验证（运行时行为在 Task 16 手动清单里验证）。

- [ ] **Step 1: 写 requestCode 失败测试**

```kotlin
package com.battor.freshmate.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderIdsTest {
    @Test
    fun `同一食品不同序号的请求码不同`() {
        assertEquals(120L, ReminderIds.requestCode(12L, 0).toLong())
        assertEquals(121L, ReminderIds.requestCode(12L, 1).toLong())
        assertEquals(122L, ReminderIds.requestCode(12L, 2).toLong())
    }

    @Test
    fun `不同食品请求码不冲突`() {
        assertEquals(130L, ReminderIds.requestCode(13L, 0).toLong())
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `./gradlew testDebugUnitTest --tests "com.battor.freshmate.notification.ReminderIdsTest"`
预期：编译失败，`unresolved reference: ReminderIds`

- [ ] **Step 3: ReminderIds 与 ReminderScheduler**

`ReminderIds.kt`：

```kotlin
package com.battor.freshmate.notification

object ReminderIds {
    const val CHANNEL_ID = "expiry_reminders"

    /** requestCode = itemId * 10 + index（index 0..2），同一通知 id 也用它。 */
    fun requestCode(itemId: Long, index: Int): Int = (itemId * 10 + index).toInt()
}
```

`ReminderScheduler.kt`：

```kotlin
package com.battor.freshmate.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.data.FoodItemDatabase
import com.battor.freshmate.util.expiryDateTime
import com.battor.freshmate.util.futureReminderTimes
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

interface ReminderScheduling {
    fun schedule(item: FoodItem)
    fun cancel(itemId: Long)
    fun rescheduleAll()
}

class ReminderScheduler(private val context: Context) : ReminderScheduling {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(item: FoodItem) {
        cancel(item.id)
        val expiry = expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
        val times = futureReminderTimes(expiry, item.shelfLifeDays, LocalDateTime.now())
        times.forEachIndexed { index, time ->
            val pi = broadcast(item.id, index)
            val atMillis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        }
    }

    override fun cancel(itemId: Long) {
        (0..2).forEach { index -> broadcast(itemId, index)?.let(alarmManager::cancel) }
    }

    override fun rescheduleAll() {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = FoodItemDatabase.get(context).foodItemDao()
            dao.getAllOnce().forEach { item ->
                val expiry = expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
                if (expiry > LocalDateTime.now()) schedule(item) else cancel(item.id)
            }
        }
    }

    private fun broadcast(itemId: Long, index: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            ReminderIds.requestCode(itemId, index),
            ReminderBroadcastReceiver.intent(context, itemId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
```

- [ ] **Step 4: 接收器、BootReceiver、Application、通知图标**

`ReminderBroadcastReceiver.kt`：

```kotlin
package com.battor.freshmate.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.battor.freshmate.R
import com.battor.freshmate.data.FoodItemDatabase
import com.battor.freshmate.util.expiryDateTime
import com.battor.freshmate.util.formatRemaining
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        if (itemId == -1L) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val item = FoodItemDatabase.get(context).foodItemDao().getById(itemId)
                    ?: return@launch
                val expiry = expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
                val remaining = Duration.between(LocalDateTime.now(), expiry)
                if (remaining.isNegative || remaining.isZero) return@launch
                val notification = NotificationCompat.Builder(context, ReminderIds.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_reminder)
                    .setContentTitle("食刻 FreshMate")
                    .setContentText("「${item.name}」还有 ${formatRemaining(remaining)} 到期")
                    .setAutoCancel(true)
                    .build()
                if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    NotificationManagerCompat.from(context)
                        .notify(ReminderIds.requestCode(itemId, 0), notification)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_ITEM_ID = "item_id"
        fun intent(context: Context, itemId: Long): Intent =
            Intent(context, ReminderBroadcastReceiver::class.java)
                .putExtra(EXTRA_ITEM_ID, itemId)
    }
}
```

`BootReceiver.kt`：

```kotlin
package com.battor.freshmate.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler(context).rescheduleAll()
        }
    }
}
```

`FreshMateApp.kt`：

```kotlin
package com.battor.freshmate

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.battor.freshmate.notification.ReminderIds

class FreshMateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            ReminderIds.CHANNEL_ID,
            "过期提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
```

`app/src/main/res/drawable/ic_reminder.xml`（铃铓）：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M12,22a2,2 0 0,0 2,-2h-4a2,2 0 0,0 2,2zM18,16v-5c0,-3.07 -1.63,-5.64 -4.5,-6.32V4a1.5,1.5 0 0,0 -3,0v0.68C7.64,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"/>
</vector>
```

- [ ] **Step 5: Manifest 增加权限与接收器**

`<manifest>` 根元素内加：

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

`<application>` 加属性 `android:name=".FreshMateApp"`，并在 `<activity>` 后加：

```xml
<receiver android:name=".notification.ReminderBroadcastReceiver" android:exported="false" />
<receiver android:name=".notification.BootReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

- [ ] **Step 6: 运行测试与编译**

Run: `./gradlew testDebugUnitTest --tests "com.battor.freshmate.notification.ReminderIdsTest" assembleDebug`
预期：2 个测试 PASS，`BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat(notification): AlarmManager 提醒调度、通知接收器与开机重排"
```

---

### Task 10: MainViewModel（TDD）

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/ui/main/MainViewModel.kt`
- Test: `app/src/test/java/com/battor/freshmate/ui/main/MainViewModelTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.battor.freshmate.ui.main

import com.battor.freshmate.data.Category
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.data.FoodRepository
import com.battor.freshmate.inputmethod.InputMethodId
import com.battor.freshmate.notification.ReminderScheduling
import com.battor.freshmate.util.ShelfLifeUnit
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val now = LocalDateTime.of(2026, 8, 15, 10, 0)
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeRepository
    private lateinit var scheduler: FakeScheduler
    private lateinit var vm: MainViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeRepository()
        scheduler = FakeScheduler()
        vm = MainViewModel(repo, scheduler) { now }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun saveNew(name: String = "牛奶", shelfLife: String = "7") {
        vm.startNew(InputMethodId.MANUAL)
        vm.updateEditing { it.copy(name = name, shelfLifeValue = shelfLife) }
        vm.save()
        advanceUntilIdle()
    }

    @Test fun `新建表单的录入时间为当前时间`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        assertEquals(now, vm.uiState.value.editing?.createdAt)
    }

    @Test fun `名称为空时不保存并提示错误`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        vm.updateEditing { it.copy(shelfLifeValue = "7") }
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.editing?.nameError == true)
        assertTrue(repo.items.value.isEmpty())
    }

    @Test fun `保质期非法时不保存并提示错误`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        vm.updateEditing { it.copy(name = "牛奶", shelfLifeValue = "abc") }
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.editing?.shelfLifeError == true)
        assertTrue(repo.items.value.isEmpty())
    }

    @Test fun `合法输入保存后入库并排提醒`() = runTest(dispatcher) {
        saveNew()
        assertEquals(1, repo.items.value.size)
        assertEquals("牛奶", repo.items.value[0].name)
        assertEquals(7, repo.items.value[0].shelfLifeDays)
        assertEquals(now, repo.items.value[0].createdAt)
        assertEquals(1, scheduler.scheduled.size)
        assertNull(vm.uiState.value.editing)
    }

    @Test fun `填写单位换算成天数入库`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        vm.updateEditing { it.copy(name = "酱油", shelfLifeValue = "18", shelfLifeUnit = ShelfLifeUnit.MONTH) }
        vm.save()
        advanceUntilIdle()
        assertEquals(540, repo.items.value[0].shelfLifeDays)
    }

    @Test fun `提醒时点已过时先弹确认`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        // 生产日期 8/1 + 7 天 → 8/8 已过期，3 个时点全部已过
        vm.updateEditing {
            it.copy(name = "酸奶", shelfLifeValue = "7", productionDate = LocalDate.of(2026, 8, 1))
        }
        vm.save()
        advanceUntilIdle()
        assertTrue(repo.items.value.isEmpty())
        assertNotNull(vm.uiState.value.pendingSave)

        vm.confirmPendingSave()
        advanceUntilIdle()
        assertEquals(1, repo.items.value.size)
        assertNull(vm.uiState.value.pendingSave)
    }

    @Test fun `放弃清空编辑状态`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        vm.discard()
        assertNull(vm.uiState.value.editing)
        assertTrue(repo.items.value.isEmpty())
    }

    @Test fun `删除后可撤销恢复`() = runTest(dispatcher) {
        saveNew()
        val item = repo.items.value[0]
        vm.delete(item)
        advanceUntilIdle()
        assertTrue(repo.items.value.isEmpty())
        assertEquals(item.id, scheduler.cancelled.singleOrNull())

        vm.undoDelete()
        advanceUntilIdle()
        assertEquals(1, repo.items.value.size)
        assertEquals("牛奶", repo.items.value[0].name)
    }

    @Test fun `编辑已有条目保留录入时间`() = runTest(dispatcher) {
        saveNew()
        val item = repo.items.value[0]
        vm.startEdit(item)
        vm.updateEditing { it.copy(name = "鲜牛奶") }
        vm.save()
        advanceUntilIdle()
        assertEquals(1, repo.items.value.size)
        assertEquals("鲜牛奶", repo.items.value[0].name)
        assertEquals(now, repo.items.value[0].createdAt)
    }

    @Test fun `首次保存后请求通知权限`() = runTest(dispatcher) {
        assertFalse(vm.uiState.value.requestNotificationPermission)
        saveNew()
        assertTrue(vm.uiState.value.requestNotificationPermission)
        vm.onPermissionRequested()
        assertFalse(vm.uiState.value.requestNotificationPermission)
    }

    @Test fun `按录入时间倒序分组`() {
        val a = FoodItem(name = "a", category = Category.DAIRY, productionDate = null,
            shelfLifeDays = 7, quantity = null, createdAt = LocalDateTime.of(2026, 8, 15, 10, 0))
        val b = a.copy(name = "b", createdAt = LocalDateTime.of(2026, 8, 15, 10, 0, 30))
        val c = a.copy(name = "c", createdAt = LocalDateTime.of(2026, 8, 14, 9, 0))
        val groups = groupItems(listOf(c, a, b))
        assertEquals(2, groups.size)
        assertEquals(listOf("b", "a"), groups[0].items.map { it.name }) // 同分钟同组，组内倒序
        assertEquals(listOf("c"), groups[1].items.map { it.name })
    }
}

class FakeRepository : FoodRepository {
    val items = MutableStateFlow<List<FoodItem>>(emptyList())
    private var nextId = 1L
    override fun observeAll(): Flow<List<FoodItem>> = items
    override suspend fun insert(item: FoodItem): Long {
        val id = nextId++
        items.value = items.value + item.copy(id = id)
        return id
    }
    override suspend fun update(item: FoodItem) {
        items.value = items.value.map { if (it.id == item.id) item else it }
    }
    override suspend fun delete(item: FoodItem) {
        items.value = items.value.filterNot { it.id == item.id }
    }
    override suspend fun getAll(): List<FoodItem> = items.value
}

class FakeScheduler : ReminderScheduling {
    val scheduled = mutableListOf<FoodItem>()
    val cancelled = mutableListOf<Long>()
    override fun schedule(item: FoodItem) { scheduled.add(item) }
    override fun cancel(itemId: Long) { cancelled.add(itemId) }
    override fun rescheduleAll() {}
}
```

- [ ] **Step 2: 运行验证失败**

Run: `./gradlew testDebugUnitTest --tests "com.battor.freshmate.ui.main.MainViewModelTest"`
预期：编译失败，`unresolved reference: MainViewModel`

- [ ] **Step 3: 实现 MainViewModel.kt**

```kotlin
package com.battor.freshmate.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battor.freshmate.data.Category
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.data.FoodRepository
import com.battor.freshmate.inputmethod.InputMethodId
import com.battor.freshmate.notification.ReminderScheduling
import com.battor.freshmate.util.ShelfLifeUnit
import com.battor.freshmate.util.expiryDateTime
import com.battor.freshmate.util.reminderTimes
import com.battor.freshmate.util.shelfLifeToDays
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 列表分组：同一录入时刻（精确到分钟）的条目归为一组，按录入时间倒序。 */
data class FoodItemGroup(val createdAt: LocalDateTime, val items: List<FoodItem>)

fun groupItems(items: List<FoodItem>): List<FoodItemGroup> =
    items.groupBy { it.createdAt.truncatedTo(ChronoUnit.MINUTES) }
        .map { (minute, list) ->
            FoodItemGroup(minute, list.sortedByDescending { it.createdAt })
        }
        .sortedByDescending { it.createdAt }

class MainViewModel(
    private val repository: FoodRepository,
    private val scheduler: ReminderScheduling,
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {

    data class EditingState(
        val inputMethod: InputMethodId = InputMethodId.MANUAL,
        val editingItemId: Long? = null, // null = 新建
        val name: String = "",
        val category: Category = Category.OTHER,
        val productionDate: LocalDate? = null,
        val shelfLifeValue: String = "",
        val shelfLifeUnit: ShelfLifeUnit = ShelfLifeUnit.DAY,
        val quantity: String = "",
        val createdAt: LocalDateTime = LocalDateTime.MIN,
        val nameError: Boolean = false,
        val shelfLifeError: Boolean = false,
    )

    /** 有提醒时点已过、等待用户确认的保存（设计文档 §6）。 */
    data class PendingSave(val editing: EditingState, val days: Int, val skippedReminders: Int)

    data class UiState(
        val items: List<FoodItem> = emptyList(),
        val groups: List<FoodItemGroup> = emptyList(),
        val editing: EditingState? = null,
        val pendingSave: PendingSave? = null,
        val requestNotificationPermission: Boolean = false,
    ) {
        val isEditing: Boolean get() = editing != null
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var permissionRequested = false
    private var lastDeleted: FoodItem? = null

    init {
        viewModelScope.launch {
            repository.observeAll().collect { items ->
                _uiState.update { it.copy(items = items, groups = groupItems(items)) }
            }
        }
    }

    fun startNew(method: InputMethodId) {
        _uiState.update {
            it.copy(editing = EditingState(inputMethod = method, createdAt = nowProvider()))
        }
    }

    fun startEdit(item: FoodItem) {
        _uiState.update {
            it.copy(
                editing = EditingState(
                    inputMethod = InputMethodId.MANUAL,
                    editingItemId = item.id,
                    name = item.name,
                    category = item.category,
                    productionDate = item.productionDate,
                    shelfLifeValue = item.shelfLifeDays.toString(),
                    shelfLifeUnit = ShelfLifeUnit.DAY,
                    quantity = item.quantity ?: "",
                    createdAt = item.createdAt,
                ),
            )
        }
    }

    fun updateEditing(transform: (EditingState) -> EditingState) {
        _uiState.update { s -> s.editing?.let { s.copy(editing = transform(it)) } ?: s }
    }

    fun save() {
        val editing = _uiState.value.editing ?: return
        val name = editing.name.trim()
        val days = editing.shelfLifeValue.trim().toIntOrNull()
            ?.let { shelfLifeToDays(it, editing.shelfLifeUnit) }
        if (name.isEmpty()) {
            updateEditing { it.copy(nameError = true) }
            return
        }
        if (days == null || days <= 0) {
            updateEditing { it.copy(shelfLifeError = true) }
            return
        }
        val expiry = expiryDateTime(editing.productionDate, editing.createdAt, days)
        val skipped = reminderTimes(expiry, days).count { it <= nowProvider() }
        if (skipped > 0) {
            _uiState.update {
                it.copy(pendingSave = PendingSave(editing.copy(name = name), days, skipped))
            }
            return
        }
        persist(editing.copy(name = name), days)
    }

    fun confirmPendingSave() {
        val pending = _uiState.value.pendingSave ?: return
        _uiState.update { it.copy(pendingSave = null) }
        persist(pending.editing, pending.days)
    }

    fun cancelPendingSave() {
        _uiState.update { it.copy(pendingSave = null) }
    }

    fun discard() {
        _uiState.update { it.copy(editing = null, pendingSave = null) }
    }

    fun delete(item: FoodItem) {
        lastDeleted = item
        viewModelScope.launch {
            repository.delete(item)
            scheduler.cancel(item.id)
        }
    }

    fun undoDelete() {
        val item = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            val id = repository.insert(item.copy(id = 0))
            scheduler.schedule(item.copy(id = id))
        }
    }

    fun onPermissionRequested() {
        _uiState.update { it.copy(requestNotificationPermission = false) }
    }

    private fun persist(editing: EditingState, days: Int) {
        viewModelScope.launch {
            val item = FoodItem(
                id = editing.editingItemId ?: 0,
                name = editing.name.trim(),
                category = editing.category,
                productionDate = editing.productionDate,
                shelfLifeDays = days,
                quantity = editing.quantity.trim().ifEmpty { null },
                createdAt = editing.createdAt,
            )
            val saved = if (editing.editingItemId == null) {
                item.copy(id = repository.insert(item))
            } else {
                repository.update(item)
                item
            }
            val expiry = expiryDateTime(saved.productionDate, saved.createdAt, saved.shelfLifeDays)
            if (expiry > nowProvider()) scheduler.schedule(saved) else scheduler.cancel(saved.id)
            _uiState.update { it.copy(editing = null) }
            if (!permissionRequested) {
                permissionRequested = true
                _uiState.update { it.copy(requestNotificationPermission = true) }
            }
        }
    }
}
```

- [ ] **Step 4: 运行验证通过**

Run: `./gradlew testDebugUnitTest --tests "com.battor.freshmate.ui.main.MainViewModelTest"`
预期：12 个测试全部 PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/main/MainViewModel.kt app/src/test/java/com/battor/freshmate/ui/main/MainViewModelTest.kt
git commit -m "feat(ui): MainViewModel 状态流转（录入/编辑/校验/过期确认/删除撤销）"
```

---

### Task 11: 列表 UI（分组、卡片、颜色、滑动删除）

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/ui/main/CategoryIcon.kt`、`StatusColor.kt`、`FoodItemCard.kt`、`MainScreen.kt`

本任务先交付不带表单的列表版本（表单在 Task 12、FAB 菜单在 Task 13 接入）。UI 逻辑依赖 Task 2-5、6、10 已测试的纯函数，此处以编译 + 手动目检验证。

- [ ] **Step 1: CategoryIcon.kt（分类图标映射）**

```kotlin
package com.battor.freshmate.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import com.battor.freshmate.data.Category

fun categoryIcon(category: Category): ImageVector = when (category) {
    Category.FRUITS_VEG -> Icons.Filled.Eco
    Category.MEAT_EGG -> Icons.Filled.SetMeal
    Category.DAIRY -> Icons.Filled.WaterDrop
    Category.DRINK -> Icons.Filled.LocalDrink
    Category.SNACK -> Icons.Filled.Cookie
    Category.STAPLE -> Icons.Filled.BakeryDining
    Category.FROZEN -> Icons.Filled.AcUnit
    Category.CONDIMENT -> Icons.Filled.SoupKitchen
    Category.OTHER -> Icons.Filled.ShoppingBasket
}
```

- [ ] **Step 2: StatusColor.kt（紧急度色带，设计文档 §5.1）**

```kotlin
package com.battor.freshmate.ui.main

import androidx.compose.ui.graphics.Color
import com.battor.freshmate.util.ExpiryStatus

/** 返回 (背景容器色, 前景色)。绿 → 黄 → 橙红 → 深红 → 已过期。 */
fun statusColors(status: ExpiryStatus): Pair<Color, Color> = when (status) {
    ExpiryStatus.SAFE -> Color(0xFFDCF5CE) to Color(0xFF274F1B)
    ExpiryStatus.CAUTION -> Color(0xFFFFF3BF) to Color(0xFF6B5A00)
    ExpiryStatus.WARNING -> Color(0xFFFFE0B2) to Color(0xFF8C4A00)
    ExpiryStatus.CRITICAL -> Color(0xFFFFCDD2) to Color(0xFF8E1418)
    ExpiryStatus.EXPIRED -> Color(0xFFC62828) to Color(0xFFFFFFFF)
}
```

- [ ] **Step 3: FoodItemCard.kt（左滑删除 + 点击编辑）**

```kotlin
package com.battor.freshmate.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.util.expiryDateTime
import com.battor.freshmate.util.expiryStatus
import com.battor.freshmate.util.formatExpired
import com.battor.freshmate.util.formatRemaining
import java.time.Duration
import java.time.LocalDateTime

@Composable
fun FoodItemCard(
    item: FoodItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val now = remember { LocalDateTime.now() }
    val expiry = remember(item) { expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays) }
    val status = expiryStatus(expiry, item.shelfLifeDays, now)
    val (container, onColor) = statusColors(status)
    val remaining = Duration.between(now, expiry)
    val statusText = if (remaining.isNegative || remaining.isZero) {
        "已过期 ${formatExpired(remaining.negated())}"
    } else {
        "还有 ${formatRemaining(remaining)} 到期"
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFC62828), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color.White) }
        },
    ) {
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = container),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    categoryIcon(item.category),
                    contentDescription = item.category.label,
                    tint = onColor,
                    modifier = Modifier.size(28.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(item.name, color = onColor, fontSize = 16.sp)
                    item.quantity?.let { Text("数量：$it", color = onColor, fontSize = 12.sp) }
                }
                Text(statusText, color = onColor, fontSize = 13.sp)
            }
        }
    }
}
```

- [ ] **Step 4: MainScreen.kt（列表 + 分组头 + 撤销 Snackbar）**

```kotlin
package com.battor.freshmate.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val GroupHeaderFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("食刻 FreshMate") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val editId = state.editing?.editingItemId
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 编辑中的表单卡片在 Task 12 插到这里
            state.groups.forEach { group ->
                item(key = "header_${group.createdAt}") {
                    GroupHeader(group.createdAt)
                }
                items(group.items.filter { it.id != editId }, key = { it.id }) { item ->
                    FoodItemCard(
                        item = item,
                        onClick = { viewModel.startEdit(item) },
                        onDelete = {
                            viewModel.delete(item)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    "已删除「${item.name}」",
                                    actionLabel = "撤销",
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.undoDelete()
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(createdAt: LocalDateTime) {
    Surface(color = Color(0xFF616161), shape = RoundedCornerShape(10.dp)) {
        Text(
            createdAt.format(GroupHeaderFormat),
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
```

- [ ] **Step 5: MainActivity 暂不改动**

本任务**不改** `MainActivity.kt`（保持 Task 1 的 `Text("食刻 FreshMate")` 空壳）。列表 UI 的目检推迟到 Task 13 一次性接入真实 ViewModel 后进行，避免引入临时假依赖。

- [ ] **Step 6: 编译验证并提交**

Run: `./gradlew assembleDebug` → 预期 `BUILD SUCCESSFUL`

```bash
git add app/src/main/java/com/battor/freshmate/ui
git commit -m "feat(ui): 分组列表、紧急度色带卡片与滑动删除"
```

---

### Task 12: 输入表单 UI

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/ui/main/ItemForm.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt`（把表单插入列表顶部）

- [ ] **Step 1: ItemForm.kt（完整表单 + 语音/图片占位附加操作）**

```kotlin
package com.battor.freshmate.ui.main

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.battor.freshmate.data.Category
import com.battor.freshmate.inputmethod.InputMethodId
import com.battor.freshmate.inputmethod.InputMethods
import com.battor.freshmate.ui.main.MainViewModel.EditingState
import com.battor.freshmate.util.ExpiryStatus
import com.battor.freshmate.util.ShelfLifeUnit
import com.battor.freshmate.util.expiryDateTime
import com.battor.freshmate.util.expiryStatus
import com.battor.freshmate.util.formatExpired
import com.battor.freshmate.util.formatRemaining
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 快捷保质期：3天 / 7天 / 30天 / 3个月 / 6个月 / 1年（设计文档 §5.3） */
private val QuickShelfLives = listOf(
    Triple("3天", 3, ShelfLifeUnit.DAY),
    Triple("7天", 7, ShelfLifeUnit.DAY),
    Triple("30天", 30, ShelfLifeUnit.DAY),
    Triple("3个月", 3, ShelfLifeUnit.MONTH),
    Triple("6个月", 6, ShelfLifeUnit.MONTH),
    Triple("1年", 1, ShelfLifeUnit.YEAR),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemForm(
    state: EditingState,
    onStateChange: (EditingState) -> Unit,
    onPlaceholderHint: (String) -> Unit,
) {
    val inputMethod = InputMethods.byId(state.inputMethod)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            inputMethod.extraAction?.let { extra ->
                ExtraActionRow(state.inputMethod, extra.icon, extra.label, onPlaceholderHint)
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = { onStateChange(state.copy(name = it, nameError = false)) },
                label = { Text("食品名称 *") },
                isError = state.nameError,
                supportingText = {
                    if (state.nameError) Text("请输入名称", color = MaterialTheme.colorScheme.error)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("分类")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Category.entries.forEach { c ->
                    FilterChip(
                        selected = state.category == c,
                        onClick = { onStateChange(state.copy(category = c)) },
                        label = { Text(c.label) },
                    )
                }
            }

            ProductionDateField(
                productionDate = state.productionDate,
                onChange = { onStateChange(state.copy(productionDate = it)) },
            )

            OutlinedTextField(
                value = state.shelfLifeValue,
                onValueChange = { text ->
                    onStateChange(
                        state.copy(
                            shelfLifeValue = text.filter { it.isDigit() },
                            shelfLifeError = false,
                        ),
                    )
                },
                label = { Text("保质期 *") },
                isError = state.shelfLifeError,
                supportingText = {
                    if (state.shelfLifeError) {
                        Text("请输入大于 0 的数字", color = MaterialTheme.colorScheme.error)
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ShelfLifeUnit.entries.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = state.shelfLifeUnit == unit,
                        onClick = { onStateChange(state.copy(shelfLifeUnit = unit)) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index, ShelfLifeUnit.entries.size,
                        ),
                    ) { Text(unit.label) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickShelfLives.forEach { (label, value, unit) ->
                    AssistChip(
                        onClick = {
                            onStateChange(
                                state.copy(
                                    shelfLifeValue = value.toString(),
                                    shelfLifeUnit = unit,
                                    shelfLifeError = false,
                                ),
                            )
                        },
                        label = { Text(label) },
                    )
                }
            }

            OutlinedTextField(
                value = state.quantity,
                onValueChange = { onStateChange(state.copy(quantity = it)) },
                label = { Text("数量（可选，如 2 / 500g）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ExpiryPreview(state)
        }
    }
}

@Composable
private fun ExpiryPreview(state: EditingState) {
    val days = state.shelfLifeValue.toIntOrNull()?.takeIf { it > 0 } ?: return
    val expiry = expiryDateTime(state.productionDate, state.createdAt, days)
    val now = remember { LocalDateTime.now() }
    val status = expiryStatus(expiry, days, now)
    val remaining = Duration.between(now, expiry)
    val text = if (remaining.isNegative || remaining.isZero) {
        "已过期 ${formatExpired(remaining.negated())}，保存后将不再提醒"
    } else {
        "还有 ${formatRemaining(remaining)} 到期"
    }
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
}

@Composable
private fun ProductionDateField(
    productionDate: LocalDate?,
    onChange: (LocalDate?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { showPicker = true }) {
            Text(productionDate?.let { "生产日期：$it（点击修改）" } ?: "生产日期：不填则按录入日起算")
        }
        if (productionDate != null) {
            TextButton(onClick = { onChange(null) }) { Text("清除生产日期") }
        }
    }
    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = productionDate
                ?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault()).toLocalDate()
                            onChange(date)
                        }
                        showPicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
        ) { DatePicker(state = pickerState) }
    }
}

/** 语音/图片的附加操作占位（设计文档 §5.3，v1 不接识别）。 */
@Composable
private fun ExtraActionRow(
    id: InputMethodId,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onPlaceholderHint: (String) -> Unit,
) {
    var recording by remember { mutableStateOf(false) }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "scale",
    )
    TextButton(
        onClick = { if (id != InputMethodId.VOICE) onPlaceholderHint("图片识别即将上线") },
        modifier = if (id == InputMethodId.VOICE) {
            Modifier.pointerInput(Unit) {
                detectTapGestures(onLongPress = { recording = !recording })
            }
        } else {
            Modifier
        },
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier
                .padding(end = 6.dp)
                .scale(if (recording) scale else 1f),
        )
        Text(
            when {
                id == InputMethodId.IMAGE -> label
                recording -> "录音中…（占位，松手结束）"
                else -> "$label（长按）"
            },
        )
    }
}
```

（import `androidx.compose.ui.draw.scale`。）

- [ ] **Step 2: 把表单接入 MainScreen 列表顶部**

在 `MainScreen.kt` 的 `LazyColumn` 里、`state.groups.forEach` 之前插入：

```kotlin
state.editing?.let { editing ->
    item(key = "editing_form") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupHeader(editing.createdAt)
            ItemForm(
                state = editing,
                onStateChange = { newState -> viewModel.updateEditing { newState } },
                onPlaceholderHint = { hint ->
                    // 用 snackbarHostState 展示；在组合内可拿 rememberCoroutineScope
                },
            )
        }
    }
}
```

`onPlaceholderHint` 的具体实现：在 `MainScreen` 顶部取 `val scope = rememberCoroutineScope()`，回调里 `scope.launch { snackbarHostState.showSnackbar(hint) }`（import `kotlinx.coroutines.launch`、`androidx.compose.runtime.rememberCoroutineScope`）。

- [ ] **Step 3: 编译验证并提交**

Run: `./gradlew assembleDebug` → 预期 `BUILD SUCCESSFUL`

```bash
git add app/src/main/java/com/battor/freshmate/ui
git commit -m "feat(ui): 三种输入方式共用的录入表单（含语音/图片占位操作）"
```

---

### Task 13: FAB 菜单与主页组装

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/ui/main/FabMenu.kt`
- Modify: `app/src/main/java/com/battor/freshmate/MainActivity.kt`（真实依赖）、`app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt`（接入 FAB 与“已过时点”确认对话框）

- [ ] **Step 1: FabMenu.kt**

```kotlin
package com.battor.freshmate.ui.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.battor.freshmate.inputmethod.InputMethodId

/**
 * 未编辑：+ 号展开 4 项菜单（手动/语音/图片/完成，“完成”仅编辑中可用）。
 * 编辑中：对勾（保存）/ 叉号（放弃）两个按钮。
 */
@Composable
fun FabMenu(
    isEditing: Boolean,
    onStartInput: (InputMethodId) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    if (isEditing) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallFloatingActionButton(
                onClick = onDiscard,
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.padding(end = 12.dp),
            ) { Icon(Icons.Filled.Close, contentDescription = "放弃") }
            SmallFloatingActionButton(onClick = onSave) {
                Icon(Icons.Filled.Check, contentDescription = "保存")
            }
        }
    } else {
        var expanded by remember { mutableStateOf(false) }
        androidx.compose.foundation.layout.Box {
            FloatingActionButton(onClick = { expanded = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("手动输入") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { expanded = false; onStartInput(InputMethodId.MANUAL) },
                )
                DropdownMenuItem(
                    text = { Text("语音输入") },
                    leadingIcon = { Icon(Icons.Filled.KeyboardVoice, contentDescription = null) },
                    onClick = { expanded = false; onStartInput(InputMethodId.VOICE) },
                )
                DropdownMenuItem(
                    text = { Text("图片输入") },
                    leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                    onClick = { expanded = false; onStartInput(InputMethodId.IMAGE) },
                )
                DropdownMenuItem(
                    text = { Text("完成") },
                    leadingIcon = { Icon(Icons.Filled.Done, contentDescription = null) },
                    enabled = false, // 未在编辑中无表单可完成
                    onClick = {},
                )
            }
        }
    }
}
```

- [ ] **Step 2: MainScreen 接入 FAB 与确认对话框**

在 `MainScreen` 的 `Scaffold` 上加：

```kotlin
floatingActionButton = {
    FabMenu(
        isEditing = state.isEditing,
        onStartInput = { viewModel.startNew(it) },
        onSave = { viewModel.save() },
        onDiscard = { viewModel.discard() },
    )
},
```

并在 `Scaffold` 内容外加确认对话框（`state.pendingSave` 非空时显示）：

```kotlin
state.pendingSave?.let { pending ->
    AlertDialog(
        onDismissRequest = { viewModel.cancelPendingSave() },
        title = { Text("该食品临近过期") },
        text = {
            Text(
                "「${pending.editing.name}」已有 ${pending.skippedReminders} 个提醒时点过去，" +
                    "剩余提醒时点 ${3 - pending.skippedReminders} 个。确认保存吗？",
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
```

（import `androidx.compose.material3.AlertDialog`、`androidx.compose.material3.TextButton`。）

- [ ] **Step 3: MainActivity 真实组装**

```kotlin
package com.battor.freshmate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.battor.freshmate.data.FoodItemDatabase
import com.battor.freshmate.data.FoodItemRepository
import com.battor.freshmate.notification.ReminderScheduler
import com.battor.freshmate.ui.main.MainScreen
import com.battor.freshmate.ui.main.MainViewModel
import com.battor.freshmate.ui.theme.FreshMateTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.factory(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FreshMateTheme { MainScreen(viewModel) }
        }
    }

    companion object {
        fun factory(appContext: android.content.Context) = viewModelFactory {
            initializer {
                MainViewModel(
                    repository = FoodItemRepository(
                        FoodItemDatabase.get(appContext).foodItemDao(),
                    ),
                    scheduler = ReminderScheduler(appContext),
                )
            }
        }
    }
}
```

同时把 Task 11 Step 5 若引入的临时 `previewViewModel`/`EmptyRepo`/`NoopScheduler` 清理掉。

- [ ] **Step 4: 编译 + 全量单测**

Run: `./gradlew assembleDebug testDebugUnitTest`
预期：`BUILD SUCCESSFUL`，所有单测 PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main
git commit -m "feat(ui): FAB 菜单、过期确认对话框与主页组装"
```

---

### Task 14: 权限流程（通知权限 + 精确闹钟引导）

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/ui/main/PermissionEffects.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt`（挂载权限副作用与横幅）

- [ ] **Step 1: PermissionEffects.kt**

```kotlin
package com.battor.freshmate.ui.main

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

/** 首次保存后请求通知权限（设计文档 §7）。 */
@Composable
fun NotificationPermissionEffect(
    request: Boolean,
    onHandled: () -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> onHandled() }

    LaunchedEffect(request) {
        if (!request) return@LaunchedEffect
        val granted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT >= 33 && !granted) {
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onHandled()
        }
    }
}

/** 顶部状态横幅：通知被关 / 精确闹钟未授予时提示。 */
@Composable
fun PermissionBanners() {
    val context = LocalContext.current
    val notificationsOff = !NotificationManagerCompat.from(context).areNotificationsEnabled()
    val exactOff = Build.VERSION.SDK_INT >= 31 &&
        !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    if (notificationsOff) {
        Banner(text = "通知未开启，将收不到过期提醒") {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    } else if (exactOff) {
        Banner(text = "精确提醒未开启，提醒时间可能偏差") {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }
    }
}

@Composable
private fun Banner(text: String, onAction: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        color = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onAction) { Text("去开启") }
        }
    }
}
```

- [ ] **Step 2: MainScreen 挂载**

在 `MainScreen` 的 `Scaffold` padding 区域顶部（`LazyColumn` 之前包一层 `Column`）加：

```kotlin
NotificationPermissionEffect(
    request = state.requestNotificationPermission,
    onHandled = { viewModel.onPermissionRequested() },
)
Column(Modifier.fillMaxSize().padding(padding)) {
    PermissionBanners()
    LazyColumn(...) { /* 原有内容 */ }
}
```

- [ ] **Step 3: 编译并提交**

Run: `./gradlew assembleDebug` → 预期 `BUILD SUCCESSFUL`

```bash
git add app/src/main
git commit -m "feat(ui): 通知权限请求与提醒设置横幅"
```

---

### Task 15:（可选，需设备/模拟器）DAO 与 Compose UI 自动化测试

**Files:**
- Create: `app/src/androidTest/java/com/battor/freshmate/data/FoodItemDaoTest.kt`
- Create: `app/src/androidTest/java/com/battor/freshmate/ui/main/MainScreenTest.kt`

**执行条件：** 需要连接设备或运行模拟器（`adb devices` 可见）。没有设备时跳过本任务，直接进 Task 16；后续有设备再补。

- [ ] **Step 1: DAO 测试**

```kotlin
package com.battor.freshmate.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodItemDaoTest {
    private lateinit var db: FoodItemDatabase

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FoodItemDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() = db.close()

    private fun item(name: String, createdAt: LocalDateTime) = FoodItem(
        name = name,
        category = Category.DAIRY,
        productionDate = null,
        shelfLifeDays = 7,
        quantity = null,
        createdAt = createdAt,
    )

    @Test fun insertAndObserveAll() = runTest {
        db.foodItemDao().insert(item("b", LocalDateTime.of(2026, 8, 15, 10, 0)))
        db.foodItemDao().insert(item("a", LocalDateTime.of(2026, 8, 15, 11, 0)))
        assertEquals(listOf("a", "b"), db.foodItemDao().observeAll().first().map { it.name })
    }

    @Test fun delete() = runTest {
        val id = db.foodItemDao().insert(item("a", LocalDateTime.of(2026, 8, 15, 10, 0)))
        val inserted = db.foodItemDao().getById(id)!!
        db.foodItemDao().delete(inserted)
        assertEquals(0, db.foodItemDao().getAllOnce().size)
    }
}
```

- [ ] **Step 2: Compose UI 核心路径测试（录入 → 显示）**

```kotlin
package com.battor.freshmate.ui.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battor.freshmate.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun manualInputShowsItem() {
        composeRule.onNodeWithText("手动输入").performClick()
        composeRule.onNodeWithText("食品名称 *")
            .performTextReplacement("测试牛奶")
        composeRule.onNodeWithText("7天").performClick() // 快捷保质期
        composeRule.onNodeWithText("保存").performClick() // FAB 对勾的 contentDescription
        composeRule.onNodeWithText("测试牛奶").assertIsDisplayed()
    }
}
```

（注：`onNodeWithText("保存")` 若匹配不到对勾 FAB，改用 `onNodeWithContentDescription("保存")`。）

- [ ] **Step 3: 运行**

Run: `./gradlew connectedDebugAndroidTest`
预期：全部 PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest
git commit -m "test: DAO 与录入→显示核心路径的设备测试"
```

---

### Task 16: 最终验证

- [ ] **Step 1: 全量构建与单测**

```bash
./gradlew clean assembleDebug testDebugUnitTest
```

预期：`BUILD SUCCESSFUL`，单测全绿。

- [ ] **Step 2: 安装到设备手动验收清单**

```bash
./gradlew installDebug
```

逐项核对（对照设计文档）：
1. 主页标题“食刻 FreshMate”；空列表无崩溃。
2. FAB + → 4 项菜单；选“手动输入”→ 顶部插入绿色表单、组头显示当前时间、原内容下移；FAB 变对勾/叉号。
3. 填名称 + 保质期 7 天 → 对勾 → 列表出现绿/黄色卡片；FAB 还原 4 项。
4. 空名称点保存 → 红字提示，表单不收起。
5. 生产日期填 20 天前、保质期 30 天 → 保存 → 弹“已有提醒时点过去”对话框；确认后保存。
6. 点击条目 → 原位展开编辑；左滑 → 删除 + 撤销 Snackbar，5 秒内点撤销恢复。
7. 语音/图片输入 → 表单多一个附加图标；长按语音图标出现跳动动画（占位）；点图片图标提示“即将上线”。
8. 首次保存后弹出通知权限请求；拒绝后顶部出现横幅、“去开启”可跳设置。
9. 录一条保质期 1 天的食品，把设备时间调到 8 小时后（或用 `adb shell cmd alarm set` 不便时直接等），验证通知触发（开发期可在 `ReminderScheduler.schedule` 临时把时点改成 now+1 分钟目检，验完改回）。
10. 重启设备，已排的提醒仍生效（BOOT_COMPLETED 重排，可通过 `adb shell dumpsys alarm | grep freshmate` 查看已注册闹钟）。

- [ ] **Step 3: 收尾提交**

```bash
git add -A
git commit -m "chore: v1 验收收尾"
```

---

## 计划自审记录

- **规格覆盖**：设计文档 §2 技术基线（T1）、§3 包结构（全部任务）、§4 数据模型（T2/T6）、§5.1 列表分档（T5/T11）、§5.2 FAB 流程（T13）、§5.3 表单与 InputMethod（T8/T12）、§5.4 编辑删除（T11/T13）、§6 提醒与过期确认（T4/T9/T10/T13）、§7 权限（T9/T14）、§8 错误处理（T10/T12）、§9 测试（各任务 + T15/T16）、§10 明确不做（未实现任何占位识别）。无遗漏。
- **占位符**：无 TBD/TODO；Task 11/12 中两处“实现说明”给出了明确可执行的替代写法（`Modifier.scale`、`rememberCoroutineScope`），不属于未定义行为。
- **类型一致性**：`FoodRepository`/`ReminderScheduling` 接口名、`EditingState`/`PendingSave`/`UiState` 字段、`groupItems`、`expiryDateTime`/`reminderTimes`/`futureReminderTimes`/`expiryStatus`/`formatRemaining`/`formatExpired`/`shelfLifeToDays`、`ReminderIds.requestCode` 在各任务间已逐一核对一致。

