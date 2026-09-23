package com.jianyi.outfit.data

import com.jianyi.outfit.data.repository.OutfitTemplateRepository
import com.jianyi.outfit.data.repository.SettingsRepository
import com.jianyi.outfit.data.repository.WeatherRepository
import com.jianyi.outfit.ui.scenery.SceneryController
import kotlinx.coroutines.flow.Flow

/**
 * ViewModel 依赖接缝。
 *
 * 之前 ViewModel 直接拿 `WeatherOutfitApp` 再取 `app.container`——那把 DI 钉死在
 * Application 类上，ViewModel 就永远出不了 Android。现在 ViewModel 只依赖这个接口，
 * 实现就是 app 的 [com.jianyi.outfit.di.AppContainer]（它本来就持有这些东西）。
 *
 * 刻意不放 cityRepository / locationUtil 的具体类型：
 * CityRepository 的签名泄漏 Room 的 CityEntity（等 Room 2.7 KMP 升级），
 * 所以这里只暴露 HomeViewModel 真正需要的最小视图 [currentCity]；
 * 定位与通知是平台能力，抽象成 [locationProvider] / [extremeAlerter]。
 */
interface AppDependencies {
    val weatherRepository: WeatherRepository
    val settingsRepository: SettingsRepository
    val templateRepository: OutfitTemplateRepository

    /** 每日推送调度能力：Android = WorkManager 周期任务，iOS = 预定本地通知 */
    val pushScheduler: PushScheduler

    /** 当前手动选中的城市；null 表示走自动定位链（GPS 优先 → IP 兜底） */
    val currentCity: Flow<CitySelection?>

    val locationProvider: LocationProvider
    val extremeAlerter: ExtremeAlerter

    /** 风景换景状态机：跨页面连续，所以是容器级单例而不是某个 VM 的私有状态 */
    val sceneryController: SceneryController
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

/** 经纬度坐标（不把 Android 的 Location 类型拖进跨端代码） */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** 历史城市的最小视图：HomeViewModel 只需要省 + 市两个字段 */
data class CitySelection(val province: String, val city: String)

/**
 * 定位能力。
 *
 * 刻意只有「静默取最近一次定位」而没有「发起定位」：现有交互是
 * 有权限就静默读缓存位置、没权限直接降级 IP，从不主动弹窗打扰用户。
 */
interface LocationProvider {

    /** 系统是否已授予定位权限（未授予时调用方应直接降级 IP，不要发起定位） */
    fun hasPermission(): Boolean

    /** 最近一次已知位置；拿不到返回 null（未授权 / 定位服务关闭 / 无缓存） */
    suspend fun lastKnown(): GeoPoint?
}

/** 极端天气预警通知：拿到预警且用户开了开关时立即发一条 */
interface ExtremeAlerter {
    fun show(title: String, text: String)
}
