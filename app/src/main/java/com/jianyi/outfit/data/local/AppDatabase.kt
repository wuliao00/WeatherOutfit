package com.jianyi.outfit.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jianyi.outfit.data.local.dao.CityDao
import com.jianyi.outfit.data.local.dao.OutfitTemplateDao
import com.jianyi.outfit.data.local.dao.WeatherCacheDao
import com.jianyi.outfit.data.local.entity.CityEntity
import com.jianyi.outfit.data.local.entity.OutfitTemplateEntity
import com.jianyi.outfit.data.local.entity.WeatherCacheEntity

/**
 * Room 数据库：历史城市、穿搭模板、天气缓存。
 */
@Database(
    entities = [
        CityEntity::class,
        OutfitTemplateEntity::class,
        WeatherCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cityDao(): CityDao
    abstract fun outfitTemplateDao(): OutfitTemplateDao
    abstract fun weatherCacheDao(): WeatherCacheDao

    companion object {
        /** 构建数据库单例（调用方持有 Application 级引用） */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "jianyi.db")
                .build()
    }
}
