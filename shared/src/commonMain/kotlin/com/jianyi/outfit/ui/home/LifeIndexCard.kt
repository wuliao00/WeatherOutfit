package com.jianyi.outfit.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jianyi.outfit.data.model.LifeIndex
import com.jianyi.outfit.ui.theme.HighTempOrange
import com.jianyi.outfit.ui.theme.LowTempBlue
import com.jianyi.outfit.ui.theme.MotionSpecs
import kotlinx.coroutines.delay

/**
 * 生活指数列表。
 *
 * 每项用 5 个点表示适宜度，而不是画一根进度条：
 * 指数本身是分级结论，用连续条会暗示「3.4 分」这种并不存在的精度。
 */
@Composable
fun LifeIndexList(
    indices: List<LifeIndex>,
    modifier: Modifier = Modifier,
    dark: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        indices.forEach { index ->
            LifeIndexRow(index = index, dark = dark)
        }
    }
}

@Composable
private fun LifeIndexRow(index: LifeIndex, dark: Boolean) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = index.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            ScoreDots(score = index.score, dark = dark)
            Spacer(Modifier.weight(1f))
            Text(
                text = index.level,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    index.score >= 4 -> if (dark) LowTempBlue else MaterialTheme.colorScheme.primary
                    index.score <= 2 -> HighTempOrange
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Text(
            text = index.advice,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

/** 五个点：前 score 个实心，其余半透明。逐个错开点亮，视线会自然跟着数过去 */
@Composable
private fun ScoreDots(score: Int, dark: Boolean) {
    val color = if (dark) LowTempBlue else MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..5).forEach { i ->
            val on = i <= score
            val t = remember { Animatable(0f) }
            LaunchedEffect(on) {
                // 整体不到 130ms：快到人不会觉得「卡」，但能感觉到是一串而不是同时炸开
                delay((i - 1) * 26L)
                t.animateTo(if (on) 1f else 0f, MotionSpecs.follow())
            }
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        color.copy(alpha = if (on) 0.35f + 0.6f * t.value else 0.18f)
                    )
            )
        }
    }
}

/** 「本地估算」角标：这些指数不是接口给的，必须说清楚 */
@Composable
fun EstimatedBadge(modifier: Modifier = Modifier) {
    Text(
        text = "本地估算",
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}
