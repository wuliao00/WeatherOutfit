package com.jianyi.outfit.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * 清单解析配置。**刻意不复用** [com.jianyi.outfit.data.remote.weatherJson]。
 *
 * 天气那边开了 isLenient + coerceInputValues，因为那个接口多返回字段、
 * 数字时而是字符串 —— 为一个字段让整次查询失败不值得。
 * 同一套宽松用在这里会造出**永久静默失效**：coerceInputValues 会把
 * "versionCode":"9" 或 null 折成 0，于是 current >= 0 恒成立 ⇒ 判 UpToDate，
 * 用户再也收不到更新而没有任何异常。清单是要决定"拦不拦人"的，
 * 它对格式的容忍度必须和天气数据相反。
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

    /** 必须是 JSON 数字的字段：两个版本号决定"拦不拦人"，sizeBytes 决定进度条走哪种形态 */
    private val numericFields = listOf("versionCode", "minSupportedVersionCode", "sizeBytes")

    /** 任何"读不到/看不懂"都收敛成 null，由调用方降级成 Unreachable —— 不抛异常 */
    fun parse(text: String?): UpdateManifest? {
        if (text.isNullOrBlank()) return null
        val manifest = try {
            val root = updateJson.parseToJsonElement(text).jsonObject
            if (!root.numericFieldsAreUnquoted()) return null
            updateJson.decodeFromJsonElement(UpdateManifest.serializer(), root)
        } catch (e: Exception) {
            return null
        }
        return if (manifest.isSane()) manifest else null
    }

    /**
     * 数字字段必须是**不带引号**的 JSON 数字。
     *
     * 这一道必须显式写：`Json { ignoreUnknownKeys = true }`（不开 isLenient、
     * 不开 coerceInputValues）并不会拒掉 `"versionCode":"9"` —— kotlinx.serialization
     * 的整型解码先取字符串字面量再本地 parse，实测严格模式下照样读出 9。
     * 而 Json 配置里只有"更松"的开关，没有"更严"的开关可关这条宽容。
     * 清单里唯一决定"拦不拦人"的就是这两个整数：写成字符串到底是 9 还是别的，
     * 只有写清单的人知道，App 猜不出来 —— 所以含糊一律判无效，不判"能用就行"。
     */
    private fun JsonObject.numericFieldsAreUnquoted(): Boolean {
        for (key in numericFields) {
            val value = this[key] ?: continue // 缺失由解码那步按"必填字段缺失"处理
            if (value !is JsonPrimitive || value.isString) return false
        }
        return true
    }

    /**
     * 反序列化成功之后再过一道语义闸。
     *
     * 每一项都是"错了会静默要命"的那类，不是格式洁癖：
     * - 非 https：明文传一个可安装包，中间人可以直接塞恶意 APK；
     * - 非 .apk：拿到的东西装不上，用户点了没反应；
     * - 哈希长度不对：下载永远校验失败，而失败原因看起来像包坏了；
     * - versionCode <= 0 或 sizeBytes 负数：一定是清单写错。
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
