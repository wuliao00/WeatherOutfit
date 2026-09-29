package com.jianyi.outfit.data.update

import android.util.Log

/**
 * 真机侧唯一的可见方式：`adb logcat -s JianyiUpdate`。
 * tag 单独一个，是因为这条日志出现的时机（清单坏了）需要能从满屏日志里直接捞出来。
 */
internal actual fun logUpdateWarning(message: String) {
    Log.w("JianyiUpdate", message)
}
