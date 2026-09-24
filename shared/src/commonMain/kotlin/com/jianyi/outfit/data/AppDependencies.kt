package com.jianyi.outfit.data

import com.jianyi.outfit.data.repository.CityRepository
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
 * 刻意不放 locationUtil / 通知管理器的具体类型：定位与通知是平台能力，
 * 抽象成 [locationProvider] / [extremeAlerter] 才能两端各写一份实现。
 *
 * cityRepository 以前也不能放——它的签名带 Room 的 CityEntity，而 Room 只有
 * Android 产物。Room 进 KMP 之后这个障碍没了，城市页因此能整体下沉；
 * [currentCity] 仍然保留，首页只要省市两个字段，不该为了它去感知表结构。
 */
interface AppDependencies {
    val weatherRepository: WeatherRepository
    val settingsRepository: SettingsRepository
    val templateRepository: OutfitTemplateRepository

    /** 历史城市与当前城市切换（城市页的全部数据来源） */
    val cityRepository: CityRepository

    /** 每日推送调度能力：Android = WorkManager 周期任务，iOS = 预定本地通知 */
    val pushScheduler: PushScheduler

    /** 当前手动选中的城市；null 表示走自动定位链（GPS 优先 → IP 兜底） */
    val currentCity: Flow<CitySelection?>

    val locationProvider: LocationProvider
    val notificationGate: NotificationGate
    val extremeAlerter: ExtremeAlerter

    /** 高帧率申请的状态视图（Android 独有；iOS 实现恒为「不支持」） */
    val highFrameRate: HighFrameRateApi

    /** 风景换景状态机：跨页面连续，所以是容器级单例而不是某个 VM 的私有状态 */
    val sceneryController: SceneryController
}

/**
 * 高帧率申请的可观察状态。
 *
 * 设置页要把它显示成人话（「已按 120Hz 申请」「系统未接受精确值」），
 * 而这些数字来自 Android 的 View/Display API。抽象出来的另一个作用是
 * 让设置页不再 import 那个 Android-only 的工具对象。
 */
interface HighFrameRateApi {
    /** 本机是否可能支持高帧率申请（Android 15+ 才有对应 API） */
    val isSupported: Boolean

    /** 实际发出去的精确档位（Hz）；0 表示没发精确值 */
    val requestedHz: Float

    /** 锁模式成功后实际生效的档位（Hz） */
    val lockedHz: Float

    /** 精确值通道是否被系统接受 */
    val exactChannelAccepted: Boolean

    /** 被锁定的显示模式 id；0 表示没锁 */
    val lockedModeId: Int

    /** 读不到面板档位、退到「类别档」通道时为 true（设置页要如实说明这是退让） */
    val usedCategoryFallback: Boolean
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
 * 弹窗只发生在用户明确点了「使用精确定位」之后。
 */
interface LocationProvider {

    /** 系统是否已授予定位权限（未授予时调用方应直接降级 IP，不要发起定位） */
    fun hasPermission(): Boolean

    /** 最近一次已知位置；拿不到返回 null（未授权 / 定位服务关闭 / 无缓存） */
    suspend fun lastKnown(): GeoPoint?

    /**
     * 请求定位授权，结果在弹窗关闭后回调。
     *
     * 已授予时**直接回调 true 不弹窗** —— 这样调用方可以无脑写成
     * 「requestPermission { if (it) 精确定位 else 提示降级 }」，
     * 三个页面原本各抄了一份「先自查再 launch」的样板，语义还不完全一样。
     */
    fun requestPermission(onResult: (Boolean) -> Unit)
}

/**
 * 通知授权能力。
 *
 * Android 13 起 POST_NOTIFICATIONS 是运行时权限，更早的版本没有这个概念
 * （装了就有），所以实现要在「无需申请」时直接回调 true；
 * iOS 对应 UNUserNotificationCenter.requestAuthorization，语义正好同构。
 */
interface NotificationGate {

    fun hasPermission(): Boolean

    fun requestPermission(onResult: (Boolean) -> Unit)
}

/** 极端天气预警通知：拿到预警且用户开了开关时立即发一条 */
interface ExtremeAlerter {
    fun show(title: String, text: String)
}
