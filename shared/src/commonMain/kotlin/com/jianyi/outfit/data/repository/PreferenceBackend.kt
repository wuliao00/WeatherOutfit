package com.jianyi.outfit.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * 平台偏好存储的最小面 —— 让设置仓库的实现能留在 commonMain。
 *
 * 值域刻意限死在 String / Boolean / Int 三种：这正是现有 16 个键全部用到的类型，
 * 也刚好是 DataStore 与 NSUserDefaults 的公共子集。不多不少是有原因的 ——
 * 一旦放 Long 进来，Android 侧对应字段会从 int32 变成 int64，老用户升级后
 * 读不回原值；而 DataStore 的读取异常是被 `catch { emit(emptyPreferences()) }`
 * 兜住的，表现不是崩溃而是"所有设置静默恢复默认"，属于最难发现的那类回归。
 */
interface PreferenceBackend {

    /** 已写入的键值。没写过的键不出现在这里，默认值由仓库负责（两端共用同一套默认） */
    val entries: Flow<Map<String, Any>>

    /**
     * 一次性改写：block 拿到当前值的可变副本，返回后整体落盘。
     * 写 null 表示删除该键（用于"清除手选风景""留空回退内置凭证"这类语义）。
     * 整个回调在一次事务里完成，不会留下改了一半的状态。
     */
    suspend fun edit(block: MutableMap<String, Any?>.() -> Unit)
}
