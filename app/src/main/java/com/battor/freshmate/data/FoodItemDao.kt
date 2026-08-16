package com.battor.freshmate.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodItemDao {
    @Query("SELECT * FROM food_items WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun observeAll(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun observeDeleted(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE deleted_at IS NULL")
    suspend fun getAllOnce(): List<FoodItem>

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun getById(id: Long): FoodItem?

    @Insert
    suspend fun insert(item: FoodItem): Long

    @Update
    suspend fun update(item: FoodItem)

    /** Task 6 移除：Repository 现仍调用硬删除。 */
    @Delete
    suspend fun delete(item: FoodItem)

    /** 软删除/还原的唯一写入口：deletedAt 传 null 即还原。 */
    @Query("UPDATE food_items SET deleted_at = :deletedAt WHERE id = :id")
    suspend fun setDeletedAt(id: Long, deletedAt: LocalDateTime?)
}
