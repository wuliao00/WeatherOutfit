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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jianyi.outfit.data.model.Gender
import com.jianyi.outfit.data.model.StylePreference
import com.jianyi.outfit.data.model.TempUnit
import com.jianyi.outfit.data.model.ToleranceLevel
import com.jianyi.outfit.data.model.WindUnit
import com.jianyi.outfit.di.AppViewModelProvider
import com.jianyi.outfit.ui.components.SectionCard

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
                            subtitle = "每天早上 8:00 推送当日穿搭建议",
                            checked = state.prefs.dailyPushEnabled,
                            onCheckedChange = ::onDailyPushToggle
                        )
                        SwitchRow(
                            title = "极端天气预警",
                            subtitle = "收到暴雨、高温等预警时立即提醒",
                            checked = state.prefs.extremeAlertEnabled,
                            onCheckedChange = viewModel::setExtremeAlert
                        )
                    }
                }
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
