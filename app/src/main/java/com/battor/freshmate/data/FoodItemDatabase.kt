package com.battor.freshmate.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate
import java.time.LocalDateTime

class Converters {
    @TypeConverter fun localDateToString(v: LocalDate?): String? = v?.toString()
    @TypeConverter fun stringToLocalDate(v: String?): LocalDate? = v?.let(LocalDate::parse)
    @TypeConverter fun localDateTimeToString(v: LocalDateTime?): String? = v?.toString()
    @TypeConverter fun stringToLocalDateTime(v: String?): LocalDateTime? =
        v?.let(LocalDateTime::parse)
}

@Database(entities = [FoodItem::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class FoodItemDatabase : RoomDatabase() {
    abstract fun foodItemDao(): FoodItemDao

    companion object {
        @Volatile private var instance: FoodItemDatabase? = null

        /** deleted_at 为 TEXT：TypeConverter 把 LocalDateTime 存为字符串，迁移列类型须与其一致。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_items ADD COLUMN deleted_at TEXT")
            }
        }

        fun get(context: Context): FoodItemDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FoodItemDatabase::class.java,
                    "freshmate.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
