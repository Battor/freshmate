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
        val category: Category = Category.OTHER,
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
        val pendingSave: PendingSave? = null,
        val requestNotificationPermission: Boolean = false,
    ) {
        val isEditing: Boolean get() = editing != null
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var permissionRequested = false
    private var lastDeleted: FoodItem? = null

    init {
        viewModelScope.launch {
            repository.observeAll().collect { items ->
                _uiState.update { it.copy(items = items, groups = groupItems(items)) }
            }
        }
    }

    fun startNew(method: InputMethodId) {
        _uiState.update {
            it.copy(editing = EditingState(inputMethod = method, createdAt = nowProvider()))
        }
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

    fun discard() {
        _uiState.update { it.copy(editing = null, pendingSave = null) }
    }

    fun delete(item: FoodItem) {
        lastDeleted = item
        viewModelScope.launch {
            repository.delete(item)
            scheduler.cancel(item.id)
        }
    }

    fun undoDelete() {
        val item = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            val id = repository.insert(item.copy(id = 0))
            scheduler.schedule(item.copy(id = id))
        }
    }

    fun onPermissionRequested() {
        _uiState.update { it.copy(requestNotificationPermission = false) }
    }

    private fun persist(editing: EditingState, days: Int) {
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
            val saved = if (editing.editingItemId == null) {
                item.copy(id = repository.insert(item))
            } else {
                repository.update(item)
                item
            }
            val expiry = expiryDateTime(saved.productionDate, saved.createdAt, saved.shelfLifeDays)
            if (expiry > nowProvider()) scheduler.schedule(saved) else scheduler.cancel(saved.id)
            _uiState.update { it.copy(editing = null) }
            if (!permissionRequested) {
                permissionRequested = true
                _uiState.update { it.copy(requestNotificationPermission = true) }
            }
        }
    }
}
