package com.battor.freshmate

import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.data.FoodRepository
import com.battor.freshmate.notification.ReminderScheduling
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeRepository : FoodRepository {
    val items = MutableStateFlow<List<FoodItem>>(emptyList())
    var failNextInsert = false
    private var nextId = 1L

    override fun observeAll(): Flow<List<FoodItem>> = items.map { l -> l.filter { it.deletedAt == null } }
    override fun observeDeleted(): Flow<List<FoodItem>> =
        items.map { l -> l.filter { it.deletedAt != null } }

    override suspend fun insert(item: FoodItem): Long {
        if (failNextInsert) {
            failNextInsert = false
            throw RuntimeException("db error")
        }
        val id = nextId++
        items.value = items.value + item.copy(id = id)
        return id
    }

    override suspend fun update(item: FoodItem) {
        items.value = items.value.map { if (it.id == item.id) item else it }
    }

    override suspend fun softDelete(item: FoodItem, deletedAt: LocalDateTime) {
        items.value = items.value.map { if (it.id == item.id) it.copy(deletedAt = deletedAt) else it }
    }

    override suspend fun restore(item: FoodItem) {
        items.value = items.value.map { if (it.id == item.id) it.copy(deletedAt = null) else it }
    }

    override suspend fun getAll(): List<FoodItem> = items.value.filter { it.deletedAt == null }
}

class FakeScheduler : ReminderScheduling {
    val scheduled = mutableListOf<FoodItem>()
    val cancelled = mutableListOf<Long>()
    override fun schedule(item: FoodItem) { scheduled.add(item) }
    override fun cancel(itemId: Long) { cancelled.add(itemId) }
    override suspend fun rescheduleAll() {}
}
