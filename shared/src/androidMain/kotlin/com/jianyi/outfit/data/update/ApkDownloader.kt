package com.jianyi.outfit.data.update

import android.content.Context
import com.jianyi.outfit.data.remote.httpClientEngine
import com.jianyi.outfit.ui.update.ApkInstaller
import com.jianyi.outfit.ui.update.DownloadEvent
import com.jianyi.outfit.ui.update.DownloadFailure
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * APK 落盘下载器：Ktor 流式读 + 边写盘边算 sha256 + 每 512KB 一条进度。
 *
 * 放在 `shared/androidMain` 而不是 app 模块：shared 的 Ktor 是 `implementation()`，
 * app 的编译类路径上没有 `io.ktor.*`，而这里要用的引擎工厂 `httpClientEngine()`
 * 本来就是 shared 模块内的 internal 声明（Ruling 见 SDD 台账）。
 *
 * 四条不按直觉的规矩，都有实测依据，改之前先读它们各自的理由：
 *
 * 1. **没有断点续传**。实测 `foruda.gitee.com` 对附件地址**忽略 `Range`**
 *    （请求 `bytes=0-99` 直接开始整文件传输，30 秒推了 26MB / 58MB 才掐断），
 *    所以"从上次偏移继续"这条路根本不存在：每次先删目标文件、失败即删临时文件，
 *    文案里也不许出现"已恢复上次进度"。
 * 2. **每次重新走三跳**。Gitee 的 `attach_file` 签名直链约 15 分钟过期（过期后 401），
 *    所以这里只按清单里的 `apkUrl` 发请求，绝不缓存跟完重定向之后的地址 ——
 *    缓存下来的那条会在一会儿之后必然 401，而"点重试还是同一条死地址"是最难归因的形状。
 *    `followRedirects = true` 是这条链能走通的前提（实测 HEAD 跟完三跳是 200）。
 * 3. **进度必须真的发出去**。接缝只有 `Success | Failure` 两个终态的那一版，
 *    VM 里 `Downloading.done` 再也没有东西赋值，24MB 的下载全程一根 0% 死条，
 *    而算 `progressOf` 的算术用例与测终态跳转的 VM 用例**两边全绿**。
 *    复制循环因此被抽成 [copyWithProgress]（不依赖 HTTP、`onProgress` 是参数），
 *    这样"把那行 emit 删掉"这件事能让 [ApkDownloaderTest] 变红。
 * 4. **空间不够就别开始**（[shouldRefuseDownload]，在发第一个字节之前判）。
 *    不许"下到 90% 才报 NoSpace" —— 那是最难看的形态：白烧一遍流量，
 *    而用户已经等了半分钟。
 */

/** 下载目录名（`cacheDir/update`）。Task 8 的 `file_paths.xml` 必须覆盖这一层，否则 FileProvider 拒绝给 URI */
private const val UPDATE_DIR_NAME = "update"

/** 单次从响应体读的块大小：64KB 够摊薄系统调用，又不会让进度按块发 */
private const val READ_BUFFER_BYTES = 64 * 1024

/**
 * 进度节奏：每 512KB 一条。
 * 两端都挨着理由：一个 64KB 块发一条会让门禁卡片每秒重组十几次；
 * 只在结尾发一条等于没发（就是第 3 条规矩里那根死条）。
 */
private const val PROGRESS_CHUNK_BYTES = 512L * 1024L

/**
 * 需要的空间 = 包体 × 2.5（写盘之外还要给安装时解包留地方），宁可保守。
 * 写成 5/2 的整数比而不是 2.5 的 Double：判据的边界本身要被测（正好 2.5 倍算够），
 * 浮点会让那条用例在边界上抖。
 */
private const val SPACE_FACTOR_NUM = 5L
private const val SPACE_FACTOR_DEN = 2L

/**
 * 目标文件名带版本号：同版本重下覆盖同一个文件，不同版本不许互相踩。
 *
 * `versionName` 是清单里作者手写的字符串，会直接拼进 `cacheDir` 下的路径，
 * 所以只保留字母数字与 `._-`，其它换成下划线 —— `../x` 那种值不该有能力走出 update 目录。
 * 选清洗而不是拒绝，是因为这个字段对下载本身毫无作用（人读的是清单里那一份），
 * 拒绝会把一次笔误放大成"门禁下什么都没有"。
 *
 * 连续的点单独再夹一次，理由不是"白名单漏了它"（`.` 本来就在白名单里），而是
 * **它今天出不了目录靠的是 `"jianyi-"` 这个前缀恰好挡住了 `..`**：一个没人依赖的前缀
 * 不算守卫，哪天改文件名口径它就会静默变成 `cacheDir/update/` 之外的路径，
 * 而那时表现是 Task 8 的 `install()` 抛 `IllegalArgumentException`，归因链断在最难查的一端。
 * 影响面为零：真实版本号里不会出现两个连续的点。
 */
fun apkFileNameFor(versionName: String): String {
    val safe = versionName.map { if (it.isLetterOrDigit() || it == '.' || it == '_' || it == '-') it else '_' }
        .joinToString("")
        .replace("..", "_")
    return "jianyi-$safe.apk"
}

/**
 * 要不要在下载开始之前拒绝。`total` 是清单声明的 `sizeBytes`，`free` 是目标分区的可用空间。
 *
 * `total == null`（清单没声明大小）时**跳过**这道前置检查，而不是把 null 当 0 去判：
 * 当 0 也会得到"不拦"这个正确结果，但理由是错的，它会假装自己检查过。
 * 跳过之后写盘真满了，走的是捕获写异常那条路 ⇒ `DownloadFailure.Io`
 * ⇒ 文案"写入失败，请清理手机存储后重试"，成因仍然对得上。
 */
internal fun shouldRefuseDownload(total: Long?, free: Long): Boolean =
    total != null && free * SPACE_FACTOR_DEN < total * SPACE_FACTOR_NUM

/**
 * 收到的字节数配不配得上声明的大小。
 *
 * 这条是"Gitee 附件会传一大半自己断掉"的第二道闸：那种断开**可能只是通道正常结束、
 * 不抛异常**（实测 26MB / 58MB 被掐）。只靠 sha256 兜不住 —— 清单里 `sha256` 是可选字段，
 * 没声明时半截包会被当成 `Success` 交出去，表现是系统安装页"解析软件包时出现问题"，
 * 而我们自己那条链一路绿。0 字节永远不算完整（它是"通道立刻 EOF"的返回值，不是 APK）。
 */
internal fun isBodyComplete(written: Long, declaredSize: Long?): Boolean =
    written > 0L && (declaredSize == null || written == declaredSize)

/** 复制循环的结果：写盘字节数 + 这些小字节的 sha256（小写十六进制） */
internal data class CopyOutcome(val written: Long, val sha256Hex: String)

/**
 * 边读、边写、边算哈希、边按 512KB 节奏发进度。一次调用的循环体，抽出来的唯一理由是可测。
 *
 * `readChunk` 返回已读字节数，`<= 0` 表示结束（Ktor 的 `readAvailable` 在 EOF 给 -1；
 * 真出现 0 也当结束处理，宁可少读也不能空转 —— 少读的后果由 [isBodyComplete] 兜住）。
 *
 * `onProgress` 必须在**调用方同一个协程**里被调用：这里传进来的是 `flow {}` 的 `emit`，
 * 跨协程 emit 会抛 IllegalStateException。本函数不切上下文、不 launch，所以成立。
 */
internal suspend fun copyWithProgress(
    readChunk: suspend (ByteArray) -> Int,
    write: (buffer: ByteArray, count: Int) -> Unit,
    total: Long?,
    onProgress: suspend (DownloadEvent.Progress) -> Unit
): CopyOutcome {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(READ_BUFFER_BYTES)
    var written = 0L
    var sinceReport = 0L
    while (true) {
        val count = readChunk(buffer)
        if (count <= 0) break
        digest.update(buffer, 0, count)
        write(buffer, count)
        written += count
        sinceReport += count
        if (sinceReport >= PROGRESS_CHUNK_BYTES) {
            sinceReport = 0L
            onProgress(DownloadEvent.Progress(written, total))
        }
    }
    // 尾巴：不足一个 512KB 的响应如果只在整块时发，就一条都发不出去，
    // 而 ApkInstaller 的约定是"下载过程中至少一条 Progress"。
    if (sinceReport > 0L) {
        onProgress(DownloadEvent.Progress(written, total))
    }
    return CopyOutcome(written, digest.digest().toLowerHex())
}

/**
 * 响应体读失败与写盘失败要报不同的话：前者是"下载中断，请检查网络后重试"，
 * 后者是"写入失败，请清理手机存储后重试"。两者都是 IOException，从异常本身分不出来，
 * 所以在发生的现场各包一层 —— 靠猜（或按 message 字符串匹配）会把一次网络中断
 * 说成存储问题，用户去清理存储而真正坏的是链路。
 */
private class BodyReadFailure(cause: Exception) : Exception(cause)

private class BodyWriteFailure(cause: Exception) : Exception(cause)

class ApkDownloader(private val context: Context) : ApkInstaller {

    private val client = HttpClient(httpClientEngine()) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            // 只夹"连接"和"单次读"。刻意**不设 requestTimeoutMillis**（Ktor 默认 0 = 不限）：
            // 那是整条请求的总时限，24MB 的包在弱网下本来就该慢慢下完，
            // 设了它表现是"每次都在同一段进度上失败"，而看起来像链路不稳。
            socketTimeoutMillis = 30_000
        }
        // 三跳（gitee.com → raw/下载跳转 → foruda 签名直链）都在这一条请求里跟完
        followRedirects = true
        // 状态码自己判：401 是签名过期、404 是 release 被删，两者都要落进可重试的 Network
        expectSuccess = false
    }

    override fun download(manifest: UpdateManifest): Flow<DownloadEvent> = flow {
        val dir = File(context.cacheDir, UPDATE_DIR_NAME).apply { mkdirs() }
        val target = File(dir, apkFileNameFor(manifest.versionName))
        // 没有续传，所以先删干净再开：留着上一次的半截包不但会被覆盖写，
        // 还会吃掉下面那道空间检查正在量的那点余量。
        target.delete()

        // 第一个字节都不发之前就拒（第 4 条规矩）
        if (shouldRefuseDownload(manifest.sizeBytes, dir.usableSpace)) {
            emit(DownloadEvent.Failure(DownloadFailure.NoSpace))
            return@flow
        }

        val response = try {
            client.get(manifest.apkUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(DownloadEvent.Failure(DownloadFailure.Network))
            return@flow
        }
        if (!response.status.isSuccess()) {
            // 401（签名直链过期）走这里。不缓存任何跟完重定向后的地址，
            // 所以用户再点一次"重试"就是重新走一遍三跳、拿一条新签名地址。
            emit(DownloadEvent.Failure(DownloadFailure.Network))
            return@flow
        }

        val channel = response.bodyAsChannel()
        val outcome = try {
            FileOutputStream(target).use { out ->
                copyWithProgress(
                    readChunk = { buffer ->
                        try {
                            channel.readAvailable(buffer)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            throw BodyReadFailure(e)
                        }
                    },
                    write = { buffer, count ->
                        try {
                            out.write(buffer, 0, count)
                        } catch (e: Exception) {
                            throw BodyWriteFailure(e)
                        }
                    },
                    total = manifest.sizeBytes,
                    onProgress = { event -> emit(event) }
                )
            }
        } catch (e: BodyReadFailure) {
            target.delete()
            emit(DownloadEvent.Failure(DownloadFailure.Network))
            return@flow
        } catch (e: BodyWriteFailure) {
            target.delete()
            emit(DownloadEvent.Failure(DownloadFailure.Io))
            return@flow
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            target.delete()
            emit(DownloadEvent.Failure(DownloadFailure.Io))
            return@flow
        }

        if (!isBodyComplete(outcome.written, manifest.sizeBytes)) {
            target.delete()
            emit(DownloadEvent.Failure(DownloadFailure.Network))
            return@flow
        }

        val expected = manifest.sha256
        if (expected != null) {
            // 严格相等，不用 ignoreCase：清单那一侧由 isSane() 保证是小写，
            // 这一侧由 toLowerHex() 保证是小写。"大小写都接受"会把任何一侧的口径漂移
            // 从"立刻变红"变成"以后永远静默"。
            if (outcome.sha256Hex != expected) {
                // 删掉坏包：留着它，将来任何"这个文件已经下过了"的优化都会直接把它装上
                target.delete()
                emit(DownloadEvent.Failure(DownloadFailure.ChecksumMismatch))
                return@flow
            }
        }
        emit(DownloadEvent.Success(target.absolutePath))
    }.flowOn(Dispatchers.IO) // 写盘是阻塞 IO，不该占着容器的 Default 计算池
}

/**
 * 小写十六进制，逐位查表。
 *
 * 口径由解析器单方面定死：`UpdateManifestParser.isSha256Hex()` 只认 64 位**小写**十六进制。
 * 这条串要是大写，症状不是"校验失败"而是**整份清单被拒 ⇒ 判 Unreachable ⇒
 * 永远没人收到更新，零日志零红测**（Task 10 的实现者在 CI 脚本里真抓到过这条不一致）。
 * 不用 `"%02x".format(it)`：那是 JDK 的 Formatter，查表版本一眼看得出"没有大写的可能"。
 */
private fun ByteArray.toLowerHex(): String {
    val builder = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        builder.append(HEX_DIGITS[v shr 4]).append(HEX_DIGITS[v and 0x0F])
    }
    return builder.toString()
}

private val HEX_DIGITS = "0123456789abcdef".toCharArray()

/**
 * 整个文件的 sha256。生产路径用不到它（下载时是增量算的），它存在的理由是：
 * 哈希口径（分块喂 MessageDigest + 小写十六进制）要在普通 JVM 里被公开向量测一遍，
 * 而 [copyWithProgress] 增量算出来的结果由一条用例拿去和这里比对同一个文件 ——
 * 于是偏移量写错这类问题也有锁。
 */
internal fun sha256Of(file: File): String? = try {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(READ_BUFFER_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break // FileInputStream 只在 EOF 给 -1，缓冲区非空不会给 0
            digest.update(buffer, 0, count)
        }
    }
    digest.digest().toLowerHex()
} catch (e: Exception) {
    null
}
