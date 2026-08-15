package com.battor.freshmate.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battor.freshmate.data.Category
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.data.FoodRepository
import com.battor.freshmate.inputmethod.InputMethodId
import com.battor.freshmate.notification.ReminderScheduling
import com.battor.freshmate.util.ShelfLifeUnit
import com.battor.freshmate.util.expiryDateTime
import com.battor.freshmate.util.reminderTimes
import com.battor.freshmate.util.shelfLifeToDays
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 列表分组：同一录入时刻（精确到分钟）的条目归为一组，按录入时间倒序。 */
data class FoodItemGroup(val createdAt: LocalDateTime, val items: List<FoodItem>)

fun groupItems(items: List<FoodItem>): List<FoodItemGroup> =
    items.groupBy { it.createdAt.truncatedTo(ChronoUnit.MINUTES) }
        .map { (minute, list) ->
            FoodItemGroup(minute, list.sortedByDescending { it.createdAt })
        }
        .sortedByDescending { it.createdAt }

class MainViewModel(
    private val repository: FoodRepository,
    private val scheduler: ReminderScheduling,
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {

    data class EditingState(
        val inputMethod: InputMethodId = InputMethodId.MANUAL,
        val editingItemId: Long? = null, // null = 新建
        val name: String = "",
        val category: Category = Category.FRUITS_VEG,
        val productionDate: LocalDate? = null,
        val shelfLifeValue: String = "",
        val shelfLifeUnit: ShelfLifeUnit = ShelfLifeUnit.DAY,
        val quantity: String = "",
        val createdAt: LocalDateTime = LocalDateTime.MIN,
        val nameError: Boolean = false,
        val shelfLifeError: Boolean = false,
    )

    /** 有提醒时点已过、等待用户确认的保存（设计文档 §6）。 */
    data class PendingSave(val editing: EditingState, val days: Int, val skippedReminders: Int)

    data class UiState(
        val items: List<FoodItem> = emptyList(),
        val groups: List<FoodItemGroup> = emptyList(),
        val editing: EditingState? = null,
        /** 最近一次暂存成功的目标组时刻（「本次添加」标记归属），暂存成功才转移。 */
        val activeGroup: LocalDateTime? = null,
        val pendingSave: PendingSave? = null,
        val requestNotificationPermission: Boolean = false,
    ) {
        val isEditing: Boolean get() = editing != null
        val isAddForm: Boolean get() = editing != null && editing.editingItemId == null

        /** 「本次添加」组内的条目。 */
        val activeGroupItems: List<FoodItem>
            get() = activeGroup?.let { a -> items.filter { it.createdAt == a } } ?: emptyList()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 一次性错误提示（Snackbar），展示后由 UI 调用 onErrorShown() 清空。 */
    private val _errorEvent = MutableStateFlow<String?>(null)
    val errorEvent: StateFlow<String?> = _errorEvent.asStateFlow()

    fun onErrorShown() {
        _errorEvent.value = null
    }

    private var permissionRequested = false
    private var saving = false

    init {
        viewModelScope.launch {
            repository.observeAll().collect { items ->
                _uiState.update { it.copy(items = items, groups = groupItems(items)) }
            }
        }
    }

    /** + 菜单录入：永远新开一组（新时间戳）。 */
    fun startNew(method: InputMethodId) {
        if (saving) return
        _uiState.update {
            it.copy(editing = EditingState(inputMethod = method, createdAt = nowProvider()))
        }
    }

    /** 点击组框续加：表单挂到该组（复用其录入时刻），新条目归入该组。 */
    fun startAddTo(createdAt: LocalDateTime) {
        if (saving) return
        _uiState.update {
            it.copy(editing = EditingState(inputMethod = InputMethodId.MANUAL, createdAt = createdAt))
        }
    }

    /** ← 放弃当前表单（新增/编辑通用）：关闭表单，「本次添加」标记不动。 */
    fun backToMethodSelection() {
        if (saving) return
        _uiState.update { it.copy(editing = null, pendingSave = null) }
    }

    fun startEdit(item: FoodItem) {
        _uiState.update {
            it.copy(
                editing = EditingState(
                    inputMethod = InputMethodId.MANUAL,
                    editingItemId = item.id,
                    name = item.name,
                    category = item.category,
                    productionDate = item.productionDate,
                    shelfLifeValue = item.shelfLifeDays.toString(),
                    shelfLifeUnit = ShelfLifeUnit.DAY,
                    quantity = item.quantity ?: "",
                    createdAt = item.createdAt,
                ),
            )
        }
    }

    fun updateEditing(transform: (EditingState) -> EditingState) {
        _uiState.update { s -> s.editing?.let { s.copy(editing = transform(it)) } ?: s }
    }

    fun save() {
        val editing = _uiState.value.editing ?: return
        val name = editing.name.trim()
        val days = editing.shelfLifeValue.trim().toIntOrNull()
            ?.let { shelfLifeToDays(it, editing.shelfLifeUnit) }
        if (name.isEmpty()) {
            updateEditing { it.copy(nameError = true) }
            return
        }
        if (days == null || days <= 0) {
            updateEditing { it.copy(shelfLifeError = true) }
            return
        }
        val expiry = expiryDateTime(editing.productionDate, editing.createdAt, days)
        val skipped = reminderTimes(expiry, days).count { it <= nowProvider() }
        if (skipped > 0) {
            _uiState.update {
                it.copy(pendingSave = PendingSave(editing.copy(name = name), days, skipped))
            }
            return
        }
        persist(editing.copy(name = name), days)
    }

    fun confirmPendingSave() {
        val pending = _uiState.value.pendingSave ?: return
        _uiState.update { it.copy(pendingSave = null) }
        persist(pending.editing, pending.days)
    }

    fun cancelPendingSave() {
        _uiState.update { it.copy(pendingSave = null) }
    }

    fun delete(item: FoodItem) {
        viewModelScope.launch {
            try {
                repository.delete(item)
                scheduler.cancel(item.id)
            } catch (e: Exception) {
                _errorEvent.value = "删除失败，请重试"
            }
        }
    }

    /** 恢复指定的被删条目（避免多条删除排队时撤销错对象）。 */
    fun undoDelete(item: FoodItem) {
        viewModelScope.launch {
            try {
                val id = repository.insert(item.copy(id = 0))
                scheduleOrCancel(item.copy(id = id))
            } catch (e: Exception) {
                _errorEvent.value = "恢复失败，请重试"
            }
        }
    }

    fun onPermissionRequested() {
        _uiState.update { it.copy(requestNotificationPermission = false) }
    }

    /** 未过期则排提醒，已过期则取消（保持调度与数据状态对称）。 */
    private fun scheduleOrCancel(item: FoodItem) {
        val expiry = expiryDateTime(item.productionDate, item.createdAt, item.shelfLifeDays)
        if (expiry > nowProvider()) scheduler.schedule(item) else scheduler.cancel(item.id)
    }

    private fun persist(editing: EditingState, days: Int) {
        if (saving) return // 防重复保存（save()/confirmPendingSave() 均经由本方法落库）
        saving = true
        viewModelScope.launch {
            val item = FoodItem(
                id = editing.editingItemId ?: 0,
                name = editing.name.trim(),
                category = editing.category,
                productionDate = editing.productionDate,
                shelfLifeDays = days,
                quantity = editing.quantity.trim().ifEmpty { null },
                createdAt = editing.createdAt,
            )
            val itemId = try {
                if (editing.editingItemId == null) {
                    repository.insert(item)
                } else {
                    repository.update(item)
                    item.id
                }
            } catch (e: Exception) {
                _errorEvent.value = "保存失败，请重试"
                return@launch // 编辑表单保留，等待用户重试
            } finally {
                saving = false
            }
            val saved = item.copy(id = itemId)
            scheduleOrCancel(saved)
            _uiState.update {
                // 新条目暂存后表单清空继续，「本次添加」标记转移到该组；
                // 编辑已有条目仍是保存即退出，标记不动
                if (editing.editingItemId == null) {
                    it.copy(editing = clearedForm(editing), activeGroup = editing.createdAt)
                } else {
                    it.copy(editing = null)
                }
            }
            if (!permissionRequested) {
                permissionRequested = true
                _uiState.update { it.copy(requestNotificationPermission = true) }
            }
        }
    }

    /**
     * 暂存一条后返回的"下一张空白表单"（2026-08-15 用户选定策略：保留输入方式和分类）。
     *
     * 名称/日期/保质期/数量/错误标记必须清空（数据安全）；createdAt 必须沿用会话时刻
     * （否则下一条会脱离「本次添加」分组）。
     */
    private fun clearedForm(current: EditingState): EditingState = EditingState(
        inputMethod = current.inputMethod,
        category = current.category,
        createdAt = current.createdAt,
    )
}
