package com.jianyi.outfit.platform

/**
 * 平台墙钟毫秒。
 *
 * 为什么要有这个文件：`shared/commonMain` 刻意保持零依赖（见 build.gradle.kts），
 * 所以不为了一个时间戳去引 kotlinx-datetime。`System.currentTimeMillis()` 是 JVM-only，
 * 它在 Android 上编得好好的，只有 iOS 编译会当场报 "Unresolved reference 'System'" ——
 * 这个坑是 CI 的 :shared iOS job 抓出来的，本机验证不到。
 */
expect fun currentTimeMillis(): Long

/**
 * 当前月份，**一律返回 1~12**。
 *
 * 之所以单独开一个函数而不是让调用方自己 +1：Java 的 `Calendar.MONTH` 是 0 基的，
 * 而 iOS 的 `NSCalendar` 拿到的月份本来就是 1 基。两边都写 `+1` 的话，
 * Android 正确、iOS 变成 2~13，而「秋季换装感」这类规则会在 iOS 上悄悄选错风景，
 * 不崩溃、不报错，只是偶尔不对。
 */
expect fun currentMonth(): Int
