package com.jianyi.outfit.platform

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** minSdk 26，java.time 可用；toEpochDay 与 commonMain 的纯算术同源（都是 proleptic Gregorian） */
actual fun todayEpochDay(): Long = java.time.LocalDate.now().toEpochDay()

/** 与旧实现逐字一致：SimpleDateFormat("HH:mm", 系统默认 Locale) */
actual fun localClockText(epochSeconds: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000))

/** 与迁移前逐字一致：SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) */
actual fun localDateHourText(epochSeconds: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(epochSeconds * 1000))

/** 与迁移前逐字一致：Calendar.getInstance() 换 timeInMillis 后取 HOUR_OF_DAY */
actual fun hourOfDayFromEpochSeconds(epochSeconds: Long): Int =
    Calendar.getInstance().apply { timeInMillis = epochSeconds * 1000 }.get(Calendar.HOUR_OF_DAY)
