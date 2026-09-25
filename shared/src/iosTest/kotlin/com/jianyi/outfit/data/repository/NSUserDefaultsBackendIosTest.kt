package com.jianyi.outfit.data.repository

import com.jianyi.outfit.data.model.TempUnit
import com.jianyi.outfit.platform.currentTimeMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * iOS 偏好后端（NSUserDefaults 里那个带类型标签的 JSON）的**运行**测试。
 *
 * 为什么值得单独跑：Android 侧那 6 条契约测试已经在 JVM 上过了一遍仓库逻辑，
 * 这里要验的是 iOS 特有的一段 —— encode/decode 往返、以及"整份偏好挂单个键"
 * 真的能跨实例读回来。类型标签一旦写错，表现是**类型悄悄漂移**
 * （存成 "8" 的字符串被当 Int 读回来），比崩溃更难发现。
 */
class NSUserDefaultsBackendIosTest {

    @Test
    fun types_survive_encode_and_decode() = runBlocking {
        val backend = NSUserDefaultsPreferenceBackend()
        val stamp = currentTimeMillis()
        val strKey = "t_str_$stamp"
        val intKey = "t_int_$stamp"
        val boolKey = "t_bool_$stamp"

        backend.edit {
            this[strKey] = "摄氏$stamp"
            this[intKey] = 7
            this[boolKey] = true
        }

        val snapshot = backend.entries.first()
        assertEquals("摄氏$stamp", snapshot[strKey])
        assertEquals(7, snapshot[intKey], "Int 必须仍以 Int 回来，不能变成字符串")
        assertEquals(true, snapshot[boolKey])

        // 新实例 = 重新从 defaults 读那份 blob，证明磁盘上真的写下了且解得开
        val reread = NSUserDefaultsPreferenceBackend().entries.first()
        assertEquals(7, reread[intKey])
        assertEquals("摄氏$stamp", reread[strKey])

        backend.edit { this[intKey] = null }
        assertFalse(backend.entries.first().containsKey(intKey), "写 null 必须删键")
        assertEquals("摄氏$stamp", backend.entries.first()[strKey], "删一个键不该牵连其它键")
    }

    /**
     * 读不懂的那份偏好**不能被下一次写静默抹掉**。
     *
     * 钉的是最危险的一类回归：`catch → 空表 → 整体写回`。表现不是崩溃也不是报错，
     * 而是"用户所有设置悄悄回到默认"，而且一旦写回原数据就永久没了，事后连查都没得查。
     */
    @Test
    fun unreadable_blob_is_preserved_instead_of_being_overwritten() = runBlocking {
        // 与 NSUserDefaultsPreferenceBackend 里同一个键名（那边是 private，测试只能照抄）。
        // 改了那边忘了改这里的话，这个用例会因"读不到旁路键"而失败，不会假绿。
        val blobKey = "jianyi.preferences.v1"
        val sideKey = "$blobKey.unreadable"
        val defaults = NSUserDefaults.standardUserDefaults
        val stamp = currentTimeMillis()
        val foreign = """{"temp_unit":{"z":"开尔文"},"only_unknown_$stamp":{"q":1}}"""

        defaults.setObject(value = foreign, forKey = blobKey)
        defaults.setObject(value = null, forKey = sideKey)

        val backend = NSUserDefaultsPreferenceBackend()
        // 标签不认识 → 整份视为解不开，内存从空开始
        assertNull(backend.entries.first()["temp_unit"])

        backend.edit { this["temp_unit"] = "CELSIUS" }

        assertEquals(
            foreign,
            defaults.stringForKey(sideKey),
            "解不开的原文必须被转到旁路键，不能被这次写覆盖掉"
        )
        assertEquals("CELSIUS", backend.entries.first()["temp_unit"])

        // 清场，别污染同机后续用例
        defaults.setObject(value = null, forKey = blobKey)
        defaults.setObject(value = null, forKey = sideKey)
    }

    /** 仓库 → 后端整条链在 iOS 上跑通（读回的值来自磁盘上那份 JSON，不是内存） */
    @Test
    fun settings_repository_round_trips_through_ios_backend() = runBlocking {
        val repo = SettingsRepositoryImpl(NSUserDefaultsPreferenceBackend())

        val original = repo.preferences.first()
        repo.setTempUnit(TempUnit.FAHRENHEIT)
        repo.setDailyPushHour(9)
        repo.setSceneryKey("sea_sunset")

        val reread = SettingsRepositoryImpl(NSUserDefaultsPreferenceBackend()).preferences.first()
        assertEquals(TempUnit.FAHRENHEIT, reread.tempUnit)
        assertEquals(9, reread.dailyPushHour)
        assertEquals("sea_sunset", reread.sceneryKey)

        repo.setSceneryKey(null)
        assertNull(
            SettingsRepositoryImpl(NSUserDefaultsPreferenceBackend()).preferences.first().sceneryKey,
            "清除手选风景后新实例应读到 null"
        )

        // 还原，避免影响同一台模拟器上后续用例的默认观感
        repo.setTempUnit(original.tempUnit)
        repo.setDailyPushHour(original.dailyPushHour)
    }
}
