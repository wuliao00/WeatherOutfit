package com.jianyi.outfit.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.jianyi.outfit.data.GeoPoint
import com.jianyi.outfit.data.HighFrameRateApi
import com.jianyi.outfit.data.LocationProvider
import com.jianyi.outfit.data.NotificationGate
import java.lang.ref.WeakReference

/**
 * 当前可用来弹窗的 Activity。
 *
 * 权限弹窗必须有 Activity，而依赖容器只拿得到 applicationContext。
 * MainActivity 在 onCreate 挂上来、onDestroy 摘掉；用 WeakReference 是
 * 防止容器（生命周期等于进程）把 Activity 钉住不放。
 */
class ActivityHolder {

    private var ref: WeakReference<ComponentActivity>? = null

    fun attach(activity: ComponentActivity) {
        ref = WeakReference(activity)
    }

    fun detach(activity: ComponentActivity) {
        if (ref?.get() === activity) ref = null
    }

    /** 拿不到（没起来 / 已销毁 / 正在收尾）就返回 null，调用方按「授权失败」处理 */
    fun get(): ComponentActivity? = ref?.get()?.takeIf { !it.isDestroyed && !it.isFinishing }
}

/**
 * 一次性注册 → 回调 → 立刻注销。
 *
 * 用 activityResultRegistry 而不是 compose 的 rememberLauncherForActivityResult：
 * 后者只能在 composable 里注册，会把权限能力锁死在 Android UI 层，
 * 而这套接口最终要能被 shared 里的页面调用。
 */
private fun <I, O> ComponentActivity.requestOnce(
    key: String,
    contract: ActivityResultContract<I, O>,
    input: I,
    onResult: (O) -> Unit
) {
    var launcher: ActivityResultLauncher<I>? = null
    launcher = activityResultRegistry.register(key, contract) { result ->
        launcher?.unregister()
        onResult(result)
    }
    launcher.launch(input)
}

/** 定位能力：权限自查 + FusedLocation 最近位置 + 运行时授权 */
class AndroidLocationProvider(
    private val context: Context,
    private val locationUtil: LocationUtil,
    private val activities: ActivityHolder
) : LocationProvider {

    override fun hasPermission(): Boolean = LocationUtil.hasLocationPermission(context)

    override suspend fun lastKnown(): GeoPoint? =
        locationUtil.lastKnownLocation()?.let { GeoPoint(it.latitude, it.longitude) }

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        if (hasPermission()) {
            onResult(true)
            return
        }
        val activity = activities.get() ?: run { onResult(false); return }
        activity.requestOnce(
            KEY,
            ActivityResultContracts.RequestMultiplePermissions(),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        ) { grants -> onResult(grants.values.any { it }) }
    }

    private companion object {
        const val KEY = "jianyi-location-permission"
    }
}

/**
 * 通知授权。
 *
 * Android 13 之前没有运行时通知权限（装了就能发），所以 hasPermission 在非 TIRAMISU
 * 直接返回 true —— 设置页因此可以无脑写「requestPermission { granted -> setDailyPush(granted) }」，
 * 不必再自己抄一遍版本号判断。
 */
class AndroidNotificationGate(
    private val context: Context,
    private val activities: ActivityHolder
) : NotificationGate {

    private val needsRuntimeGrant: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun hasPermission(): Boolean = !needsRuntimeGrant ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        if (hasPermission()) {
            onResult(true)
            return
        }
        val activity = activities.get() ?: run { onResult(false); return }
        activity.requestOnce(
            KEY,
            ActivityResultContracts.RequestPermission(),
            Manifest.permission.POST_NOTIFICATIONS
        ) { granted -> onResult(granted) }
    }

    private companion object {
        const val KEY = "jianyi-notification-permission"
    }
}

/** 高帧率状态转发：设置页只认接口，不 import Android-only 的 FrameRate 工具 */
object AndroidHighFrameRate : HighFrameRateApi {
    override val isSupported: Boolean get() = FrameRate.isSupported
    override val requestedHz: Float get() = FrameRate.requestedHz
    override val lockedHz: Float get() = FrameRate.lockedHz
    override val exactChannelAccepted: Boolean get() = FrameRate.exactChannelAccepted
    override val lockedModeId: Int get() = FrameRate.lockedModeId
    override val usedCategoryFallback: Boolean get() = FrameRate.usedCategoryFallback
}
