package com.battor.freshmate.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FoodItemDatabase::class.java,
    )

    @Test fun `迁移1到2老数据deletedAt为null`() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL(
                """INSERT INTO food_items (name, category, production_date, shelf_life_days, quantity, created_at)
                   VALUES ('牛奶', 'DAIRY', NULL, 7, NULL, '2026-08-15T10:00:00')""",
            )
            close()
        }
        helper.runMigrationsAndValidate(DB_NAME, 2, true, FoodItemDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT name, deleted_at FROM food_items").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("牛奶", c.getString(0))
                assertNull(c.getString(1))
            }
        }
    }

    companion object {
        private const val DB_NAME = "migration-test.db"
    }
}
