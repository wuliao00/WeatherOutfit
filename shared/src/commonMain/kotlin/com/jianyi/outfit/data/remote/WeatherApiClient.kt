package com.jianyi.outfit.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** 默认接口基础地址（apihz.cn 中国气象局数据） */
const val DEFAULT_BASE_URL: String = "https://cn.apihz.cn/"

/**
 * 解析配置。每一条都是为了对齐 Gson 原来的行为，不是"看起来更宽松"：
 *
 * - ignoreUnknownKeys：Gson 忽略未知字段。不打开的话，接口哪天多返回一个字段，
 *   整个查询就会抛异常 —— 而这不是接口的问题，是我们解析策略的问题。
 * - isLenient：Gson 会把 "29" 这类字符串数字转成数值。apihz 的部分字段确实时数字时字符串。
 * - coerceInputValues：字段类型对不上或给了 null 时回退到默认值，而不是抛。
 * - explicitNulls=false：不把我们没声明的 null 字段序列化出来。
 */
internal val weatherJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/** 平台 HTTP 引擎：Android 走 OkHttp，iOS 走 NSURLSession(Darwin) */
internal expect fun httpClientEngine(): HttpClientEngine

/**
 * 天气接口客户端。取代原来的 Retrofit `WeatherApiService`。
 *
 * 方法签名刻意与旧的 Retrofit 接口逐一对应（同名、同参数、同返回类型），
 * 这样 Repository 只换构造方式、业务逻辑一行不动 —— 换网络库时能对照审查的改动面越小越好。
 *
 * baseUrl 每次构造时归一化补斜杠，与原 RetrofitClient.create() 的行为一致
 * （设置页允许用户自填接口地址）。
 */
class WeatherApiClient(baseUrl: String = DEFAULT_BASE_URL) {

    private val root: String = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    private val client = HttpClient(httpClientEngine()) {
        install(ContentNegotiation) { json(weatherJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 25_000
            socketTimeoutMillis = 15_000
        }
        // 该接口用响应体里的 code 表达业务失败（HTTP 仍是 200），
        // 但 HTTP 层出错时仍然要抛，好让 Repository 统一走缓存回退那条路。
        expectSuccess = true
    }

    private suspend inline fun <reified T> get(
        path: String,
        vararg params: Pair<String, Any?>
    ): T = client.get("$root$path") {
        params.forEach { (name, value) -> if (value != null) parameter(name, value) }
    }.body()

    /** IP 自动定位查询：不传 ip 时自动获取调用方 IP 所在地天气 */
    suspend fun queryByIp(id: String, key: String, ip: String? = null): WeatherResponse =
        get("api/tianqi/tqybip.php", "id" to id, "key" to key, "ip" to ip)

    /** 地址查询：省 + 市/区；day=7 时返回 7 天预报 */
    suspend fun queryByAddress(
        id: String,
        key: String,
        province: String,
        city: String,
        day: Int? = null,
        hourType: Int? = null
    ): WeatherResponse = get(
        "api/tianqi/tqyb.php",
        "id" to id, "key" to key, "sheng" to province, "place" to city,
        "day" to day, "hourtype" to hourType
    )

    /** 经纬度查询：GPS 定位后精确查询（响应结构为全球数据源，独立 DTO） */
    suspend fun queryByLatLon(id: String, key: String, lat: Double, lon: Double): LatLonWeatherResponse =
        get("api/tianqi/tqybjw1.php", "id" to id, "key" to key, "lat" to lat, "lon" to lon)
}
