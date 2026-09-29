package com.jianyi.outfit.data.update

/** ViewModel 只认这个接口，好让状态机能用假实现跑完全部跳转。 */
interface UpdateChecker {
    suspend fun check(currentVersionCode: Int): UpdateDecision
}

/**
 * 把"读清单 → 判要不要提示 → 确认包真能下"串成一次调用。
 *
 * 三处次序都是刻意的，不是风格问题：
 * 1. **清单没拿到就不发 HEAD** —— 那时连 apkUrl 都没有，探了是白探；
 * 2. **正文拿到了但解析失败 ⇒ 报一条 warning** —— 这是"没人收到更新"这种静默故障唯一的信号，
 *    见 [logUpdateWarning]；离线不发，否则每次飞行模式都刷一条，信号会被噪声埋掉；
 * 3. **版本比较只写在 [decideUpdate] / [needsApkProbe] 里** —— 本类一度自己抄了一遍 `>=`
 *    来省掉探包，于是同一个决定"拦不拦人"的比较存在于两个文件；两处一旦漂移
 *    （改了其中一处），结果就是判定层想拦而探针没跑，门禁静默退化成 Optional，
 *    正是整个功能最防的那件事。现在这里只问谓词，不出现任何阈值。
 */
class UpdateRepository(
    private val http: UpdateHttp,
    private val manifestUrl: String = UPDATE_MANIFEST_URL,
    private val logWarning: (String) -> Unit = ::logUpdateWarning
) : UpdateChecker {

    override suspend fun check(currentVersionCode: Int): UpdateDecision {
        val body = http.getText(manifestUrl)
        if (body == null) return UpdateDecision(UpdateVerdict.Unreachable, null)

        val manifest = UpdateManifestParser.parse(body)
        if (manifest == null) {
            logWarning(
                "更新清单读到了但解析失败：$manifestUrl ⇒ 对所有版本判 Unreachable，" +
                    "也就是没有人会收到更新。检查 versionCode / minSupportedVersionCode / apkUrl " +
                    "是否齐全，以及 apkUrl 是否 https 且以 .apk 结尾"
            )
            return UpdateDecision(UpdateVerdict.Unreachable, null)
        }
        // 只有 verdict 真的依赖探针结论时才发这次 HEAD —— 谓词与判定共用同一阈值，
        // 所以"少发一次请求"和"不会漏发"这两件事不可能互相矛盾。
        val reachable =
            if (needsApkProbe(manifest, currentVersionCode)) http.headOk(manifest.apkUrl) else false
        return decideUpdate(manifest, currentVersionCode, reachable)
    }
}
