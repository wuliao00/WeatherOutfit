package com.jianyi.outfit.platform

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDayOfYear
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * iOS 侧用 NSDate 的 Unix 时间，语义与 Android 的 System.currentTimeMillis() 一致：
 * 都是「距 1970-01-01 UTC 的毫秒数」，所以模板的 createdAt 跨端可比、可排序。
 */
actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

/**
 * NSCalendar 的月份**已经是 1~12**，所以这里不能再 +1。
 * 与 Android 侧的差别写在 currentMonth 的 KDoc 里 —— 这是最容易写错成
 * "两边都 +1" 的地方，而且错了也不崩，只是 iOS 上偶尔选错季节风景。
 */
actual fun currentMonth(): Int =
    NSCalendar.currentCalendar.component(NSCalendarUnitMonth, NSDate()).toInt()

/** 0~23，与 Android 的 Calendar.HOUR_OF_DAY 同语义（都是本地时区墙钟） */
actual fun currentHourOfDay(): Int =
    NSCalendar.currentCalendar.component(NSCalendarUnitHour, NSDate()).toInt()

/** 1~366，与 Android 的 Calendar.DAY_OF_YEAR 同语义 */
actual fun currentDayOfYear(): Int =
    NSCalendar.currentCalendar.component(NSCalendarUnitDayOfYear, NSDate()).toInt()
