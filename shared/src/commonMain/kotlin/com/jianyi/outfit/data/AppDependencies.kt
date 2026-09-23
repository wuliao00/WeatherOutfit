package com.jianyi.outfit.data

import com.jianyi.outfit.data.repository.SettingsRepository
import com.jianyi.outfit.data.repository.OutfitTemplateRepository
import com.jianyi.outfit.data.repository.WeatherRepository

/**
 * ViewModel 依赖接缝。
 *
 * 之前 ViewModel 直接拿 `WeatherOutfitApp` 再取 `app.container`——那把 DI 钉死在
 * Application 类上，ViewModel 就永远出不了 Android。现在 ViewModel 只依赖这个接口，
 * 实现就是 app 的 [com.jianyi.outfit.di.AppContainer]（它本来就持有这些东西）。
 *
 * 刻意不放 cityRepository：它的签名泄漏 Room 的 CityEntity，等 Room 2.7 KMP 升级。
 * 刻意不放 sceneryController / locationUtil / notifier：HomeViewModel 还在 app，
 * 等它们的平台点抽象完再进接口，避免接口一次摊太大。
 */
interface AppDependencies {
    val weatherRepository: WeatherRepository
    val settingsRepository: SettingsRepository
    val templateRepository: OutfitTemplateRepository

    /** 每日推送调度能力：Android = WorkManager 周期任务，iOS = 预定本地通知 */
    val pushScheduler: PushScheduler
}

/**
 * 每日推送的调度能力（原 SettingsViewModel 直接调 WorkManager 的静态工具类）。
 * 两端语义对齐：ensureScheduled 注册/更新「每天 pushHour 整」的持久化任务，
 * cancel 撤销之。iOS 实现是预定本地通知，天然跨重启，不需要额外恢复逻辑。
 */
interface PushScheduler {
    fun ensureScheduled(pushHour: Int)
    fun cancel()
}
