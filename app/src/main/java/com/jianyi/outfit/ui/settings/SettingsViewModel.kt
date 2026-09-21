package com.jianyi.outfit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianyi.outfit.WeatherOutfitApp
import com.jianyi.outfit.data.model.Gender
import com.jianyi.outfit.data.model.GlassQuality
import com.jianyi.outfit.data.model.SceneryMode
import com.jianyi.outfit.data.model.StylePreference
import com.jianyi.outfit.data.model.TempUnit
import com.jianyi.outfit.data.model.ToleranceLevel
import com.jianyi.outfit.data.model.UserPreferences
import com.jianyi.outfit.data.model.WindUnit
import com.jianyi.outfit.notification.Notifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 设置页 UI 状态 */
data class SettingsUiState(
    val prefs: UserPreferences = UserPreferences(),
    val message: String? = null
)

/**
 * 设置页 ViewModel：穿搭偏好、单位设置、通知设置。
 * 每日推送开关切换时同步注册/取消系统闹钟（默认早 8 点）。
 */
class SettingsViewModel(private val app: WeatherOutfitApp) : ViewModel() {

    private val container = app.container

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            container.settingsRepository.preferences.collect { prefs ->
                _uiState.update { it.copy(prefs = prefs) }
            }
        }
    }

    fun setTempUnit(unit: TempUnit) {
        viewModelScope.launch { container.settingsRepository.setTempUnit(unit) }
    }

    fun setWindUnit(unit: WindUnit) {
        viewModelScope.launch { container.settingsRepository.setWindUnit(unit) }
    }

    fun setTolerance(level: ToleranceLevel) {
        viewModelScope.launch { container.settingsRepository.setTolerance(level) }
    }

    fun setStyle(style: StylePreference) {
        viewModelScope.launch { container.settingsRepository.setStyle(style) }
    }

    fun setGender(gender: Gender) {
        viewModelScope.launch { container.settingsRepository.setGender(gender) }
    }

    /** 每日穿搭推送：持久化偏好 + 注册/取消闹钟 */
    fun setDailyPush(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setDailyPush(enabled)
            if (enabled) Notifier.scheduleDaily(app) else Notifier.cancelDaily(app)
        }
    }

    /** 极端天气预警开关 */
    fun setExtremeAlert(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setExtremeAlert(enabled) }
    }

    /* ============ 视觉与性能 ============ */

    fun setSceneryMode(mode: SceneryMode) {
        viewModelScope.launch { container.settingsRepository.setSceneryMode(mode) }
    }

    /**
     * 选一张固定风景。
     * 同时把模式切到 FIXED —— 否则用户选完，下次刷新天气又被自动换掉，
     * 那会让人以为设置没生效。
     */
    fun pinScenery(key: String) {
        viewModelScope.launch {
            container.settingsRepository.setSceneryMode(SceneryMode.FIXED)
            container.settingsRepository.setSceneryKey(key)
        }
    }

    fun setGlassQuality(quality: GlassQuality) {
        viewModelScope.launch { container.settingsRepository.setGlassQuality(quality) }
    }

    fun setParallax(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setParallaxEnabled(enabled) }
    }

    fun setBreathing(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setBreathingEnabled(enabled) }
    }

    fun setHighFrameRate(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setHighFrameRateEnabled(enabled) }
    }

    /** 重置免责声明确认状态：下次启动重新弹出使用须知 */
    fun resetDisclaimer() {
        viewModelScope.launch {
            container.settingsRepository.setDisclaimerAccepted(false)
            _uiState.update { it.copy(message = "已重置，下次启动将重新展示使用须知") }
        }
    }

    /** 消费一次性提示 */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
