package com.jianyi.outfit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianyi.outfit.data.AppDependencies
import com.jianyi.outfit.data.model.Gender
import com.jianyi.outfit.data.model.GlassQuality
import com.jianyi.outfit.data.model.SceneryMode
import com.jianyi.outfit.data.model.StylePreference
import com.jianyi.outfit.data.model.TempUnit
import com.jianyi.outfit.data.model.ToleranceLevel
import com.jianyi.outfit.data.model.UserPreferences
import com.jianyi.outfit.data.model.WindUnit
import com.jianyi.outfit.data.repository.ApiCredentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 设置页 UI 状态 */
data class SettingsUiState(
    val prefs: UserPreferences = UserPreferences(),
    /** 用户自填的 API 凭证（原始值，空 = 未自定义） */
    val apiCredentials: ApiCredentials? = null,
    val message: String? = null
)

/**
 * 设置页 ViewModel：穿搭偏好、单位设置、通知设置、API 凭证。
 * 每日推送开关 / 时刻变化时同步对齐持久化任务（Android = WorkManager 周期任务，
 * 重启自动恢复；iOS = 预定本地通知，系统自带持久性）。
 */
class SettingsViewModel(private val deps: AppDependencies) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            deps.settingsRepository.preferences.collect { prefs ->
                _uiState.update { it.copy(prefs = prefs) }
            }
        }
        viewModelScope.launch {
            deps.settingsRepository.apiCredentials.collect { creds ->
                _uiState.update { it.copy(apiCredentials = creds) }
            }
        }
    }

    fun setTempUnit(unit: TempUnit) {
        viewModelScope.launch { deps.settingsRepository.setTempUnit(unit) }
    }

    fun setWindUnit(unit: WindUnit) {
        viewModelScope.launch { deps.settingsRepository.setWindUnit(unit) }
    }

    fun setTolerance(level: ToleranceLevel) {
        viewModelScope.launch { deps.settingsRepository.setTolerance(level) }
    }

    fun setStyle(style: StylePreference) {
        viewModelScope.launch { deps.settingsRepository.setStyle(style) }
    }

    fun setGender(gender: Gender) {
        viewModelScope.launch { deps.settingsRepository.setGender(gender) }
    }

    /** 每日穿搭推送：持久化偏好 + 注册/取消持久化周期任务 */
    fun setDailyPush(enabled: Boolean) {
        viewModelScope.launch {
            deps.settingsRepository.setDailyPush(enabled)
            if (enabled) {
                deps.pushScheduler.ensureScheduled(deps.settingsRepository.preferences.first().dailyPushHour)
            } else {
                deps.pushScheduler.cancel()
            }
        }
    }

    /** 修改推送时刻：持久化 + 已开启推送时按新时刻对齐周期任务 */
    fun setDailyPushHour(hour: Int) {
        viewModelScope.launch {
            deps.settingsRepository.setDailyPushHour(hour)
            if (deps.settingsRepository.preferences.first().dailyPushEnabled) {
                deps.pushScheduler.ensureScheduled(hour)
            }
        }
    }

    /** 极端天气预警开关 */
    fun setExtremeAlert(enabled: Boolean) {
        viewModelScope.launch { deps.settingsRepository.setExtremeAlert(enabled) }
    }


    /** 保存用户自填的 API 凭证（空值 = 清除自填，回退内置默认凭证） */
    fun saveApiCredentials(id: String, key: String, apiUrl: String) {
        viewModelScope.launch {
            deps.settingsRepository.setApiCredentials(id, key, apiUrl)
            _uiState.update {
                it.copy(
                    message = if (id.isBlank() && key.isBlank()) {
                        "已清除自填凭证，回退内置默认"
                    } else {
                        "接口凭证已保存，下次查询生效"
                    }
                )
            }
        }
    }

/* ============ 视觉与性能 ============ */

    fun setSceneryMode(mode: SceneryMode) {
        viewModelScope.launch { deps.settingsRepository.setSceneryMode(mode) }
    }

    /**
     * 选一张固定风景。
     * 同时把模式切到 FIXED —— 否则用户选完，下次刷新天气又被自动换掉，
     * 那会让人以为设置没生效。
     */
    fun pinScenery(key: String) {
        viewModelScope.launch {
            deps.settingsRepository.setSceneryMode(SceneryMode.FIXED)
            deps.settingsRepository.setSceneryKey(key)
        }
    }

    fun setGlassQuality(quality: GlassQuality) {
        viewModelScope.launch { deps.settingsRepository.setGlassQuality(quality) }
    }

    fun setParallax(enabled: Boolean) {
        viewModelScope.launch { deps.settingsRepository.setParallaxEnabled(enabled) }
    }

    fun setBreathing(enabled: Boolean) {
        viewModelScope.launch { deps.settingsRepository.setBreathingEnabled(enabled) }
    }

    fun setHighFrameRate(enabled: Boolean) {
        viewModelScope.launch { deps.settingsRepository.setHighFrameRateEnabled(enabled) }
    }

    /** 重置免责声明确认状态：下次启动重新弹出使用须知 */
    fun resetDisclaimer() {
        viewModelScope.launch {
            deps.settingsRepository.setDisclaimerAccepted(false)
            _uiState.update { it.copy(message = "已重置，下次启动将重新展示使用须知") }
        }
    }

    /** 消费一次性提示 */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
