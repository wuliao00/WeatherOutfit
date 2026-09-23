package com.jianyi.outfit.data.repository

import com.jianyi.outfit.data.model.ForecastDay
import com.jianyi.outfit.data.model.WeatherNow
import kotlinx.coroutines.flow.Flow

/** 接口业务错误（code != 200 时抛出，msg 用于用户提示） */
open class ApiException(message: String) : Exception(message)

/** 限流错误（公共凭证共享频次被打爆时出现），retryAfterSec 为接口建议的等待秒数 */
class RateLimitedException(message: String, val retryAfterSec: Int) : ApiException(message)

/**
 * 天气数据仓库接口：统一「缓存 → 网络 → 过期缓存回退」策略，
 * 面向 ViewModel 与后台任务的抽象，便于 JVM 单测替换实现。
 * 接口在 shared（iOS 侧 ViewModel 也要依赖它），实现留在 app（Room/WorkManager 是 Android 的）。
 */
interface WeatherRepository {

    /** IP 自动定位查询（GPS 不可用时的兜底定位） */
    suspend fun byIp(ip: String? = null, force: Boolean = false): Result<WeatherNow>

    /** 地址查询（省 + 市/区） */
    suspend fun byAddress(province: String, city: String, force: Boolean = false): Result<WeatherNow>

    /** 经纬度查询（GPS 定位后精确查询） */
    suspend fun byLatLon(lat: Double, lon: Double, force: Boolean = false): Result<WeatherNow>

    /** 查询未来 7 天预报（不缓存） */
    suspend fun forecast(province: String, city: String): Result<List<ForecastDay>>

    /**
     * 读取缓存的天气快照（忽略 TTL）。
     * 供穿搭详情页按缓存 key 复用首页刚加载的数据，避免重复请求与全局可变状态。
     */
    suspend fun cachedWeather(cacheKey: String): WeatherNow?
}
