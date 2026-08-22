package com.jianyi.outfit.data.remote

/** 两类天气响应的公共信封：code / msg 约定一致（200 成功，400 失败） */
interface WeatherEnvelope {
    val code: Int
    val msg: String?
    /** 限流错误时接口返回的建议等待秒数（如「请45秒后再试」→ s=45），非限流时为空 */
    val s: Long? get() = null
    val isSuccess: Boolean get() = code == 200
}

/**
 * apihz.cn 中国气象局数据接口的响应 DTO。
 * 字段名与接口返回 JSON 一一对应（Gson 直接映射），全部可为空以增强兼容性。
 *
 * IP / 地址端点（tqybip.php / tqyb.php）返回本结构：
 * - weather1 / weather2：今日 / 明日天气现象
 * - wd1 / wd2：今日最高 / 最低温
 * - nowinfo：实况数据（体感温度、湿度、风速等）
 * - alarm：生效中的气象预警列表
 */
data class WeatherResponse(
    override val code: Int,              // 200 成功，400 失败
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
    // 以下为 day=7 时返回的 7 天预报，格式为竖线分隔字符串：
    // 日期|天气|最低温|最高温|风向|风力
    val day1: String? = null,
    val day2: String? = null,
    val day3: String? = null,
    val day4: String? = null,
    val day5: String? = null,
    val day6: String? = null,
    val day7: String? = null
) : WeatherEnvelope

/**
 * 经纬度端点（tqybjw1.php）响应 DTO。
 * ⚠️ 该端点为全球数据源，字段结构与 IP / 地址端点完全不同：
 * 无省份、无昼夜温度、无预警；城市名为拼音；temp 为开氏度、temph 为摄氏度；
 * 无风力文字与体感温度（由引擎按风速换算风级、体感缺省回气温）。
 */
data class LatLonWeatherResponse(
    override val code: Int,              // 200 成功，400 失败
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
data class AlarmItem(
    val id: String? = null,
    val title: String? = null,           // 预警标题
    val signaltype: String? = null,      // 预警类型，如“暴雨”
    val signallevel: String? = null,     // 预警级别，如“黄色”
    val effective: String? = null,       // 生效时间
    val eventType: String? = null,
    val severity: String? = null
)
