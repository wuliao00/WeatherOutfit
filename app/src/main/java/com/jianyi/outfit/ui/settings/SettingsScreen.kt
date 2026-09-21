package com.jianyi.outfit.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jianyi.outfit.data.model.Gender
import com.jianyi.outfit.data.model.GlassQuality
import com.jianyi.outfit.data.model.SceneryMode
import com.jianyi.outfit.data.model.StylePreference
import com.jianyi.outfit.data.model.TempUnit
import com.jianyi.outfit.data.model.ToleranceLevel
import com.jianyi.outfit.data.model.WindUnit
import com.jianyi.outfit.di.AppViewModelProvider
import com.jianyi.outfit.ui.glass.GlassEmphasis
import com.jianyi.outfit.ui.glass.GlassIconButton
import com.jianyi.outfit.ui.glass.GlassRole
import com.jianyi.outfit.ui.glass.GlassShapes
import com.jianyi.outfit.ui.glass.GlassSurface
import com.jianyi.outfit.ui.glass.glassMaterial
import com.jianyi.outfit.ui.theme.MotionSpecs
import com.jianyi.outfit.ui.scenery.LocalSceneryController
import com.jianyi.outfit.ui.scenery.SceneryGrid
import com.jianyi.outfit.ui.scenery.SceneryStrip
import com.jianyi.outfit.ui.theme.LocalScenery
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build

/**
 * 设置页。
 *
 * 「背景与动效」「玻璃与性能」放在最前面：这两组直接决定用户看到的观感和
 * 手机跑不跑得动，比穿搭偏好更值得先被看到。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val controller = LocalSceneryController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scenery = LocalScenery.current
    val dark = scenery.dark || isSystemInDarkTheme()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.setDailyPush(granted) }

    fun onDailyPushToggle(enabled: Boolean) {
        when {
            !enabled -> viewModel.setDailyPush(false)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> viewModel.setDailyPush(true)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.statusBarsPadding().height(60.dp))

            // ===== 背景与动效 =====
            Section("背景与动效") {
                Text(
                    text = "选景方式",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Segments(
                    options = SceneryMode.entries.map { it.label },
                    selectedIndex = SceneryMode.entries.indexOf(state.prefs.sceneryMode),
                    dark = dark,
                    onSelect = { viewModel.setSceneryMode(SceneryMode.entries[it]) }
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = when (state.prefs.sceneryMode) {
                        SceneryMode.AUTO_WEATHER -> "跟随天气自动选景，也可以直接点一张固定下来"
                        SceneryMode.FIXED -> "固定使用下面选中的这一张"
                        SceneryMode.DAILY_ROTATE -> "每天自动轮换一张，与天气无关"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                // 固定模式用大图网格更直观；其他模式用胶片条省地方
                if (state.prefs.sceneryMode == SceneryMode.FIXED) {
                    SceneryGrid(
                        current = scenery,
                        onSelect = { picked ->
                            controller?.onUserSelect(picked)
                            viewModel.pinScenery(picked.key)
                        },
                        columns = 2,
                        modifier = Modifier.height(300.dp)
                    )
                } else {
                    SceneryStrip(
                        current = scenery,
                        onSelect = { picked ->
                            controller?.onUserSelect(picked)
                            viewModel.pinScenery(picked.key)
                        }
                    )
                }

                Spacer(Modifier.height(6.dp))
                SwitchRow(
                    title = "滚动视差",
                    subtitle = "背景随列表缓慢反向移动，产生远近层次",
                    checked = state.prefs.parallaxEnabled,
                    onCheckedChange = viewModel::setParallax,
                    dark = dark
                )
                SwitchRow(
                    title = "背景呼吸",
                    subtitle = "极缓慢的缩放漂移，画面更有空气感（略增功耗）",
                    checked = state.prefs.breathingEnabled,
                    onCheckedChange = viewModel::setBreathing,
                    dark = dark
                )
            }

            // ===== 玻璃与性能 =====
            Section("玻璃与性能") {
                Text(
                    text = "玻璃模糊档位",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Segments(
                    options = GlassQuality.entries.map { it.label },
                    selectedIndex = GlassQuality.entries.indexOf(state.prefs.glassQuality),
                    dark = dark,
                    onSelect = { viewModel.setGlassQuality(GlassQuality.entries[it]) }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = GlassQuality.entries[state.prefs.glassQuality.ordinal].desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                SwitchRow(
                    title = "高帧率渲染",
                    subtitle = "向系统请求以屏幕最高刷新率绘制动画（Android 12+ 生效，更费电）",
                    checked = state.prefs.highFrameRateEnabled,
                    onCheckedChange = viewModel::setHighFrameRate,
                    dark = dark
                )
            }

            // ===== 穿搭偏好 =====
            Section("穿搭偏好") {
                LabeledSegments(
                    label = "耐寒耐热程度",
                    options = ToleranceLevel.entries.map { it.label },
                    selectedIndex = ToleranceLevel.entries.indexOf(state.prefs.coldHeatTolerance),
                    dark = dark,
                    onSelect = { viewModel.setTolerance(ToleranceLevel.entries[it]) }
                )
                LabeledSegments(
                    label = "常用风格",
                    options = StylePreference.entries.map { it.label },
                    selectedIndex = StylePreference.entries.indexOf(state.prefs.style),
                    dark = dark,
                    onSelect = { viewModel.setStyle(StylePreference.entries[it]) }
                )
                LabeledSegments(
                    label = "性别",
                    options = Gender.entries.map { it.label },
                    selectedIndex = Gender.entries.indexOf(state.prefs.gender),
                    dark = dark,
                    onSelect = { viewModel.setGender(Gender.entries[it]) }
                )
            }

            // ===== 单位设置 =====
            Section("单位设置") {
                LabeledSegments(
                    label = "温度单位",
                    options = listOf("℃", "℉"),
                    selectedIndex = TempUnit.entries.indexOf(state.prefs.tempUnit),
                    dark = dark,
                    onSelect = { viewModel.setTempUnit(TempUnit.entries[it]) }
                )
                LabeledSegments(
                    label = "风力单位",
                    options = listOf("级", "m/s"),
                    selectedIndex = WindUnit.entries.indexOf(state.prefs.windUnit),
                    dark = dark,
                    onSelect = { viewModel.setWindUnit(WindUnit.entries[it]) }
                )
            }

            // ===== 通知设置 =====
            Section("通知设置") {
                SwitchRow(
                    title = "每日穿搭推送",
                    subtitle = "每天早上 8:00 推送当日穿搭建议",
                    checked = state.prefs.dailyPushEnabled,
                    onCheckedChange = ::onDailyPushToggle,
                    dark = dark
                )
                SwitchRow(
                    title = "极端天气预警",
                    subtitle = "收到暴雨、高温等预警时立即提醒",
                    checked = state.prefs.extremeAlertEnabled,
                    onCheckedChange = viewModel::setExtremeAlert,
                    dark = dark
                )
            }

            // ===== 关于 =====
            Section("关于") {
                TextButtonRow("重新查看使用须知", viewModel::resetDisclaimer)
                TextButtonRow("版本 2.0.0", null)
                Text(
                    text = "生活指数与紫外线等级由本地规则估算，不是官方发布值。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(28.dp).navigationBarsPadding())
        }

        // 悬浮玻璃顶栏
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .glassBar(dark)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                    dark = dark
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 10.dp)
        )
    }
}

/* ============ 局部组件 ============ */

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        emphasis = GlassEmphasis.REGULAR,
        role = GlassRole.CARD,
        dark = LocalScenery.current.dark || isSystemInDarkTheme(),
        contentPadding = PaddingValues(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            content()
        }
    }
}

@Composable
private fun LabeledSegments(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    dark: Boolean,
    onSelect: (Int) -> Unit
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Segments(options = options, selectedIndex = selectedIndex, dark = dark, onSelect = onSelect)
    }
}

/**
 * 自绘分段选择器。
 *
 * 没用 Material3 的 SingleChoiceSegmentedButtonRow，是因为它在半透明玻璃上
 * 分隔线会和填充色互相打架；这里改成「一块滑块 + 若干标签」，
 * 滑块位置由弹簧驱动连续位移 —— 切换时是滑过去的，不是跳过去的。
 */
@Composable
private fun Segments(
    options: List<String>,
    selectedIndex: Int,
    dark: Boolean,
    onSelect: (Int) -> Unit
) {
    var trackWidth by remember { mutableIntStateOf(0) }
    val slider = remember { Animatable(0f) }
    LaunchedEffect(selectedIndex, options.size) {
        slider.animateTo(selectedIndex.toFloat(), MotionSpecs.follow())
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { trackWidth = it.width }
    ) {
        if (trackWidth > 0) {
            val segPx = trackWidth / options.size.toFloat()
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(1f / options.size)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = slider.value * segPx }
                    .padding(3.dp)
                    .glassCard(dark)
            )
        }
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, option ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.titleSmall,
                        fontSize = 13.sp,
                        fontWeight = if (index == selectedIndex) FontWeight.Medium else FontWeight.Normal,
                        color = if (index == selectedIndex) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


/** 顶栏玻璃 */
@Composable
private fun Modifier.glassBar(dark: Boolean): Modifier = glassMaterial(
    shape = GlassShapes.bar,
    emphasis = GlassEmphasis.THIN,
    role = GlassRole.BAR,
    dark = dark
)

/** 卡片玻璃 */
@Composable
private fun Modifier.glassCard(dark: Boolean): Modifier = glassMaterial(
    shape = RoundedCornerShape(15.dp),
    emphasis = GlassEmphasis.ULTRA_THIN,
    role = GlassRole.CARD,
    dark = dark
)

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    dark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                // 轨道在半透明玻璃上要够重才看得出边界
                uncheckedTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun TextButtonRow(text: String, onClick: (() -> Unit)?) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = if (onClick != null) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 8.dp)
    )
}
