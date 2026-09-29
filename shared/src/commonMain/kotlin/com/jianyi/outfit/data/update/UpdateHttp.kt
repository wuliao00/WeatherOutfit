package com.jianyi.outfit.data.update

import com.jianyi.outfit.data.remote.httpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * 读清单的结果，**按成因分开**。
 *
 * 上一版这里返回 `String?`，于是"仓库被转私有(403)""raw 路径改了(404)""限流(5xx)"
 * 和"用户离线"折成同一个 null —— 前三种都是**永久静默失效**（永远没人收到更新），
 * 最后一种是正常态。同一条观测通道要么全报（离线时刷屏，信号被噪声埋掉）
 * 要么全不报（那三种就永远看不见）。所以成因必须是返回值的一部分，不是日志里的修辞。
 */
sealed interface ManifestFetch {
    /** 2xx 正文 */
    data class Body(val text: String) : ManifestFetch

    /** 拿到了响应但状态码不是 2xx；code 是要写进日志的那个数字 */
    data class HttpStatus(val code: Int) : ManifestFetch

    /** 连响应都没有：DNS/连接/超时 */
    data class NetworkFailed(val reason: String) : ManifestFetch
}

/**
 * 读清单与探包的两个动作。抽成接口是为了让判定逻辑能在普通 JVM 里测 ——
 * 不然"拦不拦人"这件事只能靠真机连着 Gitee 才能验证，而它恰恰是最不能靠运气验证的一段。
 */
interface UpdateHttp {
    suspend fun fetchManifest(url: String): ManifestFetch

    /** HEAD 跟完重定向后是否落在 2xx。401（签名过期）与 404（tag 不存在）都算 false */
    suspend fun headOk(url: String): Boolean
}

/**
 * 清单地址。走 raw 域：实测免鉴权（302 → raw.giteeusercontent.com → 200），
 * 且 HEAD 与 Range 都支持（同域实测 HEAD=200、`-r 0-99` = 206）。
 *
 * 刻意不用 `releases/latest`：那个地址的响应形态由 User-Agent 决定
 * （okhttp 拿 JSON、Dalvik 拿 HTML），而 App 侧正是 Dalvik 系的 UA ——
 * 表现是每次解析都失败、每次都被压成 Unreachable，也就是"没有人收到更新"。
 */
const val UPDATE_MANIFEST_URL =
    "https://gitee.com/wuliao11541/WeatherOutfit/raw/main/update.json"

/**
 * 超时值取得比天气接口短：这次请求在冷启动路径上，
 * 拿不到清单的代价只是"不提示更新"，不值得让启动多等 30 秒。
 */
class KtorUpdateHttp : UpdateHttp {

    private val client = HttpClient(httpClientEngine()) {
        install(HttpTimeout) {
            connectTimeoutMillis = 8_000
            requestTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
        // raw 域实测是 302 跳过去，不跟重定向就永远拿不到正文
        followRedirects = true
        // 自己判状态码：404 是"清单还没放"的正常情形，不该走异常路径
        expectSuccess = false
    }

    override suspend fun fetchManifest(url: String): ManifestFetch = try {
        val response: HttpResponse = client.get(url)
        if (response.status.isSuccess()) {
            ManifestFetch.Body(response.bodyAsText())
        } else {
            ManifestFetch.HttpStatus(response.status.value)
        }
    } catch (e: Exception) {
        ManifestFetch.NetworkFailed(e.message ?: e::class.simpleName.orEmpty())
    }

    override suspend fun headOk(url: String): Boolean = try {
        client.head(url).status.isSuccess()
    } catch (e: Exception) {
        false
    }
}
