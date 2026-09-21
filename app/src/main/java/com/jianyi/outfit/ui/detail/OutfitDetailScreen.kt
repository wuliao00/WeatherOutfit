package com.jianyi.outfit.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jianyi.outfit.data.model.CustomOutfitTemplate
import com.jianyi.outfit.data.model.OutfitPlan
import com.jianyi.outfit.data.model.OutfitRecommendation
import com.jianyi.outfit.di.AppViewModelProvider
import com.jianyi.outfit.engine.OutfitRecommendationEngine
import com.jianyi.outfit.ui.components.EmptyHint
import com.jianyi.outfit.ui.glass.GlassEmphasis
import com.jianyi.outfit.ui.glass.GlassIconButton
import com.jianyi.outfit.ui.glass.GlassRole
import com.jianyi.outfit.ui.glass.GlassShapes
import com.jianyi.outfit.ui.glass.GlassSurface
import com.jianyi.outfit.ui.glass.glassMaterial
import com.jianyi.outfit.ui.theme.LocalScenery
import com.jianyi.outfit.ui.theme.MotionSpecs
import kotlinx.coroutines.launch

/**
 * 穿搭详情页。
 *
 * 三套场景用 HorizontalPager 而不是三个 Tab：
 * 手指拖动时页面本身跟手，指示器再读同一个 pagerState 的 offsetFraction，
 * 两者天然同步——不需要「点击后动画追上」这一步。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitDetailScreen(
    onBack: () -> Unit,
    viewModel: OutfitDetailViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(false)
    }
    val scenery = LocalScenery.current
    val dark = scenery.dark || isSystemInDarkTheme()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.statusBarsPadding().height(60.dp))

            state.recommendation?.let { rec ->
                ScenePager(
                    recommendation = rec,
                    temperatureText = state.weather?.let {
                        com.jianyi.outfit.util.Formatters.temp(it.temperature, state.prefs.tempUnit)
                    } ?: "",
                    dark = dark
                )
                Spacer(Modifier.height(14.dp))
            }

            TemplatesSection(
                templates = state.templates,
                dark = dark,
                onAdd = { showAddDialog = true },
                onDelete = viewModel::deleteTemplate
            )

            Spacer(Modifier.height(30.dp).navigationBarsPadding())
        }

        // 悬浮玻璃顶栏，与首页同一套语言
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .glassMaterial(
                        shape = GlassShapes.bar,
                        emphasis = GlassEmphasis.THIN,
                        role = GlassRole.BAR,
                        dark = dark
                    )
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
                    text = "穿搭方案",
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

    if (showAddDialog) {
        AddTemplateDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, scene, minTemp, maxTemp, items, tip ->
                viewModel.saveTemplate(name, scene, minTemp, maxTemp, items, tip)
                showAddDialog = false
            }
        )
    }
}

/* ============ 场景横滑 ============ */

@Composable
private fun ScenePager(
    recommendation: OutfitRecommendation,
    temperatureText: String,
    dark: Boolean
) {
    val plans = recommendation.plans
    if (plans.isEmpty()) return
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { plans.size })
    var trackWidthPx by remember { mutableIntStateOf(0) }

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        emphasis = GlassEmphasis.REGULAR,
        role = GlassRole.CARD,
        dark = dark,
        contentPadding = PaddingValues(18.dp)
    ) {
        Column {
            // ---- 分段选择器：滑块位置直接读 pager 的连续偏移 ----
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(1f / plans.size)
                        .fillMaxHeight()
                        .padding(3.dp)
                        .graphicsLayer {
                            if (trackWidthPx > 0) {
                                val seg = trackWidthPx / plans.size
                                translationX =
                                    (pagerState.currentPage + pagerState.currentPageOffsetFraction) * seg
                            }
                        }
                        .glassMaterial(
                            shape = RoundedCornerShape(15.dp),
                            emphasis = GlassEmphasis.THIN,
                            role = GlassRole.CARD,
                            dark = dark
                        )
                )
                Row(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { trackWidthPx = it.width }
                ) {
                    plans.forEachIndexed { index, plan ->
                        val selected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = sceneIcon(plan.scene),
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = plan.scene,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            /**
             * 页高取「各页实测高度的最大值」。
             *
             * 三套方案的单品数量不同（通勤 3 件、户外可能 6 件），写死高度会把
             * 内容较长的那页直接裁掉，真机上就出现过小贴士和下方提示文字叠在一起。
             * 用 onSizeChanged 收集每页高度再取最大值，页面之间切换时高度保持稳定，
             * 不会因为翻到短页就整个卡片缩一下。
             */
            var pageHeightPx by remember { mutableIntStateOf(0) }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        with(androidx.compose.ui.platform.LocalDensity.current) {
                            // 首帧还没测到高度时给一个够用的兜底，避免高度从 0 弹开
                            (if (pageHeightPx > 0) pageHeightPx else 320.dp.toPx().toInt()).toDp()
                        }
                    ),
                pageSpacing = 14.dp,
                beyondViewportPageCount = 1
            ) { page ->
                ScenePage(
                    plan = plans[page],
                    temperatureText = temperatureText,
                    dark = dark,
                    onHeightMeasured = { h -> if (h > pageHeightPx) pageHeightPx = h }
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "左右滑动切换场景",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (recommendation.reminders.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            emphasis = GlassEmphasis.THIN,
            role = GlassRole.CARD,
            dark = dark,
            contentPadding = PaddingValues(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "出行提醒",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                recommendation.reminders.take(4).forEach { reminder ->
                    Row {
                        Text(
                            text = "·",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = reminder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenePage(
    plan: OutfitPlan,
    temperatureText: String,
    dark: Boolean,
    onHeightMeasured: (Int) -> Unit
) {
    /**
     * 这里必须是 fillMaxWidth 而不是 fillMaxSize。
     *
     * 页高由「各页实测高度的最大值」决定，如果页面又去填满容器，
     * 量到的高度就恒等于容器高度，永远长不起来 —— 布局反馈死循环。
     * 同理不能在这里再套一层 verticalScroll：外层页面本身已经可竖滚。
     */
    Column(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightMeasured(it.height) }
    ) {
        Text(
            text = "${plan.scene} · 适配 ${plan.tempRange}" +
                if (temperatureText.isNotBlank()) " · 当前 $temperatureText" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        ChecklistPanel(plan = plan)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(GlassShapes.inner)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(12.dp)
        ) {
            Text(
                text = "小贴士：${plan.tip}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** 单品清单：勾选状态仅在当前会话内记忆 */
@Composable
private fun ChecklistPanel(plan: OutfitPlan) {
    val checked = remember(plan.scene) { mutableStateMapOf<String, Boolean>() }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "穿搭清单（勾选已备好）",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        plan.items.forEach { item ->
            val on = checked[item] == true
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { checked[item] = !on }
                    .padding(vertical = 2.dp)
            ) {
                Checkbox(checked = on, onCheckedChange = { checked[item] = it })
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (on) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/* ============ 我的模板 ============ */

@Composable
private fun TemplatesSection(
    templates: List<CustomOutfitTemplate>,
    dark: Boolean,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "我的模板",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "自定义",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(2.dp))
            GlassIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "新建模板",
                onClick = onAdd,
                size = 34.dp,
                dark = dark
            )
        }
    }
    Spacer(Modifier.height(10.dp))

    if (templates.isEmpty()) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            emphasis = GlassEmphasis.ULTRA_THIN,
            dark = dark,
            contentPadding = PaddingValues(20.dp)
        ) {
            EmptyHint("还没有模板。长按首页穿搭卡片即可把今天的方案存下来。")
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            templates.forEach { template ->
                TemplateItem(
                    template = template,
                    dark = dark,
                    onDelete = { onDelete(template.id) }
                )
            }
        }
    }
}

/**
 * 模板条目：左滑删除。
 *
 * 手势直接交给 Material3 的 SwipeToDismissBox —— 它本身就是跟手的
 * （位移由手指驱动，不是「松手才开始动」），自己重写一遍只会更差。
 * 这里只把露出的底衬换成与主题一致的删除样式。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateItem(
    template: CustomOutfitTemplate,
    dark: Boolean,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 26.dp)
                )
            }
        }
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            emphasis = GlassEmphasis.THIN,
            dark = dark,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        template.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = template.scene,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "${template.tempRangeText} · ${template.items.joinToString("、")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (template.tip.isNotBlank()) {
                    Text(
                        text = template.tip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/* ============ 新增模板对话框 ============ */

@Composable
private fun AddTemplateDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String, scene: String,
        minTemp: Int, maxTemp: Int,
        items: List<String>, tip: String
    ) -> Unit
) {
    var name by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var scene by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(OutfitRecommendationEngine.SCENE_COMMUTE)
    }
    var minTemp by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("10") }
    var maxTemp by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("25") }
    var itemsText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var tip by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义穿搭方案") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("模板名称") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        OutfitRecommendationEngine.SCENE_COMMUTE,
                        OutfitRecommendationEngine.SCENE_OUTDOOR,
                        OutfitRecommendationEngine.SCENE_CASUAL
                    ).forEach { option ->
                        val selected = scene == option
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .clickable { scene = option }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minTemp,
                        onValueChange = { minTemp = it.filter(Char::isDigit).take(3) },
                        singleLine = true,
                        label = { Text("最低温℃") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxTemp,
                        onValueChange = { maxTemp = it.filter(Char::isDigit).take(3) },
                        singleLine = true,
                        label = { Text("最高温℃") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = itemsText,
                    onValueChange = { itemsText = it },
                    label = { Text("单品清单（用逗号分隔）") },
                    placeholder = { Text("卫衣、长裤、运动鞋") }
                )
                OutlinedTextField(
                    value = tip,
                    onValueChange = { tip = it },
                    label = { Text("搭配小贴士（可选）") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val items = itemsText.split("，", ",", "、")
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                    onConfirm(
                        name,
                        scene,
                        minTemp.toIntOrNull() ?: 10,
                        maxTemp.toIntOrNull() ?: 25,
                        items,
                        tip
                    )
                },
                enabled = name.isNotBlank() && itemsText.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 场景对应图标 */
private fun sceneIcon(scene: String): ImageVector = when (scene) {
    OutfitRecommendationEngine.SCENE_COMMUTE -> Icons.Filled.BusinessCenter
    OutfitRecommendationEngine.SCENE_OUTDOOR -> Icons.Filled.Hiking
    else -> Icons.Filled.Weekend
}
