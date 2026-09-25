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

    /** 磁盘那份的解读结果：解出来的键值 + 解不开/有丢弃时的原文 */
    private val initial: Pair<Map<String, Any>, String?> = read()

    /**
     * 上一次读磁盘时**没能完整解开**的原文。不为 null 就说明内存里这份是从空表或
     * 残缺表开始的，而磁盘上还压着我们读不懂的数据 —— 必须在覆盖它之前先挪到旁路键，
     * 否则"解不开"会被静默升级成"设置永久丢失"。
     */
    private var unreadableRaw: String? = initial.second

    private val state = MutableStateFlow(initial.first)

    override val entries: Flow<Map<String, Any>> = state

    override suspend fun edit(block: MutableMap<String, Any?>.() -> Unit) {
        val draft = state.value.toMutableMap<String, Any?>()
        draft.block()
        val saved = draft.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()

        // 先编码、后改内存：反过来（原来是先 state.value = saved 再 encode）的话，
        // 一旦 encode 因为越界类型抛出来，内存已是新值而磁盘还是旧值，
        // 之后每一次设置改动都会再抛一次 —— 一次失败变成永久坏掉。
        val text = encode(saved)

        // 读不懂的旧数据先转到旁路键保命，再覆盖主键。
        // 留着它至少还有机会被新版本（或人工）救回来，而不是被这次写彻底抹掉。
        unreadableRaw?.let { raw ->
            defaults.setObject(value = raw, forKey = "$BLOB_KEY.unreadable")
            unreadableRaw = null
        }

        state.value = saved
        defaults.setObject(value = text, forKey = BLOB_KEY)
    }

    /**
     * 返回"解开的键值 + 解不开时的原文"。
     *
     * 这里刻意不把失败兜成空表就完事：`catch → 空 → 下一次写整体覆盖` 这条链
     * 是 Android 侧 DataStore 那个 `catch { emptyPreferences() }` 的 iOS 版本，
     * 表现是"不崩溃，只是所有设置悄悄回到默认"，属于最难发现的一类回归。
     * 解不开的原文因此要带出去，由 edit() 在覆盖前先存到旁路键。
     */
    private fun read(): Pair<Map<String, Any>, String?> {
        val raw = defaults.stringForKey(BLOB_KEY) ?: return emptyMap<String, Any>() to null
        val decoded = runCatching { decode(raw) }
        if (decoded.isFailure) return emptyMap<String, Any>() to raw
        val (map, lostAnything) = decoded.getOrDefault(emptyMap<String, Any>() to true)
        return map to if (lostAnything) raw else null
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

    /**
     * 第二个返回值表示"有没有丢掉任何键"。
     * 原来遇到不认识的标签是 `continue` 静默跳过 —— 那等于把用户的数据悄悄扔掉，
     * 而调用方以为一切正常（下一次写还会把残缺结果固化回磁盘）。
     * 现在丢弃会被报出来，交给 read() 走"原文存旁路键"那条保命路径。
     */
    private fun decode(text: String): Pair<Map<String, Any>, Boolean> {
        val root = json.parseToJsonElement(text) as? JsonObject ?: return emptyMap<String, Any>() to true
        val out = LinkedHashMap<String, Any>(root.size)
        var lost = false
        for ((key, element) in root) {
            val tagged = (element as? JsonObject)?.entries?.firstOrNull()
            val raw = tagged?.value as? JsonPrimitive
            if (tagged == null || raw == null) {
                lost = true
                continue
            }
            val stored = when (tagged.key) {
                // 字符串本来就是带引号的，另两类只认字面量：
                // 免得存成 "8" 的值被当成 Int 读回来，类型悄悄漂移
                FIELD_STRING -> raw.content
                FIELD_BOOL -> if (!raw.isString) raw.content.toBooleanStrictOrNull() else null
                FIELD_INT -> if (!raw.isString) raw.content.toIntOrNull() else null
                else -> null
            }
            if (stored == null) lost = true else out[key] = stored
        }
        return out to lost
    }

    private companion object {
        const val FIELD_STRING = "s"
        const val FIELD_BOOL = "b"
        const val FIELD_INT = "i"

        /** 后端只做存取，不认识任何业务键名，所以配置里没有业务假设 */
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
