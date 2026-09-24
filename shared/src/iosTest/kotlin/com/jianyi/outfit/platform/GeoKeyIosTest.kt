package com.jianyi.outfit.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * GPS 缓存键在 iOS 上的格式化测试。
 *
 * 这个字符串是**落盘的缓存主键**（weather_cache 表的主键），格式一变表现不是崩溃，
 * 而是"缓存永不命中、每次都打网络"—— 而接口限流下这会直接变成用户可见的加载失败。
 * Android 侧用 String.format("%.2f") 钉死，iOS 侧是手写的两位小数格式化，
 * 所以 iOS 这一份必须自己有一组断言，不能只靠"能编译"。
 */
class GeoKeyIosTest {

    @Test
    fun pads_two_decimals_and_keeps_sign() {
        assertEquals("loc|29.50|103.25", formatGeoKey(29.5, 103.25))
        assertEquals("loc|39.00|116.00", formatGeoKey(39.0, 116.0), "整数也要补足两位小数，否则键宽不一致")
        assertEquals("loc|31.23|121.47", formatGeoKey(31.23, 121.47))
        assertEquals("loc|-33.87|151.21", formatGeoKey(-33.8689, 151.2093), "四舍五入到两位")
    }

    @Test
    fun quantization_is_stable_for_jittering_gps() {
        // GPS 连续上报会带微小抖动；量化到两位小数后必须落到同一个键，否则次次打网络
        val a = formatGeoKey(30.5741283, 104.0667)
        val b = formatGeoKey(30.5741290, 104.0667001)
        assertEquals(a, b, "同一坐标的抖动应量化成同一个键")

        assertNotEquals(a, formatGeoKey(30.58, 104.0667), "跨过分位数时应换成新键")
    }

    @Test
    fun negative_zero_keeps_the_jvm_style_sign() {
        // Java 的 %.2f 对 -0.001 给 "-0.00"，这里逐字对齐（Android 与 iOS 各自平台内自洽即可，
        // 但同平台内一改口径就会让老缓存行永远命中不上）
        assertEquals("loc|-0.00|0.00", formatGeoKey(-0.001, 0.0))
    }

    @Test
    fun key_is_deterministic_across_repeated_calls() {
        val lat = 22.5431
        val lon = 114.0579
        repeat(5) { assertEquals(formatGeoKey(lat, lon), formatGeoKey(lat, lon)) }
    }
}
