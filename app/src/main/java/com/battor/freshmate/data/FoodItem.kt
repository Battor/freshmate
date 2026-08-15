package com.battor.freshmate.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "food_items")
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: Category,
    @ColumnInfo(name = "production_date") val productionDate: LocalDate?,
    @ColumnInfo(name = "shelf_life_days") val shelfLifeDays: Int,
    val quantity: String?,
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime,
)
