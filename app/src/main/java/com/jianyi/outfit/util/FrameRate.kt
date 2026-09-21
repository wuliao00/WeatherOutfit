package com.jianyi.outfit.util

import android.os.Build
import android.view.View

/**
 * 高帧率申请。
 *
 * Android 从 12 起把「选哪个刷新率」收归系统统一调度：即使手机是 120Hz 屏，
 * 普通 App 默认也只会被分到 60Hz，因为系统要省电。想跑满必须显式表态。
 *
 * 这里用的是 View 的「帧率类别」通道（Android 12+）：
 * `setRequestedFrameRate(REQUESTED_FRAME_RATE_CATEGORY_HIGH)`
 * 告诉 SurfaceFlinger 本图层属于高帧内容类别，由它挑该设备可用的最高档。
 *
 * 之所以不写死 120：设备可能是 90/120/144Hz，写死数字反而会被调度器降回默认档；
 * 交类别给系统判断，才是各机型都能生效的写法。
 */
object FrameRate {

    fun request(view: View, high: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.requestedFrameRate =
                if (high) {
                    View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
                } else {
                    View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE
                }
        }
    }
}
