package com.jianyi.outfit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianyi.outfit.WeatherOutfitApp
import com.jianyi.outfit.data.model.Gender
import com.jianyi.outfit.data.model.StylePreference
import com.jianyi.outfit.data.model.TempUnit
import com.jianyi.outfit.data.model.ToleranceLevel
import com.jianyi.outfit.data.model.UserPreferences
import com.jianyi.outfit.data.model.WindUnit
import com.jianyi.outfit.data.repository.ApiCredentials
import com.jianyi.outfit.notification.DailyPushScheduler
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
 * 每日推送开关 / 时刻变化时同步对齐 WorkManager 周期任务（持久化，重启自动恢复）。
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
        viewModelScope.launch {
            container.settingsRepository.apiCredentials.collect { creds ->
                _uiState.update { it.copy(apiCredentials = creds) }
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

    /** 每日穿搭推送：持久化偏好 + 注册/取消 WorkManager 周期任务 */
    fun setDailyPush(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setDailyPush(enabled)
            if (enabled) {
                DailyPushScheduler.ensureScheduled(app, container.settingsRepository.preferences.first().dailyPushHour)
            } else {
                DailyPushScheduler.cancel(app)
            }
        }
    }

    /** 修改推送时刻：持久化 + 已开启推送时按新时刻对齐周期任务 */
    fun setDailyPushHour(hour: Int) {
        viewModelScope.launch {
            container.settingsRepository.setDailyPushHour(hour)
            if (container.settingsRepository.preferences.first().dailyPushEnabled) {
                DailyPushScheduler.ensureScheduled(app, hour)
            }
        }
    }

    /** 极端天气预警开关 */
    fun setExtremeAlert(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setExtremeAlert(enabled) }
    }

    /** 保存用户自填的 API 凭证（空值 = 清除自填，回退内置默认凭证） */
    fun saveApiCredentials(id: String, key: String, apiUrl: String) {
        viewModelScope.launch {
            container.settingsRepository.setApiCredentials(id, key, apiUrl)
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
