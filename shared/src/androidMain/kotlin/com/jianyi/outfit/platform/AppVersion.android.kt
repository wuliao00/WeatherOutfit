package com.jianyi.outfit.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 读自己包的安装信息。
 *
 * 用 `getPackageInfo(String, Int)` 这个重载：新签名（`PackageInfoFlags`）要 API 33，
 * 而本库 minSdk 26，走旧重载是唯一不用运行期版本分支的写法，deprecation 因此是预期的。
 * 问的是安装包而不是 BuildConfig —— versionName 定义在 :app，shared 侧没有它。
 */
@Composable
@Suppress("DEPRECATION")
actual fun installedVersionName(): String? {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }
}
