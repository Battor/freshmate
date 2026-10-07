package com.battor.freshmate.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battor.freshmate.data.DEFAULT_DIGEST_TIMES
import com.battor.freshmate.data.PushMode
import com.battor.freshmate.data.SettingsRepository
import com.battor.freshmate.data.ThemeMode
import com.battor.freshmate.notification.DigestScheduling
import com.battor.freshmate.notification.ReminderScheduling
import java.time.LocalTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val digestScheduler: DigestScheduling,
    private val reminderScheduler: ReminderScheduling,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> =
        repository.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val pushMode: StateFlow<PushMode> =
        repository.pushMode.stateIn(viewModelScope, SharingStarted.Eagerly, PushMode.DIGEST)

    val digestTimes: StateFlow<List<LocalTime>> =
        repository.digestTimes.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_DIGEST_TIMES)

    val startupUpdateCheck: StateFlow<Boolean> =
        repository.startupUpdateCheck.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setStartupUpdateCheck(enabled: Boolean) {
        viewModelScope.launch { repository.setStartupUpdateCheck(enabled) }
    }

    /** 切统一：装摘要闹钟（物品闹钟不取消——receiver 闸门兜住）；切逐个：清摘要 + 还原语义重排。 */
    fun setPushMode(mode: PushMode) {
        viewModelScope.launch {
            repository.setPushMode(mode)
            when (mode) {
                PushMode.DIGEST -> digestScheduler.arm(repository.digestTimes.first())
                PushMode.INDIVIDUAL -> {
                    digestScheduler.cancelAll()
                    reminderScheduler.rescheduleAllAsRestored()
                }
            }
        }
    }

    /** 时间入库；仅统一模式下立即重装（逐个模式切回来时 setPushMode 会装）。 */
    fun setDigestTimes(times: List<LocalTime>) {
        viewModelScope.launch {
            repository.setDigestTimes(times)
            if (repository.pushMode.first() == PushMode.DIGEST) digestScheduler.arm(times)
        }
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
