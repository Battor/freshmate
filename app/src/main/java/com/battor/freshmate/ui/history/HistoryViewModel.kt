package com.battor.freshmate.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.data.FoodRepository
import com.battor.freshmate.notification.ReminderScheduling
import com.battor.freshmate.notification.scheduleOrCancel
import com.battor.freshmate.ui.main.groupKey
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 已删除条目按删除时刻（截断到分钟）分组，新→旧。 */
data class DeletedGroup(val deletedAt: LocalDateTime, val items: List<FoodItem>)

class HistoryViewModel(
    private val repository: FoodRepository,
    private val scheduler: ReminderScheduling,
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {

    data class UiState(
        val groups: List<DeletedGroup> = emptyList(),
        /** 主列表活跃条目的组键集合——还原条件（设计文档 §7.2：仅组活跃时可还原）。 */
        val activeGroupKeys: Set<LocalDateTime> = emptySet(),
        val restoring: FoodItem? = null,
    ) {
        fun isRestorable(item: FoodItem): Boolean = groupKey(item.createdAt) in activeGroupKeys
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 一次性提示（Snackbar），展示后 UI 调 clearMessage()。 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    init {
        viewModelScope.launch {
            repository.observeAll().collect { active ->
                _uiState.update {
                    it.copy(activeGroupKeys = active.map { i -> groupKey(i.createdAt) }.toSet())
                }
            }
        }
        viewModelScope.launch {
            repository.observeDeleted().collect { deleted ->
                _uiState.update {
                    // deletedAt 非 null 由 DAO 过滤保证，违反即快速失败
                    it.copy(groups = deleted.groupBy { d -> groupKey(requireNotNull(d.deletedAt)) }
                        .map { (at, list) -> DeletedGroup(at, list) }
                        .sortedByDescending { g -> g.deletedAt })
                }
            }
        }
    }

    /** 右滑触发：仅组活跃的条目可进入待确认状态。 */
    fun requestRestore(item: FoodItem) {
        if (uiState.value.isRestorable(item)) _uiState.update { it.copy(restoring = item) }
    }

    fun cancelRestore() {
        _uiState.update { it.copy(restoring = null) }
    }

    fun confirmRestore() {
        val item = _uiState.value.restoring ?: return
        if (!uiState.value.isRestorable(item)) {
            _uiState.update { it.copy(restoring = null) }
            return
        }
        _uiState.update { it.copy(restoring = null) }
        viewModelScope.launch {
            try {
                repository.restore(item)
                scheduler.scheduleOrCancel(item, nowProvider())
                _message.value = "已还原「${item.name}」到原组"
            } catch (e: CancellationException) {
                throw e // 取消照常上抛，不按失败处理
            } catch (e: Exception) {
                _message.value = "还原失败，请重试"
            }
        }
    }
}
