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
