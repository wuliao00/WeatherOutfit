package com.jianyi.outfit.data.update

import com.jianyi.outfit.data.remote.newHttpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * 读清单与探包的两个动作。抽成接口是为了让判定逻辑能在普通 JVM 里测 ——
 * 不然"拦不拦人"这件事只能靠真机连着 Gitee 才能验证，而它恰恰是最不能靠运气验证的一段。
 */
interface UpdateHttp {
    /** 正文；非 2xx、超时、连接失败一律 null（不抛） */
    suspend fun getText(url: String): String?

    /** HEAD 跟完重定向后是否落在 2xx。401（签名过期）与 404（tag 不存在）都算 false */
    suspend fun headOk(url: String): Boolean
}

/**
 * 清单地址。走 raw 域：实测免鉴权（302 → raw.giteeusercontent.com → 200）且支持 Range。
 *
 * 刻意不用 `releases/latest`：那个地址的响应形态由 User-Agent 决定
 * （okhttp 拿到 JSON、Dalvik 拿到 HTML），而 App 侧正是 Dalvik 系的 UA ——
 * 表现是每次解析都失败、每次都被压成 Unreachable，也就是"没有人收到更新"。
 */
const val UPDATE_MANIFEST_URL =
    "https://gitee.com/wuliao11541/WeatherOutfit/raw/main/update.json"

/**
 * 超时值取得比天气接口短：这次请求在冷启动路径上，
 * 拿不到清单的代价只是"不提示更新"，不值得让启动多等 30 秒。
 */
class KtorUpdateHttp : UpdateHttp {

    private val client = HttpClient(newHttpClientEngine()) {
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

    override suspend fun getText(url: String): String? = try {
        val response: HttpResponse = client.get(url)
        if (response.status.isSuccess()) response.bodyAsText() else null
    } catch (e: Exception) {
        null
    }

    override suspend fun headOk(url: String): Boolean = try {
        client.head(url).status.isSuccess()
    } catch (e: Exception) {
        false
    }
}
