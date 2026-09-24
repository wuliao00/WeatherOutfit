package com.jianyi.outfit.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import coil3.compose.AsyncImage
import com.jianyi.outfit.data.model.TempUnit
import com.jianyi.outfit.data.model.WeatherNow
import com.jianyi.outfit.ui.components.ErrorPanel
import com.jianyi.outfit.ui.components.LoadingPanel
import com.jianyi.outfit.ui.glass.GlassEmphasis
import com.jianyi.outfit.ui.glass.GlassIconButton
import com.jianyi.outfit.ui.glass.GlassRole
import com.jianyi.outfit.ui.glass.GlassShapes
import com.jianyi.outfit.ui.glass.GlassSurface
import com.jianyi.outfit.ui.glass.glassMaterial
import com.jianyi.outfit.ui.scenery.LocalSceneryController
import com.jianyi.outfit.ui.scenery.Scenery
import com.jianyi.outfit.ui.scenery.SceneryPickerSheet
import com.jianyi.outfit.ui.theme.HighTempOrange
import com.jianyi.outfit.ui.theme.LocalScenery
import com.jianyi.outfit.ui.theme.LowTempBlue
import com.jianyi.outfit.data.LocalAppDependencies
import com.jianyi.outfit.ui.theme.MotionSpecs
import com.jianyi.outfit.util.Formatters
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 首页。
 *
 * 动效骨架：整页只有一个滚动源（Column 的 verticalScroll），
 * 顶栏折叠量、背景视差、玻璃浓度全部由它派生出同一个 0..1 进度。
 * 因为读的是同一个数，元素之间不可能出现「谁慢半拍」；
 * 又因为只写进 FloatState、只在 graphicsLayer 里读，滚动全程零重组。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToCity: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val controller = LocalSceneryController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val deps = LocalAppDependencies.current
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val collapseRangePx = with(density) { 116.dp.toPx() }
    val collapse: MutableFloatState = remember { mutableFloatStateOf(0f) }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    // 首页「使用精确定位」的权限申请（IP 降级提示条触发）。
    // 「已授予就直接定位、否则弹窗、按结果分支」三步全在能力接口里，
    // 页面不碰 Manifest、也不碰 activity-result API —— 这样它才能进 commonMain。
    fun requestGpsFromHome() {
        deps.locationProvider.requestPermission { granted ->
            if (granted) viewModel.locateByGps() else viewModel.onLocationPermissionDenied()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // 滚动 → 折叠进度 + 背景视差。写 FloatState，不产生重组
    LaunchedEffect(scroll, controller) {
        snapshotFlow { scroll.value.toFloat() }.collect { px ->
            collapse.floatValue = (px / collapseRangePx).coerceIn(0f, 1f)
            controller?.scrollPx?.floatValue = px
        }
    }
    DisposableEffect(controller) {
        onDispose { controller?.scrollPx?.floatValue = 0f }
    }

    Box(Modifier.fillMaxSize()) {
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.statusBarsPadding().height(62.dp))

                val rateLimitRetryIn = state.rateLimitRetryIn
                when {
                    state.isLoading -> LoadingPanel(Modifier.fillMaxWidth())

                    rateLimitRetryIn != null -> RateLimitPanel(
                        retryInSec = rateLimitRetryIn,
                        onCountdownEnd = viewModel::onRateLimitCountdownEnd,
                        onManualRetry = viewModel::refresh,
                        modifier = Modifier.fillMaxWidth()
                    )

                    state.weather == null -> state.error?.let {
                        ErrorPanel(
                            message = it,
                            onRetry = viewModel::refresh,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    else -> HomeContent(
                        state = state,
                        // 携带缓存 key 跳转：详情页从仓库读快照，全 app 无全局可变状态
                        onNavigateToDetail = {
                            viewModel.currentCacheKey?.let(onNavigateToDetail)
                        },
                        onSaveTemplate = viewModel::saveCurrentAsTemplate,
                        onUseGpsLocation = ::requestGpsFromHome,
                        onOpenPicker = { showPicker = true }
                    )
                }

                Spacer(Modifier.height(26.dp).navigationBarsPadding())
            }

            HomeTopBar(
                collapse = collapse,
                state = state,
                onCityClick = onNavigateToCity,
                onRefresh = viewModel::refresh,
                onSettings = onNavigateToSettings
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 10.dp)
        )
    }

    if (showPicker) {
        SceneryPickerSheet(
            current = LocalScenery.current,
            reason = controller?.reason ?: "",
            onSelect = { picked -> controller?.onUserSelect(picked) },
            onDismiss = { showPicker = false }
        )
    }
}

/* ============ 悬浮玻璃顶栏 ============ */

@Composable
private fun HomeTopBar(
    collapse: FloatState,
    state: HomeUiState,
    onCityClick: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    val scenery = LocalScenery.current
    val dark = scenery.dark || isSystemInDarkTheme()

    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        /* 玻璃底：随折叠进度从「几乎不可见」长到「实心导航条」。
         * bodyAlpha 传的是「折叠到底时」的本体色浓度 —— 整层 alpha 已经跟着折叠在走，
         * 这里若传 collapse.floatValue 就把滚动状态读进了组合，每帧重组。
         * 需要本体色而不是「第二份模糊」来压住滚过来的正文，原因见 Glass.kt 文件头。 */
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = 0.10f + 0.90f * collapse.floatValue }
                .glassMaterial(
                    shape = GlassShapes.bar,
                    emphasis = GlassEmphasis.THIN,
                    role = GlassRole.BAR,
                    dark = dark,
                    bodyAlpha = 0.80f
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onCityClick)
                    .padding(vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.weather?.city ?: "定位中…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    LocationBadge(state.locationSource)
                }
                // 副标题只在折叠后出现：展开时这些信息在 hero 区已经给过了
                Text(
                    text = state.weather?.let {
                        "${it.condition} · ${Formatters.temp(it.temperature, state.prefs.tempUnit)}"
                    } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer { alpha = collapse.floatValue }
                )
            }

            RefreshIconButton(
                loading = state.isLoading || state.isRefreshing,
                dark = dark,
                onClick = onRefresh
            )
            Spacer(Modifier.width(2.dp))
            GlassIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = "设置",
                onClick = onSettings,
                dark = dark
            )
        }
    }
}

/** 定位来源标识：GPS 精确 / IP 粗略（警示色）/ 手动选择不显示 */
@Composable
private fun LocationBadge(source: LocationSource) {
    when (source) {
        LocationSource.GPS -> Icon(
            imageVector = Icons.Filled.MyLocation,
            contentDescription = "GPS 精确定位",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp).size(15.dp)
        )
        LocationSource.IP -> Icon(
            imageVector = Icons.Filled.Wifi,
            contentDescription = "IP 粗略定位",
            tint = HighTempOrange,
            modifier = Modifier.padding(start = 6.dp).size(15.dp)
        )
        LocationSource.MANUAL -> Unit
    }
}

/** 刷新按钮：仅在真正加载时才把无限动画挂进组合，平时一帧都不空转 */
@Composable
private fun RefreshIconButton(loading: Boolean, dark: Boolean, onClick: () -> Unit) {
    GlassIconButton(
        icon = Icons.Filled.Refresh,
        contentDescription = "刷新",
        onClick = onClick,
        dark = dark,
        modifier = if (loading) Modifier.spinWhileLoading() else Modifier
    )
}

@Composable
private fun Modifier.spinWhileLoading(): Modifier {
    val transition = rememberInfiniteTransition(label = "refresh")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "angle"
    )
    // 只在图层里读：读到组合层就会让整棵顶栏每帧重组
    return graphicsLayer { rotationZ = angle }
}

/* ============ 正文 ============ */

@Composable
private fun HomeContent(
    state: HomeUiState,
    onNavigateToDetail: () -> Unit,
    onSaveTemplate: () -> Unit,
    onUseGpsLocation: () -> Unit,
    onOpenPicker: () -> Unit
) {
    val weather = state.weather ?: return
    val recommendation = state.recommendation
    val scenery = LocalScenery.current
    val dark = scenery.dark || isSystemInDarkTheme()
    val onColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    // ===== IP 降级提示：不静默降级，让用户能一键纠正 =====
    if (state.locationSource == LocationSource.IP) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = GlassShapes.inner,
            emphasis = GlassEmphasis.ULTRA_THIN,
            dark = dark,
            onClick = onUseGpsLocation,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Wifi,
                    contentDescription = null,
                    tint = HighTempOrange,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "IP 粗略定位，城市可能有偏差 · 点此改用 GPS",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // ===== Hero：天气图标 + 大号温度 =====
    HeroSection(weather = weather, state = state)

    // ===== 四指标 =====
    MetricsRow(weather = weather, state = state, dark = dark)

    // ===== 气象预警 =====
    if (weather.alarms.isNotEmpty()) {
        weather.alarms.take(2).forEach { alarm ->
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = GlassShapes.inner,
                emphasis = GlassEmphasis.THIN,
                dark = dark,
                scrim = HighTempOrange.copy(alpha = 0.14f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = HighTempOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = alarm.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = onColor
                        )
                        if (alarm.effective.isNotBlank()) {
                            Text(
                                text = "生效时间：${alarm.effective}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = mutedColor
                            )
                        }
                    }
                }
            }
        }
    }

    // ===== 今日穿搭 =====
    if (recommendation != null) {
        OutfitHeroCard(
            summary = recommendation.summary,
            reminders = recommendation.reminders,
            sceneCount = recommendation.plans.size,
            dark = dark,
            onOpen = onNavigateToDetail,
            onSaveTemplate = onSaveTemplate
        )
    }

    // ===== 生活指数 =====
    if (state.lifeIndices.isNotEmpty()) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            emphasis = GlassEmphasis.REGULAR,
            dark = dark,
            role = GlassRole.CARD
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "生活指数",
                    style = MaterialTheme.typography.titleMedium,
                    color = onColor
                )
                EstimatedBadge()
            }
            Spacer(Modifier.height(12.dp))
            LifeIndexList(indices = state.lifeIndices, dark = dark)
        }
    }

    // ===== 七日预报 =====
    if (state.forecast.isNotEmpty() || state.forecastNote != null) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            emphasis = GlassEmphasis.REGULAR,
            dark = dark,
            role = GlassRole.CARD
        ) {
            ForecastCard(
                days = state.forecast,
                note = state.forecastNote,
                tempUnit = state.prefs.tempUnit,
                dark = dark
            )
        }
    }

    // ===== 只有经纬度定位才拿得到的补充实况 =====
    AtmosphereSection(weather = weather, dark = dark)

    // ===== 背景换景入口 =====
    SceneryRow(
        scenery = scenery,
        dark = dark,
        onOpen = onOpenPicker
    )

    Text(
        text = "长按穿搭卡片可存为模板 · 左右拖动背景可换景",
        style = MaterialTheme.typography.labelSmall,
        color = mutedColor,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
    )
}

/* ============ Hero ============ */

@Composable
private fun HeroSection(weather: WeatherNow, state: HomeUiState) {
    val onColor = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        weather.iconUrl?.let { url ->
            // 图标拉不到时整块收起：留一个 64dp 空洞比没有图标更难看。
            // 天气图标是外部 CDN 的图，DNS 失败/无网/被分流都是常态
            // （实测某台机器 VPN 分流下 rescdn.apihz.cn 直接 ERR_NAME_NOT_RESOLVED）。
            var iconFailed by remember(url) { mutableStateOf(false) }
            if (!iconFailed) {
                AsyncImage(
                    model = url,
                    contentDescription = weather.condition,
                    onError = { iconFailed = true },
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        val (value, unit) = Formatters.tempSplit(
            weather.temperature, state.prefs.tempUnit
        )
        val tempColor = when {
            weather.temperature >= 32 -> HighTempOrange
            weather.temperature <= 5 -> LowTempBlue
            else -> onColor
        }
        AnimatedTempText(
            target = value.toFloatOrNull() ?: weather.temperature.toFloat(),
            unit = unit,
            color = tempColor
        )

        Text(
            text = weather.condition,
            style = MaterialTheme.typography.titleLarge,
            color = onColor
        )

        // 今日温度区间：有高低才有这根条，缺数据时不硬画
        val high = weather.dayHigh
        val low = weather.dayLow
        if (high != null && low != null && high > low) {
            Spacer(Modifier.height(10.dp))
            TempRangeBar(low, high, current = weather.temperature, unit = state.prefs.tempUnit)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "更新于 ${weather.updateTime.ifBlank { "--" }}",
            style = MaterialTheme.typography.labelSmall,
            color = muted
        )
    }
}

/**
 * 大号温度：数值变化时用弹簧过渡，读数会「滚」到新值。
 *
 * 单独成组件是为了把每帧重组限制在这一个小叶子里 ——
 * 温度动画期间不会牵动整页。
 */
@Composable
private fun AnimatedTempText(target: Float, unit: String, color: Color) {
    val anim = remember { Animatable(target) }
    LaunchedEffect(target) {
        anim.animateTo(target, MotionSpecs.follow())
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = anim.value.roundToInt().toString(),
            style = MaterialTheme.typography.displayLarge,
            color = color
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 14.dp, start = 2.dp)
        )
    }
}

/** 今日最低 ~ 最高，当前温度在条上标一个点 */
@Composable
private fun TempRangeBar(
    low: Double,
    high: Double,
    current: Double,
    unit: TempUnit,
    modifier: Modifier = Modifier
) {
    val ratio = ((current - low) / (high - low).coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    val anim = remember { Animatable(ratio) }
    LaunchedEffect(ratio) { anim.animateTo(ratio, MotionSpecs.settle()) }

    Column(
        modifier = modifier.width(180.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(LowTempBlue.copy(alpha = 0.55f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .graphicsLayer { scaleX = anim.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(LowTempBlue, HighTempOrange)
                        )
                    )
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = Formatters.temp(low, unit),
                style = MaterialTheme.typography.labelMedium,
                color = LowTempBlue
            )
            Text(
                text = Formatters.temp(high, unit),
                style = MaterialTheme.typography.labelMedium,
                color = HighTempOrange
            )
        }
    }
}

/* ============ 四指标 ============ */

@Composable
private fun MetricsRow(weather: WeatherNow, state: HomeUiState, dark: Boolean) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        emphasis = GlassEmphasis.REGULAR,
        dark = dark,
        role = GlassRole.CARD,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp, 16.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Metric(
                icon = Icons.Filled.Thermostat,
                label = "体感",
                value = Formatters.feelsLike(weather.feelsLike, state.prefs.tempUnit),
                modifier = Modifier.weight(1f)
            )
            Metric(
                icon = Icons.Filled.WaterDrop,
                label = "湿度",
                value = Formatters.humidity(weather.humidity),
                modifier = Modifier.weight(1f)
            )
            Metric(
                icon = Icons.Filled.Air,
                label = "风力",
                value = Formatters.wind(weather, state.prefs.windUnit),
                modifier = Modifier.weight(1f)
            )
            Metric(
                icon = Icons.Filled.WbSunny,
                label = "紫外线",
                value = "${weather.uvIndex}·${weather.uvLevel}",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun Metric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(19.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            fontSize = 13.sp
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ============ 穿搭主卡 ============ */

@Composable
private fun OutfitHeroCard(
    summary: String,
    reminders: List<String>,
    sceneCount: Int,
    dark: Boolean,
    onOpen: () -> Unit,
    onSaveTemplate: () -> Unit
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        emphasis = GlassEmphasis.REGULAR,
        dark = dark,
        role = GlassRole.CARD,
        onClick = onOpen,
        onLongClick = onSaveTemplate,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "今日穿搭",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "$sceneCount 套场景",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (reminders.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            reminders.take(3).forEach { reminder ->
                Row(Modifier.padding(top = 5.dp)) {
                    Text(
                        text = "·",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 7.dp)
                    )
                    Text(
                        text = reminder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "查看三套场景方案",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.Layers,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/* ============ 补充实况 ============ */

/**
 * 气压 / 能见度 / 云量 / 日出日落。
 *
 * 这些字段只有经纬度端点提供，缺就整块不显示 ——
 * 与其摆一排「--」，不如不摆，也不至于让人以为数据坏了。
 */
@Composable
private fun AtmosphereSection(weather: WeatherNow, dark: Boolean) {
    val items = buildList {
        weather.pressureHpa?.let { add("气压" to Formatters.pressure(it)) }
        weather.visibilityM?.let { add("能见度" to Formatters.visibility(it)) }
        weather.cloudCover?.let { add("云量" to Formatters.cloudCover(it)) }
        if (weather.sunriseAt != null) add("日出" to Formatters.clock(weather.sunriseAt))
        if (weather.sunsetAt != null) add("日落" to Formatters.clock(weather.sunsetAt))
    }
    if (items.isEmpty()) return

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        emphasis = GlassEmphasis.THIN,
        dark = dark,
        role = GlassRole.CARD,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp, 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth()) {
                    rowItems.forEach { (name, value) ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = value,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    // 奇数个时补一个空位，保持两列对齐
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/* ============ 换景入口 ============ */

@Composable
private fun SceneryRow(scenery: Scenery, dark: Boolean, onOpen: () -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = GlassShapes.inner,
        emphasis = GlassEmphasis.ULTRA_THIN,
        dark = dark,
        onClick = onOpen,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(scenery.resId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "背景 · ${scenery.label}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = scenery.poetic,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = "换景",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/* ============ 限流友好卡片 ============ */

/**
 * 公共凭证共享额度被打爆时展示。
 * retryInSec > 0：倒计时到点自动重试一次；= 0：自动重试已用过，仅手动重试。
 */
@Composable
private fun RateLimitPanel(
    retryInSec: Int,
    onCountdownEnd: () -> Unit,
    onManualRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 必须 remember：否则每次重组都会把倒计时重置回初始值，卡片会卡在同一个数字
    var remaining by remember(retryInSec) { mutableIntStateOf(retryInSec) }
    val scenery = LocalScenery.current
    val dark = scenery.dark || isSystemInDarkTheme()

    LaunchedEffect(remaining) {
        if (remaining > 0) {
            delay(1000)
            remaining--
            if (remaining == 0) onCountdownEnd()
        }
    }

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        emphasis = GlassEmphasis.REGULAR,
        dark = dark,
        scrim = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "天气数据暂时不可用",
                style = MaterialTheme.typography.titleMedium,
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
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.OutlinedButton(onClick = onManualRetry) {
                Text("立即重试")
            }
        }
    }
}
