package probe

import platform.UIKit.UIScreen

/**
 * iOS 侧的闸门。
 *
 * 这里刻意**不**照搬 Android 的写法。Android 上 `supportsRealtimeBlur` 表示
 * "系统有没有 RenderEffect"（API 31 门槛），而 iOS 的背景模糊由
 * `UIVisualEffectView` 提供，早年是 UIKit-only、没有 Compose 通道，
 * 真正要确认的是 **Haze 的 iOS 产物到底把模糊做了什么实现** —— 那只能等这个
 * 探针编过之后再在真机/模拟器上看效果，不能靠这里返回一个 true 就当作成立。
 *
 * 所以这个 actual 只回答"编译期有没有这个 API"，不回答"运行时好不好看"。
 * 后者必须用截图验收，这也是简衣这个项目一贯的做法。
 */
actual val supportsRealtimeBlur: Boolean = true

/** 探针自身的存在性检查：能拿到 UIScreen 说明 iOS 平台库确实链进来了 */
fun mainScreenScale(): Double = UIScreen.mainScreen.scale.toDouble()
