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

    /**
     * 每次调用现读：locale 切换会重建 Activity，但 NavBackStackEntry 的 ViewModelStore
     * 跨重建保留（本 VM 不随之重建），缓存 val 会陈旧。
     */
    fun currentLanguage(): AppLanguage = AppLanguage.fromLocales(AppCompatDelegate.getApplicationLocales())

    fun setLanguage(language: AppLanguage) {
        AppLanguage.applyToApp(language)
    }
}
