package com.jianyi.outfit.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jianyi.outfit.data.model.WeatherNow
import com.jianyi.outfit.di.AppViewModelProvider
import com.jianyi.outfit.ui.components.ErrorPanel
import com.jianyi.outfit.ui.components.LoadingPanel
import com.jianyi.outfit.ui.components.MetricChip
import com.jianyi.outfit.ui.theme.HighTempOrange
import com.jianyi.outfit.ui.theme.LowTempBlue
import com.jianyi.outfit.util.Formatters
import com.jianyi.outfit.util.LocationUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 首页：核心天气信息 + 穿搭推荐卡片。
 * 遵循「顺从」理念——首屏只保留温度、天气状态与穿搭推荐，辅助功能全部收纳在顶栏。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onNavigateToDetail: () -> Unit,
    onNavigateToCity: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // 首页「使用精确定位」的权限申请（IP 降级提示条触发）
    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) viewModel.locateByGps()
        else viewModel.onLocationPermissionDenied()
    }

    fun requestGpsFromHome() {
        if (LocationUtil.hasLocationPermission(context)) {
            viewModel.locateByGps()
        } else {
            gpsPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // 一次性提示（如“已保存模板”）用 Snackbar 呈现
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // 滚动时顶部栏自动收缩为仅“城市名 + 操作按钮”
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onNavigateToCity)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = state.weather?.city ?: "定位中…",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        // 定位来源标识：GPS 精确 / IP 粗略（警示色）/ 手动选择不显示
                        when (state.locationSource) {
                            LocationSource.GPS -> Icon(
                                imageVector = Icons.Filled.MyLocation,
                                contentDescription = "GPS 精确定位",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 6.dp).size(16.dp)
                            )
                            LocationSource.IP -> Icon(
                                imageVector = Icons.Filled.Wifi,
                                contentDescription = "IP 粗略定位",
                                tint = HighTempOrange,
                                modifier = Modifier.padding(start = 6.dp).size(16.dp)
                            )
                            LocationSource.MANUAL -> Unit
                        }
                    }
                },
                actions = {
                    RefreshIconButton(
                        loading = state.isLoading || state.isRefreshing,
                        onClick = viewModel::refresh
                    )
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "设置"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
            val rateLimitRetryIn = state.rateLimitRetryIn
            when {
                state.isLoading -> LoadingPanel(Modifier.padding(innerPadding))
                rateLimitRetryIn != null -> RateLimitPanel(
                    retryInSec = rateLimitRetryIn,
                    onCountdownEnd = viewModel::onRateLimitCountdownEnd,
                    onManualRetry = viewModel::refresh,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
                state.weather == null -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    state.error?.let {
                        ErrorPanel(
                            message = it,
                            onRetry = viewModel::refresh,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
                else -> HomeContent(
                    state = state,
                    innerPadding = innerPadding,
                    onNavigateToDetail = onNavigateToDetail,
                    onSaveTemplate = viewModel::saveCurrentAsTemplate,
                    onUseGpsLocation = ::requestGpsFromHome
                )
            }
        }
    }
}

/** 首页主体内容 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    state: HomeUiState,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    onNavigateToDetail: () -> Unit,
    onSaveTemplate: () -> Unit,
    onUseGpsLocation: () -> Unit
) {
    val weather = state.weather ?: return
    val recommendation = state.recommendation

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = 24.dp,
            start = 20.dp,
            end = 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ===== IP 降级提示条（仅在 IP 粗略定位时展示，不静默降级） =====
        if (state.locationSource == LocationSource.IP) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onUseGpsLocation),
                    shape = MaterialTheme.shapes.medium,
                    color = HighTempOrange.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Wifi,
                            contentDescription = null,
                            tint = HighTempOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "当前为 IP 粗略定位，城市可能有偏差，点击使用 GPS 精确定位",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // ===== 核心信息区 =====
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                weather.iconUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = weather.condition,
                        modifier = Modifier.size(56.dp)
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    val (value, unit) = Formatters.tempSplit(
                        weather.temperature, state.prefs.tempUnit
                    )
                    // 状态色仅用于提示：高温浅橙 / 低温浅蓝
                    val tempColor = when {
                        weather.temperature >= 32 -> HighTempOrange
                        weather.temperature <= 5 -> LowTempBlue
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = value,
                        style = TextStyle(fontSize = 72.sp, fontWeight = FontWeight.Bold),
                        color = tempColor
                    )
                    Text(
                        text = unit,
                        style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                Text(
                    text = weather.condition,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "更新于 ${weather.updateTime.ifBlank { "--" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ===== 四指标：体感温度 / 湿度 / 风力 / 紫外线 =====
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricChip(
                        icon = Icons.Filled.Thermostat,
                        label = "体感",
                        value = Formatters.feelsLike(weather.feelsLike, state.prefs.tempUnit),
                        modifier = Modifier.weight(1f)
                    )
                    MetricChip(
                        icon = Icons.Filled.WaterDrop,
                        label = "湿度",
                        value = Formatters.humidity(weather.humidity),
                        modifier = Modifier.weight(1f)
                    )
                    MetricChip(
                        icon = Icons.Filled.Air,
                        label = "风力",
                        value = Formatters.wind(weather, state.prefs.windUnit),
                        modifier = Modifier.weight(1f)
                    )
                    MetricChip(
                        icon = Icons.Filled.WbSunny,
                        label = "紫外线",
                        value = "${weather.uvIndex}·${weather.uvLevel}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ===== 气象预警（有预警时才展示） =====
        if (weather.alarms.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    weather.alarms.take(2).forEach { alarm ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = HighTempOrange.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    tint = HighTempOrange
                                )
                                Column {
                                    Text(
                                        text = alarm.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (alarm.effective.isNotBlank()) {
                                        Text(
                                            text = "生效时间：${alarm.effective}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ===== 穿搭推荐卡片 =====
        if (recommendation != null) {
            item {
                // 长按卡片放大 1.02 倍并提升色调海拔，模拟物理浮起
                var pressed by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(
                    targetValue = if (pressed) 1.02f else 1f,
                    label = "cardScale"
                )
                val scope = rememberCoroutineScope()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .combinedClickable(
                            onClick = onNavigateToDetail,
                            onLongClick = {
                                pressed = true
                                onSaveTemplate()
                                scope.launch {
                                    delay(500)
                                    pressed = false
                                }
                            }
                        ),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = if (pressed) 6.dp else 1.dp
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "今日穿搭建议",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = recommendation.summary,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        recommendation.reminders.take(2).forEach { reminder ->
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "·",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = reminder,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onNavigateToDetail) {
                                Text("查看详情")
                            }
                        }
                    }
                }
            }
        }

        // ===== 操作提示 =====
        item {
            Text(
                text = "长按推荐卡片可保存为穿搭模板",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** 刷新按钮：加载中时图标缓慢旋转 */
@Composable
private fun RefreshIconButton(loading: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "refresh")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "angle"
    )
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "刷新",
            modifier = if (loading) {
                Modifier.graphicsLayer { rotationZ = angle
                }
            } else {
                Modifier
            }
        )
    }
}

/**
 * 限流友好卡片：公共凭证共享额度被打爆时展示。
 * retryInSec > 0：倒计时到点自动重试一次；= 0：自动重试已用过，仅手动重试。
 */
@Composable
private fun RateLimitPanel(
    retryInSec: Int,
    onCountdownEnd: () -> Unit,
    onManualRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var remaining by remember(retryInSec) { mutableStateOf(retryInSec) }

    // 倒计时：每秒递减，归零触发一次自动重试（手动模式不启动倒计时）
    LaunchedEffect(remaining) {
        if (remaining > 0) {
            delay(1000)
            remaining--
            if (remaining == 0) onCountdownEnd()
        }
    }

    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "天气数据暂时不可用",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = if (retryInSec > 0) {
                        "公共接口繁忙（共享额度有限），${remaining} 秒后自动重试…"
                    } else {
                        "公共接口持续繁忙，已自动重试过一次，请稍后手动重试"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "建议注册个人 API 凭证以获得独享频次，配置方法见使用说明 3.1 节",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onManualRetry) {
                        Text("立即重试")
                    }
                }
            }
        }
    }
}
