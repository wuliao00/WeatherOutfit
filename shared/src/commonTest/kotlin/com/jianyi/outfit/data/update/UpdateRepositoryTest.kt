package com.jianyi.outfit.data.update

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 假实现而不是 Ktor MockEngine：接缝本身就一层（GET 正文 / HEAD 探包），
 * 再引一个测试依赖只是多一个会随 ktor 升级而坏的零件。
 */
private class FakeHttp(
    private val text: String? = null,
    private val reachable: Boolean = true
) : UpdateHttp {
    var getTextCalls = 0
    var getTextUrl: String? = null
    var headCalls = 0
    var headUrl: String? = null

    override suspend fun getText(url: String): String? {
        getTextCalls++
        getTextUrl = url
        return text
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
}

private const val MANIFEST =
    """{"versionCode":9,"versionName":"2.2.0","minSupportedVersionCode":9,"apkUrl":"https://gitee.com/x.apk"}"""

private const val OPTIONAL_MANIFEST =
    """{"versionCode":9,"versionName":"2.2.0","minSupportedVersionCode":5,"apkUrl":"https://gitee.com/x.apk"}"""

class UpdateRepositoryTest {

    @Test fun reachable_package_and_forced_verdict() = runTest {
        val d = UpdateRepository(FakeHttp(MANIFEST, true)).check(8)
        assertEquals(UpdateVerdict.Forced, d.verdict)
    }

    /**
     * 没网 = 正常情形，既不该发 HEAD（清单都没拿到，探无可探），
     * 更不该报警：报警只留给"读到了但看不懂"那种零信号的故障。
     */
    @Test fun http_failure_is_unreachable_and_skips_probe_and_stays_silent() = runTest {
        val http = FakeHttp(null, true)
        val log = RecordingLog()
        val d = UpdateRepository(http, logWarning = log::record).check(8)
        assertEquals(UpdateVerdict.Unreachable, d.verdict)
        assertEquals(0, http.headCalls)
        assertTrue(
            log.messages.isEmpty(),
            "读不到正文属于离线正常态，不该报警：${log.messages}"
        )
    }

    /**
     * 清单里一个拼错 = 永远没人收到更新，而测试、CI、日志原本全绿。
     * 这条用例钉的就是那条唯一的信号：正文拿到了、解析失败 ⇒ 必须报警。
     */
    @Test fun unreadable_body_is_unreachable_and_warns() = runTest {
        val log = RecordingLog()
        val d = UpdateRepository(FakeHttp("<html>404</html>", true), logWarning = log::record).check(8)
        assertEquals(UpdateVerdict.Unreachable, d.verdict)
        assertEquals(1, log.messages.size)
        assertTrue(
            log.messages.single().contains(UPDATE_MANIFEST_URL),
            "告警要带上清单地址，否则运维不知道该改哪个文件：${log.messages}"
        )
    }

    /** 已经是最新版，不必探包：省一次请求，也避免"探了个寂寞" */
    @Test fun up_to_date_skips_probe() = runTest {
        val http = FakeHttp(MANIFEST, true)
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
        val http = FakeHttp(OPTIONAL_MANIFEST, reachable = false)
        assertEquals(UpdateVerdict.Optional, UpdateRepository(http).check(8).verdict)
        assertEquals(0, http.headCalls)
    }

    /** 探包失败 ⇒ 降级 Optional（门禁的硬前提在 decideUpdate 里，这里只负责传对话） */
    @Test fun unreachable_package_degrades_to_optional() = runTest {
        assertEquals(
            UpdateVerdict.Optional,
            UpdateRepository(FakeHttp(MANIFEST, reachable = false)).check(8).verdict
        )
    }

    @Test fun probe_url_is_the_manifest_apk_url() = runTest {
        val http = FakeHttp(MANIFEST, true)
        UpdateRepository(http).check(8)
        assertEquals("https://gitee.com/x.apk", http.headUrl)
    }

    /** 调用方不传地址时走那个常量，而不是空串/别的地址 */
    @Test fun default_manifest_url_is_used_when_not_overridden() = runTest {
        val http = FakeHttp(MANIFEST, true)
        UpdateRepository(http).check(9)
        assertEquals(UPDATE_MANIFEST_URL, http.getTextUrl)
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
