package com.jianyi.outfit.platform

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** minSdk 26，java.time 可用；toEpochDay 与 commonMain 的纯算术同源（都是 proleptic Gregorian） */
actual fun todayEpochDay(): Long = java.time.LocalDate.now().toEpochDay()

/** 与旧实现逐字一致：SimpleDateFormat("HH:mm", 系统默认 Locale) */
actual fun localClockText(epochSeconds: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000))
