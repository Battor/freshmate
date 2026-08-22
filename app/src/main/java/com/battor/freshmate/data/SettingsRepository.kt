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
