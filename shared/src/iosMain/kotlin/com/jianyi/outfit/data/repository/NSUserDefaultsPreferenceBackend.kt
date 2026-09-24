package com.jianyi.outfit.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.Foundation.NSUserDefaults

/** 存全部偏好的那一个键：整份偏好以带类型标签的 JSON 挂在它下面 */
private const val BLOB_KEY = "jianyi.preferences.v1"

/**
 * PreferenceBackend 的 iOS 实现。
 *
 * 为什么不是"每个偏好一个 NSUserDefaults 键"（Android 侧的对应做法）：
 * `dictionaryRepresentation()` 返回的是**含全局域与注册域的并集**，
 * 后端无从挑出"我们自己写过的那些键"，会把系统注入的键当成用户偏好读回来。
 * 所以整份偏好收进一个带类型标签的 JSON、挂在单个自定义键下：
 * 读写范围天然只到自己，类型也不再依赖 NSNumber 的装箱推断。
 * 磁盘格式：`{"daily_push_hour": {"i": 8}, "temp_unit": {"s": "CELSIUS"}}`，
 * 标签 s/b/i 对应仓库侧允许的三个类型 String / Boolean / Int。
 *
 * 响应式：内存里那份 MutableStateFlow 是唯一真相，edit 同时更新它和磁盘。
 * 设置页"改一处立刻反映到界面"靠的就是这个 flow；这些键是 app 私有的，
 * 不会有外部改动，所以不去接 KVO 监听。
 */
class NSUserDefaultsPreferenceBackend : PreferenceBackend {

    private val defaults = NSUserDefaults.standardUserDefaults

    private val state = MutableStateFlow(read())

    override val entries: Flow<Map<String, Any>> = state

    override suspend fun edit(block: MutableMap<String, Any?>.() -> Unit) {
        val draft = state.value.toMutableMap<String, Any?>()
        draft.block()
        val saved = draft.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
        state.value = saved
        defaults.setObject(value = encode(saved), forKey = BLOB_KEY)
    }

    private fun read(): Map<String, Any> {
        val raw = defaults.stringForKey(BLOB_KEY) ?: return emptyMap()
        return runCatching { decode(raw) }.getOrDefault(emptyMap())
    }

    private fun encode(values: Map<String, Any>): String {
        val root = buildJsonObject {
            for ((key, value) in values) {
                val tagged = buildJsonObject {
                    when (value) {
                        is String -> put(FIELD_STRING, JsonPrimitive(value))
                        is Boolean -> put(FIELD_BOOL, JsonPrimitive(value))
                        is Int -> put(FIELD_INT, JsonPrimitive(value))
                        else -> throw IllegalArgumentException(
                            "偏好值不支持类型 ${value::class.simpleName}：只允许 String / Boolean / Int"
                        )
                    }
                }
                put(key, tagged)
            }
        }
        return root.toString()
    }

    private fun decode(text: String): Map<String, Any> {
        val root = json.parseToJsonElement(text) as? JsonObject ?: return emptyMap()
        val out = LinkedHashMap<String, Any>(root.size)
        for ((key, element) in root) {
            val tagged = (element as? JsonObject)?.entries?.firstOrNull() ?: continue
            val raw = tagged.value as? JsonPrimitive ?: continue
            when (tagged.key) {
                // 字符串本来就是带引号的，另两类只认字面量：
                // 免得存成 "8" 的值被当成 Int 读回来，类型悄悄漂移
                FIELD_STRING -> out[key] = raw.content
                FIELD_BOOL -> if (!raw.isString) raw.content.toBooleanStrictOrNull()?.let { out[key] = it }
                FIELD_INT -> if (!raw.isString) raw.content.toIntOrNull()?.let { out[key] = it }
            }
        }
        return out
    }

    private companion object {
        const val FIELD_STRING = "s"
        const val FIELD_BOOL = "b"
        const val FIELD_INT = "i"

        /** 后端只做存取，不认识任何业务键名，所以配置里没有业务假设 */
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
