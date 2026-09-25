package com.jianyi.outfit.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 取位总预算：8 秒。
 *
 * **为什么必须有上限**：2026-09-24 在 vivo V2156A（Android 11）真机实测，城市页点
 * 「GPS 定位」后界面停在「定位中…」约 25 秒才自己回弹。FusedLocation 的
 * `getCurrentLocation` 在信号差、系统没有缓存位置、用户平时从不开定位这些场景下
 * 可以几十秒不回调、甚至永不回调（Task 没有 cancel 能力），所以不能等它自己结束。
 * iOS 侧的 IosLocationProvider 早就是「一次性定位 + 封顶」同一套契约。
 *
 * **为什么是 8 秒这个量级**：上限要明显短到用户不会以为程序卡死（十几秒以上就会，
 * 实测那次 25 秒已经是"看起来死了"）；下限要盖住一次真实的融合定位 ——
 * `lastLocation` 读的是本地缓存、毫秒级，只有 `getCurrentLocation` 要真跑一轮
 * Wi-Fi/基站定位，正常 1~5 秒出结果，8 秒留了近一倍余量，不会把「慢但能成」砍掉。
 * iOS 用 10 秒，因为它没有缓存这一档、只有一次 `requestLocation`。
 *
 * **超时后调用方的行为不变**：`withTimeoutOrNull` 给的是 null 而不是异常，
 * AndroidLocationProvider.lastKnown → HomeViewModel / CityViewModel 沿用既有的
 * 「拿不到坐标就回退 IP 定位 / 提示改用搜索」分支。本函数内部没有任何重试，
 * 超时只是**放弃等待**、不会重发定位请求，因此也不会多出并发定位。
 */
internal const val LOCATION_BUDGET_MS = 8_000L

/**
 * 定位工具：封装 FusedLocationProviderClient（Google Play Services）。
 * 权限由调用方（UI 层）先行申请；设备无 Play Services 时返回 null，
 * 调用方应回退到 IP 定位。
 */
class LocationUtil(context: Context) {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * 获取最近一次位置；没有缓存位置时发起一次平衡精度请求。
     * 整段受 [LOCATION_BUDGET_MS] 总超时约束，超时返回 null（不抛异常）。
     * 需要调用方已获得 ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION 权限。
     */
    suspend fun lastKnownLocation(): Location? = acquireWithinBudget(
        budgetMs = LOCATION_BUDGET_MS,
        cached = { cachedLocation() },
        fresh = { fusedLocation() }
    )

    /** 系统缓存的最近位置：本地读取，毫秒级，通常这一步就出结果 */
    @SuppressLint("MissingPermission")
    private suspend fun cachedLocation(): Location? = client.lastLocation.awaitOrNull()

    /** 没有缓存时才要一次真实定位（网络/Wi-Fi/基站融合），慢的时候几十秒不回 */
    @SuppressLint("MissingPermission")
    private suspend fun fusedLocation(): Location? =
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).awaitOrNull()

    companion object {
        /**
         * 是否已授予任一定位权限。
         * 冷启动「GPS 优先」策略的静默前置条件：未授权时不触发定位请求，
         * 直接走 IP 兜底，避免无权限异常与打扰。
         */
        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * 在 [budgetMs] 的总预算内依次尝试两个取位来源，先拿到结果者胜（缓存优先于新请求）。
 *
 * 之所以做成与 Android / Play Services 无关的泛型函数：这样「超时确实生效」这件事
 * 能在普通 JVM 单测里用挂起的假实现驱动。本项目的 app 测试没有 Robolectric，
 * `android.location.Location` 和 GMS 的 `Task` 在 JVM 上都构造不出来，
 * 而这段逻辑的全部风险恰恰集中在超时分支上 —— 只测编得过等于没测。
 */
internal suspend fun <T : Any> acquireWithinBudget(
    budgetMs: Long,
    cached: suspend () -> T?,
    fresh: suspend () -> T?
): T? = withTimeoutOrNull(budgetMs) { unavailableAsNull(cached) ?: unavailableAsNull(fresh) }

/**
 * 「来源不可用」和「取不到位置」在这层是同一件事：无权限时部分机型同步抛
 * SecurityException，无 Play Services / 定位服务未开启时 Task 以失败结束 ——
 * 一律收敛成 null，让上层统一走 IP 兜底而不是弹错误。
 *
 * 唯一的例外是取消：超时正是靠 CancellationException 生效的。顺手把它一起吞掉，
 * 表现是「第一个来源超时后，又给了第二个来源同样的时间」，总时长突破预算，
 * 所以这里原样上抛，交给外层 withTimeoutOrNull 收成 null。
 */
private suspend fun <T : Any> unavailableAsNull(source: suspend () -> T?): T? =
    try {
        source()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

/** 将 Google Play Services 的 Task 转为挂起函数，失败/取消时返回 null */
private suspend fun <T : Any> Task<T>.awaitOrNull(): T? =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            continuation.resume(if (task.isSuccessful) task.result else null)
        }
    }
