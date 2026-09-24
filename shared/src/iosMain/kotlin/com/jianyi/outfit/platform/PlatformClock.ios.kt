package com.jianyi.outfit.platform

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

/** 用 NSCalendar 取本地年月日，再交给 commonMain 的纯算术换算成儒略日 */
actual fun todayEpochDay(): Long {
    val cal = NSCalendar.currentCalendar
    val now = NSDate()
    val y = cal.component(NSCalendarUnitYear, now).toInt()
    val m = cal.component(NSCalendarUnitMonth, now).toInt()
    val d = cal.component(NSCalendarUnitDay, now).toInt()
    return epochDayFromCivil(y, m, d)
}

/** 本地时区的 HH:mm，不足两位补零（与 Android 的 SimpleDateFormat("HH:mm") 同观感） */
actual fun localClockText(epochSeconds: Long): String {
    val cal = NSCalendar.currentCalendar
    val date = NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble())
    val h = cal.component(NSCalendarUnitHour, date).toInt()
    val min = cal.component(NSCalendarUnitMinute, date).toInt()
    return "${h.toString().padStart(2, '0')}:${min.toString().padStart(2, '0')}"
}

/**
 * 「yyyy-MM-dd HH:mm:ss」定宽格式，与 Android 侧 SimpleDateFormat(Locale.US) 对齐。
 * 定宽是硬要求：调用方会把这个串再截 [11,13] 反推小时，格式一变就解析不出来了。
 *
 * 用 NSCalendar 逐位拼而不是 NSDateFormatter：后者要靠 localeWithIdentifier 拿到
 * en_US 才会稳定输出定宽数字，而那个 companion 扩展在 K/N 里的名字大小写我不敢凭记忆写
 * （iOS 这边只能编译验证、跑不到，猜错就是 CI 上才知道）。逐位拼完全确定，
 * 而且与上面的 localClockText 同源。
 */
actual fun localDateHourText(epochSeconds: Long): String {
    val cal = NSCalendar.currentCalendar
    val date = NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble())
    val y = cal.component(NSCalendarUnitYear, date).toInt()
    val mo = cal.component(NSCalendarUnitMonth, date).toInt()
    val d = cal.component(NSCalendarUnitDay, date).toInt()
    val h = cal.component(NSCalendarUnitHour, date).toInt()
    val mi = cal.component(NSCalendarUnitMinute, date).toInt()
    val s = cal.component(NSCalendarUnitSecond, date).toInt()
    return "$y-${p2(mo)}-${p2(d)} ${p2(h)}:${p2(mi)}:${p2(s)}"
}

actual fun hourOfDayFromEpochSeconds(epochSeconds: Long): Int =
    NSCalendar.currentCalendar.component(
        NSCalendarUnitHour,
        NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble())
    ).toInt()

/** 补齐两位（年份不补，因为四位年份恒成立） */
private fun p2(v: Int): String = v.toString().padStart(2, '0')
