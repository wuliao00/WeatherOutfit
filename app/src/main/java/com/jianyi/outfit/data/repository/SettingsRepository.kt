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
 * 天气接口凭证配置。
 * 三个字段均为「用户自填值」：留空表示未自定义，由取数方回退到 BuildConfig 内置默认
 * （见 [resolve]，参数注入而非直接依赖 BuildConfig，保持可单测）。
 */
data class ApiCredentials(
    val id: String = "",
    val key: String = "",
    val apiUrl: String = ""
) {
    /** 用户配置优先，留空回退默认值 */
    fun resolve(defaultId: String, defaultKey: String, defaultUrl: String): ApiCredentials =
        ApiCredentials(
            id = id.ifBlank { defaultId },
            key = key.ifBlank { defaultKey },
            apiUrl = apiUrl.ifBlank { defaultUrl }
        )
}

/** 设置仓库接口：面向 ViewModel 与其他仓库的抽象，便于 JVM 单测替换实现 */
interface SettingsRepository {

    /** 观察用户偏好 */
    val preferences: Flow<UserPreferences>

    /** 是否已确认首次启动免责声明 */
    val disclaimerAccepted: Flow<Boolean>

    /** API 凭证配置（原始自填值，字段可能为空，取数方负责 resolve） */
    val apiCredentials: Flow<ApiCredentials>

    suspend fun setTempUnit(unit: TempUnit)
    suspend fun setWindUnit(unit: WindUnit)
    suspend fun setTolerance(level: ToleranceLevel)
    suspend fun setStyle(style: StylePreference)
    suspend fun setGender(gender: Gender)
    suspend fun setDailyPush(enabled: Boolean)

    /** 每日推送时刻（0~23 点整） */
    suspend fun setDailyPushHour(hour: Int)
    suspend fun setExtremeAlert(enabled: Boolean)
    suspend fun setDisclaimerAccepted(accepted: Boolean)

    /** 保存用户自填的 API 凭证（空字符串表示清除自填、回退内置默认） */
    suspend fun setApiCredentials(id: String, key: String, apiUrl: String)

    /* ---- 视觉与性能：背景选景、玻璃档位、视差/呼吸、高帧率 ---- */
    suspend fun setSceneryMode(mode: SceneryMode)
    suspend fun setSceneryKey(key: String?)
    suspend fun setGlassQuality(quality: GlassQuality)
    suspend fun setParallaxEnabled(enabled: Boolean)
    suspend fun setBreathingEnabled(enabled: Boolean)
    suspend fun setHighFrameRateEnabled(enabled: Boolean)
}

/**
 * 设置仓库实现：基于 Preferences DataStore 的轻量配置持久化。
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
