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

    /** 删除早于阈值时间戳的记录（按 cachedAt 判断），防止缓存表只增不清 */
    @Query("DELETE FROM weather_cache WHERE cachedAt < :staleBefore")
    suspend fun deleteStale(staleBefore: Long)
}
