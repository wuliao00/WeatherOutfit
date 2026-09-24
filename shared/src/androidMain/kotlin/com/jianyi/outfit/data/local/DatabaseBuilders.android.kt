package com.jianyi.outfit.data.local

import android.content.Context
import androidx.room.Room

/**
 * Android 侧开库。
 *
 * 刻意沿用 `Room.databaseBuilder(context, Class, name)` 这个老重载 ——
 * Room 2.7 里它没有标废弃（room-runtime-android 的 `@JvmStatic fun
 * databaseBuilder(Context, Class<T>, String?)`），而新写的 reified 版本
 * 默认工厂走的是 associated object 查找。用老写法的意义是：**这一行与
 * 2.6.1 时代逐字相同**，DB 文件名、目录、迁移都一样，已发布用户的数据
 * 不会因为这次 KMP 化而挪位置或重建。
 */
fun buildAppDatabase(context: Context): AppDatabase =
    Room.databaseBuilder(context, AppDatabase::class.java, "jianyi.db")
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .build()

/** 天气缓存库：独立文件，便于备份规则按文件排除 */
fun buildWeatherCacheDatabase(context: Context): WeatherCacheDatabase =
    Room.databaseBuilder(context, WeatherCacheDatabase::class.java, "weather_cache.db")
        .build()
