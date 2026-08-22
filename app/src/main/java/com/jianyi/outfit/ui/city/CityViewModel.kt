package com.jianyi.outfit.ui.city

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianyi.outfit.WeatherOutfitApp
import com.jianyi.outfit.data.local.entity.CityEntity
import com.jianyi.outfit.data.repository.RateLimitedException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 城市管理 UI 状态 */
data class CityUiState(
    val province: String = CityViewModel.PROVINCES.first(),
    val cityInput: String = "",
    val searching: Boolean = false,
    val locating: Boolean = false,
    val error: String? = null,
    val savedCities: List<CityEntity> = emptyList(),
    val currentCity: CityEntity? = null,
    val finished: Boolean = false    // 切换成功后通知 UI 返回首页
)

/**
 * 城市管理 ViewModel：
 * - 手动搜索（省 + 市/区）
 * - GPS 自动定位（失败自动回退提示改用搜索/IP 定位）
 * - 历史城市快速切换与删除
 */
class CityViewModel(private val app: WeatherOutfitApp) : ViewModel() {

    private val container = app.container

    private val _uiState = MutableStateFlow(CityUiState())
    val uiState: StateFlow<CityUiState> = _uiState.asStateFlow()

    companion object {
        /** 省份短名列表（接口约定 sheng 参数不带“省/市”后缀） */
        val PROVINCES = listOf(
            "北京", "天津", "上海", "重庆", "河北", "山西", "内蒙古", "辽宁",
            "吉林", "黑龙江", "江苏", "浙江", "安徽", "福建", "江西", "山东",
            "河南", "湖北", "湖南", "广东", "广西", "海南", "四川", "贵州",
            "云南", "西藏", "陕西", "甘肃", "青海", "宁夏", "新疆",
            "香港", "澳门", "台湾"
        )
    }

    init {
        viewModelScope.launch {
            container.cityRepository.allCities.collect { list ->
                _uiState.update { it.copy(savedCities = list) }
            }
        }
        viewModelScope.launch {
            container.cityRepository.currentCity.collect { city ->
                _uiState.update { it.copy(currentCity = city) }
            }
        }
    }

    fun onProvinceChange(province: String) {
        _uiState.update { it.copy(province = province) }
    }

    fun onCityInputChange(text: String) {
        _uiState.update { it.copy(cityInput = text) }
    }

    /** 地址搜索：查询成功即保存为历史城市并设为当前城市 */
    fun search() {
        val current = _uiState.value
        if (current.searching) return
        val city = current.cityInput.trim()
        if (city.isEmpty()) {
            _uiState.update { it.copy(error = "请输入城市或区县名称") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(searching = true, error = null) }
            container.weatherRepository.byAddress(current.province, city, force = true).fold(
                onSuccess = { weather ->
                    container.cityRepository.switchTo(
                        province = weather.province.ifBlank { current.province },
                        city = weather.city,
                        source = "search"
                    )
                    _uiState.update { it.copy(searching = false, finished = true) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(searching = false, error = friendlyError(e, "查询失败，请确认省份与城市名"))
                    }
                }
            )
        }
    }

    /** GPS 定位（UI 层已确认定位权限） */
    fun locateByGps() {
        if (_uiState.value.locating) return
        viewModelScope.launch {
            _uiState.update { it.copy(locating = true, error = null) }
            val location = container.locationUtil.lastKnownLocation()
            if (location == null) {
                _uiState.update {
                    it.copy(
                        locating = false,
                        error = "定位失败，请确认系统定位已开启，或改用搜索 / IP 定位"
                    )
                }
                return@launch
            }
            container.weatherRepository.byLatLon(location.latitude, location.longitude).fold(
                onSuccess = { weather ->
                    if (weather.province.isNotBlank()) {
                        container.cityRepository.switchTo(
                            province = weather.province,
                            city = weather.city,
                            source = "gps"
                        )
                    } else {
                        // 经纬度数据源不含省份，无法持久化为地址查询；
                        // 清除手选城市，让首页走「GPS 优先」自动定位链
                        container.cityRepository.clearCurrentSelection()
                    }
                    _uiState.update { it.copy(locating = false, finished = true) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(locating = false, error = friendlyError(e, "定位查询失败，请稍后再试"))
                    }
                }
            )
        }
    }

    /** UI 层未授予/拒绝了定位权限时调用 */
    fun onLocationPermissionDenied() {
        _uiState.update {
            it.copy(locating = false, error = "未授予定位权限，无法使用 GPS 定位，可改用搜索")
        }
    }

    /** 城市页错误文案：限流错误附带等待秒数与个人凭证指引 */
    private fun friendlyError(e: Throwable, fallback: String): String =
        if (e is RateLimitedException) {
            "接口繁忙（公共凭证共享额度），约 ${e.retryAfterSec} 秒后可重试；" +
                "注册个人 API 凭证可获独享频次（见使用说明 3.1 节）"
        } else {
            e.message ?: fallback
        }

    /** 点击历史城市：切换为当前城市 */
    fun selectCity(city: CityEntity) {
        viewModelScope.launch {
            container.cityRepository.switchTo(city.province, city.city, city.source)
            _uiState.update { it.copy(finished = true) }
        }
    }

    /** 左滑删除历史城市 */
    fun deleteCity(city: CityEntity) {
        viewModelScope.launch { container.cityRepository.delete(city) }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }
}
