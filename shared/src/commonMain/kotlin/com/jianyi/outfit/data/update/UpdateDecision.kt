package com.jianyi.outfit.data.update

/**
 * 判定结果。**整个功能只有这一个函数决定"拦不拦人"**，
 * UI、ViewModel、下载器都不允许自己再比较版本号 ——
 * 两处各比一次就有一处会漏掉 apkReachable 那条降级。
 */
enum class UpdateVerdict {
    /** 拿不到 / 看不懂清单，或包下不到 —— 什么都不显示，照常使用 */
    Unreachable,

    /** 已经是最新（或更新） */
    UpToDate,

    /** 有新版本，可跳过 */
    Optional,

    /** 低于最低可用版本，且包确实能下 —— 硬门禁 */
    Forced
}

data class UpdateDecision(val verdict: UpdateVerdict, val manifest: UpdateManifest?)

/**
 * @param manifest 解析成功的清单；null 表示读不到或看不懂
 * @param currentVersionCode 装机包的 versionCode，由 app 侧注入（commonMain 不许碰 BuildConfig）
 * @param apkReachable 对 manifest.apkUrl 发 HEAD、跟完重定向后是否 2xx
 */
fun decideUpdate(
    manifest: UpdateManifest?,
    currentVersionCode: Int,
    apkReachable: Boolean
): UpdateDecision {
    val latest = manifest ?: return UpdateDecision(UpdateVerdict.Unreachable, null)
    if (currentVersionCode >= latest.versionCode) {
        return UpdateDecision(UpdateVerdict.UpToDate, latest)
    }
    // 写错的 minSupported（比 latest 还大）钳到 latest：
    // 不钳的话它会拦掉所有人，包括已经装了最新版的人。
    // 解析器刻意不钳（见 UpdateManifestTest.min_above_latest_still_parses：它原样透出 12），
    // 判定语义只该有这一处，所以钳位也必须在这里。
    val minSupported = minOf(latest.minSupportedCode, latest.versionCode)
    val verdict =
        if (currentVersionCode >= minSupported) UpdateVerdict.Optional
        else if (apkReachable) UpdateVerdict.Forced
        else UpdateVerdict.Optional
    return UpdateDecision(verdict, latest)
}
