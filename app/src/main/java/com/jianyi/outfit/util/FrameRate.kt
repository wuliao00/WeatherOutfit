package com.jianyi.outfit.util

import android.os.Build
import android.view.View

/**
 * 高帧率申请。
 *
 * Android 从 12 起把「选哪个刷新率」收归系统统一调度：即使手机是 120Hz 屏，
 * 普通 App 默认也只会被分到 60Hz，因为系统要省电。想跑满必须显式表态。
 *
 * 这里用的是 View 的「帧率类别」通道：
 * `setRequestedFrameRate(REQUESTED_FRAME_RATE_CATEGORY_HIGH)`
 * 告诉 SurfaceFlinger 本图层属于高帧内容类别，由它挑该设备可用的最高档。
 *
 * 为什么不写死 120：设备可能是 90/120/144Hz，写死数字反而会被调度器降回默认档；
 * 交类别给系统判断，才是各机型都能生效的写法。
 *
 * ⚠ 守卫必须是 API 35，不是 31。lint 实测 `View#setRequestedFrameRate`
 * 要求 API 35 —— 早先用 VERSION_CODES.S(31) 守卫会在 Android 12~14 上
 * 直接抛 NoSuchMethodError。31~34 的设备这里什么都不做，
 * 由系统自行调度，不影响功能，只是拿不到额外帧率档。
 */
object FrameRate {

    /** 该方法可用的最低系统版本 */
    private const val MIN_SDK = Build.VERSION_CODES.VANILLA_ICE_CREAM  // API 35

    fun request(view: View, high: Boolean) {
        if (Build.VERSION.SDK_INT < MIN_SDK) return
        view.requestedFrameRate =
            if (high) {
                View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
            } else {
                View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE
            }
    }

    /** 当前设备是否具备申请高帧率的能力（设置页据此解释开关为何无效） */
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= MIN_SDK
}
