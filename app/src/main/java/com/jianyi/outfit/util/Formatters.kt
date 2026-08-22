package com.jianyi.outfit.util

import com.jianyi.outfit.data.model.TempUnit
import com.jianyi.outfit.data.model.WeatherNow
import com.jianyi.outfit.data.model.WindUnit
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

    /** 摄氏 → 华氏 */
    private fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9 / 5 + 32
}
