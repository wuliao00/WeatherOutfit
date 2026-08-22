package com.jianyi.outfit.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jianyi.outfit.data.model.CustomOutfitTemplate
import com.jianyi.outfit.data.model.OutfitPlan
import com.jianyi.outfit.di.AppViewModelProvider
import com.jianyi.outfit.engine.OutfitRecommendationEngine
import com.jianyi.outfit.ui.components.EmptyHint

/**
 * 穿搭详情页：按场景（通勤 / 户外 / 休闲）展示方案，
 * 支持勾选已拥有单品、管理自定义穿搭模板（新增 / 左滑删除）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitDetailScreen(
    onBack: () -> Unit,
    viewModel: OutfitDetailViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("穿搭方案", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                start = 20.dp,
                end = 20.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val recommendation = state.recommendation
            if (recommendation == null) {
                item { EmptyHint("暂无推荐，请先返回首页加载天气数据") }
            } else {
                item {
                    ScenePlansSection(
                        recommendation = recommendation,
                        temperatureText = state.weather?.let {
                            com.jianyi.outfit.util.Formatters.temp(it.temperature, state.prefs.tempUnit)
                        } ?: ""
                    )
                }
            }

            // ===== 我的模板 =====
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "我的模板",
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("自定义方案")
                    }
                }
            }
            if (state.templates.isEmpty()) {
                item { EmptyHint("暂无自定义模板，长按首页推荐卡片或点击“自定义方案”新建") }
            } else {
                items(state.templates, key = { it.id }) { template ->
                    TemplateItem(
                        template = template,
                        onDelete = { viewModel.deleteTemplate(template.id) }
                    )
                }
            }
        }
    }

    // 新增自定义模板对话框
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

/** 场景方案区：场景切换 + 单品清单（可勾选） + 小贴士 */
@Composable
private fun ScenePlansSection(
    recommendation: com.jianyi.outfit.data.model.OutfitRecommendation,
    temperatureText: String
) {
    val plans = recommendation.plans
    var selectedScene by rememberSaveable(plans.firstOrNull()?.scene) {
        mutableStateOf(plans.firstOrNull()?.scene ?: OutfitRecommendationEngine.SCENE_COMMUTE)
    }
    val selectedPlan = plans.firstOrNull { it.scene == selectedScene } ?: plans.first()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 场景切换
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                plans.forEach { plan ->
                    FilterChip(
                        selected = plan.scene == selectedScene,
                        onClick = { selectedScene = plan.scene },
                        label = { Text(plan.scene) },
                        leadingIcon = {
                            Icon(
                                imageVector = sceneIcon(plan.scene),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            // 场景信息
            Text(
                text = "${selectedPlan.scene} · 适配 ${selectedPlan.tempRange} · 当前 $temperatureText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 穿搭清单（可勾选已拥有单品）
            ChecklistPanel(plan = selectedPlan)

            // 小贴士
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "小贴士：${selectedPlan.tip}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }

            // 出行提醒
            if (recommendation.reminders.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "出行提醒",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    recommendation.reminders.take(4).forEach { reminder ->
                        Text(
                            text = "· $reminder",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 单品清单：勾选状态仅在当前会话内记忆 */
@Composable
private fun ChecklistPanel(plan: OutfitPlan) {
    val checked = remember(plan.scene) { mutableStateMapOf<String, Boolean>() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("穿搭清单（勾选已备好）", style = MaterialTheme.typography.titleMedium)
        plan.items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Checkbox(
                    checked = checked[item] == true,
                    onCheckedChange = { checked[item] = it }
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (checked[item] == true) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

/** 模板条目：左滑删除 */
@Composable
private fun TemplateItem(template: CustomOutfitTemplate, onDelete: () -> Unit) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(template.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = template.scene,
                        style = MaterialTheme.typography.bodyMedium,
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 新增自定义模板对话框 */
@Composable
private fun AddTemplateDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String, scene: String,
        minTemp: Int, maxTemp: Int,
        items: List<String>, tip: String
    ) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var scene by rememberSaveable { mutableStateOf(OutfitRecommendationEngine.SCENE_COMMUTE) }
    var minTemp by rememberSaveable { mutableStateOf("10") }
    var maxTemp by rememberSaveable { mutableStateOf("25") }
    var itemsText by rememberSaveable { mutableStateOf("") }
    var tip by rememberSaveable { mutableStateOf("") }

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
                        FilterChip(
                            selected = scene == option,
                            onClick = { scene = option },
                            label = { Text(option) }
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
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 场景对应图标 */
private fun sceneIcon(scene: String): ImageVector = when (scene) {
    OutfitRecommendationEngine.SCENE_COMMUTE -> Icons.Filled.BusinessCenter
    OutfitRecommendationEngine.SCENE_OUTDOOR -> Icons.Filled.Hiking
    else -> Icons.Filled.Weekend
}
