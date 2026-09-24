package com.jianyi.outfit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 可复用 UI 组件：指标条目、分节卡片、空态、错误面板。
 * 布局遵循留白规则：卡片内边距 24dp。
 */

/** 首屏指标条目（体感温度 / 湿度 / 风力 / 紫外线） */
@Composable
fun MetricChip(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 分节卡片：统一圆角 16dp、色调海拔（比背景浅 5%，无重阴影） */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    tonalElevation: androidx.compose.ui.unit.Dp = 1.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = tonalElevation
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

/** 列表为空时的占位提示 */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

/** 全屏加载中 */
@Composable
fun LoadingPanel(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

/** 加载失败面板：错误信息 + 重试按钮 */
@Composable
fun ErrorPanel(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}
