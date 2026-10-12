package com.jianyi.outfit.data.remote

import kotlinx.serialization.Serializable

/** 两类天气响应的公共信封：code / msg 约定一致（200 成功，400 失败）。
 *  sealed：实现类只有本文件两个 DTO，when 分支可以穷尽校验（缓存编解码依赖这一点） */
sealed interface WeatherEnvelope {
    val code: Int
    val msg: String?
    /** 限流错误时接口返回的建议等待秒数（如「请45秒后再试」→ s=45），非限流时为空 */
    val s: Long? get() = null
    val isSuccess: Boolean get() = code == 200
}

/**
 * apihz.cn 中国气象局数据接口的响应 DTO。
 * 字段名与接口返回 JSON 一一对应，全部可为空以增强兼容性。
 *
 * IP / 地址端点（tqybip.php / tqyb.php）返回本结构：
 * - weather1 / weather2：今日 / 明日天气现象
 * - wd1 / wd2：今日最高 / 最低温
 * - nowinfo：实况数据（体感温度、湿度、风速等）
 * - alarm：生效中的气象预警列表
 *
 * ## 从 Gson 换到 kotlinx.serialization 时必须对齐的三件事
 * 换序列化库不是换个注解就完事，两边默认行为差别很大，而且**都是不报错的那种差**：
 * 1. Gson 忽略未知字段，kotlinx 默认**抛异常** → Json 配置里必须 ignoreUnknownKeys
 *    （见 WeatherApiClient），否则接口哪天多加一个字段，App 就整体查询失败。
 * 2. Gson 把缺失的 int 填成 0，kotlinx 对没有默认值的非空字段**抛 MissingField**
 *    → code 给默认值 0，与 Gson 完全一致（0 不等于 200，走失败分支）。
 * 3. Gson 会把 "29" 这种字符串数字转成 Double，kotlinx 默认**类型严格**
 *    → Json 配置 isLenient + coerceInputValues。
 * 这三条都有对应的单测钉住（见 WeatherResponseParsingTest）。
 */
@Serializable
data class WeatherResponse(
    override val code: Int = 0,          // 200 成功，400 失败；缺字段时与 Gson 一样落到 0
    override val msg: String? = null,    // 失败时的提示信息
    override val s: Long? = null,        // 限流建议等待秒数
    val guo: String? = null,             // 国家
    val sheng: String? = null,           // 省
    val shi: String? = null,             // 市
    val name: String? = null,            // 查询地名称
    val weather1: String? = null,        // 今日天气现象
    val weather2: String? = null,        // 明日天气现象
    val wd1: String? = null,             // 今日最高温（℃）
    val wd2: String? = null,             // 今日最低温（℃）
    val winddirection1: String? = null,  // 今日风向
    val winddirection2: String? = null,  // 明日风向
    val windleve1: String? = null,       // 今日风力，如“3~4级”“微风”
    val windleve2: String? = null,       // 明日风力
    val weather1img: String? = null,     // 今日天气图标 URL
    val weather2img: String? = null,     // 明日天气图标 URL
    val lon: String? = null,             // 经度
    val lat: String? = null,             // 纬度
    val uptime: String? = null,          // 数据更新时间
    val nowinfo: NowInfo? = null,        // 实况信息
    val alarm: List<AlarmItem>? = null,  // 气象预警列表
    // day=7 时的逐日预报。**实测（2026-10-12）接口已不返回 day1~day7 竖线串**，
    // 改在 weatherday2..weatherday7 里结构化下发（今日数据在顶层 weather1/wd1/windleve1，
    // 所以逐日数组从**明天**开始）。下面 day1~day7 保留作兜底：缓存里可能还有老行，
    // 也不能排除别的端点仍给这种形态。字段名与取值见 DayForecast 的注释。
    val weatherday1: DayForecast? = null,
    val weatherday2: DayForecast? = null,
    val weatherday3: DayForecast? = null,
    val weatherday4: DayForecast? = null,
    val weatherday5: DayForecast? = null,
    val weatherday6: DayForecast? = null,
    val weatherday7: DayForecast? = null,
    /** @deprecated 旧形态（竖线串「日期|天气|最低温|最高温|风向|风力」），接口已不返回，仅作兜底 */
    val day1: String? = null,
    val day2: String? = null,
    val day3: String? = null,
    val day4: String? = null,
    val day5: String? = null,
    val day6: String? = null,
    val day7: String? = null
) : WeatherEnvelope

/**
 * day=7 时接口逐日返回的预报元素。
 *
 * 字段名与取值来自 2026-10-12 对 `tqyb.php?sheng=广东省&place=湛江市&day=7` 的实测登记：
 * ```
 * "weatherday2":{"date":"2026/10/13","weekday":"星期二","monthDay":"10/13",
 *   "weather1":"晴","weather2":"晴","wd1":32,"wd2":24,
 *   "winddirection1":"无持续风向","winddirection2":"无持续风向",
 *   "windleve1":"微风","windleve2":"微风","weather1img":"…","weather2img":"…"}
 * ```
 * `wd1` 是**最高温**、`wd2` 是最低温（与顶层 weather1/wd1/wd2 同义），且接口给的是数字。
 */
@Serializable
data class DayForecast(
    val date: String? = null,
    val weekday: String? = null,
    val monthDay: String? = null,
    val weather1: String? = null,
    val weather2: String? = null,
    val wd1: Int? = null,
    val wd2: Int? = null,
    val winddirection1: String? = null,
    val winddirection2: String? = null,
    val windleve1: String? = null,
    val windleve2: String? = null
)

/**
 * 经纬度端点（tqybjw1.php）响应 DTO。
 * ⚠️ 该端点为全球数据源，字段结构与 IP / 地址端点完全不同：
 * 无省份、无昼夜温度、无预警；城市名为拼音；temp 为开氏度、temph 为摄氏度；
 * 无风力文字与体感温度（由引擎按风速换算风级、体感缺省回气温）。
 */
@Serializable
data class LatLonWeatherResponse(
    override val code: Int = 0,
    override val msg: String? = null,    // 失败时的提示信息
    override val s: Long? = null,        // 限流建议等待秒数
    val weather: String? = null,         // 天气现象（中文，如“多云”）
    val temp: Double? = null,            // 气温（开氏度）
    val temph: Double? = null,           // 气温（摄氏度）
    val pressure: Int? = null,           // 气压（hPa）
    val humidity: Int? = null,           // 相对湿度（%）
    val visibility: Int? = null,         // 能见度（米）
    val speed: Double? = null,           // 风速（m/s）
    val deg: Int? = null,                // 风向角度（0~359）
    val clouds: Int? = null,             // 云量（%）
    val country: String? = null,         // 国家代码（ISO）
    val sunrise: Long? = null,           // 日出时间戳
    val sunset: Long? = null,            // 日落时间戳
    val name: String? = null,            // 城市名称（拼音，如 Zhongshan）
    val dt: Long? = null                 // 数据更新时间戳（秒）
) : WeatherEnvelope

/** 实况信息 */
@Serializable
data class NowInfo(
    val precipitation: Double? = null,        // 降水量（mm）
    val temperature: Double? = null,          // 气温（℃）
    val pressure: Int? = null,                // 气压（hPa）
    val humidity: Int? = null,                // 相对湿度（%）
    val windDirection: String? = null,        // 风向
    val windDirectionDegree: Int? = null,     // 风向角度
    val windSpeed: Double? = null,            // 风速（m/s）
    val windScale: String? = null,            // 风力文字，如“微风”
    val feelst: Double? = null,               // 体感温度（℃）
    val uptime: String? = null                // 实况更新时间
)

/** 气象预警条目 */
@Serializable
data class AlarmItem(
    val id: String? = null,
    val title: String? = null,           // 预警标题
    val signaltype: String? = null,      // 预警类型，如“暴雨”
    val signallevel: String? = null,     // 预警级别，如“黄色”
    val effective: String? = null,       // 生效时间
    val eventType: String? = null,
    val severity: String? = null
)
