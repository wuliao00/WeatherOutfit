package com.jianyi.outfit.platform

import kotlin.math.abs

/**
 * iOS 侧手写两位小数格式化，不用 String.format：Kotlin/Native 的 `String.format`
 * 对 `%f` 系列的支持历来不完整，为一个缓存 key 去赌它不值得。
 *
 * 这个 key 只在**同平台内**做缓存匹配（iOS 的缓存行只有 iOS 会读），
 * 所以不要求与 Android 的 `%.2f` 逐字节相同，只要求自身稳定：
 * 同一个坐标每次算出同一个串。负号按 Java 的习惯保留（-0.001 → "-0.00"）。
 */
actual fun formatGeoKey(latitude: Double, longitude: Double): String =
    "loc|${fixed2(latitude)}|${fixed2(longitude)}"

/** 四舍五入到两位小数并补足尾随零（29.5 → "29.50"） */
private fun fixed2(v: Double): String {
    val negative = v < 0.0
    val scaled = (abs(v) * 100.0 + 0.5).toLong()
    val intPart = scaled / 100
    val frac = scaled % 100
    val body = intPart.toString() + "." + if (frac < 10) "0$frac" else frac.toString()
    return if (negative) "-$body" else body
}
