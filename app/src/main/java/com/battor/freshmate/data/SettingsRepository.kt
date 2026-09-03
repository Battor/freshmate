package com.battor.freshmate.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    /** 新手引导是否已完成/跳过（首启自动弹一次的闸门）。 */
    val onboardingCompleted: Flow<Boolean>
    suspend fun setOnboardingCompleted()
}

class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {
    override val themeMode: Flow<ThemeMode> = context.settingsDataStore.data
        // 未知/损坏值回落 SYSTEM，避免启动循环崩溃
        .map { prefs -> prefs[Key]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Key] = mode.name }
    }

    override val onboardingCompleted: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[OnboardingKey] ?: false }

    override suspend fun setOnboardingCompleted() {
        context.settingsDataStore.edit { it[OnboardingKey] = true }
    }

    private companion object {
        val Key = stringPreferencesKey("theme_mode")
        val OnboardingKey = booleanPreferencesKey("onboarding_completed")
    }
}
