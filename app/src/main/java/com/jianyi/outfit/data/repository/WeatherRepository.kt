package com.jianyi.outfit.data.repository

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.jianyi.outfit.BuildConfig
import com.jianyi.outfit.data.local.dao.WeatherCacheDao
import com.jianyi.outfit.data.local.entity.WeatherCacheEntity
import com.jianyi.outfit.data.model.ForecastDay
import com.jianyi.outfit.data.model.WeatherAlarm
import com.jianyi.outfit.data.model.WeatherNow
import com.jianyi.outfit.data.remote.LatLonWeatherResponse
import com.jianyi.outfit.data.remote.RetrofitClient
import com.jianyi.outfit.data.remote.WeatherApiService
import com.jianyi.outfit.data.remote.WeatherEnvelope
import com.jianyi.outfit.data.remote.WeatherResponse
import com.jianyi.outfit.engine.OutfitRecommendationEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** 接口业务错误（code != 200 时抛出，msg 用于用户提示） */
open class ApiException(message: String) : Exception(message)

/** 限流错误（公共凭证共享频次被打爆时出现），retryAfterSec 为接口建议的等待秒数 */
class RateLimitedException(message: String, val retryAfterSec: Int) : ApiException(message)

/** 缓存包装：经纬度响应与其他端点结构不同，写入时带类型标记便于回读判别 */
private data class CacheEnvelope(val type: String, val data: Any)

/**
 * 天气数据仓库接口：统一「缓存 → 网络 → 过期缓存回退」策略，
 * 面向 ViewModel 与后台任务的抽象，便于 JVM 单测替换实现。
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

/**
 * 天气数据仓库实现。
 * 凭证解析：用户在设置页自填的 id/key/apiUrl 优先，留空回退 BuildConfig 内置默认；
 * Retrofit 服务按 baseUrl 缓存复用（OkHttpClient 全局单例）。
 */
class WeatherRepositoryImpl(
    private val credentials: Flow<ApiCredentials>,
    private val gson: Gson = Gson(),
    private val cacheDao: WeatherCacheDao,
    private val apiFactory: (String) -> WeatherApiService = { RetrofitClient.create(it) }
) : WeatherRepository {

    companion object {
        /** 缓存有效期：30 分钟 */
        private const val CACHE_TTL_MS = 30 * 60 * 1000L

        /**
         * 缓存物理清理阈值：7 天。
         * 30 分钟内过期数据仍作为弱网 / 限流回退保留，超过 7 天才真正删除，
         * 既保证「断网回退过期缓存」可用，又避免 weather_cache 只增不清。
         */
        private const val CACHE_RETENTION_MS = 7 * 24 * 60 * 60 * 1000L

        /** 限流单次退避重试的最长等待（接口建议值超过则封顶，避免长时间卡住加载） */
        private const val RATE_LIMIT_RETRY_CAP_SEC = 10L
    }

    /** 是否为限流错误（公共凭证高峰期常见，msg 含「频次/过快」） */
    private fun isRateLimited(msg: String?): Boolean =
        msg?.contains("频次") == true || msg?.contains("过快") == true

    /** Retrofit 服务按 baseUrl 复用（凭证变更仅影响请求参数，无需重建） */
    private val apiCache = ConcurrentHashMap<String, WeatherApiService>()

    /** 解析当前生效凭证（用户配置优先）并取对应服务 */
    private suspend fun apiContext(): Pair<WeatherApiService, ApiCredentials> {
        val resolved = credentials.first().resolve(
            defaultId = BuildConfig.WEATHER_API_ID,
            defaultKey = BuildConfig.WEATHER_API_KEY,
            defaultUrl = RetrofitClient.DEFAULT_BASE_URL
        )
        val api = apiCache.getOrPut(resolved.apiUrl) { apiFactory(resolved.apiUrl) }
        return api to resolved
    }

    override suspend fun byIp(ip: String?, force: Boolean): Result<WeatherNow> {
        val (api, creds) = apiContext()
        return fetchOrCache("ip", force, tag = null) { api.queryByIp(creds.id, creds.key, ip) }
    }

    override suspend fun byAddress(province: String, city: String, force: Boolean): Result<WeatherNow> {
        val (api, creds) = apiContext()
        return fetchOrCache("addr|$province|$city", force, tag = null) {
            api.queryByAddress(creds.id, creds.key, province, city)
        }
    }

    override suspend fun byLatLon(lat: Double, lon: Double, force: Boolean): Result<WeatherNow> {
        val (api, creds) = apiContext()
        return fetchOrCache(String.format(Locale.US, "loc|%.2f|%.2f", lat, lon), force, tag = "latlon") {
            api.queryByLatLon(creds.id, creds.key, lat, lon)
        }
    }

    override suspend fun forecast(province: String, city: String): Result<List<ForecastDay>> = runCatching {
        val (api, creds) = apiContext()
        val response = api.queryByAddress(creds.id, creds.key, province, city, day = 7, hourType = 1)
        if (!response.isSuccess) throw ApiException(response.msg ?: "预报查询失败")
        response.toForecast()
    }

    override suspend fun cachedWeather(cacheKey: String): WeatherNow? =
        readCache(cacheKey, ignoreTtl = true)

    /* ============ 内部实现 ============ */

    /**
     * 统一的取数策略：新鲜缓存 → 网络 → 过期缓存回退（force = true 跳过缓存读取）。
     * tag 非空时缓存写入带类型包装（经纬度响应 DTO 与其他端点不同）。
     */
    private suspend fun <T : WeatherEnvelope> fetchOrCache(
        cacheKey: String,
        force: Boolean,
        tag: String?,
        networkCall: suspend () -> T
    ): Result<WeatherNow> {
        // 1. 命中未过期缓存，直接使用
        if (!force) {
            readCache(cacheKey, ignoreTtl = false)?.let { return Result.success(it) }
        }

        // 2. 发起网络请求（限流时按接口建议秒数退避重试一次）
        return try {
            var response = networkCall()
            if (!response.isSuccess && isRateLimited(response.msg)) {
                val waitSec = response.s?.coerceIn(1L, RATE_LIMIT_RETRY_CAP_SEC) ?: 6L
                delay((waitSec + 1) * 1000L)
                response = networkCall()
            }
            if (!response.isSuccess) {
                if (isRateLimited(response.msg)) {
                    // 限流：先回退过期缓存（该城市曾查询过则可继续使用），无缓存再报限流错误
                    readCache(cacheKey, ignoreTtl = true)?.let { return Result.success(it) }
                    Result.failure(
                        RateLimitedException(
                            "接口调用频次受限（公共凭证共享额度），请稍后重试",
                            response.s?.toInt() ?: 30
                        )
                    )
                } else {
                    Result.failure(ApiException(response.msg ?: "天气查询失败（code=${response.code}）"))
                }
            } else {
                // 成功后写入缓存并顺手清理过期项（经纬度响应带类型标记，回读时按标记选择 DTO）
                val payload = if (tag != null) gson.toJson(CacheEnvelope(tag, response)) else gson.toJson(response)
                cacheDao.put(
                    WeatherCacheEntity(cacheKey, payload, System.currentTimeMillis())
                )
                runCatching { cacheDao.deleteStale(System.currentTimeMillis() - CACHE_RETENTION_MS) }
                Result.success(response.toWeather())
            }
        } catch (e: Exception) {
            // 3. 网络异常时回退到过期缓存，保证弱网可用
            readCache(cacheKey, ignoreTtl = true)
                ?.let { Result.success(it) }
                ?: Result.failure(e)
        }
    }

    /** 读取并解析缓存；ignoreTtl = true 时忽略 30 分钟有效期 */
    private suspend fun readCache(cacheKey: String, ignoreTtl: Boolean): WeatherNow? {
        val cached = cacheDao.get(cacheKey) ?: return null
        val fresh = System.currentTimeMillis() - cached.cachedAt < CACHE_TTL_MS
        if (!fresh && !ignoreTtl) return null
        return runCatching {
            val root = JsonParser.parseString(cached.payloadJson).asJsonObject
            if (root.has("type") && root.get("type").asString == "latlon") {
                gson.fromJson(root.getAsJsonObject("data"), LatLonWeatherResponse::class.java)
            } else {
                gson.fromJson(cached.payloadJson, WeatherResponse::class.java)
            }
        }.getOrNull()?.takeIf { it.isSuccess }?.toWeather()
    }

    /** 按响应实际类型转换为领域模型 */
    private fun WeatherEnvelope.toWeather(): WeatherNow = when (this) {
        is LatLonWeatherResponse -> toDomain()
        is WeatherResponse -> toDomain()
        else -> throw IllegalStateException("未知响应类型")
    }
}

/* ============ DTO → 领域模型转换 ============ */

/** 实况响应转换为领域模型 */
fun WeatherResponse.toDomain(): WeatherNow {
    val condition = weather1 ?: "未知"
    val updateTimeText = nowinfo?.uptime ?: uptime ?: ""
    return WeatherNow(
        province = sheng ?: "",
        city = name ?: shi ?: "未知城市",
        temperature = nowinfo?.temperature ?: wd1?.toDoubleOrNull() ?: 0.0,
        feelsLike = nowinfo?.feelst,
        humidity = nowinfo?.humidity,
        condition = condition,
        iconUrl = weather1img,
        windDirection = nowinfo?.windDirection ?: winddirection1,
        windScaleText = nowinfo?.windScale ?: windleve1,
        windSpeedMs = nowinfo?.windSpeed,
        precipitation = nowinfo?.precipitation,
        dayHigh = wd1?.toDoubleOrNull(),
        dayLow = wd2?.toDoubleOrNull(),
        uvIndex = OutfitRecommendationEngine.estimateUvIndex(condition, parseHour(updateTimeText)),
        uvLevel = "", // 由下方扩展属性按 uvIndex 计算
        updateTime = updateTimeText,
        alarms = (alarm ?: emptyList()).mapNotNull { item ->
            item.title?.let {
                WeatherAlarm(
                    title = it,
                    signalType = item.signaltype ?: "",
                    signalLevel = item.signallevel ?: "",
                    effective = item.effective ?: ""
                )
            }
        }
    ).let { weather ->
        // 紫外线等级文案依赖指数，构建后回填
        weather.copy(uvLevel = OutfitRecommendationEngine.uvLevelText(weather.uvIndex))
    }
}

/** 解析 7 天预报：day1~day7 为“日期|天气|低温|高温|风向|风力”竖线分隔字符串 */
fun WeatherResponse.toForecast(): List<ForecastDay> {
    val raw = listOf(day1, day2, day3, day4, day5, day6, day7).filterNotNull()
    return raw.map { day ->
        val parts = day.split("|")
        ForecastDay(
            date = parts.getOrNull(0),
            condition = parts.getOrNull(1),
            lowTemp = parts.getOrNull(2)?.toDoubleOrNull()?.roundToInt(),
            highTemp = parts.getOrNull(3)?.toDoubleOrNull()?.roundToInt()
        )
    }
}

/** 从更新时间文字中解析小时（如“2026-08-22 12:00:29”→12），失败时取系统当前小时 */
private fun parseHour(timeText: String): Int {
    runCatching { timeText.substring(11, 13).toInt() }.getOrNull()?.let { return it }
    return Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
}

/**
 * 经纬度响应转换为领域模型。
 * 该端点无省份 / 昼夜温度 / 预警 / 体感温度：
 * - 体感温度缺省为 null，推荐引擎自动回退使用气温
 * - 风力无文字等级，仅保留风速（m/s），引擎按蒲福风级换算
 * - 城市名为拼音（接口为全球数据源）
 */
fun LatLonWeatherResponse.toDomain(): WeatherNow {
    val condition = weather ?: "未知"
    val cal = Calendar.getInstance().apply { timeInMillis = (dt ?: 0L) * 1000 }
    val hour = if (dt != null) cal.get(Calendar.HOUR_OF_DAY) else Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val updateTimeText = if (dt != null) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(dt * 1000))
    } else ""
    return WeatherNow(
        province = "",
        city = name ?: "当前位置",
        temperature = temph ?: temp?.minus(273.15) ?: 0.0,
        feelsLike = null,
        humidity = humidity,
        condition = condition,
        iconUrl = null,
        windDirection = degToDirection(deg),
        windScaleText = null,
        windSpeedMs = speed,
        precipitation = null,
        dayHigh = null,
        dayLow = null,
        uvIndex = OutfitRecommendationEngine.estimateUvIndex(condition, hour),
        uvLevel = "",
        updateTime = updateTimeText,
        alarms = emptyList()
    ).let { weather ->
        weather.copy(uvLevel = OutfitRecommendationEngine.uvLevelText(weather.uvIndex))
    }
}

/** 风向角度转八方位中文（0°为正北，顺时针） */
private fun degToDirection(deg: Int?): String {
    if (deg == null) return ""
    val names = arrayOf("北风", "东北风", "东风", "东南风", "南风", "西南风", "西风", "西北风")
    return names[(((deg % 360) + 360 + 22) % 360) / 45]
}
