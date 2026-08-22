package com.jianyi.outfit.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianyi.outfit.WeatherOutfitApp
import com.jianyi.outfit.data.model.OutfitRecommendation
import com.jianyi.outfit.data.model.UserPreferences
import com.jianyi.outfit.data.model.WeatherNow
import com.jianyi.outfit.engine.OutfitRecommendationEngine
import com.jianyi.outfit.notification.Notifier
import com.jianyi.outfit.util.LocationUtil
import com.jianyi.outfit.data.repository.RateLimitedException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/** 定位来源：首页城市名旁的标识与 IP 降级提示依据 */
enum class LocationSource { GPS, IP, MANUAL }

/** 首页 UI 状态 */
data class HomeUiState(
    val isLoading: Boolean = false,        // 首次加载
    val isRefreshing: Boolean = false,     // 下拉/点击刷新
    val weather: WeatherNow? = null,
    val recommendation: OutfitRecommendation? = null,
    val prefs: UserPreferences = UserPreferences(),
    val locationSource: LocationSource = LocationSource.MANUAL,
    val error: String? = null,
    val message: String? = null,           // 一次性提示（Snackbar 展示后消费）
    /**
     * 限流倒计时（秒）：>0 时首页展示限流卡片并倒数自动重试；
     * =0 表示自动重试已用过，仅提供手动重试；null 表示非限流状态。
     */
    val rateLimitRetryIn: Int? = null
)

/**
 * 首页 ViewModel：天气加载 + 推荐计算。
 * - 定位策略（按可靠性排序）：手动选择的城市缓存 → GPS 经纬度 → IP 兜底
 * - IP 定位依赖运营商 IP 库，存在跨城漂移（如同城邻近城市返回错误城市），
 *   因此仅在 GPS 不可用（未授权 / 无 Play Services / 定位服务关闭）时降级使用，
 *   并在 UI 上以来源标识 + 提示条告知用户可一键切换精确定位
 * - 当前城市变化 → 重新加载
 * - 偏好变化 → 用本地天气重算推荐（不发网络请求）
 */
class HomeViewModel(private val app: WeatherOutfitApp) : ViewModel() {

    private val container = app.container

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 已加载的数据源 key，防止 Flow 重放触发重复请求 */
    private var loadedKey: String? = null

    /** 刷新节流：两次手动请求的最小间隔（防止连点触发接口限流） */
    private var lastRequestAt = 0L

    companion object {
        private const val MIN_REQUEST_INTERVAL_MS = 10_000L
    }

    /** 限流自动重试仅允许一次，避免公共凭证持续饱和时无限循环 */
    private var rateLimitAutoRetried = false

    /** 手动刷新节流检查：间隔不足时 Snackbar 提示剩余等待秒数 */
    private fun throttleCheck(): Boolean {
        val elapsed = System.currentTimeMillis() - lastRequestAt
        if (elapsed >= MIN_REQUEST_INTERVAL_MS) {
            lastRequestAt = System.currentTimeMillis()
            return true
        }
        val wait = ((MIN_REQUEST_INTERVAL_MS - elapsed) / 1000).toInt() + 1
        _uiState.update { it.copy(message = "操作太快，请 ${wait} 秒后再试") }
        return false
    }

    init {
        viewModelScope.launch {
            container.cityRepository.currentCity.collect { city ->
                if (city == null) {
                    resolveAutoLocation(force = false)
                } else {
                    val key = "addr|${city.province}|${city.city}"
                    if (key != loadedKey) {
                        load(key, LocationSource.MANUAL) {
                            container.weatherRepository.byAddress(city.province, city.city)
                        }
                    }
                }
            }
        }
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
    }

    /**
     * 自动定位链（无手动选择城市时）：GPS 优先 → IP 兜底 → 都失败提示手动选择。
     * GPS 仅在权限已授予时静默尝试，未授权不弹窗打扰，直接走 IP。
     */
    private suspend fun resolveAutoLocation(force: Boolean) {
        if (LocationUtil.hasLocationPermission(app)) {
            val location = container.locationUtil.lastKnownLocation()
            if (location != null) {
                load(gpsKey(location.latitude, location.longitude), LocationSource.GPS) {
                    container.weatherRepository.byLatLon(location.latitude, location.longitude, force)
                }
                return
            }
        }
        load("ip", LocationSource.IP) { container.weatherRepository.byIp(force = force) }
    }

    /** 下拉/点击刷新：10 秒节流后跳过缓存强制走网络 */
    fun refresh() {
        if (!throttleCheck()) return
        forceReload()
    }

    /** 限流倒计时结束的自动重试（仅一次，之后转为手动模式） */
    fun onRateLimitCountdownEnd() {
        if (rateLimitAutoRetried) return
        rateLimitAutoRetried = true
        lastRequestAt = 0L   // 自动重试不受手动节流限制
        forceReload()
    }

    /** 强制走网络重新加载（手动刷新 / 限流自动重试共用） */
    private fun forceReload() {
        lastRequestAt = System.currentTimeMillis()
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, rateLimitRetryIn = null) }
            val city = container.cityRepository.currentCity.first()
            if (city != null) {
                load("addr|${city.province}|${city.city}", LocationSource.MANUAL) {
                    container.weatherRepository.byAddress(city.province, city.city, force = true)
                }
            } else {
                resolveAutoLocation(force = true)
            }
        }
    }

    /** 首页「使用精确定位」：GPS 定位并强制刷新（UI 层已确认权限，同样受节流保护） */
    fun locateByGps() {
        if (_uiState.value.isRefreshing) return
        if (!throttleCheck()) return
        viewModelScope.launch {
            val location = container.locationUtil.lastKnownLocation()
            if (location == null) {
                _uiState.update {
                    it.copy(message = "定位失败，请确认系统定位已开启，或点击城市名手动搜索")
                }
            } else {
                load(gpsKey(location.latitude, location.longitude), LocationSource.GPS) {
                    container.weatherRepository.byLatLon(location.latitude, location.longitude, force = true)
                }
            }
        }
    }

    /** 首页申请定位权限被拒：继续使用 IP 粗略定位并告知 */
    fun onLocationPermissionDenied() {
        _uiState.update {
            it.copy(message = "未授予定位权限，将继续使用 IP 粗略定位，可稍后在系统设置中开启")
        }
    }

    /** 长按推荐卡片：保存当前推荐方案为个人穿搭模板 */
    fun saveCurrentAsTemplate() {
        val state = _uiState.value
        val weather = state.weather ?: return
        val plan = state.recommendation?.plans?.firstOrNull() ?: return
        viewModelScope.launch {
            container.templateRepository.saveFromPlan(
                plan = plan,
                minTemp = (weather.dayLow ?: (weather.temperature - 3)).roundToInt(),
                maxTemp = (weather.dayHigh ?: (weather.temperature + 3)).roundToInt()
            )
            _uiState.update { it.copy(message = "已保存「${plan.scene}」推荐方案，可在详情页查看") }
        }
    }

    /** 消费一次性提示 */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** 统一加载入口 */
    private suspend fun load(key: String, source: LocationSource, fetch: suspend () -> Result<WeatherNow>) {
        if (_uiState.value.weather == null) {
            _uiState.update { it.copy(isLoading = true, error = null) }
        }
        fetch().fold(
            onSuccess = { weather ->
                loadedKey = key
                rateLimitAutoRetried = false
                container.sessionWeather = weather
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        weather = weather,
                        error = null,
                        rateLimitRetryIn = null,
                        locationSource = source,
                        recommendation = OutfitRecommendationEngine.recommend(weather, state.prefs)
                    )
                }
                notifyExtremeWeatherIfNeeded(weather)
            },
            onFailure = { e ->
                loadedKey = key
                val hasContent = _uiState.value.weather != null
                if (e is RateLimitedException) {
                    when {
                        // 屏上已有数据：不打断浏览，Snackbar 说明即可
                        hasContent -> _uiState.update {
                            it.copy(
                                isLoading = false, isRefreshing = false,
                                message = "接口繁忙，当前展示的是缓存数据，稍后可下拉刷新"
                            )
                        }
                        // 首次限流失败：展示倒计时卡片，到点自动重试一次
                        !rateLimitAutoRetried -> _uiState.update {
                            it.copy(
                                isLoading = false, isRefreshing = false, error = null,
                                rateLimitRetryIn = e.retryAfterSec.coerceIn(5, 60)
                            )
                        }
                        // 自动重试已用过：卡片转为手动重试模式
                        else -> _uiState.update {
                            it.copy(
                                isLoading = false, isRefreshing = false, error = null,
                                rateLimitRetryIn = 0
                            )
                        }
                    }
                } else {
                    val text = e.message ?: "天气加载失败，请下拉重试，或点击城市名手动选择城市"
                    _uiState.update { state ->
                        if (hasContent) {
                            // 屏上已有数据时错误降级为 Snackbar，避免误伤正在浏览的内容
                            state.copy(isLoading = false, isRefreshing = false, message = text)
                        } else {
                            state.copy(isLoading = false, isRefreshing = false, error = text)
                        }
                    }
                }
            }
        )
    }

    /** GPS 数据源 key（保留两位小数，位置微动不触发重复加载） */
    private fun gpsKey(lat: Double, lon: Double): String =
        String.format(Locale.US, "gps|%.2f|%.2f", lat, lon)

    /** 极端天气预警开关开启时，收到预警立即通知 */
    private suspend fun notifyExtremeWeatherIfNeeded(weather: WeatherNow) {
        val prefs = container.settingsRepository.preferences.first()
        val alarm = weather.alarms.firstOrNull() ?: return
        if (prefs.extremeAlertEnabled) {
            Notifier.showExtremeAlert(
                app,
                title = alarm.title,
                text = "生效时间：${alarm.effective.ifBlank { "见官方信息" }}"
            )
        }
    }
}
