package com.jianyi.outfit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 天气数据缓存表：以查询维度为键缓存接口原始 JSON，
 * 有效期 30 分钟，过期自动重新请求；断网时回退过期缓存。
 */
@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val cacheKey: String,   // 缓存键：ip / addr|省|市 / loc|纬度|经度
    val payloadJson: String,            // 接口原始响应 JSON
    val cachedAt: Long                  // 缓存时间戳（毫秒）
)
