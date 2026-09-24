package com.jianyi.outfit.data.repository

import com.jianyi.outfit.data.model.Gender
import com.jianyi.outfit.data.model.GlassQuality
import com.jianyi.outfit.data.model.SceneryMode
import com.jianyi.outfit.data.model.StylePreference
import com.jianyi.outfit.data.model.TempUnit
import com.jianyi.outfit.data.model.ToleranceLevel
import com.jianyi.outfit.data.model.UserPreferences
import com.jianyi.outfit.data.model.WindUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 设置仓库实现：读写全部走 [PreferenceBackend]，所以这份逻辑两端共用。
 * 平台侧只剩"怎么落盘"这一件事：Android = Preferences DataStore，iOS = NSUserDefaults。
 *
 * **键名与值类型与 DataStore 时代逐字一致**（下面 16 个常量就是全部）。
 * 这不是风格问题：Android 老用户机器上那个 preferences_pb 文件里的字段名和
 * 类型必须原样对得上，否则升级后偏好读不出来；而读取失败会被后端的
 * `catch { emptyPreferences() }` 兜成"全部恢复默认"，不崩溃、只是悄悄丢设置。
 */
class SettingsRepositoryImpl(private val backend: PreferenceBackend) : SettingsRepository {

    /** 观察用户偏好（读取异常时回退默认值） */
    override val preferences: Flow<UserPreferences> =
        backend.entries.map { it.toUserPreferences() }

    override val disclaimerAccepted: Flow<Boolean> =
        backend.entries.map { it.boolean(KEY_DISCLAIMER_ACCEPTED) ?: false }

    override val apiCredentials: Flow<ApiCredentials> =
        backend.entries.map { prefs ->
            ApiCredentials(
                id = prefs.str(KEY_API_ID).orEmpty(),
                key = prefs.str(KEY_API_KEY).orEmpty(),
                apiUrl = prefs.str(KEY_API_URL).orEmpty()
            )
        }

    /* ============ 偏好单位与风格 ============ */

    override suspend fun setTempUnit(unit: TempUnit) = write(KEY_TEMP_UNIT, unit.name)

    override suspend fun setWindUnit(unit: WindUnit) = write(KEY_WIND_UNIT, unit.name)

    override suspend fun setTolerance(level: ToleranceLevel) = write(KEY_TOLERANCE, level.name)

    override suspend fun setStyle(style: StylePreference) = write(KEY_STYLE, style.name)

    override suspend fun setGender(gender: Gender) = write(KEY_GENDER, gender.name)

    /* ============ 推送与预警 ============ */

    override suspend fun setDailyPush(enabled: Boolean) = write(KEY_DAILY_PUSH, enabled)

    override suspend fun setDailyPushHour(hour: Int) =
        write(KEY_DAILY_PUSH_HOUR, hour.coerceIn(0, 23))

    override suspend fun setExtremeAlert(enabled: Boolean) = write(KEY_EXTREME_ALERT, enabled)

    /* ============ 视觉与性能 ============ */

    override suspend fun setSceneryMode(mode: SceneryMode) = write(KEY_SCENERY_MODE, mode.name)

    /** 手选风景；传 null 表示清除选择（写 null = 删键，与旧实现一致） */
    override suspend fun setSceneryKey(key: String?) = backend.edit { this[KEY_SCENERY_KEY] = key }

    override suspend fun setGlassQuality(quality: GlassQuality) =
        write(KEY_GLASS_QUALITY, quality.name)

    override suspend fun setParallaxEnabled(enabled: Boolean) = write(KEY_PARALLAX, enabled)

    override suspend fun setBreathingEnabled(enabled: Boolean) = write(KEY_BREATHING, enabled)

    override suspend fun setHighFrameRateEnabled(enabled: Boolean) = write(KEY_HIGH_FPS, enabled)

    /* ============ 凭证与须知 ============ */

    /** 保存用户自填的 API 凭证；空白视为"未填"，删键以回退内置默认凭证 */
    override suspend fun setApiCredentials(id: String, key: String, apiUrl: String) =
        backend.edit {
            this[KEY_API_ID] = id.trim().ifBlank { null }
            this[KEY_API_KEY] = key.trim().ifBlank { null }
            this[KEY_API_URL] = apiUrl.trim().ifBlank { null }
        }

    /** 标记 / 重置免责声明确认状态（false 时下次启动重新弹出） */
    override suspend fun setDisclaimerAccepted(accepted: Boolean) {
        if (accepted) write(KEY_DISCLAIMER_ACCEPTED, true)
        else backend.edit { this[KEY_DISCLAIMER_ACCEPTED] = null }
    }

    private suspend fun write(key: String, value: Any) =
        backend.edit { this[key] = value }

    /* ============ 读取端：Map → 领域模型（未写入过的键取默认值） ============ */

    private fun Map<String, Any>.toUserPreferences(): UserPreferences = UserPreferences(
        tempUnit = TempUnit.safe(str(KEY_TEMP_UNIT)),
        windUnit = WindUnit.safe(str(KEY_WIND_UNIT)),
        coldHeatTolerance = ToleranceLevel.safe(str(KEY_TOLERANCE)),
        style = StylePreference.safe(str(KEY_STYLE)),
        gender = Gender.safe(str(KEY_GENDER)),
        dailyPushEnabled = boolean(KEY_DAILY_PUSH) ?: false,
        dailyPushHour = int(KEY_DAILY_PUSH_HOUR) ?: UserPreferences.DEFAULT_DAILY_PUSH_HOUR,
        extremeAlertEnabled = boolean(KEY_EXTREME_ALERT) ?: true,
        sceneryMode = SceneryMode.safe(str(KEY_SCENERY_MODE)),
        sceneryKey = str(KEY_SCENERY_KEY),
        glassQuality = GlassQuality.safe(str(KEY_GLASS_QUALITY)),
        parallaxEnabled = boolean(KEY_PARALLAX) ?: true,
        breathingEnabled = boolean(KEY_BREATHING) ?: true,
        highFrameRateEnabled = boolean(KEY_HIGH_FPS) ?: true
    )

    /**
     * 三个读取器都用 `as?`：值类型不匹配时得到 null，等同于"没设置过" → 走默认值。
     * 宁可退成默认，也不要抛 ClassCastException —— 但注意这条容错同时也是
     * "静默丢设置"的来源，所以键名/类型改动必须连同后端一起核对。
     */
    private fun Map<String, Any>.str(key: String): String? = this[key] as? String

    private fun Map<String, Any>.int(key: String): Int? = this[key] as? Int

    private fun Map<String, Any>.boolean(key: String): Boolean? = this[key] as? Boolean

    companion object {
        internal const val KEY_TEMP_UNIT = "temp_unit"
        internal const val KEY_WIND_UNIT = "wind_unit"
        internal const val KEY_TOLERANCE = "tolerance"
        internal const val KEY_STYLE = "style"
        internal const val KEY_GENDER = "gender"
        internal const val KEY_DAILY_PUSH = "daily_push"
        internal const val KEY_DAILY_PUSH_HOUR = "daily_push_hour"
        internal const val KEY_EXTREME_ALERT = "extreme_alert"
        internal const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted"
        internal const val KEY_API_ID = "api_id"
        internal const val KEY_API_KEY = "api_key"
        internal const val KEY_API_URL = "api_url"
        internal const val KEY_SCENERY_MODE = "scenery_mode"
        internal const val KEY_SCENERY_KEY = "scenery_key"
        internal const val KEY_GLASS_QUALITY = "glass_quality"
        internal const val KEY_PARALLAX = "parallax"
        internal const val KEY_BREATHING = "breathing"
        internal const val KEY_HIGH_FPS = "high_fps"
    }
}
