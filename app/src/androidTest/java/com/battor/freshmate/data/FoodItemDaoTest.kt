package com.battor.freshmate.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodItemDaoTest {
    private lateinit var db: FoodItemDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FoodItemDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private fun item(name: String) = FoodItem(
        name = name, category = Category.DAIRY, productionDate = null,
        shelfLifeDays = 7, quantity = null,
        createdAt = LocalDateTime.of(2026, 8, 15, 10, 0),
    )

    @Test fun `活跃与已删查询互不重叠`() = runTest {
        val idA = db.foodItemDao().insert(item("牛奶"))
        val idB = db.foodItemDao().insert(item("面包"))
        db.foodItemDao().setDeletedAt(idB, LocalDateTime.of(2026, 8, 16, 9, 0))

        assertEquals(listOf("牛奶"), db.foodItemDao().observeAll().first().map { it.name })
        assertEquals(listOf("面包"), db.foodItemDao().observeDeleted().first().map { it.name })
        assertEquals(listOf("牛奶"), db.foodItemDao().getAllOnce().map { it.name })
    }

    @Test fun `setDeletedAt传null即还原`() = runTest {
        val id = db.foodItemDao().insert(item("牛奶"))
        db.foodItemDao().setDeletedAt(id, LocalDateTime.of(2026, 8, 16, 9, 0))
        db.foodItemDao().setDeletedAt(id, null)
        assertEquals(listOf("牛奶"), db.foodItemDao().observeAll().first().map { it.name })
        assertEquals(0, db.foodItemDao().observeDeleted().first().size)
    }
}
