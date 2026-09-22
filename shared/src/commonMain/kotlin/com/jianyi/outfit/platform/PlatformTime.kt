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
