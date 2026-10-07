package com.battor.freshmate.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 主题偏好：SYSTEM=跟随系统（默认），LIGHT/DARK=用户手动固定，无回归 SYSTEM 的入口（需求-4 用户确认）。 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    /** 解析为是否深色主题（Activity 接线与设置页展示共用，避免两处 when 漂移）。 */
    fun resolvesDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/** 推送方式：DIGEST=统一推送（默认），INDIVIDUAL=逐个推送（现状行为）。 */
enum class PushMode { DIGEST, INDIVIDUAL }

/** 统一推送默认时间点（spec：每天 16:30）。 */
val DEFAULT_DIGEST_TIMES = listOf(LocalTime.of(16, 30))

/** 解析容错：未知/损坏值回落 DIGEST（与 ThemeMode 同模式）。 */
internal fun parsePushMode(raw: String?): PushMode =
    raw?.let { r -> PushMode.entries.firstOrNull { it.name == r } } ?: PushMode.DIGEST

/** 解析容错：任一段损坏则整串作废回落默认——宁可用默认也不用半份时间表。 */
internal fun parseDigestTimes(raw: String?): List<LocalTime> =
    raw?.split(',')
        ?.map { part -> runCatching { LocalTime.parse(part) }.getOrNull() }
        ?.takeIf { parts -> parts.isNotEmpty() && parts.all { it != null } }
        ?.mapNotNull { it }
        ?: DEFAULT_DIGEST_TIMES

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    /** 推送方式（统一/逐个），默认统一。 */
    val pushMode: Flow<PushMode>
    suspend fun setPushMode(mode: PushMode)

    /** 统一推送时间点（最多 2 个），默认 [DEFAULT_DIGEST_TIMES]。 */
    val digestTimes: Flow<List<LocalTime>>
    suspend fun setDigestTimes(times: List<LocalTime>)

    /** 新手引导是否已完成/跳过（首启自动弹一次的闸门）。 */
    val onboardingCompleted: Flow<Boolean>
    suspend fun setOnboardingCompleted()

    /** 启动时静默检查更新（F-Droid 审核：连开发者服务器属联网行为，默认关、用户主动开启）。 */
    val startupUpdateCheck: Flow<Boolean>
    suspend fun setStartupUpdateCheck(enabled: Boolean)
}

class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {
    override val themeMode: Flow<ThemeMode> = context.settingsDataStore.data
        // 未知/损坏值回落 SYSTEM，避免启动循环崩溃
        .map { prefs -> prefs[Key]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Key] = mode.name }
    }

    override val pushMode: Flow<PushMode> =
        context.settingsDataStore.data.map { prefs -> parsePushMode(prefs[PushModeKey]) }

    override suspend fun setPushMode(mode: PushMode) {
        context.settingsDataStore.edit { it[PushModeKey] = mode.name }
    }

    override val digestTimes: Flow<List<LocalTime>> =
        context.settingsDataStore.data.map { prefs -> parseDigestTimes(prefs[DigestTimesKey]) }

    override suspend fun setDigestTimes(times: List<LocalTime>) {
        context.settingsDataStore.edit { it[DigestTimesKey] = times.joinToString(",") }
    }

    override val onboardingCompleted: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[OnboardingKey] ?: false }

    override suspend fun setOnboardingCompleted() {
        context.settingsDataStore.edit { it[OnboardingKey] = true }
    }

    override val startupUpdateCheck: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[StartupUpdateCheckKey] ?: false }

    override suspend fun setStartupUpdateCheck(enabled: Boolean) {
        context.settingsDataStore.edit { it[StartupUpdateCheckKey] = enabled }
    }

    private companion object {
        val Key = stringPreferencesKey("theme_mode")
        val PushModeKey = stringPreferencesKey("push_mode")
        val DigestTimesKey = stringPreferencesKey("digest_times")
        val OnboardingKey = booleanPreferencesKey("onboarding_completed")
        val StartupUpdateCheckKey = booleanPreferencesKey("startup_update_check")
    }
}
