package com.jianyi.outfit.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 中国气象局数据接口（apihz.cn）的 Retrofit 封装。
 * 凭证 id / key 在调用方（Repository）统一传入，来自 BuildConfig。
 */
interface WeatherApiService {

    /** IP 自动定位查询：不传 ip 时自动获取调用方 IP 所在地天气 */
    @GET("api/tianqi/tqybip.php")
    suspend fun queryByIp(
        @Query("id") id: String,
        @Query("key") key: String,
        @Query("ip") ip: String? = null
    ): WeatherResponse

    /** 地址查询：省 + 市/区；day=7 时返回 7 天预报 */
    @GET("api/tianqi/tqyb.php")
    suspend fun queryByAddress(
        @Query("id") id: String,
        @Query("key") key: String,
        @Query("sheng") province: String,
        @Query("place") city: String,
        @Query("day") day: Int? = null,
        @Query("hourtype") hourType: Int? = null
    ): WeatherResponse

    /** 经纬度查询：GPS 定位后精确查询（响应结构为全球数据源，独立 DTO） */
    @GET("api/tianqi/tqybjw1.php")
    suspend fun queryByLatLon(
        @Query("id") id: String,
        @Query("key") key: String,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): LatLonWeatherResponse
}
