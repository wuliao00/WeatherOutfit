package com.jianyi.outfit.util

import com.jianyi.outfit.data.model.TempUnit
import com.jianyi.outfit.data.model.WeatherNow
import com.jianyi.outfit.data.model.WindUnit
import com.jianyi.outfit.platform.epochDayFromCivil
import com.jianyi.outfit.platform.localClockText
import com.jianyi.outfit.platform.todayEpochDay
import com.jianyi.outfit.platform.weekdayNameFromEpochDay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 展示格式化工具：温度/风力按用户单位设置输出。
 *
 * 从 app 搬进 shared：卡片与详情页都要用，而它们正在跨端。
 * 原来依赖 SimpleDateFormat / Calendar / String.format 的三处改成
 * 「纯日期算术 + platform 的两个 expect/actual」，输出文案与旧版保持一致。
 */
object Formatters {

    /** 温度文案，如“26℃”“79℉”（按用户单位换算） */
    fun temp(celsius: Double, unit: TempUnit): String = when (unit) {
        TempUnit.CELSIUS -> "${celsius.roundToInt()}℃"
        TempUnit.FAHRENHEIT -> "${celsiusToFahrenheit(celsius).roundToInt()}℉"
    }

    /** 大号温度数字与单位分开返回，首页大字展示用 */
    fun tempSplit(celsius: Double, unit: TempUnit): Pair<String, String> = when (unit) {
        TempUnit.CELSIUS -> celsius.roundToInt().toString() to "℃"
        TempUnit.FAHRENHEIT -> celsiusToFahrenheit(celsius).roundToInt().toString() to "℉"
    }

    /** 风力文案：级 → “3~4级”，m/s → “1.9 m/s” */
    fun wind(weather: WeatherNow, unit: WindUnit): String = when (unit) {
        WindUnit.BEAUFORT -> weather.windScaleText ?: "微风"
        WindUnit.MS -> weather.windSpeedMs?.let { "${fixed1(it)} m/s" } ?: "--"
    }

    /** 湿度文案，如“66%” */
    fun humidity(percent: Int?): String = percent?.let { "$it%" } ?: "--"

    /** 体感温度文案（缺失时显示“--”） */
    fun feelsLike(celsius: Double?, unit: TempUnit): String =
        celsius?.let { temp(it, unit) } ?: "--"

    /** 能见度：不足 1km 用米，避免“0km”这种没信息量的表达 */
    fun visibility(meters: Int?): String = when {
        meters == null -> "--"
        meters < 1000 -> "${meters} m"
        else -> "${fixed1(meters / 1000.0)} km"
    }

    /** 气压 */
    fun pressure(hpa: Int?): String = hpa?.let { "$it hPa" } ?: "--"

    /** 云量 */
    fun cloudCover(percent: Int?): String = percent?.let { "$it%" } ?: "--"

    /** 秒级时间戳 → “05:42”，用于日出日落（本地时区，见 platform.localClockText） */
    fun clock(epochSeconds: Long?): String =
        epochSeconds?.let { localClockText(it) } ?: "--"

    /**
     * 预报日期（“2026-09-22”）→ 星期文案。
     * 今天/明天比“周二”更好读，所以优先给相对说法，超出再退回星期。
     * 日期差与星期名都是纯算术（儒略日），只有「今天」要问系统。
     */
    fun forecastDayLabel(date: String?): String {
        if (date.isNullOrBlank()) return "--"
        val (y, m, d) = parseYmd(date.trim()) ?: return date
        val target = epochDayFromCivil(y, m, d)
        return when ((target - todayEpochDay()).toInt()) {
            0 -> "今天"
            1 -> "明天"
            2 -> "后天"
            else -> weekdayNameFromEpochDay(target)
        }
    }

    /** 预报日期 → “9/22”，画在星期下面做辅助信息 */
    fun forecastDateShort(date: String?): String {
        if (date.isNullOrBlank()) return ""
        val parts = date.split("-")
        val m = parts.getOrNull(1)?.toIntOrNull()
        val d = parts.getOrNull(2)?.toIntOrNull()
        return if (m != null && d != null) "$m/$d" else date
    }

    /** 摄氏 → 华氏 */
    private fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9 / 5 + 32

    /** “yyyy-MM-dd” → (年, 月, 日)；格式不对返回 null（调用方原样回显） */
    private fun parseYmd(text: String): Triple<Int, Int, Int>? {
        val parts = text.split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        return Triple(y, m, d)
    }

    /** 一位小数、四舍五入、补尾随零（1.9 → “1.9”，2 → “2.0”），替代 String.format("%.1f") */
    private fun fixed1(v: Double): String {
        val scaled = (abs(v) * 10 + 0.5).toLong()
        val body = "${scaled / 10}.${scaled % 10}"
        return if (v < 0) "-$body" else body
    }
}
