package com.battor.freshmate.ui.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battor.freshmate.data.DEFAULT_DIGEST_TIMES
import com.battor.freshmate.data.FoodRepository
import com.battor.freshmate.data.PushMode
import com.battor.freshmate.data.SettingsRepository
import com.battor.freshmate.util.DigestTiers
import com.battor.freshmate.util.digestTiers
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 测试页状态。
 * 待通知列表 = 活跃条目 reminderTimes 快照中 > now 的时点展平升序（逐个模式的闹钟队列可视化；
 * AlarmManager 无法枚举已排闹钟，正常路径下快照时点 = 已排闹钟）。
 * 统一模式下改展示实时摘要预览（digestPreview = 此刻触发会推的两档内容）。
 * now 取进页面时刻的快照（spec：不做周期刷新，YAGNI）。
 */
class TestViewModel(
    repository: FoodRepository,
    settings: SettingsRepository,
    nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {

    data class PendingReminder(val time: LocalDateTime, val itemName: String)

    data class UiState(
        val now: LocalDateTime = LocalDateTime.MIN,
        val pending: List<PendingReminder> = emptyList(),
        val pushMode: PushMode = PushMode.DIGEST,
        val digestTimes: List<LocalTime> = DEFAULT_DIGEST_TIMES,
        /** 统一模式实时摘要预览：此刻触发 DailyDigestReceiver 会推的两档内容。 */
        val digestPreview: DigestTiers = DigestTiers(emptyList(), emptyList()),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    init {
        val now = nowProvider()
        viewModelScope.launch {
            repository.observeAll().collect { items ->
                _uiState.update {
                    it.copy(
                        now = now,
                        pending = items
                            .flatMap { item ->
                                item.reminderTimes
                                    .filter { t -> t > now }
                                    .map { t -> PendingReminder(t, item.name) }
                            }
                            .sortedBy { it.time },
                        digestPreview = digestTiers(items, now),
                    )
                }
            }
        }
        viewModelScope.launch {
            settings.pushMode.collect { mode -> _uiState.update { it.copy(pushMode = mode) } }
        }
        viewModelScope.launch {
            settings.digestTimes.collect { times -> _uiState.update { it.copy(digestTimes = times) } }
        }
    }
}
