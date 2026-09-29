package com.jianyi.outfit.data.update

/**
 * 判定结果。**整个功能只有这一个函数决定"拦不拦人"**，
 * UI、ViewModel、下载器都不允许自己再比较版本号 ——
 * 两处各比一次就有一处会漏掉"包下不到就不拦"那条降级。
 */
enum class UpdateVerdict {
    /** 拿不到 / 看不懂清单 —— 什么都不显示，照常使用 */
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
 * 生效的最低可用版本：作者把 min 写成比 latest 还大时钳到 latest。
 *
 * 这个表达式**只能有一份**。`decideUpdate` 和 [needsApkProbe] 都读它 ——
 * 探针那条路径如果自己再抄一遍阈值，两处一旦漂移，后果是"判定层想拦、探针没跑"，
 * 门禁静默退化成 Optional，而且没有任何日志或测试会变红。
 */
private fun UpdateManifest.effectiveMinSupportedCode(): Int = minOf(minSupportedCode, versionCode)

/**
 * 要不要为这个清单发那次 HEAD。
 *
 * 存在的理由：`http.headOk` 是挂起函数，而 [decideUpdate] 刻意保持纯函数
 * （普通 JVM 里逐条断言，不需要协程）。所以"只在真要拦人时才探"这个决定
 * 必须留在挂起侧，但它又不能变成第二处版本比较 —— 于是把它做成一个谓词，
 * 与被钳过的同一阈值一起放在本文件里。
 *
 * 正确性由 `UpdateDecisionTest.probe_is_needed_exactly_when_the_verdict_depends_on_it`
 * 穷举 (latest, min, current) 全部组合钉住：**当且仅当**探针结论会改变 verdict 时才发探针。
 *
 * `internal` 而不是 public：唯一的消费者是同模块的 [UpdateRepository]，
 * UI/ViewModel/设置页都不该需要它 —— 把它放到 API 表面就等于亲手留下"第二处判定"的入口。
 */
internal fun needsApkProbe(manifest: UpdateManifest, currentVersionCode: Int): Boolean =
    currentVersionCode < manifest.effectiveMinSupportedCode()

/**
 * @param manifest 解析成功的清单；null 表示读不到或看不懂
 * @param currentVersionCode 装机包的 versionCode，由 app 侧注入（commonMain 不许碰 BuildConfig）
 * @param apkReachable 对 manifest.apkUrl 发 HEAD、跟完重定向后是否 2xx。
 *   传之前先问 [needsApkProbe]：UpToDate / Optional 分支不该发这个请求（那里结论与探针无关）。
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
    val minSupported = latest.effectiveMinSupportedCode()
    val verdict =
        if (currentVersionCode >= minSupported) UpdateVerdict.Optional
        else if (apkReachable) UpdateVerdict.Forced
        else UpdateVerdict.Optional
    return UpdateDecision(verdict, latest)
}
