package com.jianyi.outfit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jianyi.outfit.data.local.entity.WeatherCacheEntity

/** 天气缓存 DAO */
@Dao
interface WeatherCacheDao {

    @Query("SELECT * FROM weather_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun get(key: String): WeatherCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(cache: WeatherCacheEntity)

    @Query("DELETE FROM weather_cache WHERE cacheKey = :key")
    suspend fun evict(key: String)
}
