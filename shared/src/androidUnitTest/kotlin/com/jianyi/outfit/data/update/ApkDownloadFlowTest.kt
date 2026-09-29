package com.jianyi.outfit.data.update

import com.jianyi.outfit.data.AppUpdateGateway
import com.jianyi.outfit.data.AppVersion
import com.jianyi.outfit.data.InstallResult
import com.jianyi.outfit.ui.update.ApkInstaller
import com.jianyi.outfit.ui.update.DownloadEvent
import com.jianyi.outfit.ui.update.DownloadFailure
import com.jianyi.outfit.ui.update.DownloadView
import com.jianyi.outfit.ui.update.UpdateUiState
import com.jianyi.outfit.ui.update.UpdateViewModel
import com.jianyi.outfit.ui.update.gateDownloadView
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [downloadEvents] 那一层的用例：**整条下载链的判定与进度，从假响应一直到进度条**。
 *
 * 这个文件存在的理由就是评审那条 Critical（C3）：把 `ApkDownloader` 里
 * `onProgress = { emit(it) }` 换成空 lambda，全仓 185 条测试**一条都不红** ——
 * 因为没有任何一条测试构造过下载器。VM 那一头有用例保证"收到 Progress 就落到状态上"，
 * UI 那一头有用例保证"状态里的 done 会算成比例"，两头各自正确、中间没人接，
 * 而那正是这个仓库为同一形状的缺陷付过两次钱的地方（0% 死条）。
 *
 * 所以这里三条承重：
 * 1. [the_flow_emits_progress_events_and_a_success_inside_the_directory] ——
 *    假响应走过真实写盘之后，flow 必须**真的**发过 Progress，而 Success 的路径必须落在给定目录里；
 * 2. [a_progress_event_from_the_downloader_moves_the_gate_bar_to_half] —— 跨层：
 *    同一个 Progress 喂进真 VM（门禁态）再读 [gateDownloadView]，比例必须动；
 * 3. 完整性与空间两道闸按**服务器说的长度**判（C1/C2）：生产 `update.json` 既没 `sha256`
 *    也没 `sizeBytes`，所以这里的清单刻意用那份形状，免得用例绿在自己的假数据上。
 *
 * 网络那一半仍然不在这里打真请求：Ktor 接线属于 [ApkDownloader] 与容器那一层，
 * 见台账里"容器接线没有测试覆盖，只能靠读 diff 与真机确认"那条。
 */
class ApkDownloadFlowTest {

    /** 仓库根 `update.json` 今天的形状：只有必填四项 */
    private val productionLike = UpdateManifest(
        versionCode = 9,
        versionName = "2.2.0",
        minSupportedCode = 9,
        apkUrl = "https://gitee.com/wuliao11541/WeatherOutfit/releases/download/v2.2.0/jianyi-2.2.0.apk"
    )

    // ---- 假接缝 ----

    /**
     * 一条内存里的"响应"：声明长度与真实字节数**故意可以不一致** ——
     * 那条 CDN 实测会把 58MB 掐成 26MB 后正常结束、不抛异常（spec §3.4），
     * 那正是这里要复现的形状。每个实例只服务一次尝试（自己的读标），
     * 所以每次尝试都要新建一个。
     */
    private inner class FakeBody(
        private val declaredLength: Long?,
        private val bytes: ByteArray,
        private val breakAfter: Long? = null,
        private val breakWith: (Long) -> Exception = { NetworkBodyFailure(IOException("链路被掐")) }
    ) {
        private var pos = 0
        private var served = 0L
        var discards = 0
            private set

        fun toApkBody(): ApkBody = ApkBody(
            contentLength = declaredLength,
            readChunk = { buffer -> read(buffer) },
            discard = { discards++ }
        )

        private suspend fun read(buffer: ByteArray): Int {
            if (breakAfter != null && served >= breakAfter) throw breakWith(served)
            if (pos >= bytes.size) return -1
            val n = minOf(buffer.size, bytes.size - pos)
            bytes.copyInto(buffer, 0, pos, pos + n)
            pos += n
            served += n
            return n
        }
    }

    /** 按脚本给响应；脚本用完再 open 就返回 null（等价于"这条地址彻底拿不到"） */
    private class FakeSource(private val responses: List<ApkBody?>) {
        var opens = 0
            private set
        var lastUrl: String? = null
            private set

        fun next(url: String): ApkBody? {
            lastUrl = url
            val response = responses.getOrElse(opens) { null }
            opens++
            return response
        }
    }

    private class ReplayInstaller(private val events: List<DownloadEvent>) : ApkInstaller {
        override fun download(manifest: UpdateManifest): Flow<DownloadEvent> = flowOf(*events.toTypedArray())
    }

    private class ForcedChecker(private val manifest: UpdateManifest) : UpdateChecker {
        override suspend fun check(currentVersionCode: Int) = UpdateDecision(UpdateVerdict.Forced, manifest)
    }

    private class InstallGateway : AppUpdateGateway {
        override val supported = true
        override fun hasInstallPermission() = true
        override fun requestInstallPermission() = Unit
        override fun install(apkPath: String) = InstallResult.Launched
    }

    private suspend fun run(
        manifest: UpdateManifest,
        dir: File,
        source: FakeSource,
        logs: MutableList<String> = mutableListOf(),
        free: Long = Long.MAX_VALUE / 8
    ): List<DownloadEvent> = downloadEvents(
        manifest = manifest,
        dir = dir,
        openBody = { url -> source.next(url) },
        logWarning = { logs.add(it) },
        freeSpace = { free }
    ).toList(mutableListOf())

    private fun targetOf(dir: File, manifest: UpdateManifest = productionLike): File =
        File(dir, apkFileNameFor(manifest.versionName))

    // ---- ① 进度与落盘：C3 那条锁 ----

    /**
     * 假响应走完真实写盘之后，flow 必须发过 Progress，且 Success 的 path 落在给定目录里。
     *
     * 把 `runAttempt` 里 `onProgress = { emit(it) }` 换成空 lambda，这一条先红 ——
     * 那就是本轮要的"改成空操作必须有人响"。
     */
    @Test fun the_flow_emits_progress_events_and_a_success_inside_the_directory() = runTest {
        val dir = newDir()
        val payload = deterministicBody(3L * 1024L * 1024L)
        val source = FakeSource(listOf(FakeBody(payload.size.toLong(), payload).toApkBody()))

        val events = run(productionLike, dir, source)
        val progresses = events.filterIsInstance<DownloadEvent.Progress>()

        assertTrue(
            progresses.isNotEmpty(),
            "整条下载一条 Progress 都没发出去 —— 这就是 Task 5 那根 0% 死条的位置：$events"
        )
        val success = assertIs<DownloadEvent.Success>(events.last())
        val written = File(success.path)
        assertTrue(
            written.canonicalFile.path.startsWith(dir.canonicalFile.path + File.separator),
            "Success 的 path 不在给定目录里：${success.path} vs ${dir.absolutePath}"
        )
        assertContentEquals(payload, written.readBytes(), "落盘内容与响应体不是一字不差")
        assertEquals(1, source.opens, "Content-Length 对得上就不该再打第二次请求")
        assertEquals(
            listOf(payload.size.toLong()),
            progresses.map { it.total }.distinct(),
            "进度分母该是同一个值，而且必须是实测长度"
        )
        assertEquals(1, dir.listFiles()?.size ?: -1, "下载过程不许在目录里留下别的文件（半截包、临时文件）")
    }

    /** 每 512KB 一条的节奏在 flow 这一层也要成立（不只是复制循环内部） */
    @Test fun the_flow_paces_progress_at_512kb_rather_than_per_read_chunk() = runTest {
        val dir = newDir()
        val payload = deterministicBody(3L * 1024L * 1024L)
        val source = FakeSource(listOf(FakeBody(payload.size.toLong(), payload).toApkBody()))

        val progresses = run(productionLike, dir, source).filterIsInstance<DownloadEvent.Progress>()

        assertEquals(
            listOf(524_288L, 1_048_576L, 1_572_864L, 2_097_152L, 2_621_440L, 3_145_728L),
            progresses.map { it.done },
            "进度节奏不是复制循环里那个 512KB，就是 emit 被接错了地方"
        )
    }

    // ---- ② 跨层：downloader → VM → 进度条 ----

    /**
     * 门禁态里 `Progress(12_000_000, 24_000_000)` ⇒ `gateDownloadView(...).ratio == 0.5f`。
     *
     * 这条是"每层各自看起来正确、拼起来是死的"那个错误类别的直接反证：
     * 事件由真的 [downloadEvents] 产出（不是手写的假事件），经真的 [UpdateViewModel] 落到状态，
     * 最后由 UI 那一层唯一读的纯函数算出比例。三段里任何一段断开就红。
     *
     * 用被掐半截的响应体是刻意的：12MB / 24MB 那种"传一大半自己断掉"的实测形状，
     * 恰好让收尾那条尾巴进度落在正好一半上。
     */
    @Test fun a_progress_event_from_the_downloader_moves_the_gate_bar_to_half() = runTest {
        val dir = newDir()
        val payload = deterministicBody(12_000_000L)
        val source = FakeSource(
            listOf(
                FakeBody(24_000_000L, payload).toApkBody(),
                FakeBody(24_000_000L, payload).toApkBody()
            )
        )

        val events = run(productionLike, dir, source)
        val progresses = events.filterIsInstance<DownloadEvent.Progress>()
        assertTrue(
            progresses.contains(DownloadEvent.Progress(12_000_000L, 24_000_000L)),
            "下载器没发出那条一半的进度事件，跨层用例失去前提：${progresses.takeLast(2)}"
        )

        val vm = UpdateViewModel(
            appVersion = AppVersion(8, "2.1.2"),
            checker = ForcedChecker(productionLike),
            installer = ReplayInstaller(progresses),
            gateway = InstallGateway(),
            scope = this
        )
        vm.checkOnce()
        advanceUntilIdle()
        vm.startDownload()
        advanceUntilIdle()

        assertIs<UpdateUiState.Downloading>(vm.state.value)
        val view = assertIs<DownloadView>(gateDownloadView(vm.state.value))
        assertEquals(0.5f, view.ratio, "下载器的进度没走到进度条上：done/total 断在中间某一层")
    }

    // ---- ③ 完整性闸按实测长度判（C1）----

    /**
     * 生产清单的形状（无 sha256、无 sizeBytes）+ 服务器说 3MB 而只回来 1.3MB ⇒ 不许算成功。
     *
     * 这就是 C1：过去"没声明就当非空即完整"，半截包会被当 Success 交给安装页，
     * 表现为"解析软件包时出现问题"，而我们整条链全绿。把 `isBodyComplete` 退化成
     * `written > 0`（变异 M-b），这一组用例一起红。
     */
    @Test fun a_body_shorter_than_the_declared_content_length_is_not_a_success() = runTest {
        val dir = newDir()
        val payload = deterministicBody(1_300_000L)
        val source = FakeSource(
            listOf(
                FakeBody(3_000_000L, payload).toApkBody(),
                FakeBody(3_000_000L, payload).toApkBody()
            )
        )

        val events = run(productionLike, dir, source)

        val failure = assertIs<DownloadEvent.Failure>(events.last())
        assertEquals(DownloadFailure.Network, failure.reason)
        assertFalse(events.any { it is DownloadEvent.Success }, "半截包被判成功：$events")
        assertFalse(targetOf(dir).exists(), "半截包必须删掉，不许留给下一次")
        assertEquals(2, source.opens, "半截响应是网络成因，按 spec §7 重试一次")
    }

    /**
     * 清单手写值与实测值差一个字节，也必须按下完的算（C2）。
     *
     * `sizeBytes` 是人在 APK 还不存在时手写的，CI 重跑一次、zip 时间戳变一字节就会差；
     * 用精确等式比手写值 ⇒ 每次下载都以"网络中断"告终，而硬门禁下没有任何出路。
     */
    @Test fun a_handwritten_size_bytes_off_by_one_does_not_fail_the_download() = runTest {
        val dir = newDir()
        val payload = deterministicBody(1_048_576L)
        val source = FakeSource(listOf(FakeBody(payload.size.toLong(), payload).toApkBody()))
        val staleManifest = productionLike.copy(sizeBytes = payload.size.toLong() + 1L)
        val logs = mutableListOf<String>()

        val events = run(staleManifest, dir, source, logs)

        assertIs<DownloadEvent.Success>(events.last(), "手写值差一字节就把下载判死：$events")
        assertTrue(
            logs.any { it.contains("sizeBytes") && it.contains("Content-Length") },
            "清单与实测不符却不吭声，这道漂移就永远没人知道：$logs"
        )
    }

    /** 进度分母也用实测值：清单手写 999MB 时那根条不许走到 300% 再被夹住 */
    @Test fun the_progress_denominator_is_the_measured_length_not_the_handwritten_one() = runTest {
        val dir = newDir()
        val payload = deterministicBody(1_200_000L)
        val source = FakeSource(listOf(FakeBody(payload.size.toLong(), payload).toApkBody()))
        val wrongManifest = productionLike.copy(sizeBytes = 999_000_000L)

        val progresses = run(wrongManifest, dir, source).filterIsInstance<DownloadEvent.Progress>()

        assertTrue(progresses.isNotEmpty())
        assertEquals(
            listOf(payload.size.toLong()),
            progresses.map { it.total }.distinct(),
            "Progress.total 用的是手写 sizeBytes 而不是实测长度"
        )
    }

    /** 服务器没给 Content-Length（chunked）时才退回手写值：兜底路径也得判截断 */
    @Test fun a_missing_content_length_falls_back_to_the_declared_size() = runTest {
        val dir = newDir()
        val payload = deterministicBody(1_000_000L)
        val declared = productionLike.copy(sizeBytes = 2_000_000L)
        val source = FakeSource(
            listOf(
                FakeBody(null, payload).toApkBody(),
                FakeBody(null, payload).toApkBody()
            )
        )

        val events = run(declared, dir, source)
        val failure = assertIs<DownloadEvent.Failure>(events.last())

        assertEquals(DownloadFailure.Network, failure.reason, "没有 Content-Length 时该拿手写值判截断")
        assertFalse(events.any { it is DownloadEvent.Success })
    }

    /**
     * 两边都不知道长度（chunked + 清单没填）时只剩"非空即完整" —— 已知并接受的残留，
     * 但必须留痕：不吭声的话，"完整性闸今天根本没在工作"这件事只能靠读代码发现。
     */
    @Test fun an_unknown_length_accepts_a_non_empty_body_but_leaves_a_trace() = runTest {
        val dir = newDir()
        val payload = deterministicBody(700_000L)
        val source = FakeSource(listOf(FakeBody(null, payload).toApkBody()))
        val logs = mutableListOf<String>()

        val events = run(productionLike, dir, source, logs)

        assertIs<DownloadEvent.Success>(events.last())
        assertTrue(
            logs.any { it.contains("sha256") || it.contains("sizeBytes") },
            "无从判定完整性却不留痕，就等于悄悄关掉这道闸：$logs"
        )
    }

    // ---- ④ 空间前置闸按实测长度判（C2 的另一半）----

    /**
     * 手写 `sizeBytes` 大十倍时，前置闸不许把下载拒在门外（C2：那种拒绝在硬门禁下是死的）。
     *
     * 实测 1MB、手写 240MB、余量 2.5MB（正好是实测的 2.5 倍）：按手写值判会永远 NoSpace。
     */
    @Test fun the_space_precheck_uses_the_measured_length_not_the_stale_declaration() = runTest {
        val dir = newDir()
        val payload = deterministicBody(1_000_000L)
        val source = FakeSource(listOf(FakeBody(payload.size.toLong(), payload).toApkBody()))
        val tenTimesTooBig = productionLike.copy(sizeBytes = 240_000_000L)

        val events = run(tenTimesTooBig, dir, source, free = 2_500_000L)

        assertIs<DownloadEvent.Success>(
            events.last(),
            "前置闸按手写值判 ⇒ 永久 NoSpace，门禁下没有出路：$events"
        )
    }

    /** 反过来：实测确实装不下时要**在读第一个字节之前**就拒，一个字都不落盘，并且放弃这条响应 */
    @Test fun an_unfit_download_refuses_before_writing_any_byte() = runTest {
        val dir = newDir()
        val body = FakeBody(4_000_000L, deterministicBody(4_000_000L))
        val source = FakeSource(listOf(body.toApkBody()))

        val events = run(productionLike, dir, source, free = 1_000_000L)

        val failure = assertIs<DownloadEvent.Failure>(events.single())
        assertEquals(DownloadFailure.NoSpace, failure.reason)
        assertEquals(1, body.discards, "空间不够就放弃了这条响应，必须 cancel，否则连接被挂着不回池")
        assertFalse(targetOf(dir).exists(), "不许下到一半才发现装不下")
        assertEquals(1, source.opens, "NoSpace 不是网络成因，不该自动重试")
    }

    // ---- ⑤ 自动重试：只对 Network（spec §7）----

    /** 第一次被掐半截、第二次完整 ⇒ 用户只看到成功，而且中间那条失败不 emit */
    @Test fun a_network_break_is_retried_once_and_only_once() = runTest {
        val dir = newDir()
        val full = deterministicBody(1_000_000L)
        val source = FakeSource(
            listOf(
                FakeBody(3_000_000L, full).toApkBody(), // 第一次半截
                FakeBody(full.size.toLong(), full).toApkBody(), // 第二次成功
                FakeBody(full.size.toLong(), full).toApkBody() // 第三次：不该发生
            )
        )
        val logs = mutableListOf<String>()

        val events = run(productionLike, dir, source, logs)

        assertIs<DownloadEvent.Success>(events.last(), "重试一次就该把包拿全：$events")
        assertEquals(2, source.opens, "spec §7 是\"最多重试 1 次\"，不是无限重试")
        assertFalse(
            events.any { it is DownloadEvent.Failure },
            "中间那次失败不该 emit：门禁卡片会在下载中途闪成失败态又跳回来：$events"
        )
        assertTrue(logs.any { it.contains("重试") }, "自动重试要留痕：$logs")
    }

    /** 校验不通过重一遍只是再下一遍坏包 ⇒ 不重试 */
    @Test fun a_checksum_mismatch_is_not_retried() = runTest {
        val dir = newDir()
        val payload = deterministicBody(700_000L)
        val tampered = productionLike.copy(sha256 = "0".repeat(64))
        val source = FakeSource(listOf(FakeBody(payload.size.toLong(), payload).toApkBody()))

        val events = run(tampered, dir, source)

        // 用 last() 而不是 single()：哈希是在**完整复制之后**才比的，所以那两条 512KB 节奏的
        // 进度事件早就发出去了，flow 里不止一个元素。
        val failure = assertIs<DownloadEvent.Failure>(events.last())
        assertEquals(DownloadFailure.ChecksumMismatch, failure.reason)
        assertEquals(1, source.opens, "哈希对不上是包本身的问题，重一遍只会再下一次坏的")
        assertFalse(targetOf(dir, tampered).exists(), "坏包必须删掉")
    }

    /**
     * 不是"链路中断"这一类的失败（这里用实现自己抛出的意外异常）按 Io 报、不重试。
     *
     * 成因分类错了的话，用户会照着文案去清存储、或者以为网络不好一直重连，
     * 而真正坏的可能是我们的代码 —— 所以这条既断 reason 也断 opens。
     */
    @Test fun an_unexpected_failure_is_not_reported_as_network_and_not_retried() = runTest {
        val dir = newDir()
        // 断点刻意放在 512KB 之后：否则复制循环一条进度都还没发就断了，
        // 最后那句"断之前的进度仍要发出去"就变成在测夹具而不是测代码。
        val payload = deterministicBody(1_000_000L)
        val source = FakeSource(
            List(3) {
                FakeBody(payload.size.toLong(), payload, breakAfter = 600_000L) { IOException("写盘炸了") }.toApkBody()
            }
        )

        val events = run(productionLike, dir, source)

        val failure = assertIs<DownloadEvent.Failure>(events.last())
        assertEquals(DownloadFailure.Io, failure.reason, "非网络成因不许报成\"请检查网络后重试\"")
        assertEquals(1, source.opens, "Io 不在自动重试的那条例外里")
        assertTrue(events.filterIsInstance<DownloadEvent.Progress>().isNotEmpty(), "断之前收到的进度仍要发出去")
    }

    /** 链路真的抛异常（不是正常结束）也算 Network，并且同样只重试一次 */
    @Test fun a_thrown_body_failure_is_reported_as_network_and_retried_once() = runTest {
        val dir = newDir()
        val payload = deterministicBody(1_000_000L)
        val source = FakeSource(
            listOf(
                FakeBody(payload.size.toLong(), payload, breakAfter = 600_000L).toApkBody(),
                FakeBody(payload.size.toLong(), payload, breakAfter = 600_000L).toApkBody()
            )
        )

        val events = run(productionLike, dir, source)

        assertEquals(DownloadFailure.Network, assertIs<DownloadEvent.Failure>(events.last()).reason)
        assertEquals(2, source.opens)
        assertTrue(events.filterIsInstance<DownloadEvent.Progress>().isNotEmpty(), "断开之前的进度仍然要发出去")
        assertFalse(targetOf(dir).exists(), "被掐断的半截包必须删掉")
    }

    // ---- ⑥ 目录建不出来时给得出成因 ----

    /**
     * `mkdirs()` 的返回值必须判，而且要说清是目录问题而不是存储问题。
     *
     * 过去这条路径会被 `FileOutputStream` 的异常折成 Io，文案是"请清理手机存储后重试"，
     * 而真正的原因可能是同路径上挡着一个同名文件 —— 用户清完存储还是装不上，
     * 排障方向从第一句就被带偏。这里额外断言"一个请求都没发"：前置检查在开网之前。
     */
    @Test fun an_unusable_directory_reports_its_own_cause_without_touching_the_network() = runTest {
        val blocker = File.createTempFile("jianyi-not-a-dir", ".bin").apply { deleteOnExit() }
        val dir = File(blocker, "update")
        val source = FakeSource(emptyList())
        val logs = mutableListOf<String>()

        val events = run(productionLike, dir, source, logs)

        assertEquals(DownloadFailure.Io, assertIs<DownloadEvent.Failure>(events.single()).reason)
        assertEquals(0, source.opens, "目录都建不出来，不该去打网络")
        assertTrue(logs.any { it.contains("建不出来") }, "没留下成因，只剩一句误导的文案：$logs")
    }

    // ---- 夹具 ----

    private fun newDir(): File = Files.createTempDirectory("jianyi-update-flow").toFile().apply { deleteOnExit() }

    /** 含 0x80 以上字节的内容，形状与 ApkDownloaderTest 里那份一致 */
    private fun deterministicBody(length: Long): ByteArray = ByteArray(length.toInt()) { (it % 251).toByte() }
}
