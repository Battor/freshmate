package com.battor.freshmate.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battor.freshmate.data.Category
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.data.FoodRepository
import com.battor.freshmate.inputmethod.InputMethodId
import com.battor.freshmate.notification.ReminderScheduling
import com.battor.freshmate.notification.scheduleOrCancel
import com.battor.freshmate.R
import com.battor.freshmate.ui.common.UiText
import com.battor.freshmate.util.ExpiryStatus
import com.battor.freshmate.util.ShelfLifeUnit
import com.battor.freshmate.util.computeReminderTimes
import com.battor.freshmate.util.expiryDateTime
import com.battor.freshmate.util.expiryStatus
import com.battor.freshmate.util.mergedReminderTimes
import com.battor.freshmate.util.shelfLifeToDays
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 列表分桶（需求-3）：桶序 = 紧急度（EXPIRED→SAFE），桶内按到期时间升序（最紧急在前）。 */
data class ExpiryBucket(val status: ExpiryStatus, val items: List<FoodItem>)

fun bucketItems(items: List<FoodItem>, now: LocalDateTime): List<ExpiryBucket> {
    val byStatus = items.groupBy {
        expiryStatus(expiryDateTime(it.productionDate, it.createdAt, it.shelfLifeDays), now)
    }
    return ExpiryStatus.entries.mapNotNull { status ->
        byStatus[status]?.let { list ->
            ExpiryBucket(
                status,
                list.sortedBy { expiryDateTime(it.productionDate, it.createdAt, it.shelfLifeDays) },
            )
        }
    }
}

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

    /** 有提醒时点已过、等待用户确认的保存（设计文档 §6；需求-3 改按快照统计）。 */
    data class PendingSave(
        val editing: EditingState,
        val days: Int,
        val skippedReminders: Int,
        val remainingReminders: Int,
    )

    data class UiState(
        val items: List<FoodItem> = emptyList(),
        /** 页面级统一时刻：分桶与卡片状态文本共用（卡片不再各自 remember）。 */
        val now: LocalDateTime = LocalDateTime.MIN,
        /** 本次会话新录入条目 id：驱动「本次添加」置顶区；下拉刷新/冷启动清空后散入各桶。 */
        val sessionItemIds: Set<Long> = emptySet(),
        val editing: EditingState? = null,
        val pendingSave: PendingSave? = null,
        val requestNotificationPermission: Boolean = false,
        /** 「本次添加」置顶区条目（新→旧）。 */
        val pinnedItems: List<FoodItem> = emptyList(),
        /** 过期时间分桶；置顶区条目不重复入桶。 */
        val buckets: List<ExpiryBucket> = emptyList(),
    ) {
        val isEditing: Boolean get() = editing != null
        val isAddForm: Boolean get() = editing != null && editing.editingItemId == null

        /**
         * 正在编辑的目标条目（表单上方展示的卡片）；新增表单/无表单时为 null。
         * 计算属性而非 withDerived 派生态：startEdit/backToMethodSelection 不经 withDerived，存储态会陈旧。
         */
        val editingTarget: FoodItem?
            get() = editing?.editingItemId?.let { id -> items.firstOrNull { it.id == id } }

        /**
         * 表单是否已有内容：新增表单 = 任一字段非空；编辑表单 = 与原条目有差异。
         * 驱动 ✓ 按钮显隐。
         */
        val hasFormContent: Boolean
            get() = editing?.let { e ->
                if (e.editingItemId == null) {
                    e.name.isNotBlank() || e.shelfLifeValue.isNotBlank() ||
                        e.quantity.isNotBlank() || e.productionDate != null
                } else {
                    val item = items.firstOrNull { it.id == e.editingItemId } ?: return@let true
                    e.name != item.name ||
                        e.category != item.category ||
                        e.productionDate != item.productionDate ||
                        e.quantity.trim().ifEmpty { null } != item.quantity ||
                        e.shelfLifeValue.toIntOrNull()
                            ?.let { shelfLifeToDays(it, e.shelfLifeUnit) } != item.shelfLifeDays
                }
            } ?: false
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 一次性错误提示（Snackbar），展示后由 UI 调用 onErrorShown() 清空。 */
    private val _errorEvent = MutableStateFlow<UiText?>(null)
    val errorEvent: StateFlow<UiText?> = _errorEvent.asStateFlow()

    fun onErrorShown() {
        _errorEvent.value = null
    }

    private var permissionRequested = false
    private var saving = false

    /** 派生态随 items/now/sessionItemIds 变化时重算（避免每次重组全量重算）。 */
    private fun withDerived(state: UiState): UiState = state.copy(
        pinnedItems = state.items
            .filter { it.id in state.sessionItemIds }
            .sortedByDescending { it.createdAt },
        buckets = bucketItems(state.items.filter { it.id !in state.sessionItemIds }, state.now),
    )

    init {
        viewModelScope.launch {
            repository.observeAll().collect { items ->
                _uiState.update { withDerived(it.copy(items = items, now = nowProvider())) }
            }
        }
    }

    /** + 菜单录入：新开表单（新时间戳，作保质期无生产日期时的起算点）。 */
    fun startNew(method: InputMethodId) {
        if (saving) return
        _uiState.update {
            it.copy(editing = EditingState(inputMethod = method, createdAt = nowProvider()))
        }
    }

    /** 下拉刷新：本次添加区散入各桶（清空会话集合，纯内存态）。 */
    fun disperseSession() {
        _uiState.update { withDerived(it.copy(sessionItemIds = emptySet())) }
    }

    /** ON_RESUME 时刷新页面时刻：分桶随时间流动（条目跨档后桶迁移，不依赖数据库变化）。 */
    fun refreshNow() {
        _uiState.update { withDerived(it.copy(now = nowProvider())) }
    }

    /** ← 放弃当前表单（新增/编辑通用）：仅关闭表单，「本次添加」置顶区不动。 */
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
        // 只统计"真实错过"的时点：晚于生产开始（expiry−days）且不晚于 now。
        // 早于生产开始的绝对档（如 3 天保质期的 7 天档）对新录入条目无意义，不触发确认弹窗。
        val start = expiry.minusDays(days.toLong())
        val now = nowProvider() // skipped 统计与剩余提醒共用同一时刻
        val skipped = mergedReminderTimes(expiry, days).count { it > start && it <= now }
        if (skipped > 0) {
            _uiState.update {
                it.copy(
                    pendingSave = PendingSave(
                        editing.copy(name = name),
                        days,
                        skippedReminders = skipped,
                        remainingReminders = computeReminderTimes(expiry, days, now).size,
                    ),
                )
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

    /** 软删除：条目移入历史页，取消提醒。 */
    fun delete(item: FoodItem) {
        viewModelScope.launch {
            try {
                repository.softDelete(item, nowProvider())
                scheduler.cancel(item.id)
            } catch (e: CancellationException) {
                throw e // 取消照常上抛，不按失败处理
            } catch (e: Exception) {
                _errorEvent.value = UiText(R.string.error_delete_failed)
            }
        }
    }

    /** 5 秒 Snackbar 撤销 = 立即还原（同一条目同 id，闹钟重排）。 */
    fun undoDelete(item: FoodItem) {
        viewModelScope.launch {
            try {
                repository.restore(item)
                scheduler.scheduleOrCancel(item, nowProvider())
            } catch (e: CancellationException) {
                throw e // 取消照常上抛，不按失败处理
            } catch (e: Exception) {
                _errorEvent.value = UiText(R.string.error_recover_failed)
            }
        }
    }

    fun onPermissionRequested() {
        _uiState.update { it.copy(requestNotificationPermission = false) }
    }

    private fun persist(editing: EditingState, days: Int) {
        if (saving) return // 防重复保存（save()/confirmPendingSave() 均经由本方法落库）
        saving = true
        viewModelScope.launch {
            val expiry = expiryDateTime(editing.productionDate, editing.createdAt, days)
            val item = FoodItem(
                id = editing.editingItemId ?: 0,
                name = editing.name.trim(),
                category = editing.category,
                productionDate = editing.productionDate,
                shelfLifeDays = days,
                quantity = editing.quantity.trim().ifEmpty { null },
                createdAt = editing.createdAt,
                reminderTimes = computeReminderTimes(expiry, days, nowProvider()),
                // 编辑保存回到活跃态（编辑入口只对活跃条目开放）
            )
            val itemId = try {
                if (editing.editingItemId == null) {
                    repository.insert(item)
                } else {
                    repository.update(item)
                    item.id
                }
            } catch (e: CancellationException) {
                throw e // 取消照常上抛，不按失败处理
            } catch (e: Exception) {
                _errorEvent.value = UiText(R.string.error_save_failed)
                return@launch // 编辑表单保留，等待用户重试
            } finally {
                saving = false
            }
            val saved = item.copy(id = itemId)
            scheduler.scheduleOrCancel(saved, nowProvider())
            _uiState.update {
                // 新条目暂存后表单清空继续，并进入「本次添加」置顶区；
                // 编辑已有条目仍是保存即退出，会话集合不动
                if (editing.editingItemId == null) {
                    withDerived(
                        it.copy(editing = clearedForm(editing), sessionItemIds = it.sessionItemIds + itemId),
                    )
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
     * 名称/日期/保质期/数量/错误标记必须清空（数据安全）；createdAt 沿用当前值，
     * 供下一张表单在无生产日期时作保质期起算点。
     */
    private fun clearedForm(current: EditingState): EditingState = EditingState(
        inputMethod = current.inputMethod,
        category = current.category,
        createdAt = current.createdAt,
    )
}
