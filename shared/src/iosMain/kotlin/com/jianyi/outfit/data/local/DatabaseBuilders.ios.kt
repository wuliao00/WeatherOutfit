package com.jianyi.outfit.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * iOS 侧开库。与 androidMain 那份同名，区别只有两处：
 *
 * 1. 路径要自己给。Android 由 Context 推出 `databases/jianyi.db`，
 *    iOS 走沙盒 Documents 目录，文件名保持一致。
 * 2. **必须显式 setDriver**。这不是风格问题：Room 2.7.2 的 native
 *    `RoomDatabase.Builder.build()` 第一行就是
 *    `requireNotNull(driver) { "Cannot create a RoomDatabase without providing a
 *    SQLiteDriver via setDriver()." }` —— 平台侧没有 Android 那套框架驱动可兜底，
 *    漏掉就是启动即崩，而 iOS 这边我们只能编译、跑不到，所以只能靠读源码钉死。
 *    驱动来自 `androidx.sqlite:sqlite-bundled`（自带编译好的 sqlite3 二进制）。
 */
fun buildAppDatabase(): AppDatabase =
    Room.databaseBuilder<AppDatabase>(name = databaseFile("jianyi.db"))
        .setDriver(BundledSQLiteDriver())
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .build()

/** 天气缓存库（独立文件，理由见 WeatherCacheDatabase 的注释） */
fun buildWeatherCacheDatabase(): WeatherCacheDatabase =
    Room.databaseBuilder<WeatherCacheDatabase>(name = databaseFile("weather_cache.db"))
        .setDriver(BundledSQLiteDriver())
        .build()

/**
 * 沙盒 Documents 目录 + 文件名。
 *
 * `error = null` 那个参数是 `CPointer<ObjCObjectVar<NSError?>>?`，属于 C 互操作面，
 * Kotlin 2.x 要求显式 @OptIn(ExperimentalForeignApi) —— 本机是 Windows，iOS target
 * 直接不参与编译，这个错只能由 CI 的 mac job 报出来（第一次推就报了）。
 */
@OptIn(ExperimentalForeignApi::class)
private fun databaseFile(name: String): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null
    )
    return requireNotNull(url?.path) { "取不到 iOS 沙盒 Documents 目录" } + "/$name"
}
