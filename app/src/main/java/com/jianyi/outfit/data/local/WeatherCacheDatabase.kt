package com.jianyi.outfit.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jianyi.outfit.data.local.dao.WeatherCacheDao
import com.jianyi.outfit.data.local.entity.WeatherCacheEntity

/**
 * 天气缓存独立数据库（weather_cache.db）。
 * 与业务库分离，便于通过 backup_rules / data_extraction_rules
 * 按文件排除出云备份与设备迁移（缓存为 30 分钟级临时数据，无备份价值）。
 */
@Database(
    entities = [WeatherCacheEntity::class],
    version = 1,
    exportSchema = true
)
abstract class WeatherCacheDatabase : RoomDatabase() {

    abstract fun weatherCacheDao(): WeatherCacheDao

    companion object {
        /** 构建缓存库单例（调用方持有 Application 级引用） */
        fun build(context: Context): WeatherCacheDatabase =
            Room.databaseBuilder(context, WeatherCacheDatabase::class.java, "weather_cache.db")
                .build()
    }
}
