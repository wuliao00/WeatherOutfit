package com.jianyi.outfit.data.update

/** ViewModel 只认这个接口，好让状态机能用假实现跑完全部跳转。 */
interface UpdateChecker {
    suspend fun check(currentVersionCode: Int): UpdateDecision
}

/**
 * 把"读清单 → 判要不要提示 → 确认包真能下"串成一次调用。
 *
 * 四条次序/取舍都是刻意的：
 * 1. **清单没拿到就不发 HEAD** —— 那时连 apkUrl 都没有，探了是白探；
 * 2. **三种失败分开**（见 [ManifestFetch]）：离线不报（正常态，报了会把信号埋进噪声里），
 *    非 2xx 与"读到了但看不懂"都报 —— 这两类都是"永远没人收到更新"的永久静默失效，
 *    没有这条日志就只能靠人猜；
 * 3. **门禁降级也报**：低于 minSupported 却因探包失败退成 Optional，是本功能唯一
 *    "作者想拦人而系统决定不拦"的时刻，必须留痕（否则真机上表现为"只弹可跳过提示"，
 *    没有人知道为什么）；
 * 4. **版本比较只写在 [decideUpdate] / [needsApkProbe] 里** —— 本类一度自己抄了一遍 `>=`
 *    来省掉探包，于是同一个"拦不拦人"的比较存在于两个文件；两处一旦漂移，
 *    结果就是判定层想拦而探针没跑，门禁静默退化成 Optional。现在这里只问谓词，不出现任何阈值。
 */
class UpdateRepository(
    private val http: UpdateHttp,
    private val manifestUrl: String = UPDATE_MANIFEST_URL,
    private val logWarning: (String) -> Unit = ::logUpdateWarning
) : UpdateChecker {

    override suspend fun check(currentVersionCode: Int): UpdateDecision {
        val manifest = when (val fetch = http.fetchManifest(manifestUrl)) {
            is ManifestFetch.NetworkFailed ->
                return UpdateDecision(UpdateVerdict.Unreachable, null)

            is ManifestFetch.HttpStatus -> {
                logWarning(
                    "更新清单读取失败：HTTP ${fetch.code} @ $manifestUrl ⇒ 对所有版本判 Unreachable，" +
                        "也就是没有人会收到更新（404 多半是文件被移动/删除，403 多半是仓库转私有或被限流）"
                )
                return UpdateDecision(UpdateVerdict.Unreachable, null)
            }

            is ManifestFetch.Body -> {
                val parsed = UpdateManifestParser.parse(fetch.text)
                if (parsed == null) {
                    logWarning(
                        "更新清单读到了但解析失败：$manifestUrl ⇒ 对所有版本判 Unreachable，" +
                            "也就是没有人会收到更新。检查 versionCode / minSupportedVersionCode / apkUrl " +
                            "是否齐全，以及 apkUrl 是否 https 且以 .apk 结尾"
                    )
                    return UpdateDecision(UpdateVerdict.Unreachable, null)
                }
                parsed
            }
        }

        val reachable = if (needsApkProbe(manifest, currentVersionCode)) {
            val ok = http.headOk(manifest.apkUrl)
            if (!ok) {
                logWarning(
                    "门禁降级：本包低于 minSupportedVersionCode=${manifest.minSupportedCode}，" +
                        "但 HEAD ${manifest.apkUrl} 没落在 2xx ⇒ 只弹可跳过提示、不拦人。" +
                        "附件没上传、tag 与 versionName 不一致、或签名 URL 过期都会是这个样子"
                )
            }
            ok
        } else {
            false
        }
        return decideUpdate(manifest, currentVersionCode, reachable)
    }
}
