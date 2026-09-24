package com.jianyi.outfit.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * 应用级 DataStore 实例。
 *
 * 名字仍是 "user_settings"，落盘路径由它决定（filesDir/user_settings.preferences_pb），
 * 与这段代码还在 app 模块时**完全一致** —— 搬家搬的是声明位置，不是磁盘上的东西。
 */
private val Context.settingsStore: DataStore<androidx.datastore.preferences.core.Preferences> by
    preferencesDataStore(name = "user_settings")

/**
 * PreferenceBackend 的 Android 实现：Preferences DataStore。
 *
 * 两条"数据不能丢"的细节：
 *
 * 1. **值类型按运行时类型分派回原始键类型**。仓库侧只允许写 String / Boolean / Int
 *    （约束写在 PreferenceBackend 的注释里），这里对应 string/boolean/int 三种键。
 *    若统一按 Long 或按某一种键写，老用户文件里的字段类型就变了，升级后读不回来 ——
 *    而 DataStore 的读取异常被下面的 catch 兜成空偏好，表现是"设置全部静默恢复默认"。
 * 2. **只改动真正变化的键**。没被 block 碰过的键不重写，原类型原值留在
 *    MutablePreferences 里；置 null 或从草稿中消失的键才删。
 *    删除按名字生效：Preferences.Key 的 equals/hashCode 只比较 name
 *    （读过制品源码确认），所以借一个同名的 string 键就能删掉一个 int 键。
 */
class DataStorePreferenceBackend(private val context: Context) : PreferenceBackend {

    override val entries: Flow<Map<String, Any>> =
        context.applicationContext.settingsStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs.asMap().entries.associate { it.key.name to it.value } }

    override suspend fun edit(block: MutableMap<String, Any?>.() -> Unit) {
        context.applicationContext.settingsStore.edit { prefs ->
            val before: Map<String, Any> =
                prefs.asMap().entries.associate { it.key.name to it.value }
            val draft: MutableMap<String, Any?> = before.toMutableMap()
            draft.block()

            for ((name, value) in draft) {
                if (value == null) {
                    if (before.containsKey(name)) prefs.removeKey(name)
                    continue
                }
                if (before[name] == value) continue
                when (value) {
                    is String -> prefs[stringPreferencesKey(name)] = value
                    is Boolean -> prefs[booleanPreferencesKey(name)] = value
                    is Int -> prefs[intPreferencesKey(name)] = value
                    else -> throw IllegalArgumentException(
                        "偏好值不支持类型 ${value::class.simpleName}：只允许 String / Boolean / Int，" +
                            "加类型会改变 Android 侧已落盘字段的读法"
                    )
                }
            }
            // 草稿里整个消失的键（理论上不会出现：仓库只会置 null），一并清掉
            for (name in before.keys - draft.keys) prefs.removeKey(name)
        }
    }

    /** 按名字删键。Key 的相等性只看 name，所以借哪种类型的键都能删掉 */
    private fun MutablePreferences.removeKey(name: String) {
        remove(stringPreferencesKey(name))
    }
}
