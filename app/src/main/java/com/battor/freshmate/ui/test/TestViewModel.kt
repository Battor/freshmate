package com.battor.freshmate.ui.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battor.freshmate.data.FoodRepository
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 测试页状态：待通知列表 = 活跃条目 reminderTimes 快照中 > now 的时点展平升序。
 * AlarmManager 无法枚举已排闹钟；正常路径下快照时点 = 已排闹钟，本列表即闹钟队列的可视化。
 * now 取进页面时刻的快照（spec：不做周期刷新，YAGNI）。
 */
class TestViewModel(
    repository: FoodRepository,
    nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {

    data class PendingReminder(val time: LocalDateTime, val itemName: String)

    data class UiState(
        val now: LocalDateTime = LocalDateTime.MIN,
        val pending: List<PendingReminder> = emptyList(),
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
                    )
                }
            }
        }
    }
}
