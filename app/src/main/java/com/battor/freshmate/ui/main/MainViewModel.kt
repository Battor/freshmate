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

    /** 批量添加会话：同会话内新增条目共享 createdAt，列表中以「本次添加」区块展示（2026-08-15 验收反馈）。 */
    data class BatchState(val createdAt: LocalDateTime)

    data class UiState(
        val items: List<FoodItem> = emptyList(),
        val groups: List<FoodItemGroup> = emptyList(),
        val editing: EditingState? = null,
        val batch: BatchState? = null,
        val pendingSave: PendingSave? = null,
        val requestNotificationPermission: Boolean = false,
    ) {
        val isEditing: Boolean get() = editing != null
        val isBatchForm: Boolean get() = editing != null && editing.editingItemId == null

        /** 本次批量会话已暂存（入库）的条目。 */
        val batchItems: List<FoodItem>
            get() = batch?.let { b -> items.filter { it.createdAt == b.createdAt } } ?: emptyList()
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

    fun startNew(method: InputMethodId) {
        _uiState.update {
            // 无会话则开启新批量会话；表单 createdAt 用会话时刻，保证同批条目精确同组
            val batch = it.batch ?: BatchState(nowProvider())
            it.copy(
                batch = batch,
                editing = EditingState(inputMethod = method, createdAt = batch.createdAt),
            )
        }
    }

    /** 批量模式中关闭表单、退回输入方式选择，会话继续（+ 菜单「完成」可用）。 */
    fun backToMethodSelection() {
        if (saving) return
        _uiState.update { it.copy(editing = null) }
    }

    /** 结束批量会话（「完成」）；已入库条目保留为普通条目。 */
    fun finishBatch() {
        if (saving) return
        _uiState.update { it.copy(editing = null, batch = null) }
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

    /** 放弃：丢弃未提交的表单并结束批量会话（已入库条目保留为普通条目）。 */
    fun discard() {
        if (saving) return // 保存进行中不允许放弃，避免与落库竞争
        _uiState.update { it.copy(editing = null, batch = null, pendingSave = null) }
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
                // 新条目暂存后表单清空继续（批量模式）；编辑已有条目仍是保存即退出
                val editing = if (editing.editingItemId == null) clearedForm(editing) else null
                it.copy(editing = editing)
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
