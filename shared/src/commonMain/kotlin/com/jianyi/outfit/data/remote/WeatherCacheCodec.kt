package com.jianyi.outfit.data.remote

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.encodeToString

/**
 * 天气缓存的 JSON 编解码。
 *
 * 为什么单独成一个对象而不是让 Repository 直接调序列化库：
 * 缓存是**落在磁盘上的格式**，老用户机器上已经有 Gson 写进去的行。
 * 格式必须逐字节兼容，而且必须能被单测钉住 —— 一旦哪天解码不出来，
 * 表现是"断网时没有旧数据可回退"，而不是报错，很难归因。
 *
 * 两种历史格式（都继续支持）：
 * - 普通端点：整个 WeatherResponse 的 JSON
 * - 经纬度端点：`{"type":"latlon","data":{...}}`
 *   （因为两个端点返回结构完全不同，要靠 type 区分该按哪个 DTO 解）
 */
object WeatherCacheCodec {

    /** 把响应编码成可入库的 JSON；tag 非空时套上信封（目前只有 "latlon" 用） */
    fun encode(response: WeatherEnvelope, tag: String? = null): String =
        if (tag == null) {
            weatherJson.encodeToString(WeatherResponse.serializer(), response as WeatherResponse)
        } else {
            val body = when (response) {
                is LatLonWeatherResponse ->
                    weatherJson.encodeToString(LatLonWeatherResponse.serializer(), response)
                is WeatherResponse ->
                    weatherJson.encodeToString(WeatherResponse.serializer(), response)
            }
            buildJsonObject {
                put("type", tag)
                put("data", weatherJson.parseToJsonElement(body).jsonObject)
            }.toString()
        }

    /**
     * 解出缓存里的响应；格式不认识或字段缺失时返回 null（调用方按"无缓存"处理）。
     *
     * 注意这里刻意不抛异常：缓存是尽力而为的回退路径，解不开就当作没有，
     * 不能因为一条脏数据把整个查询流程带崩。
     */
    fun decode(payload: String): WeatherEnvelope? = runCatching {
        val root = weatherJson.parseToJsonElement(payload) as? JsonObject ?: return null
        val type = root["type"]?.jsonPrimitive?.contentOrNull
        if (type == "latlon") {
            val data = root["data"] as? JsonObject ?: return null
            weatherJson.decodeFromJsonElement(LatLonWeatherResponse.serializer(), data)
        } else {
            weatherJson.decodeFromJsonElement(WeatherResponse.serializer(), root)
        }
    }.getOrNull()
}
