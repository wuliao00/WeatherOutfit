package com.jianyi.outfit.platform

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * iOS 侧用 NSDate 的 Unix 时间，语义与 Android 的 System.currentTimeMillis() 一致：
 * 都是「距 1970-01-01 UTC 的毫秒数」，所以模板的 createdAt 跨端可比、可排序。
 */
actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
