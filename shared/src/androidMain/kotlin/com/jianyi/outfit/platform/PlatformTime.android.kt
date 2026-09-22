package com.jianyi.outfit.platform

import java.util.Calendar

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

/** Calendar.MONTH 是 0 基，这里 +1 统一成 1~12 */
actual fun currentMonth(): Int = Calendar.getInstance().get(Calendar.MONTH) + 1
