package com.jianyi.outfit.update

import android.content.Context
import com.jianyi.outfit.data.AppUpdateGateway
import com.jianyi.outfit.data.InstallResult

/**
 * Android 侧实现。本 Task 只放能编译过的最小骨架（supported 恒 true、
 * install 走 Failed），Task 8 补 FileProvider 与安装 Intent ——
 * 先把接口面钉住，让 shared 的 UI/VM 能并行推进。
 */
class AndroidUpdateGateway(private val context: Context) : AppUpdateGateway {

    override val supported: Boolean = true

    /**
     * 本包 minSdk=26，而"安装未知应用"这套权限正是 26(O) 引入的 ——
     * 所以 `SDK_INT < O` 那种兼容分支在这里恒为 false，是不可能执行到的死码，不写。
     */
    override fun hasInstallPermission(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    override fun requestInstallPermission() = Unit

    override fun install(apkPath: String): InstallResult = InstallResult.Failed
}
