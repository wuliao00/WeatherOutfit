package com.jianyi.outfit.util

import com.jianyi.outfit.data.model.TempUnit
import com.jianyi.outfit.data.model.WeatherNow
import com.jianyi.outfit.data.model.WindUnit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 展示格式化工具：温度/风力按用户单位设置输出。
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
        WindUnit.MS -> weather.windSpeedMs?.let { String.format("%.1f m/s", it) } ?: "--"
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
        else -> String.format("%.1f km", meters / 1000.0)
    }

    /** 气压 */
    fun pressure(hpa: Int?): String = hpa?.let { "$it hPa" } ?: "--"

    /** 云量 */
    fun cloudCover(percent: Int?): String = percent?.let { "$it%" } ?: "--"

    /** 秒级时间戳 → “05:42”，用于日出日落 */
    fun clock(epochSeconds: Long?): String {
        if (epochSeconds == null) return "--"
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000))
    }

    /**
     * 预报日期（“2026-09-22”）→ 星期文案。
     * 今天/明天比“周二”更好读，所以优先给相对说法，超出 7 天再退回星期。
     */
    fun forecastDayLabel(date: String?): String {
        if (date.isNullOrBlank()) return "--"
        val target = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date.trim())
        }.getOrNull() ?: return date
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val days = ((target.time - today.timeInMillis) / (24L * 3600 * 1000)).toInt()
        return when (days) {
            0 -> "今天"
            1 -> "明天"
            2 -> "后天"
            else -> SimpleDateFormat("EEE", Locale.CHINA).format(target).replace("周", "星期")
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
}
