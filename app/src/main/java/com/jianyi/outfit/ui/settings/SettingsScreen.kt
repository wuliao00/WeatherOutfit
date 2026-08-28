package com.jianyi.outfit.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jianyi.outfit.data.model.Gender
import com.jianyi.outfit.data.model.StylePreference
import com.jianyi.outfit.data.model.TempUnit
import com.jianyi.outfit.data.model.ToleranceLevel
import com.jianyi.outfit.data.model.WindUnit
import com.jianyi.outfit.data.repository.ApiCredentials
import com.jianyi.outfit.di.AppViewModelProvider
import com.jianyi.outfit.ui.components.SectionCard

/** 可选的每日推送时刻（点整），与推送时间选择器一一对应 */
private val PUSH_HOUR_OPTIONS = listOf(6, 7, 8, 9)

/**
 * 设置页：穿搭偏好 / 单位设置 / 通知设置。
 * Android 13+ 开启推送时先申请通知权限。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 一次性提示（如「已重置使用须知」）以 Snackbar 呈现
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // 通知权限（Android 13+）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.setDailyPush(granted) }

    fun onDailyPushToggle(enabled: Boolean) {
        when {
            // 关闭直接生效
            !enabled -> viewModel.setDailyPush(false)
            // Android 13+ 先检查通知权限
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> viewModel.setDailyPush(true)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.headlineMedium) },
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
            // ===== 穿搭偏好 =====
            item {
                SectionCard(title = "穿搭偏好") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        PreferenceRow(label = "耐寒耐热程度") {
                            SegmentedOptions(
                                options = ToleranceLevel.entries.map { it.label },
                                selectedIndex = ToleranceLevel.entries
                                    .indexOf(state.prefs.coldHeatTolerance),
                                onSelect = {
                                    viewModel.setTolerance(ToleranceLevel.entries[it])
                                }
                            )
                        }
                        PreferenceRow(label = "常用风格") {
                            SegmentedOptions(
                                options = StylePreference.entries.map { it.label },
                                selectedIndex = StylePreference.entries.indexOf(state.prefs.style),
                                onSelect = { viewModel.setStyle(StylePreference.entries[it]) }
                            )
                        }
                        PreferenceRow(label = "性别") {
                            SegmentedOptions(
                                options = Gender.entries.map { it.label },
                                selectedIndex = Gender.entries.indexOf(state.prefs.gender),
                                onSelect = { viewModel.setGender(Gender.entries[it]) }
                            )
                        }
                    }
                }
            }

            // ===== 单位设置 =====
            item {
                SectionCard(title = "单位设置") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        PreferenceRow(label = "温度单位") {
                            SegmentedOptions(
                                options = listOf("℃", "℉"),
                                selectedIndex = TempUnit.entries.indexOf(state.prefs.tempUnit),
                                onSelect = { viewModel.setTempUnit(TempUnit.entries[it]) }
                            )
                        }
                        PreferenceRow(label = "风力单位") {
                            SegmentedOptions(
                                options = listOf("级", "m/s"),
                                selectedIndex = WindUnit.entries.indexOf(state.prefs.windUnit),
                                onSelect = { viewModel.setWindUnit(WindUnit.entries[it]) }
                            )
                        }
                    }
                }
            }

            // ===== 通知设置 =====
            item {
                SectionCard(title = "通知设置") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SwitchRow(
                            title = "每日穿搭推送",
                            subtitle = "按下方设定的推送时间每天提醒一次（默认 8:00）",
                            checked = state.prefs.dailyPushEnabled,
                            onCheckedChange = ::onDailyPushToggle
                        )
                        PreferenceRow(label = "推送时间") {
                            SegmentedOptions(
                                options = PUSH_HOUR_OPTIONS.map { "${it}:00" },
                                selectedIndex =
                                    (state.prefs.dailyPushHour - PUSH_HOUR_OPTIONS.first())
                                        .coerceIn(0, PUSH_HOUR_OPTIONS.lastIndex),
                                onSelect = { viewModel.setDailyPushHour(PUSH_HOUR_OPTIONS[it]) }
                            )
                        }
                        SwitchRow(
                            title = "极端天气预警",
                            subtitle = "收到暴雨、高温等预警时立即提醒",
                            checked = state.prefs.extremeAlertEnabled,
                            onCheckedChange = viewModel::setExtremeAlert
                        )
                    }
                }
            }

            // ===== 接口凭证：用户自填，留空回退内置默认 =====
            item {
                ApiCredentialsSection(
                    credentials = state.apiCredentials,
                    onSave = viewModel::saveApiCredentials
                )
            }

            // ===== 关于：重新查看使用须知 =====
            item {
                TextButton(
                    onClick = viewModel::resetDisclaimer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "重新查看使用须知",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 接口凭证表单：自填 appid / appkey / apiurl 覆盖内置公共凭证。
 * 留空表示回退默认；本地输入态仅首次加载时初始化，避免覆盖用户输入。
 */
@Composable
private fun ApiCredentialsSection(
    credentials: ApiCredentials?,
    onSave: (id: String, key: String, apiUrl: String) -> Unit
) {
    var idText by rememberSaveable { mutableStateOf("") }
    var keyText by rememberSaveable { mutableStateOf("") }
    var urlText by rememberSaveable { mutableStateOf("") }
    var initialized by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(credentials) {
        if (credentials != null && !initialized) {
            initialized = true
            idText = credentials.id
            keyText = credentials.key
            urlText = credentials.apiUrl
        }
    }

    SectionCard(title = "接口凭证（可选）") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "留空使用内置公共测试凭证（全站共享频次）；" +
                    "在 apihz.cn 注册后自填个人 id / key 可获独享频次。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = idText,
                onValueChange = { idText = it },
                singleLine = true,
                label = { Text("appid (id)") },
                placeholder = { Text("默认内置") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = keyText,
                onValueChange = { keyText = it },
                singleLine = true,
                label = { Text("appkey (key)") },
                placeholder = { Text("默认内置") }
            )
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                singleLine = true,
                label = { Text("接口地址 (apiurl)") },
                placeholder = { Text("https://cn.apihz.cn/") }
            )
            TextButton(
                onClick = { onSave(idText, keyText, urlText) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存凭证")
            }
        }
    }
}

/** 偏好行：左标签 + 右侧选项控件 */
@Composable
private fun PreferenceRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

/** 单选分段按钮组 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentedOptions(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size)
            ) {
                Text(option)
            }
        }
    }
}

/** 开关行 */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
