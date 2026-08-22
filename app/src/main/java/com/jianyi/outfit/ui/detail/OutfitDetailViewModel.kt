package com.jianyi.outfit.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianyi.outfit.WeatherOutfitApp
import com.jianyi.outfit.data.model.CustomOutfitTemplate
import com.jianyi.outfit.data.model.OutfitRecommendation
import com.jianyi.outfit.data.model.UserPreferences
import com.jianyi.outfit.data.model.WeatherNow
import com.jianyi.outfit.engine.OutfitRecommendationEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 穿搭详情页 UI 状态 */
data class OutfitDetailUiState(
    val weather: WeatherNow? = null,
    val recommendation: OutfitRecommendation? = null,
    val prefs: UserPreferences = UserPreferences(),
    val templates: List<CustomOutfitTemplate> = emptyList(),
    val message: String? = null
)

/**
 * 穿搭详情页 ViewModel：
 * - 复用首页会话中的天气快照生成三场景方案（不重复请求）
 * - 管理用户自定义穿搭模板
 */
class OutfitDetailViewModel(private val app: WeatherOutfitApp) : ViewModel() {

    private val container = app.container

    private val _uiState = MutableStateFlow(OutfitDetailUiState())
    val uiState: StateFlow<OutfitDetailUiState> = _uiState.asStateFlow()

    init {
        // 首页已加载的天气快照
        container.sessionWeather?.let { weather ->
            _uiState.update {
                it.copy(
                    weather = weather,
                    recommendation = OutfitRecommendationEngine.recommend(weather, it.prefs)
                )
            }
        }
        // 偏好变化 → 重算推荐
        viewModelScope.launch {
            container.settingsRepository.preferences.collect { prefs ->
                _uiState.update { state ->
                    state.copy(
                        prefs = prefs,
                        recommendation = state.weather
                            ?.let { OutfitRecommendationEngine.recommend(it, prefs) }
                    )
                }
            }
        }
        // 模板列表
        viewModelScope.launch {
            container.templateRepository.templates.collect { list ->
                _uiState.update { it.copy(templates = list) }
            }
        }
    }

    /** 保存自定义穿搭模板 */
    fun saveTemplate(
        name: String,
        scene: String,
        minTemp: Int,
        maxTemp: Int,
        items: List<String>,
        tip: String
    ) {
        if (name.isBlank() || items.isEmpty()) {
            _uiState.update { it.copy(message = "请至少填写模板名称与一件单品") }
            return
        }
        viewModelScope.launch {
            container.templateRepository.save(
                CustomOutfitTemplate(
                    name = name.trim(),
                    scene = scene,
                    minTemp = minTemp,
                    maxTemp = maxTemp,
                    items = items,
                    tip = tip.trim()
                )
            )
            _uiState.update { it.copy(message = "已保存模板「${name.trim()}」") }
        }
    }

    /** 删除模板 */
    fun deleteTemplate(id: Long) {
        viewModelScope.launch { container.templateRepository.delete(id) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
