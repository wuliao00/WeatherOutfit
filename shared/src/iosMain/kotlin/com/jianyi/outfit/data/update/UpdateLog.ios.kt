package com.jianyi.outfit.data.update

import platform.Foundation.NSLog

/**
 * iOS 侧的落地方式。用 `%@` 而不是把 message 直接当格式串：
 * 一旦串里出现 `%`（URL 里百分号编码就可能），裸串会被 NSLog 当格式说明符解析而打错。
 *
 * 这条 actual 在功能上其实用不到 —— iOS 不允许应用内自装，`AppUpdateGateway.supported` 恒 false，
 * 门禁 UI 整块不显示。但 commonMain 的 expect 必须有 actual 才能编过，
 * 而且"清单坏了"在 iOS 上也该留一条痕迹（清单是两端共享的同一个文件）。
 */
internal actual fun logUpdateWarning(message: String) {
    NSLog("JianyiUpdate: %@", message)
}
