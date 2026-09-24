package com.jianyi.outfit.data.repository

import com.jianyi.outfit.data.model.SceneryMode
import com.jianyi.outfit.data.model.TempUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 设置仓库的"钉子"测试。
 *
 * 这层刚从 Android 的 DataStore 代码里拆出来，而它管的是**用户设置本身**：
 * 键名或值类型一旦改动，Android 老用户升级后就读不回原值，
 * 且读取异常会被后端的 catch 兜成"全部恢复默认" —— 不崩溃，只是悄悄丢设置。
 * 所以这里钉的不是业务逻辑，而是**落盘契约**：键名、类型、删键语义、默认值。
 *
 * 用假后端（内存 Map）跑，commonTest 里两端都能执行。
 */
class SettingsRepositoryTest {

    /** 与真实后端同语义的假实现：null 表示删键，整批改动一次生效 */
    private class FakeBackend(initial: Map<String, Any> = emptyMap()) : PreferenceBackend {
        val current = MutableStateFlow(initial)

        override val entries: MutableStateFlow<Map<String, Any>> = current

        override suspend fun edit(block: MutableMap<String, Any?>.() -> Unit) {
            val draft = current.value.toMutableMap<String, Any?>()
            draft.block()
            current.value = draft.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
        }
    }

    @Test
    fun 空偏好全部落到默认值() = runTest {
        val prefs = SettingsRepositoryImpl(FakeBackend()).preferences.first()
        assertEquals(TempUnit.CELSIUS, prefs.tempUnit, "温度默认摄氏")
        assertEquals(8, prefs.dailyPushHour, "默认推送时刻是 UserPreferences 里那个 8 点")
        assertTrue(prefs.extremeAlertEnabled, "极端天气预警默认开")
        assertTrue(prefs.parallaxEnabled && prefs.breathingEnabled && prefs.highFrameRateEnabled)
        assertFalse(prefs.dailyPushEnabled, "每日推送默认关（要先授权通知）")
    }

    @Test
    fun 写入用的是历史那套键名与值类型() = runTest {
        val backend = FakeBackend()
        val repo = SettingsRepositoryImpl(backend)
        repo.setTempUnit(TempUnit.FAHRENHEIT)
        repo.setDailyPushHour(7)
        repo.setDailyPush(true)
        repo.setSceneryMode(SceneryMode.FIXED)

        val saved = backend.current.value
        assertEquals("FAHRENHEIT", saved["temp_unit"], "键名与 Android 侧 stringPreferencesKey 时代一致")
        assertEquals(7, saved["daily_push_hour"], "推送时刻仍然是 Int —— 改成 Long 会换掉落盘字段类型")
        assertEquals(true, saved["daily_push"])
        assertEquals("FIXED", saved["scenery_mode"])
    }

    @Test
    fun 推送小时夹在零到二十三() = runTest {
        val backend = FakeBackend()
        SettingsRepositoryImpl(backend).apply {
            setDailyPushHour(25)
            setExtremeAlert(false)
        }
        assertEquals(23, backend.current.value["daily_push_hour"])
        SettingsRepositoryImpl(backend).setDailyPushHour(-4)
        assertEquals(0, backend.current.value["daily_push_hour"])
        assertFalse(backend.current.value["extreme_alert"] as Boolean)
    }

    @Test
    fun 空白凭证与空手选风景都是删键而不是写空串() = runTest {
        val backend = FakeBackend(
            mapOf(
                "api_id" to "123", "api_key" to "abc", "api_url" to "https://x/",
                "scenery_key" to "mountain_snow", "disclaimer_accepted" to true
            )
        )
        val repo = SettingsRepositoryImpl(backend)

        repo.setApiCredentials(id = "   ", key = "", apiUrl = "\t")
        assertFalse("api_id" in backend.current.value, "留空表示不覆盖内置默认，必须删键")
        assertFalse("api_key" in backend.current.value)
        assertFalse("api_url" in backend.current.value)

        repo.setSceneryKey(null)
        assertFalse("scenery_key" in backend.current.value, "清除手选风景 = 回到跟随天气")

        repo.setDisclaimerAccepted(false)
        assertFalse("disclaimer_accepted" in backend.current.value, "重置后下次启动要重新弹须知")

        repo.setDisclaimerAccepted(true)
        assertEquals(true, backend.current.value["disclaimer_accepted"])
    }

    @Test
    fun 无法识别的枚举值回退默认而不抛() = runTest {
        // 模拟"版本回退"或用户数据被外部改坏：值存在但不是任何合法枚举名
        val backend = FakeBackend(mapOf("temp_unit" to "开尔文", "glass_quality" to "ULTRA"))
        val prefs = SettingsRepositoryImpl(backend).preferences.first()
        assertEquals(TempUnit.CELSIUS, prefs.tempUnit)
        assertEquals("REALTIME", prefs.glassQuality.name)
    }

    @Test
    fun 改过的设置在重新读取时保持一致() = runTest {
        val backend = FakeBackend()
        val repo = SettingsRepositoryImpl(backend)
        repo.setTempUnit(TempUnit.FAHRENHEIT)
        repo.setSceneryKey("sea_sunset")
        repo.setApiCredentials("id1", "key1", "https://api.example/")

        val creds = repo.apiCredentials.first()
        assertEquals("id1" to "key1", creds.id to creds.key)
        assertEquals("https://api.example/", creds.apiUrl)

        val prefs = repo.preferences.first()
        assertEquals(TempUnit.FAHRENHEIT, prefs.tempUnit)
        assertEquals("sea_sunset", prefs.sceneryKey)
    }
}
