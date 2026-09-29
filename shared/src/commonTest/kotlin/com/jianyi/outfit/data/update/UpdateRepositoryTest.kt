package com.jianyi.outfit.data.update

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 假实现而不是 Ktor MockEngine：接缝本身就一层（读清单 / HEAD 探包），
 * 再引一个测试依赖只是多一个会随 ktor 升级而坏的零件。
 */
private class FakeHttp(
    private val fetch: ManifestFetch = ManifestFetch.NetworkFailed("没配正文"),
    private val reachable: Boolean = true
) : UpdateHttp {
    var fetchCalls = 0
    var fetchedUrl: String? = null
    var headCalls = 0
    var headUrl: String? = null

    override suspend fun fetchManifest(url: String): ManifestFetch {
        fetchCalls++
        fetchedUrl = url
        return fetch
    }

    override suspend fun headOk(url: String): Boolean {
        headCalls++
        headUrl = url
        return reachable
    }
}

/**
 * 告警走注入而不是直接调 `logUpdateWarning` 的 actual：
 * commonTest 跑在普通 JVM 上，而 android 的 actual 里是 `android.util.Log` ——
 * 本模块没配 Robolectric、也没配 `unitTests.isReturnDefaultValues`，那个桩是抛 `Stub!` 的。
 * 顺带这条接缝让"什么时候报警"变成能被断言的行为，而不是只能人工看 logcat。
 */
private class RecordingLog {
    val messages = mutableListOf<String>()
    fun record(message: String) {
        messages.add(message)
    }

    fun single(): String {
        assertEquals(1, messages.size, "应当恰好一条告警，实际：$messages")
        return messages.single()
    }
}

private const val MANIFEST =
    """{"versionCode":9,"versionName":"2.2.0","minSupportedVersionCode":9,"apkUrl":"https://gitee.com/x.apk"}"""

private const val OPTIONAL_MANIFEST =
    """{"versionCode":9,"versionName":"2.2.0","minSupportedVersionCode":5,"apkUrl":"https://gitee.com/x.apk"}"""

private fun body(text: String) = ManifestFetch.Body(text)

class UpdateRepositoryTest {

    @Test fun reachable_package_and_forced_verdict() = runTest {
        val d = UpdateRepository(FakeHttp(body(MANIFEST), true)).check(8)
        assertEquals(UpdateVerdict.Forced, d.verdict)
    }

    /**
     * 没网 = 正常情形，既不该发 HEAD（清单都没拿到，探无可探），更不该报警。
     * 报警只留给"永远没人收到更新"那两类：非 2xx、以及读到了但看不懂。
     */
    @Test fun network_failure_is_unreachable_and_skips_probe_and_stays_silent() = runTest {
        val http = FakeHttp(ManifestFetch.NetworkFailed("timeout"), true)
        val log = RecordingLog()
        val d = UpdateRepository(http, logWarning = log::record).check(8)
        assertEquals(UpdateVerdict.Unreachable, d.verdict)
        assertEquals(0, http.headCalls)
        assertTrue(log.messages.isEmpty(), "离线属于正常态，不该报警：${log.messages}")
    }

    /**
     * 非 2xx 与离线必须分得开：仓库被转私有(403)、raw 路径改了(404)、被限流(5xx)
     * 都会让"没有人收到更新"变成永久状态，而上一版 `getText` 把它们折成同一个 null ⇒ 零信号。
     */
    @Test fun http_status_is_unreachable_and_warns_with_the_code() = runTest {
        val log = RecordingLog()
        val d = UpdateRepository(FakeHttp(ManifestFetch.HttpStatus(403), true), logWarning = log::record)
            .check(8)
        assertEquals(UpdateVerdict.Unreachable, d.verdict)
        assertTrue(
            log.single().contains("403"),
            "告警要带上状态码，否则运维分不清是被拒还是没找到：${log.messages}"
        )
    }

    /**
     * 清单里一个拼错 = 永远没人收到更新，而测试、CI、日志原本全绿。
     * 这条钉的是那条唯一的信号：正文拿到了、解析失败 ⇒ 必须报警，且**带上真正那个地址**。
     *
     * 地址是显式传进去的自定义值，不是 `UPDATE_MANIFEST_URL`：
     * 用默认值去断言"消息里含 UPDATE_MANIFEST_URL"等于拿常量比自己，
     * 实现里把地址写错或漏写也照样绿。
     */
    @Test fun unreadable_body_is_unreachable_and_warns_with_the_real_url() = runTest {
        val customUrl = "https://gitee.com/someone/else/raw/main/update.json"
        val log = RecordingLog()
        val d = UpdateRepository(
            FakeHttp(body("<html>404</html>"), true),
            manifestUrl = customUrl,
            logWarning = log::record
        ).check(8)
        assertEquals(UpdateVerdict.Unreachable, d.verdict)
        assertTrue(
            log.single().contains(customUrl),
            "告警要带上实际请求的那个地址：${log.messages}"
        )
    }

    /** 已经是最新版，不必探包：省一次请求，也避免"探了个寂寞" */
    @Test fun up_to_date_skips_probe() = runTest {
        val http = FakeHttp(body(MANIFEST), true)
        assertEquals(UpdateVerdict.UpToDate, UpdateRepository(http).check(9).verdict)
        assertEquals(0, http.headCalls)
    }

    /**
     * 高于 minSupported、低于 latest ⇒ Optional，同样一次 HEAD 都不该发。
     *
     * 这条与 `UpdateDecisionTest.probe_is_needed_exactly_when_the_verdict_depends_on_it` 是一对：
     * 那边穷举证明谓词与判定等价，这边证明仓库真的按谓词办事。
     * 谓词若退化成一律 true，所有 verdict 断言照样全绿，表现只剩"每次冷启动多打一次 HEAD 到 Gitee"。
     */
    @Test fun optional_verdict_does_not_probe() = runTest {
        val http = FakeHttp(body(OPTIONAL_MANIFEST), reachable = false)
        assertEquals(UpdateVerdict.Optional, UpdateRepository(http).check(8).verdict)
        assertEquals(0, http.headCalls)
    }

    /**
     * 该拦却下不到 ⇒ 降级 Optional，并且**必须留一条日志**。
     *
     * 这是全功能里唯一"作者想拦人而系统决定不拦"的时刻。不报的话，
     * 真机上的表现只是"只弹可跳过提示"，没有人能知道是附件没传、tag 写错、还是签名过期。
     */
    @Test fun probe_failure_degrades_to_optional_and_warns() = runTest {
        val log = RecordingLog()
        val d = UpdateRepository(FakeHttp(body(MANIFEST), reachable = false), logWarning = log::record)
            .check(8)
        assertEquals(UpdateVerdict.Optional, d.verdict)
        assertTrue(
            log.single().contains("https://gitee.com/x.apk"),
            "降级告警要带上探的那个 apkUrl：${log.messages}"
        )
    }

    @Test fun probe_url_is_the_manifest_apk_url() = runTest {
        val http = FakeHttp(body(MANIFEST), true)
        UpdateRepository(http).check(8)
        assertEquals("https://gitee.com/x.apk", http.headUrl)
    }

    /** 调用方不传地址时走那个常量，而不是空串/别的地址 */
    @Test fun default_manifest_url_is_used_when_not_overridden() = runTest {
        val http = FakeHttp(body(MANIFEST), true)
        UpdateRepository(http).check(9)
        assertEquals(1, http.fetchCalls)
        assertEquals(UPDATE_MANIFEST_URL, http.fetchedUrl)
    }

    /**
     * 清单地址必须是 raw 域那一条。
     *
     * 这不是形式检查：实测 `/releases/latest` 的响应形态由 User-Agent 决定
     * （okhttp 拿 JSON、Dalvik 拿 HTML），换回那个地址就会**每次**解析失败，
     * 而失败被压成 Unreachable ⇒ 表现为"没有人收到更新"。
     */
    @Test fun manifest_url_is_the_gitee_raw_endpoint_not_the_releases_api() {
        assertTrue(
            UPDATE_MANIFEST_URL.startsWith("https://gitee.com/") &&
                UPDATE_MANIFEST_URL.contains("/raw/") &&
                UPDATE_MANIFEST_URL.endsWith("update.json"),
            "清单必须走免鉴权的 raw 域，实际：$UPDATE_MANIFEST_URL"
        )
        assertTrue(
            !UPDATE_MANIFEST_URL.contains("/releases"),
            "不能用 /releases/latest：它的响应形态随 User-Agent 变（Dalvik UA 拿到 HTML）"
        )
    }
}
