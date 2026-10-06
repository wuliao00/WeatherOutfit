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
 * 秒级时间戳 → 本地时区的「yyyy-MM-dd HH:mm:ss」。
 *
 * 这是经纬度查询接口给的时间格式，会被当作 WeatherNow.updateTime 直接显示，
 * 也会被 parseHour 反过来截 [11,13] 取小时 —— 所以两端必须给出同样的定宽格式，
 * 差一个空格都会让紫外线指数按错误时刻估算。Android 沿用 SimpleDateFormat 的
 * Locale.US，iOS 用 NSDateFormatter + en_US 对齐。
 */
expect fun localDateHourText(epochSeconds: Long): String

/** 秒级时间戳 → 本地时区的 0~23 点（接口只给 UTC 秒数，估算紫外线要本地时刻） */
expect fun hourOfDayFromEpochSeconds(epochSeconds: Long): Int

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

/** 公历日期，1 基（month 1~12、day 1~31）。只读值对象，不放逻辑 */
data class CivilDate(val year: Int, val month: Int, val day: Int)

/**
 * 儒略日数 → 公历日期（Howard Hinnant 的 civil_from_days）。
 *
 * 是 [epochDayFromCivil] 的逆函数，纯数学、零平台 API。之所以不新增一对
 * expect/actual 的 currentYear()/currentDayOfMonth()：那样两端各写一份
 * Calendar/NSCalendar 取值，而这份仓库已有的 todayEpochDay() 只要换算一次，
 * 就同时得到年月日，且与既有 epochDayFromCivil 天然互为校验。
 */
fun civilFromEpochDay(epochDay: Long): CivilDate {
    val z = epochDay + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
    return CivilDate((if (month <= 2) y + 1 else y).toInt(), month, day)
}

/** 今天的公历日期（本地时区）。引擎取「当下」只经这里，便于单测替换 */
fun todayCivilDate(): CivilDate = civilFromEpochDay(todayEpochDay())

/**
 * 儒略日数 → 中文星期（“星期一”…“星期日”）。
 * epoch day 0 是星期四，所以 +3 取模后 0=星期一。
 */
fun weekdayNameFromEpochDay(epochDay: Long): String {
    val names = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
    val index = (((epochDay + 3) % 7) + 7).rem(7).toInt()
    return names[index]
}
