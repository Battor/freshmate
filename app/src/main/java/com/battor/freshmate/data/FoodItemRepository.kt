package com.battor.freshmate.data

import kotlinx.coroutines.flow.Flow

interface FoodRepository {
    fun observeAll(): Flow<List<FoodItem>>
    suspend fun insert(item: FoodItem): Long
    suspend fun update(item: FoodItem)
    suspend fun delete(item: FoodItem)
    suspend fun getAll(): List<FoodItem>
}

class FoodItemRepository(private val dao: FoodItemDao) : FoodRepository {
    override fun observeAll(): Flow<List<FoodItem>> = dao.observeAll()
    override suspend fun insert(item: FoodItem): Long = dao.insert(item)
    override suspend fun update(item: FoodItem) = dao.update(item)
    override suspend fun delete(item: FoodItem) = dao.delete(item)
    override suspend fun getAll(): List<FoodItem> = dao.getAllOnce()
}
