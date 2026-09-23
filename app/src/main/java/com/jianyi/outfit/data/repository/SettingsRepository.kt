package com.jianyi.outfit.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jianyi.outfit.data.model.Gender
import com.jianyi.outfit.data.model.GlassQuality
import com.jianyi.outfit.data.model.SceneryMode
import com.jianyi.outfit.data.model.StylePreference
import com.jianyi.outfit.data.model.TempUnit
import com.jianyi.outfit.data.model.ToleranceLevel
import com.jianyi.outfit.data.model.UserPreferences
import com.jianyi.outfit.data.model.WindUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** 应用级 DataStore 实例（懒加载单例） */
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

/**
 * 设置仓库实现：基于 Preferences DataStore 的轻量配置持久化。
 * 接口与 ApiCredentials 在 shared 的同名文件里（同包，无需 import）。
 */
class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    /** 观察用户偏好（读取异常时回退默认值） */
    override val preferences: Flow<UserPreferences> = context.settingsStore.data
        .catch { emit(emptyPreferences()) }
        .map { it.toUserPreferences() }

    override val disclaimerAccepted: Flow<Boolean> = context.settingsStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[KEY_DISCLAIMER_ACCEPTED] ?: false }

    /* ============ 视觉与性能偏好 ============ */

    override suspend fun setSceneryMode(mode: SceneryMode) =
        update { it[KEY_SCENERY_MODE] = mode.name }

    /** 手选风景；传 null 表示清除选择 */
    override suspend fun setSceneryKey(key: String?): Unit = update { prefs ->
        if (key == null) prefs.remove(KEY_SCENERY_KEY) else prefs[KEY_SCENERY_KEY] = key
    }

    override suspend fun setGlassQuality(quality: GlassQuality) =
        update { it[KEY_GLASS_QUALITY] = quality.name }

    override suspend fun setParallaxEnabled(enabled: Boolean) =
        update { it[KEY_PARALLAX] = enabled }

    override suspend fun setBreathingEnabled(enabled: Boolean) =
        update { it[KEY_BREATHING] = enabled }

    override suspend fun setHighFrameRateEnabled(enabled: Boolean) =
        update { it[KEY_HIGH_FPS] = enabled }

    override val apiCredentials: Flow<ApiCredentials> = context.settingsStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            ApiCredentials(
                id = prefs[KEY_API_ID].orEmpty(),
                key = prefs[KEY_API_KEY].orEmpty(),
                apiUrl = prefs[KEY_API_URL].orEmpty()
            )
        }

    override suspend fun setTempUnit(unit: TempUnit) = update { it[KEY_TEMP_UNIT] = unit.name }

    override suspend fun setWindUnit(unit: WindUnit) = update { it[KEY_WIND_UNIT] = unit.name }

    override suspend fun setTolerance(level: ToleranceLevel) = update { it[KEY_TOLERANCE] = level.name }

    override suspend fun setStyle(style: StylePreference) = update { it[KEY_STYLE] = style.name }

    override suspend fun setGender(gender: Gender) = update { it[KEY_GENDER] = gender.name }

    override suspend fun setDailyPush(enabled: Boolean) = update { it[KEY_DAILY_PUSH] = enabled }

    override suspend fun setDailyPushHour(hour: Int) =
        update { it[KEY_DAILY_PUSH_HOUR] = hour.coerceIn(0, 23) }

    override suspend fun setExtremeAlert(enabled: Boolean) = update { it[KEY_EXTREME_ALERT] = enabled }

    /** 标记 / 重置免责声明确认状态（false 时下次启动重新弹出） */
    override suspend fun setDisclaimerAccepted(accepted: Boolean) {
        if (accepted) update { it[KEY_DISCLAIMER_ACCEPTED] = true }
        else context.settingsStore.edit { it.remove(KEY_DISCLAIMER_ACCEPTED) }
    }

    /** 保存用户自填的 API 凭证；空值清除对应键，回退内置默认凭证 */
    override suspend fun setApiCredentials(id: String, key: String, apiUrl: String) {
        context.settingsStore.edit { prefs ->
            putOrRemove(prefs, KEY_API_ID, id.trim())
            putOrRemove(prefs, KEY_API_KEY, key.trim())
            putOrRemove(prefs, KEY_API_URL, apiUrl.trim())
        }
    }

    private suspend fun update(block: (MutablePreferences) -> Unit) {
        context.settingsStore.edit { block(it) }
    }

    private fun putOrRemove(prefs: MutablePreferences, key: Preferences.Key<String>, value: String) {
        if (value.isBlank()) prefs.remove(key) else prefs[key] = value
    }

    companion object {
        private val KEY_TEMP_UNIT = stringPreferencesKey("temp_unit")
        private val KEY_WIND_UNIT = stringPreferencesKey("wind_unit")
        private val KEY_TOLERANCE = stringPreferencesKey("tolerance")
        private val KEY_STYLE = stringPreferencesKey("style")
        private val KEY_GENDER = stringPreferencesKey("gender")
        private val KEY_DAILY_PUSH = booleanPreferencesKey("daily_push")
        private val KEY_DAILY_PUSH_HOUR = intPreferencesKey("daily_push_hour")
        private val KEY_EXTREME_ALERT = booleanPreferencesKey("extreme_alert")
        private val KEY_DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
        private val KEY_API_ID = stringPreferencesKey("api_id")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_API_URL = stringPreferencesKey("api_url")
        private val KEY_SCENERY_MODE = stringPreferencesKey("scenery_mode")
        private val KEY_SCENERY_KEY = stringPreferencesKey("scenery_key")
        private val KEY_GLASS_QUALITY = stringPreferencesKey("glass_quality")
        private val KEY_PARALLAX = booleanPreferencesKey("parallax")
        private val KEY_BREATHING = booleanPreferencesKey("breathing")
        private val KEY_HIGH_FPS = booleanPreferencesKey("high_fps")

        /** Preferences → 领域模型（未写入过的键取默认值） */
        private fun Preferences.toUserPreferences(): UserPreferences = UserPreferences(
            tempUnit = TempUnit.safe(this[KEY_TEMP_UNIT]),
            windUnit = WindUnit.safe(this[KEY_WIND_UNIT]),
            coldHeatTolerance = ToleranceLevel.safe(this[KEY_TOLERANCE]),
            style = StylePreference.safe(this[KEY_STYLE]),
            gender = Gender.safe(this[KEY_GENDER]),
            dailyPushEnabled = this[KEY_DAILY_PUSH] ?: false,
            dailyPushHour = this[KEY_DAILY_PUSH_HOUR] ?: UserPreferences.DEFAULT_DAILY_PUSH_HOUR,
            extremeAlertEnabled = this[KEY_EXTREME_ALERT] ?: true,
            sceneryMode = SceneryMode.safe(this[KEY_SCENERY_MODE]),
            sceneryKey = this[KEY_SCENERY_KEY],
            glassQuality = GlassQuality.safe(this[KEY_GLASS_QUALITY]),
            parallaxEnabled = this[KEY_PARALLAX] ?: true,
            breathingEnabled = this[KEY_BREATHING] ?: true,
            highFrameRateEnabled = this[KEY_HIGH_FPS] ?: true
        )
    }
}
