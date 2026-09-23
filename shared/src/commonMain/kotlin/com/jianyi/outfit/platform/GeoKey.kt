package com.jianyi.outfit.platform

/**
 * GPS 数据源的缓存 key：`loc|纬度|经度`，固定两位小数。
 *
 * 为什么要 expect/actual 而不是就地 `String.format`：这个字符串是**落盘的缓存主键**，
 * 老用户机器上已经有 Android 侧 `String.format(Locale.US, "loc|%.2f|%.2f", ...)` 写进去的行；
 * 换库/换写法导致 key 变了，表现是"缓存全部失效、每次都重新请求"，
 * 而限流的接口下这会直接变成用户可见的加载失败。
 * 所以 Android 的 actual 必须与历史写法逐字节一致。
 *
 * 两位小数的作用是防抖：GPS 每次上报都有微小抖动，不量化就会每次都是新 key、次次打网络。
 */
expect fun formatGeoKey(latitude: Double, longitude: Double): String
