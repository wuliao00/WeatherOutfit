package com.jianyi.outfit.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.jianyi.outfit.data.local.dao.WeatherCacheDao
import com.jianyi.outfit.data.local.entity.WeatherCacheEntity

/**
 * 天气缓存独立数据库（weather_cache.db）。
 * 与业务库分离，便于通过 backup_rules / data_extraction_rules
 * 按文件排除出云备份与设备迁移（缓存为 30 分钟级临时数据，无备份价值）。
 *
 * 开库在平台侧：androidMain 用 Context，iosMain 用 Documents 目录，
 * 文件名与 Android 历史版本逐字一致，老用户的缓存文件原地复用。
 */
@Database(
    entities = [WeatherCacheEntity::class],
    version = 1,
    exportSchema = true
)
@ConstructedBy(WeatherCacheDatabaseConstructor::class)
abstract class WeatherCacheDatabase : RoomDatabase() {

    abstract fun weatherCacheDao(): WeatherCacheDao
}

/** 同 AppDatabaseConstructor：Kotlin/Native 没有反射，构造入口由 KSP 按平台生成 actual */
@Suppress("KotlinNoActualForExpect")
expect object WeatherCacheDatabaseConstructor : RoomDatabaseConstructor<WeatherCacheDatabase> {
    override fun initialize(): WeatherCacheDatabase
}
