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
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * APK 落盘下载器：Ktor 流式读 + 边写盘边算 sha256 + 每 512KB 一条进度。
 *
 * 放在 `shared/androidMain` 而不是 app 模块：shared 的 Ktor 是 `implementation()`，
 * app 的编译类路径上没有 `io.ktor.*`，而这里要用的引擎工厂 `httpClientEngine()`
 * 本来就是 shared 模块内的 internal 声明（Ruling 见 SDD 台账）。
 *
 * 分成两层是本轮评审逼出来的：
 * - [downloadEvents] 是**判定与组装**（目录、空间前置闸、完整性、哈希、进度节奏、重试），
 *   它只认一个 [OpenApkBody] 接缝，所以能在普通 JVM 里被真构造、被用例钉住；
 * - [ApkDownloader] 是**平台接线**（Ktor GET、cacheDir、flowOn），一行判定都不写。
 *   之前判定和接线揉在同一个 `flow {}` 里，结果是"全仓没有任何测试构造过 ApkDownloader"，
 *   把 `onProgress = { emit(it) }` 改成空 lambda 之后 185 条全绿 —— 接缝没锁。
 *
 * 六条不按直觉的规矩，都有实测依据，改之前先读它们各自的理由：
 *
 * 1. **完整性与进度分母只看服务器说的长度**（[ApkBody.contentLength]，即响应头里的
 *    `Content-Length`），清单里手写的 `sizeBytes` 只在它缺席时兜底、外加"对不上就报警"。
 *    两个理由各对应一种真实事故：
 *    - 生产 `update.json` 既没 `sha256` 也没 `sizeBytes`，于是"声明缺失就当非空即完整"，
 *      而实测那条 CDN 会把 58MB 掐成 26MB 后**正常结束、不抛异常**（spec §3.4）⇒
 *      半截包被当 `Success` 交给安装页，表现为"解析软件包时出现问题"，我们整条链全绿；
 *    - `sizeBytes` 是人在 APK 还不存在时手写的（`sha256` 改成可选的同一条理由），
 *      CI 重跑一次、zip 时间戳变一字节 ⇒ 精确等式让每次下载都以"网络中断"告终；
 *      写大十倍 ⇒ 前置闸按错的数算 ⇒ 永久 `NoSpace`，硬门禁下没有任何出路。
 *    `Content-Length` 由服务器在传第一个字节之前给出，它描述的就是这一次正在传的响应，
 *    所以它比任何手写数字更有资格当判据。
 * 2. **没有断点续传**。实测 `foruda.gitee.com` 对附件地址**忽略 `Range`**
 *    （请求 `bytes=0-99` 直接开始整文件传输，30 秒推了 26MB / 58MB 才掐断），
 *    所以"从上次偏移继续"这条路根本不存在：每次先删目标文件、失败即删临时文件，
 *    文案里也不许出现"已恢复上次进度"。
 * 3. **每次重新走三跳**。Gitee 的 `attach_file` 签名直链约 15 分钟过期（过期后 401），
 *    所以这里只按清单里的 `apkUrl` 发请求，绝不缓存跟完重定向之后的地址 ——
 *    缓存下来的那条会在一会儿之后必然 401，而"点重试还是同一条死地址"是最难归因的形状。
 *    `followRedirects = true` 是这条链能走通的前提（实测 HEAD 跟完三跳是 200）。
 * 4. **进度必须真的发出去**。接缝只有 `Success | Failure` 两个终态的那一版，
 *    VM 里 `Downloading.done` 再也没有东西赋值，24MB 的下载全程一根 0% 死条，
 *    而算 `progressOf` 的算术用例与测终态跳转的 VM 用例**两边全绿**。
 *    复制循环因此被抽成 [copyWithProgress]（不依赖 HTTP、`onProgress` 是参数），
 *    而 flow 组装因此被抽成 [downloadEvents] —— 于是"把 emit 换成空 lambda"这件事
 *    会让 [ApkDownloadFlowTest] 变红，包括那条一直通往进度条的跨层用例。
 * 5. **空间不够就别开始**（[shouldRefuseDownload]，在打开文件、读第一个字节之前判）。
 *    不许"下到 90% 才报 NoSpace" —— 那是最难看的形态：白烧一遍流量，
 *    而用户已经等了半分钟。判据用的是实测长度而不是手写长度（第 1 条）。
 * 6. **只有网络成因自动重试一次**（[MAX_AUTO_RETRIES]）。spec §7 写的就是"最多重试 1 次"；
 *    `ChecksumMismatch` 重试只是再下一遍坏包，`NoSpace` 重试必然还是不够，`Io` 是本地盘的问题 ——
 *    把它们也重一遍等于把一次故障变成两次流量。
 */

/**
 * 下载目录名（`cacheDir/update`）。
 *
 * public 而不是 private 的理由：同一个目录名现在是三处共同的契约 ——
 * 这里的落盘位置、Task 8 的 `file_paths.xml`（`path="update/"`）、
 * 以及 `AndroidUpdateGateway` 的安装路径守卫（只允许从这个目录里装包）。
 * 各写一份字面量就会漂，而漂了的症状是安装页弹不出或守卫放行/拒错。
 */
const val UPDATE_DIR_NAME = "update"

/** 单次从响应体读的块大小：64KB 够摊薄系统调用，又不会让进度按块发 */
private const val READ_BUFFER_BYTES = 64 * 1024

/**
 * 进度节奏：每 512KB 一条。
 * 两端都挨着理由：一个 64KB 块发一条会让门禁卡片每秒重组十几次；
 * 只在结尾发一条等于没发（就是第 4 条规矩里那根死条）。
 */
private const val PROGRESS_CHUNK_BYTES = 512L * 1024L

/**
 * 需要的空间 = 包体 × 2.5（写盘之外还要给安装时解包留地方），宁可保守。
 * 写成 5/2 的整数比而不是 2.5 的 Double：判据的边界本身要被测（正好 2.5 倍算够），
 * 浮点会让这个边界抖。
 */
private const val SPACE_FACTOR_NUM = 5L
private const val SPACE_FACTOR_DEN = 2L

/**
 * 空间前置闸的包体上限 = Gitee 单附件配额（spec §3.7：100MB）。
 *
 * 加顶有两个理由，第二个才是致命的：清单里的 `sizeBytes` 是手写的、解析器只夹住"必须为正"
 * （`isSane()` 没有上限），一个 1e18 那种量级的笔误会让下面 `total * 5` **溢出成负数**，
 * 于是 `free * 2 < 负数` 恒 false ⇒ "永远不拦" —— 一道为了省流量而设的闸被一个笔误关掉。
 * 真包不可能超过配额，所以按配额夹住不改变任何正确判定，只是让溢出没有输入。
 */
private const val SPACE_CAP_BYTES = 100L * 1024L * 1024L

/** 比较两侧的乘积都在这个量级内，所以两边都不可能溢出（`free` 也照同一个顶夹住） */
private const val SPACE_COMPARE_CAP_BYTES = SPACE_CAP_BYTES * SPACE_FACTOR_NUM

/** 网络成因的自动重试次数（第 6 条规矩）：spec §7 的"最多重试 1 次" */
private const val MAX_AUTO_RETRIES = 1

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
 * 要不要在下载开始之前拒绝。`total` 是**这次响应的实测长度**（Content-Length，
 * 缺席时才退回清单声明的 `sizeBytes`），`free` 是目标分区的可用空间。
 *
 * `total == null`（服务器没说、清单也没声明）时**跳过**这道前置检查，而不是把 null 当 0 去判：
 * 当 0 也会得到"不拦"这个正确结果，但理由是错的，它会假装自己检查过。
 * 跳过之后写盘真满了，走的是捕获写异常那条路 ⇒ `DownloadFailure.Io`
 * ⇒ 文案"写入失败，请清理手机存储后重试"，成因仍然对得上。
 */
internal fun shouldRefuseDownload(total: Long?, free: Long): Boolean {
    if (total == null) return false
    val boundedTotal = total.coerceAtMost(SPACE_CAP_BYTES)
    val boundedFree = free.coerceIn(0L, SPACE_COMPARE_CAP_BYTES)
    return boundedFree * SPACE_FACTOR_DEN < boundedTotal * SPACE_FACTOR_NUM
}

/**
 * 收到的字节数配不配得上**这次下载期望的长度**。
 *
 * `declaredSize` 这个参数的含义在评审后变了：它不再是"清单里手写的那份"，
 * 而是调用方（[downloadEvents]）算出来的实测期望长度 = `Content-Length ?: sizeBytes`。
 * 于是清单没填 `sizeBytes` 也能判截断 —— 那正是生产 `update.json` 今天的形状。
 *
 * 这条是"Gitee 附件会传一大半自己断掉"的闸：那种断开**可能只是通道正常结束、不抛异常**
 * （实测 26MB / 58MB 被掐，spec §3.4）。只靠 sha256 兜不住 —— `sha256` 是可选字段，
 * 没声明时半截包会被当成 `Success` 交出去，表现是系统安装页"解析软件包时出现问题"，
 * 而我们自己那条链一路绿。0 字节永远不算完整（它是"通道立刻 EOF"的返回值，不是 APK）。
 *
 * 两边都不知长度时（chunked 响应 + 清单没写 sizeBytes）只剩"非空"这一条，
 * 那是**已知并接受的**残留风险：spec §5 要求 T11 把 `sha256`/`sizeBytes` 回填进清单。
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
 *
 * internal 而不是 private：[downloadEvents] 要捕获它们，而假接缝也得能抛同一种成因。
 */
internal class NetworkBodyFailure(cause: Exception) : Exception(cause)

internal class DiskWriteFailure(cause: Exception) : Exception(cause)

/**
 * 一次 GET 的结果：**只读到响应头**就交出来，字节还没开始传。
 *
 * 拆成"先拿头、再由调用方决定要不要开始写盘"这两步，是第 5 条规矩（空间前置闸）
 * 能用**实测长度**判、又不至于"下到大一半才报 NoSpace"的前提。
 *
 * - [contentLength]：响应头里的 `Content-Length`，第 1 条规矩的判据来源；chunked/没给则是 null。
 * - [readChunk]：与 [copyWithProgress] 同签名；链路中断要包成 [NetworkBodyFailure] 抛出来。
 * - [discard]：调用方提前退出（空间不够）时必须取消它，否则连接被挂着不回池。
 */
internal class ApkBody(
    val contentLength: Long?,
    val readChunk: suspend (ByteArray) -> Int,
    val discard: () -> Unit
)

/**
 * 取包接缝：`url` → 响应头 + 字节源，拿不到（连接失败/超时/非 2xx）返回 null。
 * 真实实现是 [ApkDownloader] 的 Ktor GET，测试注入的是内存里的假 body。
 */
internal typealias OpenApkBody = suspend (url: String) -> ApkBody?

/**
 * 下载的全部判定，组装成一条 flow —— 这一层能在普通 JVM 里被构造、被用例钉住。
 *
 * [dir] 由调用方给（生产是 `cacheDir/update`），[openBody] 与 [freeSpace] 是接缝，
 * [logWarning] 沿用既有的注入告警通道（清单手写值与实测不符时用它，别静默接受）。
 */
internal fun downloadEvents(
    manifest: UpdateManifest,
    dir: File,
    openBody: OpenApkBody,
    logWarning: (String) -> Unit,
    freeSpace: (File) -> Long = { it.usableSpace }
): Flow<DownloadEvent> = flow {
    if (!ensureUpdateDir(dir, logWarning)) {
        emit(DownloadEvent.Failure(DownloadFailure.Io))
        return@flow
    }

    // 第 6 条规矩：只有 Network 这一种成因自动重试。ChecksumMismatch 重一遍只是再下一次
    // 同一个坏包，NoSpace 重一遍必然还是不够，Io 是本地盘的问题 —— 把它们也重一遍
    // 等于把一次故障变成两次流量，而门禁下面向用户的进度条会来回跳。
    // 重试期间不 emit 中间那条失败：用户最终看到的仍然只有一句"下载中断，请检查网络后重试"。
    var retriesLeft = MAX_AUTO_RETRIES
    var terminal = runAttempt(manifest, dir, openBody, logWarning, freeSpace)
    while (terminal is DownloadEvent.Failure && terminal.reason == DownloadFailure.Network && retriesLeft > 0) {
        logWarning("下载中断，自动重试第 ${MAX_AUTO_RETRIES - retriesLeft + 1} 次（spec §7：最多重试 $MAX_AUTO_RETRIES 次，只对网络成因）")
        retriesLeft--
        terminal = runAttempt(manifest, dir, openBody, logWarning, freeSpace)
    }
    emit(terminal)
}

/**
 * 目录可用性检查。`mkdirs()` 的返回值过去没人判：目录建不出来时后面 `FileOutputStream`
 * 抛 FileNotFoundException，被那个 catch 折成 `Io` ⇒ 文案"写入失败，请清理手机存储后重试"，
 * 而真正的原因是**没有目录**（用户去清存储，清完还是装不上）。
 * 先判 `isDirectory` 是因为 `mkdirs()` 对已存在的目录返回 false，直接判它会把正常情况报成失败。
 */
private fun ensureUpdateDir(dir: File, logWarning: (String) -> Unit): Boolean {
    if (dir.isDirectory) return true
    if (dir.mkdirs()) return true
    logWarning(
        "更新目录建不出来：${dir.absolutePath}（cacheDir 不可写，或同路径上存在同名文件挡着）" +
            "—— 这不是存储空间问题，报成\"请清理手机存储\"会把排障方向带偏"
    )
    return false
}

/** 一次完整的取包尝试：拿头 → 空间前置闸 → 流式写盘 → 长度闸 → 哈希闸 */
private suspend fun FlowCollector<DownloadEvent>.runAttempt(
    manifest: UpdateManifest,
    dir: File,
    openBody: OpenApkBody,
    logWarning: (String) -> Unit,
    freeSpace: (File) -> Long
): DownloadEvent {
    val target = File(dir, apkFileNameFor(manifest.versionName))
    // 没有续传，所以先删干净再开：留着上一次的半截包不但会被覆盖写，
    // 还会吃掉空间前置闸正在量的那点余量。
    target.delete()

    val body = try {
        openBody(manifest.apkUrl)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logWarning("取包失败（连接/超时/非 2xx）：${manifest.apkUrl}，原因：${e.message ?: e::class.simpleName}")
        return DownloadEvent.Failure(DownloadFailure.Network)
    }
    if (body == null) return DownloadEvent.Failure(DownloadFailure.Network)

    // 第 1 条规矩：实测长度优先，手写值只兜底 + 报警
    val measured = body.contentLength
    val expectedTotal = measured ?: manifest.sizeBytes
    if (measured != null && manifest.sizeBytes != null && measured != manifest.sizeBytes) {
        logWarning(
            "清单声明的 sizeBytes=${manifest.sizeBytes} 与本次响应实测的 Content-Length=$measured 不符 —— " +
                "完整性判定与进度分母按**实测**的那份走。请核对 update.json 与 Release 附件是不是漂移了" +
                "（CI 重跑会改 zip 里的时间戳字节，手写值不会跟着变）"
        )
    }
    if (expectedTotal == null) {
        // 这道闸今天在生产数据上是**开着的**：响应没给长度、清单也没填 ⇒ 只能判"收到过字节"，
        // 判不了截断。不吭声的话这件事只能靠读代码发现，而 T11 要回填的就是这两项。
        logWarning(
            "本次下载没有任何长度依据（响应没有 Content-Length，清单也没有 sizeBytes）" +
                "⇒ 完整性只能判\"收到过字节\"，截断判不了。请给 update.json 回填 sha256 与 sizeBytes（spec §5）"
        )
    }

    // 第 5 条规矩：读第一个字节之前就拒
    if (shouldRefuseDownload(expectedTotal, freeSpace(dir))) {
        body.discard()
        return DownloadEvent.Failure(DownloadFailure.NoSpace)
    }

    val outcome = try {
        FileOutputStream(target).use { out ->
            copyWithProgress(
                readChunk = body.readChunk,
                write = { buffer, count ->
                    try {
                        out.write(buffer, 0, count)
                    } catch (e: Exception) {
                        throw DiskWriteFailure(e)
                    }
                },
                total = expectedTotal,
                // 这一行就是 Task 5 那根 0% 死条的位置。它现在是**测试对象**：
                // 换成空 lambda，ApkDownloadFlowTest 里"至少一条 Progress"和那条跨层用例一起红。
                onProgress = { event -> emit(event) }
            )
        }
    } catch (e: NetworkBodyFailure) {
        body.discard()
        target.delete()
        return DownloadEvent.Failure(DownloadFailure.Network)
    } catch (e: DiskWriteFailure) {
        target.delete()
        return DownloadEvent.Failure(DownloadFailure.Io)
    } catch (e: CancellationException) {
        target.delete()
        throw e
    } catch (e: Exception) {
        body.discard()
        target.delete()
        return DownloadEvent.Failure(DownloadFailure.Io)
    }
    body.discard()

    if (!isBodyComplete(outcome.written, expectedTotal)) {
        target.delete()
        return DownloadEvent.Failure(DownloadFailure.Network)
    }

    val expected = manifest.sha256
    if (expected != null) {
        // 严格相等，不用 ignoreCase：清单那一侧由 isSane() 保证是小写，
        // 这一侧由 toLowerHex() 保证是小写。"大小写都接受"会把任何一侧的口径漂移
        // 从"立刻变红"变成"以后永远静默"。
        if (outcome.sha256Hex != expected) {
            // 删掉坏包：留着它，将来任何"这个文件已经下过了"的优化都会直接把它装上
            target.delete()
            return DownloadEvent.Failure(DownloadFailure.ChecksumMismatch)
        }
    }
    return DownloadEvent.Success(target.absolutePath)
}

class ApkDownloader(
    private val context: Context,
    private val logWarning: (String) -> Unit = ::logUpdateWarning
) : ApkInstaller {

    /**
     * 惰性建客户端。评审提这条的理由是**冷启动崩 App**：容器在 `AppContainer` 构造时
     * 就 new 了 ApkDownloader（eager），而 `HttpClient(engine)` 会真的去起引擎
     * （OkHttp 的连接池与线程池）。引擎起不来时（类加载冲突、ROM 差异）
     * 症状是开屏就崩 —— 而这条功能自己的态度明明是 fail-open（[runCheck] 兜住任何异常，
     * 宁可这次不提示更新也不拦人、不崩）。惰性化之后，最坏情况变成"点下载才失败"，
     * 用户看得见、也退得出。
     */
    private val client by lazy {
        HttpClient(httpClientEngine()) {
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
    }

    override fun download(manifest: UpdateManifest): Flow<DownloadEvent> = downloadEvents(
        manifest = manifest,
        dir = File(context.cacheDir, UPDATE_DIR_NAME),
        openBody = ::openBody,
        logWarning = logWarning
    ).flowOn(Dispatchers.IO) // 写盘是阻塞 IO，不该占着容器的 Default 计算池

    /**
     * 发一条 GET，只读到响应头就交出去（判定层要在写第一个字节之前用实测长度判空间与完整性）。
     *
     * 非 2xx 与连接失败都折成 null：401（签名直链过期）与 404（release 被删）在这里
     * 都是"可重试的 Network"，而第 3 条规矩保证下一次点击重新走三跳、拿一条新签名地址。
     */
    private suspend fun openBody(url: String): ApkBody? {
        val response = try {
            client.get(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        if (!response.status.isSuccess()) return null

        val channel = response.bodyAsChannel()
        return ApkBody(
            // 直接读响应头，不用 Ktor 的 `contentLength()` 扩展：那层包装会把
            // "这个数就是从 Content-Length 来的"这件事藏进 API 名字里，而它正是第 1 条规矩的判据来源。
            contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull(),
            readChunk = { buffer ->
                try {
                    channel.readAvailable(buffer)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw NetworkBodyFailure(e)
                }
            },
            discard = {
                // 提前退出（空间不够）时把这条响应取消掉，别让连接挂着不回池
                try {
                    channel.cancel(null)
                } catch (e: Exception) {
                    // 已经关闭的通道再 cancel 会抛：这里没有需要留痕的成因，放弃本身就是要的效果
                }
            }
        )
    }
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
