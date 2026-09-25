package com.jianyi.outfit.platform

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian

/**
 * 全项目 iOS 侧唯一允许的日历。
 *
 * 不用 `NSCalendar.currentCalendar`：它返回的是**用户 locale 设置里的那个日历**，
 * 而 iOS 允许把"备用历法"设成佛历 / 民国历 / 回历 / 农历（设置 → 通用 → 语言与地区 →
 * 日历）。一旦用户这么设了，`currentCalendar` 给的"年"就不是公历年 ——
 * 佛历会多 543 年。我们的日期算术全押在公历上：
 * `todayEpochDay()` 把年月日喂给 `epochDayFromCivil`（Hinnant 的 proleptic Gregorian），
 * 年一错就是差十几万天，于是首页的"今天/明天/后天"、每日轮换与季节选景全部错位，
 * **不崩溃、不报错，只是偶尔不对** —— 正是最难发现的那一类。
 *
 * Android 侧不存在这个问题：`java.time.LocalDate` 永远是公历。
 * 所以两端语义要一致，必须在这里显式钉住 Gregorian，而不是依赖"用户没改设置"。
 *
 * 顺手说明为什么现有的 `PlatformDateAlignmentTest` 抓不到它：那条测试拿
 * `todayEpochDay()` 反推年份再和 `currentDayOfYear()` 对账，两个值都来自同一个
 * 错位日历，佛历 +543 年下等式照样成立 —— 自洽不等于正确。要验只能像它注释里
 * 写的那样，在模拟器上改历法再跑一遍。
 */
internal fun jianyiCalendar(): NSCalendar =
    NSCalendar.calendarWithIdentifier(NSCalendarIdentifierGregorian) ?: NSCalendar.currentCalendar
