package com.jianyi.outfit.platform

import androidx.compose.runtime.Composable
import platform.Foundation.NSBundle

/**
 * iOS 侧取 Info.plist 的 CFBundleShortVersionString。
 *
 * 不走 BuildConfig 之类：那是 Android 产物。取不到（键缺失或类型不是字符串）返回 null，
 * 由 UI 显示「版本未知」——与 Android 侧同一套约定，不在平台层编一个兜底版本号。
 */
@Composable
actual fun installedVersionName(): String? =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
