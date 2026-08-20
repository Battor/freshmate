package com.battor.freshmate.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.data.FoodRepository
import com.battor.freshmate.notification.ReminderScheduling
import com.battor.freshmate.notification.scheduleOrCancel
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
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
        /** 页面级统一时刻：卡片状态文本共用（卡片不再各自 remember）。 */
        val now: LocalDateTime = LocalDateTime.MIN,
        val groups: List<DeletedGroup> = emptyList(),
        val restoring: FoodItem? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 一次性提示（Snackbar），展示后 UI 调 clearMessage()。 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    init {
        viewModelScope.launch {
            repository.observeDeleted().collect { deleted ->
                _uiState.update {
                    // deletedAt 非 null 由 DAO 过滤保证，违反即快速失败
                    it.copy(
                        now = nowProvider(),
                        groups = deleted.groupBy { d -> deletedAtKey(requireNotNull(d.deletedAt)) }
                            .map { (at, list) -> DeletedGroup(at, list) }
                            .sortedByDescending { g -> g.deletedAt },
                    )
                }
            }
        }
    }

    /** 右滑触发：随时可还原（需求-3：组模型取消，无门禁），进入待确认状态。 */
    fun requestRestore(item: FoodItem) {
        _uiState.update { it.copy(restoring = item) }
    }

    fun cancelRestore() {
        _uiState.update { it.copy(restoring = null) }
    }

    fun confirmRestore() {
        val item = _uiState.value.restoring ?: return
        _uiState.update { it.copy(restoring = null) }
        viewModelScope.launch {
            try {
                repository.restore(item)
                scheduler.scheduleOrCancel(item, nowProvider())
                _message.value = "已还原「${item.name}」"
            } catch (e: CancellationException) {
                throw e // 取消照常上抛，不按失败处理
            } catch (e: Exception) {
                _message.value = "还原失败，请重试"
            }
        }
    }
}

/** 删除时刻截断到分钟：同分钟删除的条目归为一组。 */
private fun deletedAtKey(time: LocalDateTime): LocalDateTime = time.truncatedTo(ChronoUnit.MINUTES)
