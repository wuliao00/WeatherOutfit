package com.jianyi.outfit.platform

import com.jianyi.outfit.data.AppUpdateGateway
import com.jianyi.outfit.data.InstallResult

/**
 * iOS 不提供应用内更新：App Store 规则不允许自建更新通道，
 * 引导去商店也只能用 `itms-apps://`，而这个包现在根本不在商店里。
 *
 * supported=false 会让 gate 层与设置页入口整块不渲染，
 * 所以这里的方法在 iOS 上永远不该被调用 —— 仍然给出确定返回值而不是抛异常，
 * 免得将来有人加一条调用路径就把 App 弄崩。
 */
class IosUpdateGateway : AppUpdateGateway {
    override val supported: Boolean = false
    override fun hasInstallPermission(): Boolean = false
    override fun requestInstallPermission() = Unit
    override fun install(apkPath: String): InstallResult = InstallResult.Failed
}
