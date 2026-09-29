package com.jianyi.outfit.data.update

import com.jianyi.outfit.ui.update.DownloadEvent
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ApkDownloader 里**能在普通 JVM 上跑的那一半**：哈希口径、空间前置检查、文件名、
 * 以及那条"边写边算边报进度"的复制循环。
 *
 * 网络那一半不在这里打真请求：真要验证的是"Gitee 的三跳签名直链能不能下完"，
 * 那属于 Task 11 的真机回归，不属于一轮该在 CI 上跑的单测。
 *
 * ## 为什么复制循环要被抽出来单独测（本轮最承重的一条）
 * Task 5 之后那版接缝只有 `Success | Failure` 两个终态，后果是 24MB 的下载里
 * 门禁那根进度条从 0% 一路死到装完，而**两侧测试全绿**：算 `progressOf`/`sizeText`
 * 的算术用例没错，测终态跳转的 VM 用例也没错，错的是中间没人发 Progress。
 * 所以"发不发 Progress"必须有一条能变红的锁，而锁只能钉在它能被观察到的地方 ——
 * [copyWithProgress] 不依赖 HTTP，`emit` 是它的参数，把循环里那行 emit 删掉这条文件就红。
 *
 * ## 为什么 `sha256Of` 也在这儿测
 * 解析器 `UpdateManifestParser.isSha256Hex()` 只认 64 位**小写**十六进制。
 * 我们的哈希串要是写成大写，症状不是"校验失败"而是**整份清单被拒 → 判 Unreachable →
 * 永远没人收到更新，且零日志零红测**（Task 10 的实现者在 CI 脚本里真抓到过这条不一致）。
 * 所以除了公开测试向量，这里还把算出来的串原样塞进清单去过一遍解析器。
 */
class ApkDownloaderTest {

    // ---- sha256Of：公开向量 + 大小写口径 ----

    @Test fun sha256_of_known_bytes() {
        val f = tempFileOf("abc".toByteArray())
        // 公开测试向量：sha256("abc")
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Of(f)
        )
    }

    @Test fun empty_file_hashes_as_empty_string_input() {
        val f = tempFileOf(ByteArray(0))
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Of(f)
        )
    }

    /**
     * 三条断言是一件事的三个面：长度对、字符集只有小写、解析器收得下。
     * 最后那条反向断言不是洁癖 —— 少了它，前面那条"小写"检查就是空的：
     * 一个"大小写都接受"的解析器会让大写哈希也过，于是这条锁形同不存在。
     */
    @Test fun computed_hash_is_lowercase_hex_and_the_manifest_parser_accepts_it() {
        val hash = sha256Of(tempFileOf("abc".toByteArray()))
        assertNotNull(hash)
        assertTrue(
            hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' },
            "哈希串必须是 64 位小写十六进制，实际得到：$hash"
        )
        assertNotNull(
            UpdateManifestParser.parse(manifestJsonWith(hash)),
            "算出来的哈希串过不了清单解析器 = 清单一填哈希就整份作废"
        )
        assertNull(
            UpdateManifestParser.parse(manifestJsonWith(hash.uppercase())),
            "解析器其实不收大写：所以我们的 toHex 一旦变大写就是安静失效"
        )
    }

    // ---- shouldRefuseDownload：空间不够就别开始 ----

    /** 计划给的三条：2.5 倍以下拦、够就放、不知道包多大就不拦 */
    @Test fun refuses_when_free_space_below_2_5x_package() {
        assertTrue(shouldRefuseDownload(total = 24_000_000L, free = 40_000_000L))
        assertFalse(shouldRefuseDownload(total = 24_000_000L, free = 60_000_000L), "2.5 倍的空间该放行")
        assertFalse(shouldRefuseDownload(total = null, free = 1_000L), "不知道包多大时不该拦")
    }

    /**
     * 边界本身（free 正好等于 total × 2.5）算"够"：
     * 判据写成 `<` 还是 `<=` 在这里是可观察的，而浮点系数会让这个边界抖 ——
     * 所以实现用整数比，这条用例才谈得上"边界"。
     */
    @Test fun the_exact_2_5x_boundary_still_allows_the_download() {
        assertFalse(shouldRefuseDownload(total = 24_000_000L, free = 60_000_000L), "正好 2.5 倍是够的")
        assertFalse(shouldRefuseDownload(total = 10L, free = 25L), "正好 2.5 倍是够的")
    }

    /** 差一个字节就拦：余量是给安装解包用的，不是装饰 */
    @Test fun one_byte_below_the_boundary_refuses() {
        assertTrue(shouldRefuseDownload(total = 24_000_000L, free = 59_999_999L))
        assertTrue(shouldRefuseDownload(total = 10L, free = 24L))
        assertTrue(shouldRefuseDownload(total = 24_000_000L, free = 0L))
    }

    /**
     * 清单没声明 sizeBytes 时**跳过**前置检查，而不是把 null 当 0 去判。
     * 当成 0 会得到"永远不拦"这个正确结果，但理由错了：它假装自己检查过。
     * 真实代价写在这里 —— 那种情况下磁盘写满只能等写盘异常，报的是 Io
     * （"写入失败，请清理手机存储后重试"），文案仍然对得上成因。
     */
    @Test fun unknown_package_size_skips_the_precheck() {
        assertFalse(shouldRefuseDownload(total = null, free = 0L), "没有 sizeBytes 就跳过前置检查，不是一律拒绝")
        assertFalse(shouldRefuseDownload(total = null, free = -1L), "没有 sizeBytes 就跳过前置检查，不是一律拒绝")
    }

    // ---- 文件名 ----

    @Test fun target_name_includes_version() {
        assertEquals("jianyi-2.2.0.apk", apkFileNameFor("2.2.0"))
    }

    /** 同版本重下覆盖同一个文件；不同版本不许互相踩（不许把上一次的半截包当这一代的） */
    @Test fun different_versions_get_different_files() {
        assertTrue(
            apkFileNameFor("2.2.0") != apkFileNameFor("2.2.1"),
            "两个版本号映射到同一个文件名，旧包会被当成新包"
        )
    }

    /**
     * `versionName` 是清单里作者手写的字符串，而它会被拼进 cacheDir 下的路径：
     * 清洗（而不是拒绝）的判据是"这个字段对下载本身毫无作用"，
     * 所以 `../x` 那种值应当被夹成一个普通文件名，而不是让半截 APK 落到 update 目录外面。
     */
    @Test fun a_hostile_version_name_cannot_escape_the_directory() {
        val name = apkFileNameFor("../../evil")
        assertTrue('/' !in name && '\\' !in name, "清洗后仍带路径分隔符：$name")
        assertTrue(name.startsWith("jianyi-") && name.endsWith(".apk"), "文件名形状变了：$name")
    }

    // ---- 响应体长度：没有异常也可能只下一大半 ----

    /**
     * 实测 Gitee 附件会"传一大半自己断掉"（30 秒推了 26MB / 58MB 才掐断），
     * 而那种断开可能只是通道正常结束、不抛异常。不拦的下一步就是把半截包当成功：
     * 清单声明了 sha256 时会被校验拦住（运气好），没声明时安装页直接报"解析软件包时出现问题"，
     * 而我们自己的日志写着"下载成功"。
     */
    @Test fun a_short_body_with_declared_size_is_not_complete() {
        assertFalse(isBodyComplete(written = 26_000_000L, declaredSize = 58_000_000L), "半截响应不许算下完")
        assertTrue(isBodyComplete(written = 58_000_000L, declaredSize = 58_000_000L))
        // 0 字节永远不算完整：它是"通道立刻 EOF"的返回值，不是一个 APK
        assertFalse(isBodyComplete(written = 0L, declaredSize = null), "0 字节不是一个 APK")
        assertFalse(isBodyComplete(written = 0L, declaredSize = 100L), "0 字节不是一个 APK")
        // 没有声明长度时无从比对，只要真收到过字节就算走完了
        assertTrue(isBodyComplete(written = 1L, declaredSize = null))
    }

    // ---- 复制循环：进度、哈希、落盘内容 ----

    /** 每 512KB 一条：3MB 该有 6 条，而不是 1 条终态、也不是 48 条每块一条 */
    @Test fun copy_emits_a_progress_for_every_512kb() = runTest {
        val body = deterministicBody(3L * 1024L * 1024L)
        val events = mutableListOf<DownloadEvent.Progress>()
        copyOn(body, total = body.size.toLong(), events)

        assertTrue(events.size >= 5, "3MB 的下载只发了 ${events.size} 条进度 —— 进度条就是死的")
        assertTrue(
            events.size <= body.size / (128 * 1024),
            "每 3MB/${events.size} 字节发一条太密了，门禁卡片会被重组淹没"
        )
        assertEquals(
            listOf(524_288L, 1_048_576L, 1_572_864L, 2_097_152L, 2_621_440L, 3_145_728L),
            events.map { it.done },
            "进度该按 512KB 的节奏走，并且最后一条正好是总字节数"
        )
    }

    /**
     * 不足 512KB 的响应也必须发至少一条：`ApkInstaller` 的 KDoc 写着
     * "实现方必须在下载过程中发至少一条 Progress"，只在整块时发就兑现不了这句承诺。
     */
    @Test fun copy_emits_at_least_one_progress_for_a_small_body() = runTest {
        val body = deterministicBody(100L)
        val events = mutableListOf<DownloadEvent.Progress>()
        copyOn(body, total = 100L, events)

        assertEquals(1, events.size)
        assertEquals(100L, events.single().done)
    }

    /**
     * total 必须落在清单声明的 sizeBytes 上。VM 那一头 `event.total ?: manifest.sizeBytes`
     * 兜了底，但兜底不等于口径对：清单说 24MB、这里报 8MB，那根条就会走到 300% 然后被夹在 100%。
     */
    @Test fun copy_reports_the_declared_total_so_the_bar_has_a_denominator() = runTest {
        val body = deterministicBody(2L * 1024L * 1024L)
        val events = mutableListOf<DownloadEvent.Progress>()
        copyOn(body, total = 999_000L, events)

        assertTrue(events.isNotEmpty())
        assertEquals(999_000L, events.last().total, "Progress.total 不是清单声明的那个 sizeBytes")
    }

    /** 清单没声明大小时 total 传 null：UI 走不确定态，而不是画一根假的 0% */
    @Test fun copy_keeps_total_null_when_the_manifest_declares_nothing() = runTest {
        val body = deterministicBody(2L * 1024L * 1024L)
        val events = mutableListOf<DownloadEvent.Progress>()
        copyOn(body, total = null, events)

        assertEquals(1, events.map { it.total }.distinct().size)
        assertNull(events.last().total)
    }

    /**
     * 跨层锁：增量算出来的哈希 == 对同一个文件走一遍 sha256Of。
     * 这条同时钉住 `digest.update(buf, 0, read)` 的偏移量 —— 写错偏移时这里红，
     * 而"哈希对不对"在真机上的表现是装得上/装不上，没人能归因。
     */
    @Test fun copy_digest_matches_the_file_helper_for_the_same_bytes() = runTest {
        val body = deterministicBody(3L * 1024L * 1024L)
        val outcome = copyOn(body, total = body.size.toLong(), mutableListOf())

        val reference = tempFileOf(body)
        assertEquals(sha256Of(reference), outcome.sha256Hex)
        assertEquals(
            MessageDigest.getInstance("SHA-256").digest(body).toHexForTest(),
            outcome.sha256Hex
        )
        assertEquals(body.size.toLong(), outcome.written)
    }

    /** 写出去的字节必须和读进来的一字不差（偏移或长度写反时这条先红） */
    @Test fun copy_writes_the_bytes_it_read_unchanged() = runTest {
        val body = deterministicBody(700_000L)
        val sink = ByteArrayOutputStream()
        copyOn(body, total = body.size.toLong(), mutableListOf(), sink)

        assertContentEquals(body, sink.toByteArray())
    }

    // ---- 测试夹具 ----

    /**
     * 跑一遍复制循环。`readChunk` 每次给 64KB（和真实响应体的粒度一致），
     * 这样"每 512KB 发一条"才是被真的算出来、而不是被一次性喂完蒙过去的。
     */
    private suspend fun copyOn(
        body: ByteArray,
        total: Long?,
        events: MutableList<DownloadEvent.Progress>,
        sink: ByteArrayOutputStream = ByteArrayOutputStream()
    ): CopyOutcome {
        var pos = 0
        return copyWithProgress(
            readChunk = { buffer ->
                if (pos >= body.size) {
                    -1
                } else {
                    val n = minOf(buffer.size, body.size - pos, 64 * 1024)
                    body.copyInto(buffer, 0, pos, pos + n)
                    pos += n
                    n
                }
            },
            write = { buffer, count -> sink.write(buffer, 0, count) },
            total = total,
            onProgress = { event -> events.add(event) }
        )
    }

    /** 含 0x80 以上字节的内容：小写十六进制那条口径只有在高位字节上才测得出来 */
    private fun deterministicBody(length: Long): ByteArray =
        ByteArray(length.toInt()) { (it % 251).toByte() }

    private fun tempFileOf(bytes: ByteArray): File =
        File.createTempFile("jianyi-update-test", ".bin").apply {
            deleteOnExit()
            writeBytes(bytes)
        }

    private fun manifestJsonWith(sha256: String): String =
        "{\"versionCode\":9,\"versionName\":\"2.2.0\",\"minSupportedVersionCode\":9," +
            "\"apkUrl\":\"https://gitee.com/wuliao11541/WeatherOutfit/releases/download/v2.2.0/jianyi-2.2.0.apk\"," +
            "\"sha256\":\"$sha256\"}"

    /** 与 MessageDigest 的结果比对用：JDK 自己算的字节数组，按同一套小写十六进制拼串 */
    private fun ByteArray.toHexForTest(): String =
        joinToString("") { b -> (b.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
