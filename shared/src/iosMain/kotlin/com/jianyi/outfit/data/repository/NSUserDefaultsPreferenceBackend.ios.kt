package com.jianyi.outfit.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import platform.Foundation.NSUserDefaults

/** 存全部偏好的那一个键。整份偏好以带类型标签的 JSON 存在这里 */
private const val BLOB_KEY = "jianyi.preferences.v1"

/**
 * PreferenceBackend 的 iOS 实现。
 *
 * 为什么不是"每个偏好一个 NSUserDefaults 键"（Android 侧的对应做法）：
 * `dictionaryRepresentation()` 返回的是**含全局域与注册域的并集**，
 * 后端没法只挑出"我们自己写过的那些键"，会把系统注入的键当成用户偏好读回来。
 * 所以整份偏好收进一个带类型标签的 JSON、挂在单个自定义键下：
 * 读写的范围天然只到自己，类型也不再依赖 NSNumber 的装箱推断。
 *
 * 响应式：内存里维护一份 MutableStateFlow 作为唯一真相，edit 同时更新它和磁盘。
 * 设置页要"改一处立刻反映到界面上"，靠的就是这个 flow；
 * 外部改 defaults 的情况不存在（这是 app 私有键），所以不去监听 KVO。
 */
class NSUserDefaultsPreferenceBackend : PreferenceBackend {

    private val defaults = NSUserDefaults.standardUserDefaults

    private val state = MutableStateFlow(read())

    override val entries: Flow<Map<String, Any>> = state

    override suspend fun edit(block: MutableMap<String, Any?>.() -> Unit) {
        val draft = state.value.toMutableMap<String, Any?>()
        draft.block()
        val saved = draft.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
        state.value = saved
        defaults.setObject(value = encode(saved), forKey = BLOB_KEY)
    }

    /* ============ 磁盘格式：{"键名": {"s"|"b"|"i": 值}} ============ */

    private fun read(): Map<String, Any> {
        val raw = defaults.stringForKey(BLOB_KEY) ?: return emptyMap()
        return runCatching { decode(raw) }.getOrDefault(emptyMap())
    }

    private fun encode(values: Map<String, Any>): String =
        buildJsonObject {
            for ((key, value) in values) {
                put(
                    key,
                    when (value) {
                        is String -> buildJsonObject { put(FIELD_STRING, JsonPrimitive(value)) }
                        is Boolean -> buildJsonObject { put(FIELD_BOOL, JsonPrimitive(value)) }
                        is Int -> buildJsonObject { put(FIELD_INT, JsonPrimitive(value)) }
                        else -> error(
                            "偏好值不支持类型 ${value::class.simpleName}：只允许 String / Boolean / Int"
                        )
                    }
                )
            }
        }.let { json.encodeToString(JsonObject.serializer(), it) }

    private fun decode(text: String): Map<String, Any> {
        val obj = json.parseToJsonElement(text) as? JsonObject ?: return emptyMap()
        val out = LinkedHashMap<String, Any>(obj.size)
        for ((key, element) in obj) {
            val fields = (element as? JsonObject)?.values?.firstOrNull()?.jsonPrimitive ?: continue
            when ((element as JsonObject).keys.first()) {
                FIELD_STRING -> fields.content.let { out[key] = it }
                FIELD_BOOL -> fields.booleanOrNullCompat()?.let { out[key] = it }
                FIELD_INT -> fields.intOrNullCompat()?.let { out[key] = it }
            }
        }
        return out
    }

    private fun JsonPrimitive.booleanOrNullCompat(): Boolean? = contentStrict()?.toBooleanStrictOrNull()

    private fun JsonPrimitive.intOrNullCompat(): Int? = contentStrict()?.toIntOrNull()

    /** 只接受字面量（非字符串），避免把存成字符串的 "true" 误判成布尔 */
    private fun JsonPrimitive.contentStrict(): String? = if (isString) null else content

    private companion object {
        const val FIELD_STRING = "s"
        const val FIELD_BOOL = "b"
        const val FIELD_INT = "i"

        /** 与仓库侧解耦：这里只做存取，不认识任何业务键名 */
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
