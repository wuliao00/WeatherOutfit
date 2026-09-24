package com.jianyi.outfit.ui.city

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jianyi.outfit.data.LocalAppDependencies
import com.jianyi.outfit.data.local.entity.CityEntity
import com.jianyi.outfit.ui.components.EmptyHint
import com.jianyi.outfit.ui.glass.GlassEmphasis
import com.jianyi.outfit.ui.glass.GlassIconButton
import com.jianyi.outfit.ui.glass.GlassRole
import com.jianyi.outfit.ui.glass.GlassShapes
import com.jianyi.outfit.ui.glass.GlassSurface
import com.jianyi.outfit.ui.glass.glassMaterial
import com.jianyi.outfit.ui.theme.LocalScenery

/**
 * 城市管理页：省份 + 城市搜索、GPS 定位、历史城市快速切换（左滑删除）。
 *
 * viewModel 由调用方注入（app 侧的 NavHost 用 AppViewModelProvider.Factory 拿）。
 * 页面自己不再 viewModel()：那个组合期工厂在 CMP 里没有对应实现，
 * 而且"页面从哪拿 VM"本来就该是导航层的事。
 * 状态收集用 collectAsState()：collectAsStateWithLifecycle 没有 iOS 产物。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityScreen(
    onBack: () -> Unit,
    viewModel: CityViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val deps = LocalAppDependencies.current
    val scenery = LocalScenery.current
    val dark = scenery.dark || isSystemInDarkTheme()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.finished) { if (state.finished) onBack() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    // GPS 定位：已授权就直接用，未授权才弹窗，全部逻辑在能力接口里
    fun requestGps() {
        deps.locationProvider.requestPermission { granted ->
            if (granted) viewModel.locateByGps() else viewModel.onLocationPermissionDenied()
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

            // ===== 搜索区 =====
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                emphasis = GlassEmphasis.REGULAR,
                role = GlassRole.CARD,
                dark = dark,
                contentPadding = PaddingValues(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProvinceDropdown(selected = state.province, onSelect = viewModel::onProvinceChange)
                    OutlinedTextField(
                        value = state.cityInput,
                        onValueChange = viewModel::onCityInputChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("城市 / 区县（如：成都、朝阳区）") },
                        colors = glassFieldColors()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = viewModel::search,
                            enabled = !state.searching,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.searching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(Modifier.width(7.dp))
                            Text(if (state.searching) "查询中…" else "搜索并切换")
                        }
                        GlassPillButton(
                            icon = Icons.Filled.GpsFixed,
                            text = if (state.locating) "定位中…" else "GPS 定位",
                            enabled = !state.locating,
                            dark = dark,
                            onClick = { requestGps() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ===== 历史城市 =====
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "历史城市",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "左滑删除",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.savedCities.isEmpty()) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    emphasis = GlassEmphasis.ULTRA_THIN,
                    dark = dark,
                    contentPadding = PaddingValues(20.dp)
                ) {
                    EmptyHint("暂无历史城市，搜索或定位后自动保存")
                }
            } else {
                state.savedCities.forEach { city ->
                    SavedCityItem(
                        city = city,
                        isCurrent = city.id == state.currentCity?.id,
                        dark = dark,
                        onClick = { viewModel.selectCity(city) },
                        onDelete = { viewModel.deleteCity(city) }
                    )
                }
            }

            Spacer(Modifier.height(28.dp).navigationBarsPadding())
        }

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
                        dark = dark,
                        bodyAlpha = 0.72f
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
                    text = "城市管理",
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

/** 输入框在玻璃上要透明底，否则会露出一块不透明的白矩形 */
@Composable
private fun glassFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProvinceDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            readOnly = true,
            singleLine = true,
            label = { Text("省份") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = glassFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.98f)
        ) {
            CityViewModel.PROVINCES.forEach { province ->
                DropdownMenuItem(
                    text = { Text(province) },
                    onClick = {
                        onSelect(province)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** 玻璃次级按钮 */
@Composable
private fun GlassPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    enabled: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .glassMaterial(
                shape = RoundedCornerShape(16.dp),
                emphasis = GlassEmphasis.THIN,
                role = GlassRole.CARD,
                dark = dark
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** 历史城市条目：点击切换，左滑删除 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedCityItem(
    city: CityEntity,
    isCurrent: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
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
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 24.dp)
                )
            }
        }
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            shape = GlassShapes.inner,
            emphasis = GlassEmphasis.THIN,
            dark = dark,
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = city.city,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = buildString {
                            append(city.province)
                            append(" · ")
                            append(
                                when (city.source) {
                                    "gps" -> "GPS 定位"
                                    "ip" -> "IP 定位"
                                    else -> "搜索"
                                }
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isCurrent) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "当前城市",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
