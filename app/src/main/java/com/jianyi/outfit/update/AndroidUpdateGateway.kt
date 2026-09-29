package com.jianyi.outfit.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.jianyi.outfit.data.AppUpdateGateway
import com.jianyi.outfit.data.InstallResult
import com.jianyi.outfit.data.update.UPDATE_DIR_NAME
import java.io.File

/**
 * Android 侧的安装能力：查/引导「安装未知应用」权限，把下载好的 APK 交给系统安装页。
 *
 * 下载不在这里（shared 的 ApkDownloader），版本号也不在这里（容器注入 AppVersion）：
 * 这个类只碰平台 API，所以它的每一条判断都只能靠真机观察 —— 全套单测跑的注入实现
 * 都不会走到这一层。
 *
 * 持有的 context 是 applicationContext（见 AppContainer），因此所有 startActivity
 * 都必须带 FLAG_ACTIVITY_NEW_TASK，否则 8.0+ 直接抛
 * "Background activities are not allowed"。
 */
class AndroidUpdateGateway(private val context: Context) : AppUpdateGateway {

    override val supported: Boolean = true

    /**
     * 「安装未知应用」到底给没给。
     *
     * 这条返回值的前提是清单里声明了 `REQUEST_INSTALL_PACKAGES`（Task 8 才加）：
     * 没声明时系统认为本包没有这项能力，canRequestPackageInstalls() **恒 false**，
     * 表现是用户被永远停在权限引导页，而门禁与状态机那一侧一切正常。
     * 所以这里不许写一条"断言恒 false"的用例把它锁成期望 —— 那是把当时的缺失当契约。
     * 语义只能真机观察：`adb shell dumpsys package com.jianyi.outfit | grep REQUEST_INSTALL`
     * 与授权前后各读一次这里。
     */
    override fun hasInstallPermission(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /**
     * 跳系统「安装未知应用」授权页。
     *
     * 三点都是不写就不成立的：
     * - 这是特殊权限（appops），**不能**用 requestPermissions()：那条 API 对它永远返回 deny；
     * - data 必须是本包的 `package:` URI，否则有的 ROM 打开的是全局列表、有的干脆不动；
     * - FLAG_ACTIVITY_NEW_TASK：持有的是 applicationContext。
     *
     * 本包 minSdk=26，而 ACTION_MANAGE_UNKNOWN_APP_SOURCES 正是 26 引入的，
     * 所以不写 `SDK_INT < O` 那种恒 false 的兼容分支（同 Task 4 删掉的那条死码）。
     */
    override fun requestInstallPermission() {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // 部分定制 ROM 删了这页（或把它换成了自己的设置中心）。这里只留痕、不抛：
            // 状态机那一侧已经退回门禁/卡片，用户下一次点「立即更新」还会再试一遍，
            // 而抛出去会让一次跳转失败变成冷启动崩溃。
            Log.w(TAG, "打开「安装未知应用」设置页失败，ROM 可能没有这一页：${e.message}", e)
        }
    }

    /**
     * 拉起系统安装页。
     *
     * 四处细节对应的症状都不是"编译不过"，而是真机上"点了没反应"：
     *
     * - **必须走 FileProvider，不能给 file:// URI**：Android 7+ 的 StrictMode 对跨进程的
     *   file:// 直接抛 FileUriExposedException，异常只在 logcat 里，用户看到的就是一切正常但什么都没发生。
     * - **authority 与 file_paths.xml 必须成对**：前者要和清单里 `${applicationId}.fileprovider`
     *   逐字一致，后者必须覆盖 `cacheDir/update/`（Task 7 的落盘目录）。配错的形式是
     *   getUriForFile 抛 IllegalArgumentException，被这里折成 Failed 之后，文案会误报成"写入失败，
     *   请清理手机存储"—— 单测一条都不会红，因为它们测的是判定，不是 URI 授予。
     * - **权限没给时不能直接 startActivity**：8.0+ 没有这项授权时系统会把请求吞掉或抛
     *   ActivityNotFound，所以先回 [InstallResult.PermissionMissing]，让 VM 去走
     *   [requestInstallPermission] 那条引导。VM 在 forced 时会退回门禁而不是可跳过卡片，
     *   这条返回值因此是"拒绝授权不会把强更降级成可跳过"的支点。
     * - **FLAG_GRANT_READ_URI_PERMISSION**：content:// 本身对安装器不可读，靠这一个临时授权；
     *   只在这次 Intent 的生命周期内有效，所以不需要 persistable 权限。
     *
     * MIME 用 `application/vnd.android.package-archive` 而不是 `application/octet-stream`：
     * AOSP 安装器对 ACTION_VIEW 注册的 intent-filter 收的是前者
     * （content scheme + 这个 MIME），给 octet-stream 的形态是 ActivityNotFoundException
     * ⇒ InstallResult.Failed ⇒ 恰好复现"安装页弹不出"这条本 Task 要消除的症状。
     * FileProvider.getType() 对 .apk 也返回同一个 MIME，两边不冲突。
     * （ACTION_INSTALL_PACKAGE 自 API 30 起已废弃，仓库的规矩是不引入废弃 API，故不用它。）
     */
    override fun install(apkPath: String): InstallResult {
        // 层间契约：先验路径，再谈权限。理由是这条 path 来自另一个模块的另一层
        // （shared 的 ApkDownloader 发 DownloadEvent.Success(path)），中间没有任何编译器能
        // 保证它真的是下载器落盘的那个文件。守卫放在这里是因为只有本类知道 FileProvider 的 root；
        // 越界路径必须回 Failed 而不是让 getUriForFile 抛出去被折成"请清理手机存储"那句误报文案。
        precheckInstallPath(apkPath, context.cacheDir)?.let { rejection ->
            Log.w(
                TAG,
                "安装被路径守卫拒绝：$apkPath 不在 ${File(context.cacheDir, UPDATE_DIR_NAME).absolutePath} 之下" +
                    "（成因按 $rejection 处理，没有交给 FileProvider，也就没有 IllegalArgumentException）"
            )
            return rejection
        }
        val file = File(apkPath)
        if (!hasInstallPermission()) return InstallResult.PermissionMissing
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            InstallResult.Launched
        } catch (e: ActivityNotFoundException) {
            // 没有组件接这个 Intent（ROM 把安装器换了/禁了）。留全参数：
            // 事后只有一句"失败"的话，分不清是 file_paths 配错还是这台机器根本没有安装器。
            Log.w(TAG, "没有 Activity 能处理安装 Intent：${file.absolutePath}", e)
            InstallResult.Failed
        } catch (e: Exception) {
            // IllegalArgumentException（FileProvider 的 root 不含这个文件）走这里。
            Log.w(TAG, "拉起安装页失败：${file.absolutePath}", e)
            InstallResult.Failed
        }
    }

    private companion object {
        /** 与 shared 的 UpdateLog 同一个 tag，真机上一条 `adb logcat -s JianyiUpdate` 能看全这条链 */
        const val TAG = "JianyiUpdate"
    }
}
