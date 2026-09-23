package com.jianyi.outfit.platform

import java.util.Locale

/**
 * 与历史实现逐字节一致：旧版 HomeViewModel 与天气仓库都用
 * `String.format(Locale.US, "loc|%.2f|%.2f", lat, lon)` 生成这个 key，
 * 老用户库里的缓存行就是这个格式。改任何一个字符都会让缓存整体失效。
 */
actual fun formatGeoKey(latitude: Double, longitude: Double): String =
    String.format(Locale.US, "loc|%.2f|%.2f", latitude, longitude)
