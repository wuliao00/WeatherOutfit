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

/**
 * 天气接口凭证配置。
 * 三个字段均为「用户自填值」：留空表示未自定义，由取数方回退到内置默认
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

/** 设置仓库接口：面向 ViewModel 与其他仓库的抽象，便于 JVM 单测替换实现。
 *  接口在 shared，DataStore 实现留在 app。 */
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
