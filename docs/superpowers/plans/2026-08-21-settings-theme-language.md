# 需求-4：设置页主题切换 + 多语言 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 设置页新增「主题」（跟随系统/浅色/深色，内部三态、UI 两档即时切换）与「语言」（跟随系统/简中/繁中/English，per-app language），并把全 app 硬编码中文文案抽取为 strings.xml 三语资源。

**Architecture:** 主题走 DataStore + Compose 状态（`FreshMateTheme(darkTheme=…)` 重组即时生效）；语言走 `AppCompatDelegate.setApplicationLocales`（MainActivity 改继承 AppCompatActivity，API 33+ 系统 LocaleManager、API < 33 appcompat 自动持久化）；VM 里的动态文案改用 `UiText`（资源 ID + 参数）在 UI 层解析；时长/状态/分类/单位枚举的 `label: String` 全部改 `@StringRes`。

**Tech Stack:** Jetpack Compose M3、DataStore Preferences 1.1.1（新依赖）、appcompat 1.7.0（新依赖）、Robolectric 4.13（新测试依赖）。

**约定：**
- 所有 gradle 命令前缀：`JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew …`（根目录执行）。
- **禁止 `git add -A`**——仓库有未跟踪的 `resources/需求-*.txt`、`.codegraph/`，只 add 明确文件。
- Timber 日志/注释里的中文**不翻译**（开发者面向），只抽用户可见文案。
- 提交信息末尾加 `Co-Authored-By: Claude <noreply@anthropic.com>`。

**文件结构总览：**

| 文件 | 动作 | 职责 |
|---|---|---|
| `app/build.gradle.kts` | 改 | 新依赖 + robolectric 配置 |
| `app/src/main/res/values/strings.xml` | 新建 | 简体中文全量文案（兜底默认） |
| `app/src/main/res/values-en/strings.xml` | 新建 | 英文 |
| `app/src/main/res/values-zh-rTW/strings.xml` | 新建 | 繁体中文 |
| `app/src/main/res/xml/locales_config.xml` | 新建 | API 33+ 系统设置「应用语言」列表 |
| `app/src/main/AndroidManifest.xml` | 改 | label/theme/localeConfig/AutoStoreLocales service |
| `app/src/main/java/com/battor/freshmate/ui/common/UiText.kt` | 新建 | VM → UI 的本地化文案载体 |
| `app/src/main/java/com/battor/freshmate/data/SettingsRepository.kt` | 新建 | ThemeMode 枚举 + DataStore 读写 |
| `app/src/main/java/com/battor/freshmate/ui/settings/AppLanguage.kt` | 新建 | 语言枚举 + apply/fromLocales |
| `app/src/main/java/com/battor/freshmate/ui/settings/SettingsViewModel.kt` | 新建 | 设置页状态（主题/语言） |
| `MainActivity.kt` | 改 | AppCompatActivity + 主题接线 |
| `SettingsScreen.kt` | 改 | 两个新设置项 + 对话框 + 顺序调整 |
| `NavGraph.kt` | 改 | SettingsViewModel 注入 + updateHint UiText |
| 枚举类 ×4 | 改 | ExpiryStatus/Category/ShelfLifeUnit/InputMethods 的 label → @StringRes |
| `ExpiryStatus.kt` 时长函数 | 改 | formatRemaining/formatExpired 接 Resources |
| 屏幕/VM/通知 | 改 | 文案 → stringResource/UiText/getString |

---

### Task 1: 依赖 + 全量简体中文 strings.xml + UiText

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/com/battor/freshmate/ui/common/UiText.kt`
- Modify: `app/src/main/AndroidManifest.xml`（仅 label 一处）

- [ ] **Step 1: build.gradle.kts 加依赖与测试配置**

dependencies 块（`// v2：日志 / 更新 / 导航` 那组之后）加：

```kotlin
    // 需求-4：设置（DataStore）/ per-app 语言（appcompat）
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
```

`// Local tests` 组加：

```kotlin
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
```

android 块内（`sourceSets` 之后）加：

```kotlin
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
```

- [ ] **Step 2: 新建 values/strings.xml（简体中文全量，后续任务全部引用此文件）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 通用 -->
    <string name="app_name">食刻 FreshMate</string>
    <string name="back">返回</string>
    <string name="ok">确定</string>
    <string name="cancel">取消</string>
    <string name="save">保存</string>
    <string name="delete">删除</string>
    <string name="restore">还原</string>
    <string name="undo">撤销</string>
    <string name="history">历史</string>
    <string name="settings">设置</string>
    <string name="add">添加</string>
    <string name="about">关于</string>
    <string name="view_logs">查看日志</string>
    <string name="check_update">检查更新</string>

    <!-- 主列表 -->
    <string name="empty_list">暂无食品，点 + 添加</string>
    <string name="pinned_header">本次添加</string>
    <string name="disperse_into_buckets">散入各桶</string>
    <string name="deleted_snackbar">已删除「%1$s」</string>
    <string name="near_expiry_title">该食品临近过期</string>
    <string name="near_expiry_text">「%1$s」已有 %2$d 个提醒时点过去，剩余提醒时点 %3$d 个。确认保存吗？</string>
    <string name="quantity_label">数量：%1$s</string>

    <!-- 表单 -->
    <string name="field_name">食品名称 *</string>
    <string name="error_name_required">请输入名称</string>
    <string name="label_category">分类</string>
    <string name="field_production_date">生产日期</string>
    <string name="hint_production_date">不填则按录入日起算</string>
    <string name="clear_production_date">清除生产日期</string>
    <string name="field_shelf_life">保质期 *</string>
    <string name="error_shelf_life">请输入大于 0 的数字</string>
    <string name="field_quantity">数量（可选，如 2 / 500g）</string>
    <string name="expiry_preview_remaining">还有 %1$s 到期</string>
    <string name="expiry_preview_expired">已过期 %1$s，保存后将不再提醒</string>
    <string name="quick_shelf_days">%1$d天</string>
    <string name="quick_shelf_months">%1$d个月</string>
    <string name="quick_shelf_years">%1$d年</string>
    <string name="unit_day">天</string>
    <string name="unit_week">周</string>
    <string name="unit_month">月</string>
    <string name="unit_year">年</string>
    <string name="placeholder_voice">语音识别即将上线</string>
    <string name="placeholder_image">图片识别即将上线</string>
    <string name="recording">录音中…（占位，再按一次结束）</string>
    <string name="extra_voice_hint">长按说话</string>
    <string name="extra_image_hint">选择图片</string>
    <string name="extra_with_long_press">%1$s（长按）</string>
    <string name="input_manual">手动输入</string>
    <string name="input_voice">语音输入</string>
    <string name="input_image">图片输入</string>
    <string name="discard_back">放弃返回</string>
    <string name="stash_and_continue">暂存并继续</string>

    <!-- 时长与状态 -->
    <string name="duration_days">%1$d 天</string>
    <string name="duration_hours">%1$d 小时</string>
    <string name="duration_half_hour">30 分钟</string>
    <string name="duration_under_half_hour">不足 30 分钟</string>
    <string name="status_expired">已过期</string>
    <string name="status_due_1d">1 天内到期</string>
    <string name="status_due_3d">3 天内到期</string>
    <string name="status_due_7d">7 天内到期</string>
    <string name="status_due_14d">14 天内到期</string>
    <string name="status_safe">更久到期</string>

    <!-- 分类 -->
    <string name="category_fruits_veg">果蔬</string>
    <string name="category_meat_egg">肉蛋</string>
    <string name="category_dairy">乳品</string>
    <string name="category_drink">饮料</string>
    <string name="category_snack">零食</string>
    <string name="category_staple">主食</string>
    <string name="category_frozen">冷冻</string>
    <string name="category_condiment">调味</string>

    <!-- 历史 -->
    <string name="empty_history">暂无已删除条目</string>
    <string name="restore_title">还原条目</string>
    <string name="restore_confirm">把「%1$s」还原吗？还原后将回到主列表对应的过期时间桶。</string>
    <string name="deleted_at">删除于 %1$s</string>
    <string name="restored_snackbar">已还原「%1$s」</string>

    <!-- 错误（VM → UiText） -->
    <string name="error_delete_failed">删除失败，请重试</string>
    <string name="error_restore_failed">还原失败，请重试</string>
    <string name="error_recover_failed">恢复失败，请重试</string>
    <string name="error_save_failed">保存失败，请重试</string>

    <!-- 权限横幅 -->
    <string name="banner_notifications_off">通知未开启，将收不到过期提醒</string>
    <string name="banner_exact_alarm_off">精确提醒未开启，提醒时间可能偏差</string>
    <string name="banner_action_go">去开启</string>

    <!-- 日志 -->
    <string name="log_title">日志 %1$s</string>
    <string name="pick_date">选择日期</string>
    <string name="empty_logs">暂无日志</string>

    <!-- 设置 -->
    <string name="settings_theme">主题</string>
    <string name="settings_theme_light">浅色</string>
    <string name="settings_theme_dark">深色</string>
    <string name="settings_language">语言</string>
    <string name="language_follow_system">跟随系统</string>
    <!-- 语言名永远用母语显示，三个资源目录内容一致，不随界面语言翻译 -->
    <string name="language_name_zh_cn">简体中文</string>
    <string name="language_name_zh_tw">繁體中文</string>
    <string name="language_name_en">English</string>

    <!-- 关于 -->
    <string name="version">版本 %1$s (%2$d)</string>
    <string name="about_tagline">记录食品保质期，临期提醒不浪费。</string>

    <!-- 更新 -->
    <string name="update_hint">发现新版本 %1$s</string>
    <string name="update_found_title">发现新版本 %1$s</string>
    <string name="update_download">下载更新</string>
    <string name="update_downloading">下载中…</string>
    <string name="update_downloading_percent">下载中 %1$d%%</string>
    <string name="update_install">安装</string>
    <string name="update_already_latest">已是最新版本</string>
    <string name="update_check_failed">检查更新失败，请稍后重试</string>
    <string name="update_checksum_failed">安装包校验失败</string>
    <string name="update_download_failed">下载失败：%1$s</string>

    <!-- 通知 -->
    <string name="notification_channel">过期提醒</string>
    <string name="notification_title">食刻 FreshMate</string>
    <string name="notification_body">「%1$s」还有 %2$s 到期</string>
</resources>
```

- [ ] **Step 3: 新建 UiText.kt**

```kotlin
package com.battor.freshmate.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * ViewModel 产出的本地化文案载体：UI 层经 asString() 用当前资源配置解析。
 * VM 不再持有裸中文字符串（需求-4 多语言）。
 */
data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList())

@Composable
fun UiText.asString(): String = stringResource(id, *args.toTypedArray())
```

- [ ] **Step 4: manifest label 改用资源**

`android:label="食刻 FreshMate"` → `android:label="@string/app_name"`（application 标签上）。

- [ ] **Step 5: 编译验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL（资源未引用只产生 lint 警告，不阻断）。

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts app/src/main/res/values/strings.xml app/src/main/java/com/battor/freshmate/ui/common/UiText.kt app/src/main/AndroidManifest.xml
git commit -m "feat(i18n): DataStore/appcompat 依赖、全量简中文案资源与 UiText 载体"
```

---

### Task 2: ThemeMode + SettingsRepository（TDD）

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/data/SettingsRepository.kt`
- Test: `app/src/test/java/com/battor/freshmate/data/SettingsRepositoryTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.battor.freshmate.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repo = DataStoreSettingsRepository(context)

    @Test
    fun 未写入时默认跟随系统() = runBlocking {
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
    }

    @Test
    fun 写入后可读回() = runBlocking {
        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.themeMode.first())
        repo.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repo.themeMode.first())
    }
}
```

（Robolectric 每个测试方法独立临时文件系统，两个用例互不污染。DataStore 读写在 IO 线程完成，runBlocking 等待即可。）

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.data.SettingsRepositoryTest"`
Expected: 编译失败 `unresolved reference: DataStoreSettingsRepository`。

- [ ] **Step 3: 实现 SettingsRepository.kt**

```kotlin
package com.battor.freshmate.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 主题偏好：SYSTEM=跟随系统（默认），LIGHT/DARK=用户手动固定，无回归 SYSTEM 的入口（需求-4 用户确认）。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
}

class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {
    override val themeMode: Flow<ThemeMode> = context.settingsDataStore.data
        .map { prefs -> prefs[Key]?.let(ThemeMode::valueOf) ?: ThemeMode.SYSTEM }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Key] = mode.name }
    }

    private companion object {
        val Key = stringPreferencesKey("theme_mode")
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.data.SettingsRepositoryTest"`
Expected: PASS（2 tests）。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/data/SettingsRepository.kt app/src/test/java/com/battor/freshmate/data/SettingsRepositoryTest.kt
git commit -m "feat(settings): ThemeMode 与 DataStore SettingsRepository（TDD）"
```

---

### Task 3: MainActivity 改 AppCompatActivity + 主题接线

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`（theme 一处）

- [ ] **Step 1: MainActivity 重写**

```kotlin
package com.battor.freshmate

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.battor.freshmate.data.DataStoreSettingsRepository
import com.battor.freshmate.data.ThemeMode
import com.battor.freshmate.ui.navigation.FreshMateNavGraph
import com.battor.freshmate.ui.theme.FreshMateTheme

/** AppCompatActivity：per-app 语言（Task 5）依赖 appcompat 委托；主题经 Compose 状态即时切换。 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = DataStoreSettingsRepository(applicationContext)
        setContent {
            val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            FreshMateTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
            ) { FreshMateNavGraph() }
        }
    }
}
```

- [ ] **Step 2: manifest 换 AppCompat 主题**

`android:theme="@android:style/Theme.Material.Light.NoActionBar"` → `android:theme="@style/Theme.AppCompat.DayNight.NoActionBar"`（AppCompatActivity 要求 AppCompat 系主题；DayNight 让 Compose 首帧前的窗口底色也随深浅切换）。

- [ ] **Step 3: 编译 + 全量单测回归**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/MainActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat(theme): MainActivity 主题偏好接线，AppCompat 主题打底"
```

---

### Task 4: AppLanguage + 设置页主题/语言设置项

**Files:**
- Create: `app/src/main/java/com/battor/freshmate/ui/settings/AppLanguage.kt`
- Create: `app/src/main/java/com/battor/freshmate/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/AndroidManifest.xml`（locale service + localeConfig）
- Create: `app/src/main/res/xml/locales_config.xml`
- Test: `app/src/test/java/com/battor/freshmate/ui/settings/AppLanguageTest.kt`
- Test: `app/src/test/java/com/battor/freshmate/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: 写 AppLanguage 失败测试（纯 JVM，LocaleListCompat 不依赖 Android 框架）**

```kotlin
package com.battor.freshmate.ui.settings

import androidx.core.os.LocaleListCompat
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun 空列表映射为跟随系统() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLocales(LocaleListCompat.getEmptyLocaleList()))
    }

    @Test
    fun 语言标签映射() {
        assertEquals(
            AppLanguage.SIMPLIFIED_CHINESE,
            AppLanguage.fromLocales(LocaleListCompat.forLanguageTags("zh-CN")),
        )
        assertEquals(
            AppLanguage.TRADITIONAL_CHINESE,
            AppLanguage.fromLocales(LocaleListCompat.forLanguageTags("zh-TW")),
        )
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocales(LocaleListCompat.forLanguageTags("en")))
        // 系统可能回传带地区的英语
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocales(LocaleListCompat.forLanguageTags("en-US")))
    }

    @Test
    fun 不认识的语言映射为跟随系统() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLocales(LocaleListCompat.forLanguageTags("ja")))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.ui.settings.AppLanguageTest"`
Expected: 编译失败 `unresolved reference: AppLanguage`。

- [ ] **Step 3: 实现 AppLanguage.kt**

```kotlin
package com.battor.freshmate.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * 应用语言（需求-4）：SYSTEM=跟随系统（默认），其余为 BCP-47 标签。
 * 经 AppCompatDelegate.setApplicationLocales 生效：API 33+ 走系统 LocaleManager，
 * API < 33 由 manifest 里 AppLocalesMetadataHolderService(autoStoreLocales) 自动持久化。
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    SIMPLIFIED_CHINESE("zh-CN"),
    TRADITIONAL_CHINESE("zh-TW"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromLocales(locales: LocaleListCompat): AppLanguage {
            if (locales.isEmpty) return SYSTEM
            val locale = locales[0]
            return when {
                locale.language == "zh" && locale.country.equals("TW", ignoreCase = true) -> TRADITIONAL_CHINESE
                locale.language == "zh" -> SIMPLIFIED_CHINESE
                locale.language == "en" -> ENGLISH
                else -> SYSTEM
            }
        }

        fun apply(language: AppLanguage) {
            val locales = language.tag?.let(LocaleListCompat::forLanguageTags)
                ?: LocaleListCompat.getEmptyLocaleList()
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.ui.settings.AppLanguageTest"`
Expected: PASS（3 tests）。

- [ ] **Step 5: manifest 加 locale 服务与 localeConfig**

application 标签加属性 `android:localeConfig="@xml/locales_config"`；application 内追加：

```xml
        <!-- API < 33 per-app 语言自动持久化（AppLanguage.apply 依赖） -->
        <service
            android:name="androidx.appcompat.app.AppLocalesMetadataHolderService"
            android:enabled="false"
            android:exported="false">
            <meta-data
                android:name="autoStoreLocales"
                android:value="true" />
        </service>
```

新建 `app/src/main/res/xml/locales_config.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="zh-CN" />
    <locale android:name="zh-TW" />
    <locale android:name="en" />
</locale-config>
```

- [ ] **Step 6: 写 SettingsViewModel 失败测试**

```kotlin
package com.battor.freshmate.ui.settings

import com.battor.freshmate.data.SettingsRepository
import com.battor.freshmate.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeRepo(initial: ThemeMode) : SettingsRepository {
        override val themeMode = MutableStateFlow(initial)
        override suspend fun setThemeMode(mode: ThemeMode) {
            themeMode.value = mode
        }
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun 主题初值来自仓库() {
        val vm = SettingsViewModel(FakeRepo(ThemeMode.DARK))
        assertEquals(ThemeMode.DARK, vm.themeMode.value)
    }

    @Test
    fun 设置主题写入仓库() = runTest(dispatcher) {
        val repo = FakeRepo(ThemeMode.SYSTEM)
        val vm = SettingsViewModel(repo)
        vm.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repo.themeMode.value)
    }
}
```

- [ ] **Step 7: 跑测试确认失败**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.ui.settings.SettingsViewModelTest"`
Expected: 编译失败 `unresolved reference: SettingsViewModel`。

- [ ] **Step 8: 实现 SettingsViewModel.kt**

```kotlin
package com.battor.freshmate.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battor.freshmate.data.SettingsRepository
import com.battor.freshmate.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> =
        repository.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    /** 语言切换会重建 Activity 并重建本 VM，init 时读一次即可。 */
    val language: AppLanguage = AppLanguage.fromLocales(AppCompatDelegate.getApplicationLocales())

    fun setLanguage(language: AppLanguage) {
        AppLanguage.apply(language)
    }
}
```

- [ ] **Step 9: 跑测试确认通过**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.ui.settings.SettingsViewModelTest"`
Expected: PASS（2 tests）。

- [ ] **Step 10: SettingsScreen 加两个设置项 + 对话框 + 顺序调整**

SettingsScreen 签名加 `viewModel: SettingsViewModel`（第一个参数位）。import 增加：

```kotlin
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.battor.freshmate.R
import com.battor.freshmate.data.ThemeMode
```

LazyColumn 内容整体替换为（**顺序：主题 → 语言 → 查看日志 → 检查更新 → 关于**；检查更新/查看日志/关于三个现有 item 的代码不变，仅调换位置）：

```kotlin
        LazyColumn(Modifier.fillMaxWidth().padding(padding)) {
            item { ThemeSettingItem(viewModel) }
            item { LanguageSettingItem(viewModel) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.view_logs)) },
                    leadingContent = { Icon(Icons.Filled.Description, contentDescription = null) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onOpenLogs),
                )
            }
            item {
                // 检查更新 item：原样搬移（headline 换 stringResource(R.string.check_update)，其余不变）
            }
            item {
                // 关于 item：原样搬移（headline 换 stringResource(R.string.about)，其余不变）
            }
        }
```

（实现时把现有「检查更新」「关于」两个 item 的完整代码搬进来，headline 的 `Text("检查更新")` → `Text(stringResource(R.string.check_update))`、`Text("关于")` → `Text(stringResource(R.string.about))`。）

文件末尾追加两个私有组件：

```kotlin
/** 主题设置：UI 只有两档（浅色/深色）；SYSTEM 态显示系统当前模式（进入时检测）。 */
@Composable
private fun ThemeSettingItem(viewModel: SettingsViewModel) {
    val themeMode by viewModel.themeMode.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    // SYSTEM 态跟随系统当前模式展示（isSystemInDarkTheme 变化会触发重组刷新）
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val lightLabel = stringResource(R.string.settings_theme_light)
    val darkLabel = stringResource(R.string.settings_theme_dark)
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_theme)) },
        leadingContent = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
        trailingContent = { Text(if (isDark) darkLabel else lightLabel) },
        modifier = Modifier.clickable { showDialog = true },
    )
    if (showDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = listOf(lightLabel to ThemeMode.LIGHT, darkLabel to ThemeMode.DARK),
            selected = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT,
            onSelect = {
                viewModel.setThemeMode(it)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

/** 语言设置：四档（跟随系统/简中/繁中/English）；切换经 per-app language 重建 Activity。 */
@Composable
private fun LanguageSettingItem(viewModel: SettingsViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    val followSystem = stringResource(R.string.language_follow_system)
    val zhCn = stringResource(R.string.language_name_zh_cn)
    val zhTw = stringResource(R.string.language_name_zh_tw)
    val en = stringResource(R.string.language_name_en)
    val current = viewModel.language
    val currentLabel = when (current) {
        AppLanguage.SYSTEM -> followSystem
        AppLanguage.SIMPLIFIED_CHINESE -> zhCn
        AppLanguage.TRADITIONAL_CHINESE -> zhTw
        AppLanguage.ENGLISH -> en
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_language)) },
        leadingContent = { Icon(Icons.Filled.Language, contentDescription = null) },
        trailingContent = { Text(currentLabel) },
        modifier = Modifier.clickable { showDialog = true },
    )
    if (showDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = listOf(
                followSystem to AppLanguage.SYSTEM,
                zhCn to AppLanguage.SIMPLIFIED_CHINESE,
                zhTw to AppLanguage.TRADITIONAL_CHINESE,
                en to AppLanguage.ENGLISH,
            ),
            selected = current,
            onSelect = {
                viewModel.setLanguage(it)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

/** M3 单选对话框：RadioButton 行 + 点行即选。 */
@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Text(label, modifier = Modifier.padding(top = 14.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
```

TopAppBar 标题 `Text("设置")` → `Text(stringResource(R.string.settings))`；返回 contentDescription `"返回"` → `stringResource(R.string.back)`。

- [ ] **Step 11: NavGraph 注入 SettingsViewModel**

Routes.SETTINGS composable 里，`SettingsScreen(` 之前加：

```kotlin
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SettingsViewModel(DataStoreSettingsRepository(context))
                    }
                },
            )
```

（`val context = LocalContext.current.applicationContext` 已在 main 路由有，settings 路由需同样取一次。）`SettingsScreen(` 参数首位加 `viewModel = settingsViewModel,`。import 加 `com.battor.freshmate.data.DataStoreSettingsRepository` 与 `com.battor.freshmate.ui.settings.SettingsViewModel`。

- [ ] **Step 12: 编译 + 手动验证清单（装机）**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL。
装机：设置页出现主题/语言两项；切深色全 app 即时变（含六档色板深色版）；切 English/繁中**暂只有设置页相关项变**（其余页面文案 Task 6/7 抽取）；系统设置（API 33+）出现「应用语言」。

- [ ] **Step 13: Commit**

```bash
git add app/src/main/java/com/battor/freshmate/ui/settings/ app/src/main/java/com/battor/freshmate/ui/navigation/NavGraph.kt app/src/main/AndroidManifest.xml app/src/main/res/xml/locales_config.xml app/src/test/java/com/battor/freshmate/ui/settings/
git commit -m "feat(settings): 主题/语言设置项与 per-app 语言接线"
```

---

### Task 5: 状态/分类/单位/输入方式枚举 @StringRes + 时长本地化

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/util/ExpiryStatus.kt`
- Modify: `app/src/main/java/com/battor/freshmate/data/Category.kt`
- Modify: `app/src/main/java/com/battor/freshmate/util/ShelfLifeUnit.kt`
- Modify: `app/src/main/java/com/battor/freshmate/inputmethod/InputMethods.kt`
- Test: `app/src/test/java/com/battor/freshmate/util/ExpiryStatusTest.kt`（改）
- Test: `app/src/test/java/com/battor/freshmate/util/ShelfLifeUnitTest.kt`（改）
- Test: `app/src/test/java/com/battor/freshmate/inputmethod/InputMethodsTest.kt`（改）

- [ ] **Step 1: ExpiryStatus.kt 重写（label → @StringRes；时长函数接 Resources）**

```kotlin
package com.battor.freshmate.util

import android.content.res.Resources
import androidx.annotation.StringRes
import com.battor.freshmate.R
import java.time.Duration
import java.time.LocalDateTime

/**
 * 条目紧急度（需求-3 改为绝对时间六档，与主列表分桶同阈值），值越靠后越宽松。
 * 红(EXPIRED) → DUE_1D → DUE_3D → DUE_7D → DUE_14D → 绿(SAFE)。
 */
enum class ExpiryStatus(@StringRes val labelRes: Int) {
    EXPIRED(R.string.status_expired),
    DUE_1D(R.string.status_due_1d),
    DUE_3D(R.string.status_due_3d),
    DUE_7D(R.string.status_due_7d),
    DUE_14D(R.string.status_due_14d),
    SAFE(R.string.status_safe),
}

fun expiryStatus(expiry: LocalDateTime, now: LocalDateTime): ExpiryStatus {
    val remaining = Duration.between(now, expiry)
    if (remaining <= Duration.ZERO) return ExpiryStatus.EXPIRED
    return when {
        remaining <= Duration.ofDays(1) -> ExpiryStatus.DUE_1D
        remaining <= Duration.ofDays(3) -> ExpiryStatus.DUE_3D
        remaining <= Duration.ofDays(7) -> ExpiryStatus.DUE_7D
        remaining <= Duration.ofDays(14) -> ExpiryStatus.DUE_14D
        else -> ExpiryStatus.SAFE
    }
}

/** "还有 3 天 17 小时到期"里的时间段，精确到半小时（向下取整），按当前语言输出。 */
fun formatRemaining(res: Resources, remaining: Duration): String {
    val totalMinutes = remaining.toMinutes().coerceAtLeast(0)
    val days = totalMinutes / 1440
    val hours = totalMinutes % 1440 / 60
    val halfHour = totalMinutes % 60 >= 30
    val parts = buildList {
        if (days > 0) add(res.getString(R.string.duration_days, days))
        if (hours > 0) add(res.getString(R.string.duration_hours, hours))
        if (halfHour && days == 0L) add(res.getString(R.string.duration_half_hour))
    }
    return if (parts.isEmpty()) res.getString(R.string.duration_under_half_hour) else parts.joinToString(" ")
}

/** 已过期的时长文案："2 天" / "5 小时"，按当前语言输出。 */
fun formatExpired(res: Resources, overdue: Duration): String {
    val days = overdue.toDays()
    return if (days > 0) {
        res.getString(R.string.duration_days, days)
    } else {
        res.getString(R.string.duration_hours, overdue.toHours().coerceAtLeast(1))
    }
}
```

- [ ] **Step 2: Category.kt 改 @StringRes**

`enum class Category(val label: String)` → `enum class Category(@StringRes val labelRes: Int)`，八个条目对应：`FRUITS_VEG(R.string.category_fruits_veg)`、`MEAT_EGG(R.string.category_meat_egg)`、`DAIRY(R.string.category_dairy)`、`DRINK(R.string.category_drink)`、`SNACK(R.string.category_snack)`、`STAPLE(R.string.category_staple)`、`FROZEN(R.string.category_frozen)`、`CONDIMENT(R.string.category_condiment)`。import `androidx.annotation.StringRes` 与 `com.battor.freshmate.R`。

- [ ] **Step 3: ShelfLifeUnit.kt 改 @StringRes**

`enum class ShelfLifeUnit(val label: String, ...)` → `@StringRes val labelRes: Int`：`DAY(R.string.unit_day, 1)`、`WEEK(R.string.unit_week, 7)`、`MONTH(R.string.unit_month, 30)`、`YEAR(R.string.unit_year, 365)`。

- [ ] **Step 4: InputMethods.kt 改 @StringRes**

- `data class ExtraAction(val icon: ImageVector, val label: String)` → `data class ExtraAction(val icon: ImageVector, @StringRes val labelRes: Int)`
- 接口 `val menuLabel: String` → `@StringRes val menuLabelRes: Int`
- 三实现：`menuLabelRes = R.string.input_manual / R.string.input_voice / R.string.input_image`；extraAction：`ExtraAction(Icons.Filled.KeyboardVoice, R.string.extra_voice_hint)`、`ExtraAction(Icons.Filled.Image, R.string.extra_image_hint)`

- [ ] **Step 5: 更新三个枚举测试**

`ExpiryStatusTest.kt`：类头加 `@RunWith(RobolectricTestRunner::class)` 与 `@Config(sdk = [34])`，时长断言改为经 Robolectric 资源解析（默认简中，期望值不变）：

```kotlin
private val res = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().resources
// 原 assertEquals("3 天 17 小时", formatRemaining(...)) →
assertEquals("3 天 17 小时", formatRemaining(res, Duration.ofMinutes(3 * 1440 + 17 * 60 + 29)))
assertEquals("不足 30 分钟", formatRemaining(res, Duration.ofMinutes(20)))
assertEquals("30 分钟", formatRemaining(res, Duration.ofMinutes(45)))
assertEquals("已过期 2 天", "已过期 " + formatExpired(res, Duration.ofDays(2)))
assertEquals("已过期 5 小时", "已过期 " + formatExpired(res, Duration.ofHours(5)))
```

`ShelfLifeUnitTest.kt`：`assertEquals("天", ShelfLifeUnit.DAY.label)` → `assertEquals(R.string.unit_day, ShelfLifeUnit.DAY.labelRes)`（YEAR 同理）。
`InputMethodsTest.kt`：label 断言 → `assertEquals(R.string.input_manual, ManualInputMethod.menuLabelRes)`、`assertEquals(R.string.extra_voice_hint, VoiceInputMethod.extraAction?.labelRes)`、`assertEquals(R.string.extra_image_hint, ImageInputMethod.extraAction?.labelRes)`。

- [ ] **Step 6: 编译确认（UI 消费方尚未改，此步预期编译报错清单 = Task 6/7 的工作面）**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew compileDebugKotlin 2>&1 | grep "e:" `
Expected: 报错集中在 `label`/`formatRemaining`/`formatExpired` 的消费方（FoodItemCard、ItemForm、StatusColor、HistoryScreen、ReminderBroadcastReceiver 等）。**这正是 Task 6 的修改清单，记录下来。**

- [ ] **Step 7: 跑枚举测试确认通过**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.battor.freshmate.util.ExpiryStatusTest" --tests "com.battor.freshmate.util.ShelfLifeUnitTest" --tests "com.battor.freshmate.inputmethod.InputMethodsTest"`
Expected: PASS。（如因 AppLanguage/Settings 测试一起跑不受影响。）

- [ ] **Step 8: Commit（连同 Task 6 一起提交亦可——若分开提交，此处先不 commit，把 Task 5+6 视为一个提交单元）**

> **执行注意**：Task 5 改签名后工程不可编译，Task 6 修完全部消费方才恢复。因此 Task 5 与 Task 6 必须连续执行、合并提交：
>
> ```bash
> git add -u app/src/main/java/com/battor/freshmate app/src/test/java/com/battor/freshmate
> git add app/src/main/java/com/battor/freshmate 2>/dev/null; git commit -m "refactor(i18n): 枚举 label 改 @StringRes，时长文案接 Resources"
> ```

---

### Task 6: 枚举消费方文案抽取（与 Task 5 合并提交）

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/StatusColor.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/FoodItemCard.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/ItemForm.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt`（桶头部分）
- Modify: `app/src/main/java/com/battor/freshmate/ui/history/HistoryScreen.kt`
- Modify: `app/src/main/java/com/battor/freshmate/notification/ReminderBroadcastReceiver.kt`

- [ ] **Step 1: StatusColor.kt 的 expiryText 改 @Composable**

```kotlin
/** 条目右侧状态文本：已过期 x / 还有 x 到期（按当前语言）。 */
@Composable
fun expiryText(item: FoodItem, now: LocalDateTime): String {
    val expiry = expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
    val remaining = Duration.between(now, expiry)
    return if (remaining.isNegative || remaining.isZero) {
        stringResource(R.string.expiry_preview_expired, formatExpired(LocalContext.current.resources, remaining.negated()))
    } else {
        stringResource(R.string.expiry_preview_remaining, formatRemaining(LocalContext.current.resources, remaining))
    }
}
```

import：`androidx.compose.runtime.Composable`、`androidx.compose.ui.platform.LocalContext`、`androidx.compose.ui.res.stringResource`、`com.battor.freshmate.R`。（`expiry_preview_expired` 文案为「已过期 %1$s，保存后将不再提醒」——ItemForm 预览句与卡片短句**共用此资源**时预览句多出半句，故卡片短句另用 `R.string.status_expired` 拼：见 Step 2。）

> **修正**：卡片/历史页只需要「已过期 x」短句，不该带「保存后将不再提醒」。`expiryText` 改为：

```kotlin
@Composable
fun expiryText(item: FoodItem, now: LocalDateTime): String {
    val expiry = expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
    val remaining = Duration.between(now, expiry)
    val duration = if (remaining.isNegative || remaining.isZero) {
        formatExpired(LocalContext.current.resources, remaining.negated())
    } else {
        formatRemaining(LocalContext.current.resources, remaining)
    }
    return if (remaining.isNegative || remaining.isZero) {
        "${stringResource(R.string.status_expired)} $duration"
    } else {
        stringResource(R.string.expiry_preview_remaining, duration)
    }
}
```

（`expiry_preview_remaining` = 「还有 %1$s 到期」不含多余半句，可直接复用；`expiry_preview_expired` 只由 ItemForm 预览用。）

- [ ] **Step 2: FoodItemCard.kt**

- `CustomAccessibilityAction("删除")` → `CustomAccessibilityAction(stringResource(R.string.delete))`
- 删除图标 `contentDescription = "删除"` → `stringResource(R.string.delete)`
- `item.category.label`（contentDescription）→ `stringResource(item.category.labelRes)`
- `Text("数量：$it", ...)` → `Text(stringResource(R.string.quantity_label, it), ...)`
- import `androidx.compose.ui.res.stringResource` 与 `com.battor.freshmate.R`（`expiryText` 调用点签名不变，已是 @Composable 内调用）

- [ ] **Step 3: ItemForm.kt**

- `QuickShelfLives` 顶层 val 改为带资源 ID 的结构（Composable 内取文案）：

```kotlin
/** 快捷保质期：30天 / 3个月 / 6个月 / 1年（2026-08-15 用户反馈去掉 3天/7天）；labelRes 带格式参数 %1$d。 */
private data class QuickShelfLife(
    @StringRes val labelRes: Int,
    val value: Int,
    val unit: ShelfLifeUnit,
)

private val QuickShelfLives = listOf(
    QuickShelfLife(R.string.quick_shelf_days, 30, ShelfLifeUnit.DAY),
    QuickShelfLife(R.string.quick_shelf_months, 3, ShelfLifeUnit.MONTH),
    QuickShelfLife(R.string.quick_shelf_months, 6, ShelfLifeUnit.MONTH),
    QuickShelfLife(R.string.quick_shelf_years, 1, ShelfLifeUnit.YEAR),
)
```

- `Text(label)` → `Text(stringResource(labelRes, value))`（解构改 `QuickShelfLives.forEach { quick -> ... stringResource(quick.labelRes, quick.value) ... }`）
- `Text(unit.label)` → `Text(stringResource(unit.labelRes))`
- `Text(c.label)` → `Text(stringResource(c.labelRes))`
- `"食品名称 *"` → `stringResource(R.string.field_name)`；`"请输入名称"` → `R.string.error_name_required`；`"分类"` → `R.string.label_category`；`"生产日期"` → `R.string.field_production_date`；`"不填则按录入日起算"` → `R.string.hint_production_date`；`"清除生产日期"` → `R.string.clear_production_date`；`"保质期 *"` → `R.string.field_shelf_life`；`"请输入大于 0 的数字"` → `R.string.error_shelf_life`；`"数量（可选，如 2 / 500g）"` → `R.string.field_quantity`；`"确定"` → `R.string.ok`；`"取消"` → `R.string.cancel`
- `ExpiryPreview` 内：

```kotlin
    val text = if (remaining.isNegative || remaining.isZero) {
        stringResource(
            R.string.expiry_preview_expired,
            formatExpired(LocalContext.current.resources, remaining.negated()),
        )
    } else {
        stringResource(
            R.string.expiry_preview_remaining,
            formatRemaining(LocalContext.current.resources, remaining),
        )
    }
```

- `ExtraActionRow`：参数 `label: String` → `@StringRes labelRes: Int`；调用处 `extra.label` → `extra.labelRes`；`onPlaceholderHint("图片识别即将上线")` → `onPlaceholderHint(stringResource(R.string.placeholder_image))`；语音同 `R.string.placeholder_voice`；`contentDescription = label` → `stringResource(labelRes)`；`Text(when { ... })` →

```kotlin
        Text(
            when {
                id == InputMethodId.IMAGE -> stringResource(labelRes)
                recording -> stringResource(R.string.recording)
                else -> stringResource(R.string.extra_with_long_press, stringResource(labelRes))
            },
        )
```

（ExpiryPreview 的 `fontSize = 13.sp` 是已知遗留，本次顺手换 `MaterialTheme.typography.bodySmall` 并删 `import androidx.compose.ui.unit.sp`。）

- [ ] **Step 4: MainScreen.kt 桶头与置顶区**

- `BucketBox(header = "本次添加", ...)` → `header = stringResource(R.string.pinned_header)`
- `BucketBox(header = bucket.status.label, ...)` → `header = stringResource(bucket.status.labelRes)`
- 桶头 a11y 自定义动作 `CustomAccessibilityAction("散入各桶")`（在 BucketBox 组头 Text 的 semantics 里，`onHeaderAction != null` 分支）→ `CustomAccessibilityAction(stringResource(R.string.disperse_into_buckets))`
- 其余 MainScreen 文案（标题/空态/删除 Snackbar/临近过期对话框）在 Task 7 处理

- [ ] **Step 5: HistoryScreen.kt**

- `"历史"` → `stringResource(R.string.history)`；`"返回"` → `stringResource(R.string.back)`；`"暂无已删除条目"` → `R.string.empty_history`；`"还原条目"` → `R.string.restore_title`；还原对话框 text → `stringResource(R.string.restore_confirm, item.name)`；`"还原"` → `R.string.restore`；`"取消"` → `R.string.cancel`
- `"删除于 ${group.deletedAt.format(DeletedAtFormat)}"` → `stringResource(R.string.deleted_at, group.deletedAt.format(DeletedAtFormat))`
- `CustomAccessibilityAction("还原")` → `stringResource(R.string.restore)`；还原图标 contentDescription 同
- `item.category.label` → `stringResource(item.category.labelRes)`

- [ ] **Step 6: ReminderBroadcastReceiver.kt**

通知构建处（36-37 行附近）：

```kotlin
                        .setContentTitle(context.getString(R.string.notification_title))
                        .setContentText(
                            context.getString(
                                R.string.notification_body,
                                item.name,
                                formatRemaining(context.resources, remaining),
                            ),
                        )
```

import `com.battor.freshmate.R` 与 `com.battor.freshmate.util.formatRemaining`。（receiver 的 context 是 app context：API 33+ 已被 per-app locale 包裹，正常；API < 33 跟随系统语言——spec §3 已记录的限制。）

- [ ] **Step 7: 编译 + 全量单测**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL（MainViewModel/HistoryViewModel 的 UiText 改造在 Task 7，此步错误事件仍是中文字符串也能编译）。

- [ ] **Step 8: Commit（与 Task 5 合并）**

```bash
git add app/src/main/java/com/battor/freshmate app/src/test/java/com/battor/freshmate
git commit -m "refactor(i18n): 枚举 label 改 @StringRes，时长文案与消费方接资源"
```

（`git add` 目录路径只会包含已跟踪改动 + 新测试文件已在此前步骤加入；执行前 `git status` 确认没有把 `resources/`、`.codegraph/` 带入——它们不在这些目录下，安全。）

---

### Task 7: 屏幕/VM/通知剩余文案抽取 + UiText 接线

**Files:**
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/MainScreen.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/MainViewModel.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/FabMenu.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/main/PermissionEffects.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/history/HistoryViewModel.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/logviewer/LogViewerScreen.kt`
- Modify: `app/src/main/java/com/battor/freshmate/ui/settings/AboutScreen.kt`、`SettingsScreen.kt`（更新对话框部分）
- Modify: `app/src/main/java/com/battor/freshmate/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/battor/freshmate/update/UpdateViewModel.kt`
- Modify: `app/src/main/java/com/battor/freshmate/FreshMateApp.kt`
- Test: `MainViewModelTest.kt`、`HistoryViewModelTest.kt`（断言更新）

- [ ] **Step 1: MainViewModel/HistoryViewModel 错误事件 → UiText**

MainViewModel：

```kotlin
    private val _errorEvent = MutableStateFlow<UiText?>(null)
    val errorEvent: StateFlow<UiText?> = _errorEvent.asStateFlow()
```

三处赋值：`"删除失败，请重试"` → `UiText(R.string.error_delete_failed)`；`"恢复失败，请重试"` → `UiText(R.string.error_recover_failed)`；`"保存失败，请重试"` → `UiText(R.string.error_save_failed)`。import `com.battor.freshmate.ui.common.UiText`。

HistoryViewModel：`_message` 同改 `UiText?`；`"已还原「${item.name}」"` → `UiText(R.string.restored_snackbar, item.name)`；`"还原失败，请重试"` → `UiText(R.string.error_restore_failed)`。

- [ ] **Step 2: MainScreen.kt**

- `Text("食刻 FreshMate")` → `Text(stringResource(R.string.app_name))`
- `contentDescription = "历史"` → `stringResource(R.string.history)`；`"设置"` → `stringResource(R.string.settings)`
- `Text("暂无食品，点 + 添加", ...)` → `Text(stringResource(R.string.empty_list), ...)`
- 删除 Snackbar：`"已删除「${item.name}」"` → `snackbarHostState.showSnackbar(...)` 的 message 改 `context.getString(R.string.deleted_snackbar, item.name)`——在 composable 里更简：

```kotlin
                                onDeleteItem = { item ->
                                    viewModel.delete(item)
                                    val msg = stringResource(R.string.deleted_snackbar, item.name)
                                    val undoLabel = stringResource(R.string.undo)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            msg, actionLabel = undoLabel, duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(item)
                                    }
                                },
```

（两处删除回调——置顶区与桶——同改。`updateHint` 处理见下。）
- `LaunchedEffect(errorEvent)`：`showSnackbar(it)` → `showSnackbar(it.asString())`，import `com.battor.freshmate.ui.common.asString`
- `updateHint: String?` 参数 → `updateHint: UiText?`；LaunchedEffect 内 `snackbarHostState.showSnackbar(updateHint, actionLabel = "查看")` → `showSnackbar(updateHint.asString(), actionLabel = viewLabel)`，`val viewLabel = stringResource(R.string.view)`——**需要在 strings.xml 补一个键**（Task 1 资源里没有）：

```xml
    <string name="view">查看</string>
```

（加到「通用」组；Task 8 翻译文件同步加。）
- 临近过期对话框：`"该食品临近过期"` → `R.string.near_expiry_title`；长文本 → `stringResource(R.string.near_expiry_text, pending.editing.name, pending.skippedReminders, pending.remainingReminders)`；`"保存"` → `R.string.save`；`"取消"` → `R.string.cancel`

- [ ] **Step 3: FabMenu.kt（改用 InputMethods.all，消重）**

DropdownMenu 三个手写 item 替换为遍历：

```kotlin
                InputMethods.all.forEach { method ->
                    DropdownMenuItem(
                        text = { Text(stringResource(method.menuLabelRes)) },
                        leadingIcon = { Icon(method.menuIcon, contentDescription = null) },
                        onClick = { expanded = false; onStartInput(method.id) },
                    )
                }
```

- `contentDescription = "放弃返回"` → `stringResource(R.string.discard_back)`
- `if (isAddForm) "暂存并继续" else "保存"` → `stringResource(if (isAddForm) R.string.stash_and_continue else R.string.save)`
- `contentDescription = "添加"` → `stringResource(R.string.add)`
- 删除不再用的 `Icons.Filled.Edit/Image/KeyboardVoice` import（如仅菜单用）

- [ ] **Step 4: PermissionEffects.kt**

`Banner(text = "通知未开启，将收不到过期提醒")` → `Banner(text = stringResource(R.string.banner_notifications_off))`；`"精确提醒未开启，提醒时间可能偏差"` → `R.string.banner_exact_alarm_off`；`Text("去开启")` → `stringResource(R.string.banner_action_go)`。

- [ ] **Step 5: HistoryViewModel 消费方 HistoryScreen**

`snackbarHostState.showSnackbar(it)` → `showSnackbar(it.asString())`。

- [ ] **Step 6: LogViewerScreen.kt / AboutScreen.kt**

LogViewer：`Text("日志 ${selected ?: ""}")` → `Text(stringResource(R.string.log_title, selected ?: ""))`；`"返回"` → `R.string.back`；`"选择日期"` → `R.string.pick_date`；`"暂无日志"` → `R.string.empty_logs`。
About：`Text("关于")` → `R.string.about`；`"返回"` → `R.string.back`；`Text("食刻 FreshMate", ...)` → `stringResource(R.string.app_name)`；`"版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"` → `stringResource(R.string.version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)`；`"记录食品保质期，临期提醒不浪费。"` → `R.string.about_tagline`。

- [ ] **Step 7: NavGraph.kt + UpdateViewModel + SettingsScreen 更新对话框**

NavGraph：`updateHint = updateState.silentFound?.let { "发现新版本 ${it.versionName}" }` → `updateHint = updateState.silentFound?.let { UiText(R.string.update_hint, it.versionName) }`，import `com.battor.freshmate.ui.common.UiText`。

UpdateViewModel（notice: String? → UiText?）：

```kotlin
notice = if (manual) UiText(R.string.update_already_latest) else null
notice = if (manual) UiText(R.string.update_check_failed) else null
it.copy(downloading = false, notice = UiText(R.string.update_checksum_failed))
it.copy(downloading = false, notice = UiText(R.string.update_download_failed, e.message ?: ""))
```

UiState 里 `val notice: String? = null` → `val notice: UiText? = null`；import UiText 与 R。

SettingsScreen 更新对话框：`showSnackbar(it)` → `showSnackbar(it.asString())`；`Text("发现新版本 ${m.versionName}")` → `stringResource(R.string.update_found_title, m.versionName)`；`Text(if (p != null) "下载中 ${(p * 100).toInt()}%" else "下载中…")` → `if (p != null) stringResource(R.string.update_downloading_percent, (p * 100).toInt()) else stringResource(R.string.update_downloading)`；`Text("安装")` → `R.string.update_install`；`Text("下载更新")` → `R.string.update_download`；`Text("取消")` → `R.string.cancel`。

- [ ] **Step 8: FreshMateApp.kt 渠道名**

`"过期提醒"` → `getString(R.string.notification_channel)`。

- [ ] **Step 9: 更新受影响测试断言**

- `MainViewModelTest.kt:239`：`assertEquals("保存失败，请重试", vm.errorEvent.value)` →

```kotlin
        assertEquals(UiText(R.string.error_save_failed), vm.errorEvent.value)
```

（import `com.battor.freshmate.ui.common.UiText` 与 `com.battor.freshmate.R`。）
- `HistoryViewModelTest.kt:92`：`assertEquals("已还原「牛奶」", vm.message.value)` →

```kotlin
        assertEquals(UiText(R.string.restored_snackbar, "牛奶"), vm.message.value)
```

- 全量搜漏：`grep -rn '"[^"]*[一-鿿][^"]*"' app/src/main/java --include="*.kt" | grep -v '//' | grep -vE 'Timber\.(d|i|w|e)'`——期望只剩 Timber 日志、注释、require 抛错文案（`itemId 超出`、`apkUrl 必须为 https`、`空响应体`——开发者面向，不抽）。

- [ ] **Step 10: 全量验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL，全部测试通过。

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/battor/freshmate app/src/main/res/values/strings.xml app/src/test/java/com/battor/freshmate
git commit -m "feat(i18n): 全部用户可见文案接入 stringResource/UiText"
```

---

### Task 8: 英文与繁体中文翻译

**Files:**
- Create: `app/src/main/res/values-en/strings.xml`
- Create: `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `app/src/main/res/values/strings.xml`（补 `view` 键——若 Task 7 未加回 values/）

- [ ] **Step 1: values-en/strings.xml（全量，键与 values/ 一致）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Common -->
    <string name="app_name">食刻 FreshMate</string>
    <string name="back">Back</string>
    <string name="ok">OK</string>
    <string name="cancel">Cancel</string>
    <string name="save">Save</string>
    <string name="delete">Delete</string>
    <string name="restore">Restore</string>
    <string name="undo">Undo</string>
    <string name="history">History</string>
    <string name="settings">Settings</string>
    <string name="add">Add</string>
    <string name="about">About</string>
    <string name="view_logs">View logs</string>
    <string name="check_update">Check for updates</string>
    <string name="view">View</string>

    <!-- Main list -->
    <string name="empty_list">No food yet. Tap + to add</string>
    <string name="pinned_header">Added this session</string>
    <string name="disperse_into_buckets">Disperse into buckets</string>
    <string name="deleted_snackbar">Deleted “%1$s”</string>
    <string name="near_expiry_title">This item is close to expiry</string>
    <string name="near_expiry_text">“%1$s”: %2$d reminder time(s) have passed, %3$d remaining. Save anyway?</string>
    <string name="quantity_label">Qty: %1$s</string>

    <!-- Form -->
    <string name="field_name">Food name *</string>
    <string name="error_name_required">Enter a name</string>
    <string name="label_category">Category</string>
    <string name="field_production_date">Production date</string>
    <string name="hint_production_date">Counted from entry date if empty</string>
    <string name="clear_production_date">Clear production date</string>
    <string name="field_shelf_life">Shelf life *</string>
    <string name="error_shelf_life">Enter a number greater than 0</string>
    <string name="field_quantity">Quantity (optional, e.g. 2 / 500g)</string>
    <string name="expiry_preview_remaining">Expires in %1$s</string>
    <string name="expiry_preview_expired">Expired %1$s ago; no reminders after saving</string>
    <string name="quick_shelf_days">%1$dd</string>
    <string name="quick_shelf_months">%1$dm</string>
    <string name="quick_shelf_years">%1$dy</string>
    <string name="unit_day">d</string>
    <string name="unit_week">wk</string>
    <string name="unit_month">mo</string>
    <string name="unit_year">yr</string>
    <string name="placeholder_voice">Voice recognition coming soon</string>
    <string name="placeholder_image">Image recognition coming soon</string>
    <string name="recording">Recording… (placeholder, press again to stop)</string>
    <string name="extra_voice_hint">Hold to talk</string>
    <string name="extra_image_hint">Choose image</string>
    <string name="extra_with_long_press">%1$s (hold)</string>
    <string name="input_manual">Manual</string>
    <string name="input_voice">Voice</string>
    <string name="input_image">Image</string>
    <string name="discard_back">Discard and go back</string>
    <string name="stash_and_continue">Save and continue</string>

    <!-- Durations & status -->
    <string name="duration_days">%1$d d</string>
    <string name="duration_hours">%1$d h</string>
    <string name="duration_half_hour">30 min</string>
    <string name="duration_under_half_hour">under 30 min</string>
    <string name="status_expired">Expired</string>
    <string name="status_due_1d">Due within 1 day</string>
    <string name="status_due_3d">Due within 3 days</string>
    <string name="status_due_7d">Due within 7 days</string>
    <string name="status_due_14d">Due within 14 days</string>
    <string name="status_safe">Due later</string>

    <!-- Categories -->
    <string name="category_fruits_veg">Produce</string>
    <string name="category_meat_egg">Meat &amp; eggs</string>
    <string name="category_dairy">Dairy</string>
    <string name="category_drink">Drinks</string>
    <string name="category_snack">Snacks</string>
    <string name="category_staple">Staples</string>
    <string name="category_frozen">Frozen</string>
    <string name="category_condiment">Condiments</string>

    <!-- History -->
    <string name="empty_history">No deleted items</string>
    <string name="restore_title">Restore item</string>
    <string name="restore_confirm">Restore “%1$s”? It will return to its expiry bucket in the main list.</string>
    <string name="deleted_at">Deleted %1$s</string>
    <string name="restored_snackbar">Restored “%1$s”</string>

    <!-- Errors -->
    <string name="error_delete_failed">Delete failed, please retry</string>
    <string name="error_restore_failed">Restore failed, please retry</string>
    <string name="error_recover_failed">Recover failed, please retry</string>
    <string name="error_save_failed">Save failed, please retry</string>

    <!-- Permission banners -->
    <string name="banner_notifications_off">Notifications are off; expiry reminders will not arrive</string>
    <string name="banner_exact_alarm_off">Exact alarms are off; reminder times may drift</string>
    <string name="banner_action_go">Turn on</string>

    <!-- Logs -->
    <string name="log_title">Logs %1$s</string>
    <string name="pick_date">Pick date</string>
    <string name="empty_logs">No logs</string>

    <!-- Settings -->
    <string name="settings_theme">Theme</string>
    <string name="settings_theme_light">Light</string>
    <string name="settings_theme_dark">Dark</string>
    <string name="settings_language">Language</string>
    <string name="language_follow_system">Follow system</string>
    <string name="language_name_zh_cn">简体中文</string>
    <string name="language_name_zh_tw">繁體中文</string>
    <string name="language_name_en">English</string>

    <!-- About -->
    <string name="version">Version %1$s (%2$d)</string>
    <string name="about_tagline">Track food shelf life and cut food waste.</string>

    <!-- Update -->
    <string name="update_hint">New version %1$s found</string>
    <string name="update_found_title">New version %1$s found</string>
    <string name="update_download">Download update</string>
    <string name="update_downloading">Downloading…</string>
    <string name="update_downloading_percent">Downloading %1$d%%</string>
    <string name="update_install">Install</string>
    <string name="update_already_latest">Already up to date</string>
    <string name="update_check_failed">Update check failed, please try later</string>
    <string name="update_checksum_failed">APK checksum failed</string>
    <string name="update_download_failed">Download failed: %1$s</string>

    <!-- Notifications -->
    <string name="notification_channel">Expiry reminders</string>
    <string name="notification_title">食刻 FreshMate</string>
    <string name="notification_body">“%1$s” expires in %2$s</string>
</resources>
```

- [ ] **Step 2: values-zh-rTW/strings.xml（全量繁中）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 通用 -->
    <string name="app_name">食刻 FreshMate</string>
    <string name="back">返回</string>
    <string name="ok">確定</string>
    <string name="cancel">取消</string>
    <string name="save">儲存</string>
    <string name="delete">刪除</string>
    <string name="restore">還原</string>
    <string name="undo">撤銷</string>
    <string name="history">歷史</string>
    <string name="settings">設定</string>
    <string name="add">新增</string>
    <string name="about">關於</string>
    <string name="view_logs">查看日誌</string>
    <string name="check_update">檢查更新</string>
    <string name="view">查看</string>

    <!-- 主列表 -->
    <string name="empty_list">暫無食品，點 + 新增</string>
    <string name="pinned_header">本次新增</string>
    <string name="disperse_into_buckets">散入各桶</string>
    <string name="deleted_snackbar">已刪除「%1$s」</string>
    <string name="near_expiry_title">此食品即將過期</string>
    <string name="near_expiry_text">「%1$s」已有 %2$d 個提醒時點過去，剩餘提醒時點 %3$d 個。確認儲存嗎？</string>
    <string name="quantity_label">數量：%1$s</string>

    <!-- 表單 -->
    <string name="field_name">食品名稱 *</string>
    <string name="error_name_required">請輸入名稱</string>
    <string name="label_category">分類</string>
    <string name="field_production_date">生產日期</string>
    <string name="hint_production_date">不填則按輸入日起算</string>
    <string name="clear_production_date">清除生產日期</string>
    <string name="field_shelf_life">保存期限 *</string>
    <string name="error_shelf_life">請輸入大於 0 的數字</string>
    <string name="field_quantity">數量（可選，如 2 / 500g）</string>
    <string name="expiry_preview_remaining">還有 %1$s 到期</string>
    <string name="expiry_preview_expired">已過期 %1$s，儲存後將不再提醒</string>
    <string name="quick_shelf_days">%1$d天</string>
    <string name="quick_shelf_months">%1$d個月</string>
    <string name="quick_shelf_years">%1$d年</string>
    <string name="unit_day">天</string>
    <string name="unit_week">週</string>
    <string name="unit_month">月</string>
    <string name="unit_year">年</string>
    <string name="placeholder_voice">語音辨識即將上線</string>
    <string name="placeholder_image">圖片辨識即將上線</string>
    <string name="recording">錄音中…（佔位，再按一次結束）</string>
    <string name="extra_voice_hint">長按說話</string>
    <string name="extra_image_hint">選擇圖片</string>
    <string name="extra_with_long_press">%1$s（長按）</string>
    <string name="input_manual">手動輸入</string>
    <string name="input_voice">語音輸入</string>
    <string name="input_image">圖片輸入</string>
    <string name="discard_back">放棄返回</string>
    <string name="stash_and_continue">暫存並繼續</string>

    <!-- 時長與狀態 -->
    <string name="duration_days">%1$d 天</string>
    <string name="duration_hours">%1$d 小時</string>
    <string name="duration_half_hour">30 分鐘</string>
    <string name="duration_under_half_hour">不足 30 分鐘</string>
    <string name="status_expired">已過期</string>
    <string name="status_due_1d">1 天內到期</string>
    <string name="status_due_3d">3 天內到期</string>
    <string name="status_due_7d">7 天內到期</string>
    <string name="status_due_14d">14 天內到期</string>
    <string name="status_safe">更久到期</string>

    <!-- 分類 -->
    <string name="category_fruits_veg">果蔬</string>
    <string name="category_meat_egg">肉蛋</string>
    <string name="category_dairy">乳品</string>
    <string name="category_drink">飲料</string>
    <string name="category_snack">零食</string>
    <string name="category_staple">主食</string>
    <string name="category_frozen">冷凍</string>
    <string name="category_condiment">調味</string>

    <!-- 歷史 -->
    <string name="empty_history">暫無已刪除條目</string>
    <string name="restore_title">還原條目</string>
    <string name="restore_confirm">把「%1$s」還原嗎？還原後將回到主列表對應的過期時間桶。</string>
    <string name="deleted_at">刪除於 %1$s</string>
    <string name="restored_snackbar">已還原「%1$s」</string>

    <!-- 錯誤 -->
    <string name="error_delete_failed">刪除失敗，請重試</string>
    <string name="error_restore_failed">還原失敗，請重試</string>
    <string name="error_recover_failed">恢復失敗，請重試</string>
    <string name="error_save_failed">儲存失敗，請重試</string>

    <!-- 權限橫幅 -->
    <string name="banner_notifications_off">通知未開啟，將收不到過期提醒</string>
    <string name="banner_exact_alarm_off">精確提醒未開啟，提醒時間可能偏差</string>
    <string name="banner_action_go">去開啟</string>

    <!-- 日誌 -->
    <string name="log_title">日誌 %1$s</string>
    <string name="pick_date">選擇日期</string>
    <string name="empty_logs">暫無日誌</string>

    <!-- 設定 -->
    <string name="settings_theme">主題</string>
    <string name="settings_theme_light">淺色</string>
    <string name="settings_theme_dark">深色</string>
    <string name="settings_language">語言</string>
    <string name="language_follow_system">跟隨系統</string>
    <string name="language_name_zh_cn">简体中文</string>
    <string name="language_name_zh_tw">繁體中文</string>
    <string name="language_name_en">English</string>

    <!-- 關於 -->
    <string name="version">版本 %1$s (%2$d)</string>
    <string name="about_tagline">記錄食品保存期限，臨期提醒不浪費。</string>

    <!-- 更新 -->
    <string name="update_hint">發現新版本 %1$s</string>
    <string name="update_found_title">發現新版本 %1$s</string>
    <string name="update_download">下載更新</string>
    <string name="update_downloading">下載中…</string>
    <string name="update_downloading_percent">下載中 %1$d%%</string>
    <string name="update_install">安裝</string>
    <string name="update_already_latest">已是最新版本</string>
    <string name="update_check_failed">檢查更新失敗，請稍後重試</string>
    <string name="update_checksum_failed">安裝包校驗失敗</string>
    <string name="update_download_failed">下載失敗：%1$s</string>

    <!-- 通知 -->
    <string name="notification_channel">過期提醒</string>
    <string name="notification_title">食刻 FreshMate</string>
    <string name="notification_body">「%1$s」還有 %2$s 到期</string>
</resources>
```

- [ ] **Step 3: 资源完整性校验**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew lintDebug`
Expected: 无 `MissingTranslation` 错误（三个文件键集一致；`app_name`/`language_name_*`/`notification_title` 故意在三个目录重复定义）。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values-en app/src/main/res/values-zh-rTW app/src/main/res/values/strings.xml
git commit -m "feat(i18n): 英文与繁体中文全量翻译"
```

---

### Task 9: 全量验证与收尾

**Files:** 无新改动（验证任务）

- [ ] **Step 1: 全量构建**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleDebug lintDebug`
Expected: 全绿。

- [ ] **Step 2: 硬编码残留扫描**

Run: `grep -rn '"[^"]*[一-鿿][^"]*"' app/src/main/java --include="*.kt" | grep -v '//' | grep -vE 'Timber\.(d|i|w|e)'`
Expected: 只剩 require 抛错文案（`itemId 超出`、`apkUrl`、`空响应体`——开发者面向）。

- [ ] **Step 3: 装机走查清单（交用户执行）**

1. 设置页五项顺序：主题/语言/查看日志/检查更新/关于。
2. 主题对话框仅浅色/深色两项；切深色即时生效（六档色板为深色版）；杀进程重启保持。
3. 未手动选择时主题 trailing 显示系统当前模式；改系统深浅设置后回 app 值跟随。
4. 语言对话框四项，语言名恒为母语显示；切 English 后**全 app**（主列表/表单/历史/设置/关于/日志/对话框）变英文；切繁中同理；「跟随系统」恢复系统语言。
5. API 33+ 系统设置 → 应用 → FreshMate 出现「应用语言」且与 app 内选择同步。
6. 通知（造一条 1 天内到期条目等提醒触发）标题/正文为当前语言。
7. 带参数文案：删除 Snackbar「已删除 "xx"」、临近过期对话框数字、还原对话框、关于页版本号。
8. 简体中文为兜底：系统语言设为日语 + app 跟随系统 → 显示简体中文。

- [ ] **Step 4: 四 skill 重审（按惯例）**

`mobile-android-design` + `styles` 对 `git diff main..HEAD` 重审（`edge-to-edge`/`android-intent-security` 本次改动面不涉及，可略）；发现项记入 `docs/reviews/2026-08-21-skill-audit-settings-i18n.md` 并按用户选择修复。

- [ ] **Step 5: Commit（如有审查修复）+ 汇报**

走查与审查完成后汇报，走 finishing-a-development-branch 流程合并。

---

## 自审记录

- **Spec 覆盖**：§1 UI（Task 4）、§2 主题机制（Task 2/3）、§3 语言机制（Task 4/8 locales_config）、§4 抽取清单（Task 5/6/7 + Task 1 资源）、§5 范围一致、§6 测试（Task 2/4 单测 + Task 9 验证链）——全覆盖；`view`（Snackbar 查看按钮）是 spec 未列但代码存在的文案，Task 7 补键并在 Task 8 同步。
- **占位符**：Task 4 Step 10 的「检查更新/关于 item 原样搬移」给出了明确搬移来源与替换点，非占位；Task 6/7 为逐文件 old→new 替换表，均含代码。
- **类型一致性**：`ThemeMode`（Task 2 定义，Task 3/4 消费）、`UiText(id, args)`（Task 1 定义，Task 7 生产/消费）、`labelRes`/`menuLabelRes`/`formatRemaining(res, …)`（Task 5 定义，Task 6 消费）签名一致。
- **已知执行风险**：Task 5 改签名后 Task 6 完成前不可编译——两任务连续执行、合并提交；Task 9 Step 2 的残留 grep 兜底查漏。
