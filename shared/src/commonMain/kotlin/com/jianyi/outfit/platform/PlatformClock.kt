package com.jianyi.outfit.platform

/**
 * 平台时钟里「必须问操作系统」的两件事。
 *
 * 其余日期算术（儒略日、星期几、日期差）都是纯数学，放在 commonMain 自己算，
 * 只有这两个值依赖系统时区与本地日历，所以做成 expect/actual。
 */

/** 今天的儒略日数（epoch day，1970-01-01 = 0），本地时区。用于算「今天/明天/后天」 */
expect fun todayEpochDay(): Long

/** 秒级时间戳 → 本地时区的 “HH:mm”，用于日出日落展示 */
expect fun localClockText(epochSeconds: Long): String

/**
 * 公历日期 → 儒略日数（Howard Hinnant 的 days_from_civil，proleptic Gregorian）。
 * 纯数学，不碰任何平台 API，所以放 commonMain：Android 的 java.time 与
 * iOS 的 NSCalendar 算出来的 epoch day 必须和这里一致，否则「今天/明天」会错一天。
 */
fun epochDayFromCivil(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1L else year.toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

/**
 * 儒略日数 → 中文星期（“星期一”…“星期日”）。
 * epoch day 0 是星期四，所以 +3 取模后 0=星期一。
 */
fun weekdayNameFromEpochDay(epochDay: Long): String {
    val names = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
    val index = (((epochDay + 3) % 7) + 7).rem(7).toInt()
    return names[index]
}
