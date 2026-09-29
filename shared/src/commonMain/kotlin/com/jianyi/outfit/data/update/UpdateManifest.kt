package com.jianyi.outfit.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 清单解析配置。**刻意不复用** [com.jianyi.outfit.data.remote.weatherJson]。
 *
 * 天气那边开了 isLenient + coerceInputValues，因为那个接口多返回字段、
 * 数字时而是字符串 —— 为一个字段让整次查询失败不值得。
 * 同一套宽松用在这里会造出**永久静默失效**，而且要算准它具体怎么失效：
 * coerceInputValues 的语义是"类型对不上、或者给了 null，就回退到默认值"，
 * 于是 versionCode 缺失或写成 null 会被静静折成 0（Int 的回退值就是 0）
 * ⇒ `current(8) >= 0` 恒成立 ⇒ 判 UpToDate ⇒
 * 用户再也收不到更新，而测试、CI、日志全绿，没有任何异常。
 * 清单是要决定"拦不拦人"的，它对格式的容忍度必须和天气数据相反。
 *
 * 说清一个反直觉的事实（本仓库实测）：**不开这两个开关并不等于拒掉带引号的数字**。
 * kotlinx.serialization 的整型解码对 `"versionCode":"9"` 在严格模式下同样读出 9，
 * 而 Json 的配置项里只有"更松"的开关，没有"更严"的开关能关掉这条宽容。
 * 这不需要额外去堵：9 就是 9，"拦不拦人"的语义没有含糊，风险是零；
 * 真正会静默要命的是 null/缺失变成 0，那由两道闸兜住 ——
 * 不开 coerceInputValues（null 直接抛 ⇒ [UpdateManifestParser.parse] 返回 null），
 * 以及 [UpdateManifestParser] 内部 `versionCode <= 0` 的语义闸。
 *
 * 只留 ignoreUnknownKeys：允许以后往清单里加字段而不打断老版本 App。
 */
internal val updateJson = Json { ignoreUnknownKeys = true }

/**
 * Gitee 上一份可安装版本的声明。放在仓库根 `update.json`，
 * 通过 `https://gitee.com/wuliao11541/WeatherOutfit/raw/main/update.json` 读
 * —— raw 域免鉴权（实测 302 → raw.giteeusercontent.com → 200），
 * 而 `/releases/latest` 的响应形态由 User-Agent 决定（Dalvik UA 拿到的是 HTML），
 * 所以不能用它当清单。
 */
@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    /** 低于这个 versionCode 就硬门禁。与 [versionCode] 相等 = 只留最新版 */
    @SerialName("minSupportedVersionCode") val minSupportedCode: Int,
    /** 必须 https、必须以 .apk 结尾：指向 Gitee Release 附件的人类可读路径 */
    val apkUrl: String,
    /** 可选。CI 首次出包前拿不到真哈希；声明了就必须校验，没声明则跳过校验 */
    val sha256: String? = null,
    /** 可选。缺失时进度条走不确定态 */
    val sizeBytes: Long? = null,
    val notes: String? = null
)

object UpdateManifestParser {

    private const val SHA256_HEX_LENGTH = 64

    /** 任何"读不到/看不懂"都收敛成 null，由调用方降级成 Unreachable —— 不抛异常 */
    fun parse(text: String?): UpdateManifest? {
        if (text.isNullOrBlank()) return null
        val manifest = try {
            updateJson.decodeFromString(UpdateManifest.serializer(), text)
        } catch (e: Exception) {
            return null
        }
        return if (manifest.isSane()) manifest else null
    }

    /**
     * 反序列化成功之后再过一道语义闸。
     *
     * 每一项都是"错了会静默要命"的那类，不是格式洁癖：
     * - 非 https：明文传一个可安装包，中间人可以直接塞恶意 APK；
     * - 非 .apk：拿到的东西装不上，用户点了没反应；
     * - 哈希长度不对：下载永远校验失败，而失败原因看起来像包坏了；
     * - versionCode <= 0 或 sizeBytes 负数：一定是清单写错。
     *
     * 最后一条同时是"永久静默失效"的第二道闸：0 正是 coerceInputValues 会把
     * null/缺失折出来的那个值（见 [updateJson] 的 KDoc），这里明确拒掉 0，
     * 于是哪怕清单作者手滑写了 0，也不会退化成"所有人都收不到更新"。
     * 与之相对，带引号的数字解成 9 是无害的，所以不做拒引号的结构校验 ——
     * 那种手写 JSON 树校验既会误伤以后合法的新写法，又是长期维护负担。
     */
    private fun UpdateManifest.isSane(): Boolean {
        if (versionCode <= 0) return false
        if (minSupportedCode <= 0) return false
        if (versionName.isBlank()) return false
        if (!apkUrl.startsWith("https://")) return false
        if (!apkUrl.endsWith(".apk")) return false
        if (sizeBytes != null && sizeBytes <= 0L) return false
        if (sha256 != null && !sha256.isSha256Hex()) return false
        return true
    }

    private fun String.isSha256Hex(): Boolean =
        length == SHA256_HEX_LENGTH && all { it in '0'..'9' || it in 'a'..'f' }
}
