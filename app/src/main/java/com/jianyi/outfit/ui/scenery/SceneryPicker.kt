package com.jianyi.outfit.ui.scenery

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jianyi.outfit.ui.glass.GlassShapes

/**
 * 换景选择器。
 *
 * 缩略图直接用原图裁切（painterResource 复用同一份位图缓存），
 * 不额外做小图：8 张图共用解码结果，比再放一套 240px 缩略图更省包体。
 */

@Composable
fun SceneryGrid(
    current: Scenery,
    onSelect: (Scenery) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.height(360.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = true
    ) {
        items(Scenery.ordered, key = { it.key }) { scenery ->
            SceneryTile(
                scenery = scenery,
                selected = scenery == current,
                onClick = { onSelect(scenery) }
            )
        }
    }
}

/**
 * 横向胶片条。
 *
 * 设置页外层已经是竖向滚动的 Column，再嵌一个可竖滚的网格会出现
 * 「到底谁该滚」的争抢；横向列表与竖向父容器方向正交，天然不打架。
 */
@Composable
fun SceneryStrip(
    current: Scenery,
    onSelect: (Scenery) -> Unit,
    modifier: Modifier = Modifier
) {
    // 只有 8 张，用 horizontalScroll + Row 就够；
    // 换 LazyRow 还得给它定高，反而在竖向父容器里更难交代滚动归属
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Scenery.ordered.forEach { scenery ->
            Box(Modifier.width(136.dp)) {
                SceneryTile(
                    scenery = scenery,
                    selected = scenery == current,
                    onClick = { onSelect(scenery) }
                )
            }
        }
    }
}

@Composable
private fun SceneryTile(
    scenery: Scenery,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    // 选中态用一个可动画的 0/1 值同时驱动描边、缩放和勾标透明度，
    // 三处共享同一个进度，切换时不会出现「描边先到、勾标后到」的散乱感
    val t = remember { Animatable(if (selected) 1f else 0f) }
    LaunchedEffect(selected) {
        t.animateTo(if (selected) 1f else 0f, com.jianyi.outfit.ui.theme.MotionSpecs.reveal())
    }

    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = 1f - 0.04f * (1f - t.value)
                scaleY = 1f - 0.04f * (1f - t.value)
            }
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(shape)
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.35f + 0.5f * t.value),
                    shape = shape
                )
        ) {
            Image(
                painter = painterResource(scenery.resId),
                contentDescription = scenery.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 底部压一条渐变，保证白字标签在雪原这类亮图上也读得清
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f)
                        )
                    )
            )
            Text(
                text = scenery.label,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            )
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .graphicsLayer {
                        alpha = t.value
                        scaleX = 0.6f + 0.4f * t.value
                        scaleY = 0.6f + 0.4f * t.value
                    }
            )
        }
        Text(
            text = scenery.poetic,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp)
        )
    }
}

/** 底部玻璃抽屉：首页长按/点按「换景」时弹出 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneryPickerSheet(
    current: Scenery,
    reason: String,
    onSelect: (Scenery) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        shape = GlassShapes.sheet
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(top = 18.dp, bottom = 26.dp, start = 20.dp, end = 20.dp)
        ) {
            Column {
                // 抓取条：提示这块面板可以往下拖关闭
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 38.dp, height = 4.dp)
                        .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "切换背景",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "当前：${current.label}",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Text(
                    text = reason,
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                SceneryGrid(
                    current = current,
                    onSelect = { picked ->
                        // 选中即收起：背景切换动画本身就是反馈，不需要再弹提示
                        onSelect(picked)
                        onDismiss()
                    },
                    columns = 2
                )
            }
        }
    }
}
