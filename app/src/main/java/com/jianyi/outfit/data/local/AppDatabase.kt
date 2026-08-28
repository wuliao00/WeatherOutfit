package com.jianyi.outfit.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jianyi.outfit.data.local.dao.CityDao
import com.jianyi.outfit.data.local.dao.OutfitTemplateDao
import com.jianyi.outfit.data.local.entity.CityEntity
import com.jianyi.outfit.data.local.entity.OutfitTemplateEntity

/**
 * Room 业务数据库：历史城市、穿搭模板。
 * 天气缓存已拆分至独立的 WeatherCacheDatabase（便于备份排除）。
 */
@Database(
    entities = [
        CityEntity::class,
        OutfitTemplateEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cityDao(): CityDao
    abstract fun outfitTemplateDao(): OutfitTemplateDao

    companion object {
        /**
         * v1 → v2：天气缓存表迁移至独立的 weather_cache.db，业务库不再保留该表。
         * 缓存数据为临时数据，直接丢弃即可。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS weather_cache")
            }
        }

        /** 构建数据库单例（调用方持有 Application 级引用） */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "jianyi.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
