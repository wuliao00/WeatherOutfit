@file:OptIn(dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi::class)

package com.jianyi.outfit.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jianyi.outfit.data.model.GlassQuality
import com.jianyi.outfit.ui.motion.pressFollow
import com.jianyi.outfit.ui.theme.GlassBorderDark
import com.jianyi.outfit.ui.theme.GlassBorderLight
import com.jianyi.outfit.ui.theme.GlassHighlight
import com.jianyi.outfit.ui.theme.GlassShadow
import com.jianyi.outfit.ui.theme.GlassTintDark
import com.jianyi.outfit.ui.theme.GlassTintLight
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * 液态玻璃层。
 *
 * 只有一个模糊源：整屏风景图。
 * 之所以不给「滚动内容」再开一个源——顶栏和卡片都在源的内部，会形成自采样递归，
 * 每帧要重糊的面积成倍增长；而单一源在 Haze 里会被缓存成同一张模糊纹理，
 * 所有玻璃共用，模糊成本与玻璃数量无关。
 * 卡片滚到顶栏底下时靠顶栏那层随滚动加深的磨砂来压住，观感与 iOS 的导航条一致。
 */

/**
 * 玻璃角色：不决定采样哪个源，只决定「这一档性能策略下允不允许实时模糊」。
 */
enum class GlassRole {
    /** 浮在风景上的卡片：数量多，只有 REALTIME 档才做实时模糊 */
    CARD,

    /** 顶栏 / 底部浮层：面积小且只有一个，BALANCED 档也保留实时模糊 */
    BAR
}

/** 玻璃圆角体系：统一大圆角，配合高光边才有吹制玻璃的观感 */
object GlassShapes {
    val card: Shape = RoundedCornerShape(28.dp)
    val inner: Shape = RoundedCornerShape(18.dp)
    val chip: Shape = RoundedCornerShape(16.dp)
    val bar: Shape = RoundedCornerShape(26.dp)
    val sheet: Shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    val circle: Shape = CircleShape
}

/** 玻璃浓度：tintAlpha 越小越透 */
@Immutable
enum class GlassEmphasis(
    val tintAlpha: Float,
    val borderAlpha: Float,
    val elevation: Dp
) {
    ULTRA_THIN(0.30f, 0.42f, 3.dp),
    THIN(0.42f, 0.50f, 5.dp),
    REGULAR(0.56f, 0.62f, 8.dp),
    THICK(0.72f, 0.72f, 12.dp)
}

/**
 * 玻璃宿主：持有唯一的模糊源与当前档位，由根节点创建并经 [LocalGlassHost] 下发。
 */
@Stable
class GlassHost(
    val sourceState: HazeState,
    val quality: GlassQuality
) {
    /** 该角色在当前档位下是否走实时模糊；false 表示退回静态磨砂 */
    fun realtimeFor(role: GlassRole): Boolean = when (role) {
        GlassRole.CARD -> quality == GlassQuality.REALTIME
        GlassRole.BAR -> quality != GlassQuality.PERFORMANCE
    }
}

val LocalGlassHost = compositionLocalOf<GlassHost?> { null }

@Composable
fun rememberGlassHost(quality: GlassQuality): GlassHost = remember(quality) {
    GlassHost(HazeState(), quality)
}

/**
 * 把风景层登记为唯一的模糊源。
 *
 * PERFORMANCE 档下没有任何组件会去采样它，此时挂源等于白白让系统
 * 多维护一份全屏离屏缓冲 —— 所以直接不挂。
 */
fun Modifier.glassSource(host: GlassHost?): Modifier =
    if (host == null || host.quality == GlassQuality.PERFORMANCE) this
    else hazeSource(host.sourceState)

/* ============ 材质画笔 ============ */

/** 上缘高光：顶部最亮、往下迅速衰减，底部再回一点，模拟玻璃厚度带来的二次折射 */
private fun highlightBrush(dark: Boolean): Brush = Brush.verticalGradient(
    0f to GlassHighlight.copy(alpha = if (dark) 0.28f else 0.52f),
    0.16f to GlassHighlight.copy(alpha = if (dark) 0.05f else 0.10f),
    0.72f to Color.Transparent,
    1f to GlassHighlight.copy(alpha = if (dark) 0.10f else 0.05f)
)

/** 静态磨砂底：不做实时模糊时靠它撑起体积感 */
private fun frostBrush(dark: Boolean, emphasis: GlassEmphasis): Brush {
    val tint = if (dark) GlassTintDark else GlassTintLight
    return Brush.verticalGradient(
        listOf(
            tint.copy(alpha = (emphasis.tintAlpha + 0.06f).coerceAtMost(0.95f)),
            tint.copy(alpha = (emphasis.tintAlpha - 0.10f).coerceAtLeast(0.12f))
        )
    )
}

private fun borderBrush(dark: Boolean, emphasis: GlassEmphasis): Brush {
    val base = if (dark) GlassBorderDark else GlassBorderLight
    val a = emphasis.borderAlpha
    return Brush.verticalGradient(
        listOf(base.copy(alpha = a), base.copy(alpha = a * 0.28f), base.copy(alpha = a * 0.62f))
    )
}

/* ============ 组件 ============ */

/**
 * 玻璃材质修饰符版：需要在 Box 里自由分层（例如顶栏只让背景渐显、文字保持全亮）
 * 时用这个，而不是整个 GlassSurface。
 */
@Composable
fun Modifier.glassMaterial(
    shape: Shape = GlassShapes.card,
    emphasis: GlassEmphasis = GlassEmphasis.REGULAR,
    role: GlassRole = GlassRole.CARD,
    dark: Boolean = false
): Modifier {
    val host = LocalGlassHost.current
    val realtime = host?.realtimeFor(role) == true
    return this
        .shadow(
            elevation = emphasis.elevation,
            shape = shape,
            clip = false,
            ambientColor = GlassShadow,
            spotColor = GlassShadow
        )
        .clip(shape)
        .then(
            if (realtime && host != null) {
                Modifier.hazeEffect(state = host.sourceState, style = HazeMaterials.thick())
            } else {
                Modifier.background(frostBrush(dark, emphasis), shape)
            }
        )
        .then(
            if (realtime) Modifier.background(frostBrush(dark, emphasis), shape) else Modifier
        )
        .background(highlightBrush(dark), shape)
        .border(1.dp, borderBrush(dark, emphasis), shape)
}

/**
 * 玻璃面板：所有「浮在风景上的卡片」都走它，改一处即可全局调整质感。
 *
 * 内容槽是 **ColumnScope** 而不是 BoxScope —— 玻璃卡片里放的都是纵向堆的
 * 标题 / 正文 / 提醒行，用 BoxScope 的话多个兄弟节点会全部叠在左上角互相压字。
 * 真机上就是靠截图才发现的这个问题，别改回 BoxScope。
 *
 * 修饰符顺序是有讲究的：shadow 必须在 clip 之前（阴影要落在形状外），
 * 模糊 / 底色必须在 clip 之后（不能糊出圆角外），高光与描边最后压顶。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShapes.card,
    emphasis: GlassEmphasis = GlassEmphasis.REGULAR,
    role: GlassRole = GlassRole.CARD,
    dark: Boolean = false,
    alpha: Float = 1f,
    scrim: Color = Color.Unspecified,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    bouncy: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val press = interactionSource ?: remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .then(
                if (bouncy && (onClick != null || onLongClick != null)) {
                    Modifier.pressFollow(interactionSource = press)
                } else Modifier
            )
            .glassMaterial(
                shape = shape,
                emphasis = emphasis,
                role = role,
                dark = dark
            )
            .then(if (scrim != Color.Unspecified) Modifier.background(scrim, shape) else Modifier)
            .then(
                when {
                    onClick != null && onLongClick != null -> Modifier.combinedClickable(
                        interactionSource = press,
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                    onClick != null -> Modifier.clickable(
                        interactionSource = press,
                        indication = null,
                        onClick = onClick
                    )
                    onLongClick != null -> Modifier.combinedClickable(
                        interactionSource = press,
                        indication = null,
                        onClick = {},
                        onLongClick = onLongClick
                    )
                    else -> Modifier
                }
            )
            .padding(contentPadding)
            .graphicsLayerAlpha(alpha),
        verticalArrangement = verticalArrangement,
        content = content
    )
}

/** 整体透明度：放在链尾，只影响这一层玻璃自身 */
private fun Modifier.graphicsLayerAlpha(alpha: Float): Modifier =
    if (alpha >= 0.999f) this else graphicsLayer { this.alpha = alpha }

/** 圆形玻璃图标按钮：顶栏、悬浮操作 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    tint: Color = Color.Unspecified,
    dark: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .shadow(6.dp, CircleShape, clip = false, ambientColor = GlassShadow, spotColor = GlassShadow)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        (if (dark) GlassTintDark else GlassTintLight).copy(alpha = 0.52f),
                        (if (dark) GlassTintDark else GlassTintLight).copy(alpha = 0.30f)
                    )
                )
            )
            .border(1.dp, borderBrush(dark, GlassEmphasis.THIN), CircleShape)
            .pressFollow(pressedScale = 0.9f, interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.46f)
        )
    }
}

/** 玻璃胶囊：指标、标签、图例 */
@Composable
fun GlassPill(
    label: String,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    contentColor: Color = Color.Unspecified,
    shape: Shape = GlassShapes.chip,
    onClick: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(shape)
            .background(frostBrush(dark, GlassEmphasis.THIN), shape)
            .border(1.dp, borderBrush(dark, GlassEmphasis.THIN), shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .pressFollow(pressedScale = 0.94f, interactionSource = interaction)
                        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text = label, color = contentColor)
    }
}

/** 玻璃内部分隔线：两端淡出，避免在透明底上出现硬切边 */
@Composable
fun GlassDivider(modifier: Modifier = Modifier, dark: Boolean = false) {
    val base = if (dark) GlassBorderDark else GlassBorderLight
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(base.copy(alpha = 0f), base, base.copy(alpha = 0f))
                )
            )
    )
}
