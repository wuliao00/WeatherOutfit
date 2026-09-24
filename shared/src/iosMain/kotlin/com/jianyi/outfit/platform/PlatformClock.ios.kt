package com.jianyi.outfit.platform

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
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
