package com.jianyi.outfit.platform

import com.jianyi.outfit.data.ExtremeAlerter
import com.jianyi.outfit.data.GeoPoint
import com.jianyi.outfit.data.HighFrameRateApi
import com.jianyi.outfit.data.LocationProvider
import com.jianyi.outfit.data.NotificationGate
import com.jianyi.outfit.data.PushScheduler
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.Foundation.NSDateComponents
import platform.Foundation.NSError
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSettings
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNNotificationTrigger
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * iOS 侧的平台能力实现 —— Android 那边几个 `Android*` 类的对位件。
 * 两边都只实现 commonMain 里的接口，所以 ViewModel 与页面不知道平台差异。
 *
 * ⚠ 这一整份只有 **iOS 编译**验证（CI 的 :shared iosArm64），没有真机跑过：
 * 本机 Windows 不参与 iOS 编译，也没有 Mac 可装。所以只用最直白的调用形状。
 *
 * 一个统一的命名约定：ObjC 的 **property** 在 Kotlin/Native 里是属性
 * （`NSCalendar.currentCalendar`、`NSDate.timeIntervalSince1970` 都是这么用的，
 * 已在本项目其它 iOS 文件里编过），ObjC 的**类方法**才是函数
 * （`+currentNotificationCenter` → `currentNotificationCenter()`）。
 */

/* ==================== 定位 ==================== */

private fun authorized(status: CLAuthorizationStatus): Boolean =
    status == kCLAuthorizationStatusAuthorizedWhenInUse ||
        status == kCLAuthorizationStatusAuthorizedAlways

/**
 * CLLocationManager 封装。
 *
 * 接口方法名叫 `lastKnown()`（commonMain 定的），但 iOS 上没有可靠的"上次位置"可查：
 * 新建的 CLLocationManager 从没跑过就是 `location == nil`。所以这里做成
 * **一次性定位 + 10 秒封顶**：拿第一个 fix 就返回，超时给 null，
 * 由首页按既有逻辑回退 IP 定位。超时是必须的 —— requestLocation 在无服务、
 * 用户拒授权这些情况下可能一个回调都不发，不给的话首页会永远转圈。
 */
@OptIn(ExperimentalForeignApi::class)
class IosLocationProvider : LocationProvider {

    private val manager = CLLocationManager()

    /** delegate 在 CLLocationManager 里是 weak，必须自己持有，否则回调永远不来 */
    private val delegate = LocationDelegate()

    init {
        manager.delegate = delegate
    }

    override fun hasPermission(): Boolean = authorized(manager.authorizationStatus)

    override suspend fun lastKnown(): GeoPoint? {
        if (!hasPermission()) return null
        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                // 超时或页面取消时把回调摘掉：之后系统再回吐位置也不会有人 resume 第二次
                cont.invokeOnCancellation { delegate.onLocation = null }
                delegate.onLocation = { point -> if (cont.isActive) cont.resume(point) }
                manager.requestLocation()
            }
        }
    }

    /** 已授权就直接回调 true、不弹窗（与 Android 侧同一约定） */
    override fun requestPermission(onResult: (Boolean) -> Unit) {
        if (hasPermission()) {
            onResult(true)
            return
        }
        delegate.onAuthorization = { granted -> onResult(granted) }
        manager.requestWhenInUseAuthorization()
    }

    /** 10 秒 */
    private companion object {
        const val LOCATION_TIMEOUT_MS = 10_000L
    }

    /** CLLocationManagerDelegate 的实现体，只实现用到的三个回调 */
    private class LocationDelegate : NSObject(), CLLocationManagerDelegateProtocol {
        var onLocation: ((GeoPoint?) -> Unit)? = null
        var onAuthorization: ((Boolean) -> Unit)? = null

        override fun locationManager(
            manager: CLLocationManager,
            didChangeAuthorizationStatus: CLAuthorizationStatus
        ) {
            // 注册时系统会立刻回吐一次当前状态，那一次通常是 NotDetermined；
            // 只有真变成决定态（授权或拒绝）才算用户答复过
            if (didChangeAuthorizationStatus == kCLAuthorizationStatusNotDetermined) return
            val callback = onAuthorization
            onAuthorization = null
            callback?.invoke(authorized(didChangeAuthorizationStatus))
        }

        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val last = didUpdateLocations.lastOrNull() as? CLLocation ?: return
            val callback = onLocation
            onLocation = null
            callback?.invoke(GeoPoint(last.coordinate.latitude, last.coordinate.longitude))
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError?) {
            val callback = onLocation
            onLocation = null
            callback?.invoke(null)
        }
    }
}

/* ==================== 通知 ==================== */

/**
 * 通知授权。
 *
 * iOS 查授权状态只有异步一条路（getNotificationSettingsWithCompletionHandler），
 * 而 commonMain 的 `hasPermission()` 是同步的。这里不假装能同步拿到，改成**快照**：
 * 构造时刷一次、每次申请授权后再刷一次，同步读的就是这份快照。
 * 代价是"用户跑去系统设置里手改授权"要等下次刷新才反映。
 * 换来的是另一件事：不为了填一个 Boolean 去阻塞调用线程 ——
 * 这版最初写成信号量同步等，那会在主线程死锁，而且 Darwin 的 sem_t 在
 * Kotlin/Native 里根本不该那么用。
 */
@OptIn(ExperimentalForeignApi::class)
class IosNotificationGate : NotificationGate {

    private var snapshot: Boolean = false

    init {
        refresh()
    }

    override fun hasPermission(): Boolean = snapshot

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        if (center == null) {
            onResult(false)
            return
        }
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { granted: Boolean, error: NSError? ->
            snapshot = granted
            onResult(granted)
        }
    }

    /** 刷新快照（回前台时可以再调一次） */
    fun refresh() {
        UNUserNotificationCenter.currentNotificationCenter()
            ?.getNotificationSettingsWithCompletionHandler { settings: UNNotificationSettings? ->
                if (settings != null) snapshot = settings.authorizationStatus == UNAuthorizationStatusAuthorized
            }
    }
}

/**
 * 每日推送。
 *
 * Android 那边要 WorkManager 周期任务 + 开机广播"恢复"；iOS 只要一条**重复的
 * 日历触发本地通知**：预定通知由系统持久化，天然跨重启、跨杀进程 ——
 * 这正是"每日推送 + 重启后自动恢复要重新设计"的答案：不用设计，系统替做了。
 * 只给 hour/minute 两个分量，系统就理解为"每天"。
 */
@OptIn(ExperimentalForeignApi::class)
class IosPushScheduler : PushScheduler {

    override fun ensureScheduled(pushHour: Int) {
        val center = UNUserNotificationCenter.currentNotificationCenter() ?: return
        center.removePendingNotificationRequestsWithIdentifiers(listOf(DAILY_ID))
        val components = NSDateComponents().apply {
            hour = pushHour.coerceIn(0, 23).toLong()
            minute = 0L
        }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateComponents(components, repeats = true)
        center.addRequest(
            request = buildRequest(DAILY_ID, "今天的穿搭建议", "打开简衣看看今天穿什么合适", trigger),
            withCompletionHandler = null
        )
    }

    override fun cancel() {
        UNUserNotificationCenter.currentNotificationCenter()
            ?.removePendingNotificationRequestsWithIdentifiers(listOf(DAILY_ID))
    }

    private companion object {
        const val DAILY_ID = "jianyi.daily"
    }
}

/**
 * 极端天气预警：立刻发一条本地通知。
 *
 * 明写降级：本地通知只能由设备自己算出来，所以必须这台设备前/后台跑过一次天气刷新
 * 才可能触发预警；要做到 Android 那种"随时推"得接 APNs + 后端。README 里同样写明了。
 */
@OptIn(ExperimentalForeignApi::class)
class IosExtremeAlerter : ExtremeAlerter {

    /** 同一进程内递增，避免同一小时的重复预警互相顶掉、也避免刷一列通知 */
    private var seq = 0

    override fun show(title: String, text: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter() ?: return
        seq += 1
        // iOS 不接受 timeInterval = 0，1 秒即"现在就发"
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, repeats = false)
        center.addRequest(
            request = buildRequest("jianyi.alert.$seq", title, text, trigger),
            withCompletionHandler = null
        )
    }
}

/** 组一条通知请求；两个发通知的类共用 */
private fun buildRequest(
    identifier: String,
    title: String,
    body: String,
    trigger: UNNotificationTrigger
): UNNotificationRequest =
    UNMutableNotificationContent().apply {
        this.title = title
        this.body = body
        // ObjC 这边是 class property（同 NSCalendar.currentCalendar），
        // Kotlin/Native 把 property 导成属性而不是函数
        sound = UNNotificationSound.defaultSound
    }.let { content ->
        UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = trigger
        )
    }

/* ==================== 高帧率 ==================== */

/**
 * iOS 没有"申请高帧率"这个 API：ProMotion 由系统按内容自行决定。
 * 全报"不支持"，设置页那一项于是自动隐藏 —— 与 Android 上老机器的表现一致。
 */
object IosHighFrameRate : HighFrameRateApi {
    override val isSupported: Boolean = false
    override val requestedHz: Float = 0f
    override val lockedHz: Float = 0f
    override val exactChannelAccepted: Boolean = false
    override val lockedModeId: Int = 0
    override val usedCategoryFallback: Boolean = false
}
