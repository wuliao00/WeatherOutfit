package probe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * 这是简衣 App 里 ui/glass/Glass.kt 的一份**忠实但去 Android 化**的副本。
 *
 * 目的不是复用，而是让 iOS 编译器去撞一遍：把 App 真正用到的每个 Haze API
 * 都写出来（HazeStyle 的位置参数构造、HazeTint 接 Brush、hazeEffect 的 scope lambda、
 * inputScale、mask、hazeSource），只要 iOS 产物缺任何一个，这里就会编不过，
 * 而 CI 会直接指出是哪一行 —— 比读文档可靠。
 *
 * 与原版唯一的差别：风景取色的入参从 Scenery 换成了两个裸 Color
 * （App 里的 Scenery 带 @DrawableRes 与 R.drawable.*，那是 Android 资源系统专属，
 * 也正是迁移时真正要处理的地方，所以这里刻意不掩盖它）。
 */

/** 平台能力闸门：Android 靠 RenderEffect 的有无，iOS 由 actual 给值 */
expect val supportsRealtimeBlur: Boolean

enum class GlassRole { CARD, BAR }

object GlassShapes {
    val card: Shape = RoundedCornerShape(24.dp)
    val inner: Shape = RoundedCornerShape(16.dp)
    val chip: Shape = RoundedCornerShape(13.dp)
    val bar: Shape = RoundedCornerShape(22.dp)
    val circle: Shape = CircleShape
}

@Immutable
enum class GlassEmphasis(
    val blur: Dp,
    val frostAlpha: Float,
    val tintAlpha: Float,
    val rimAlpha: Float,
    val borderAlpha: Float,
    val elevation: Dp
) {
    ULTRA_THIN(18.dp, 0.34f, 0.07f, 0.16f, 0.10f, 2.dp),
    THIN(26.dp, 0.44f, 0.11f, 0.21f, 0.14f, 4.dp),
    REGULAR(38.dp, 0.56f, 0.17f, 0.27f, 0.18f, 7.dp),
    THICK(52.dp, 0.68f, 0.26f, 0.33f, 0.23f, 11.dp)
}

@Stable
class GlassHost(val sourceState: HazeState, val realtime: Boolean)

val LocalGlassHost = compositionLocalOf<GlassHost?> { null }

@Composable
fun rememberGlassHost(): GlassHost = remember { GlassHost(HazeState(), supportsRealtimeBlur) }

fun Modifier.glassSource(host: GlassHost?): Modifier =
    if (host == null || !host.realtime) this else hazeSource(host.sourceState)

@Immutable
private class Recipe(
    val style: HazeStyle?,
    val frost: Brush,
    val sheen: Brush,
    val rim: Brush,
    val rimWidth: Dp,
    val shadow: Color,
    val elevation: Dp
)

private fun recipeFor(
    emphasis: GlassEmphasis,
    dark: Boolean,
    realtime: Boolean,
    ground: Color,
    accent: Color,
    sky: Color
): Recipe {
    val base = if (dark) Color(0xFF090F16) else Color(0xFFFFFFFF)
    val top = lerp(base, ground, if (dark) 0.40f else 0.20f)
    val bottom = lerp(base, accent, if (dark) 0.30f else 0.14f)

    val ba = emphasis.borderAlpha
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = ba),
        0.38f to Color.White.copy(alpha = ba * 0.10f),
        0.72f to Color.White.copy(alpha = ba * 0.16f),
        1f to Color.White.copy(alpha = ba * 0.46f)
    )

    val ra = emphasis.rimAlpha
    val returnEdge = if (dark) Color.White else Color(0xFF262D36)
    val sheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = ra),
        0.055f to Color.White.copy(alpha = ra * 0.34f),
        0.17f to Color.Transparent,
        0.85f to Color.Transparent,
        1f to returnEdge.copy(alpha = ra * if (dark) 0.28f else 0.42f)
    )

    val style = if (realtime) {
        HazeStyle(
            sky,
            HazeTint(
                Brush.verticalGradient(
                    listOf(
                        top.copy(alpha = emphasis.tintAlpha),
                        bottom.copy(alpha = emphasis.tintAlpha * 0.72f)
                    )
                ),
                BlendMode.SrcOver
            ),
            emphasis.blur,
            0.022f
        )
    } else {
        null
    }

    val frost = Brush.verticalGradient(
        listOf(
            top.copy(alpha = emphasis.frostAlpha),
            bottom.copy(alpha = (emphasis.frostAlpha - 0.07f).coerceAtLeast(0.12f))
        )
    )

    return Recipe(
        style = style,
        frost = frost,
        sheen = sheen,
        rim = rim,
        rimWidth = if (emphasis == GlassEmphasis.ULTRA_THIN) 0.8.dp else 1.dp,
        shadow = lerp(Color(0xFF03070C), ground, 0.18f).copy(alpha = if (dark) 0.40f else 0.18f),
        elevation = emphasis.elevation
    )
}

@Composable
fun Modifier.glassMaterial(
    shape: Shape = GlassShapes.card,
    emphasis: GlassEmphasis = GlassEmphasis.REGULAR,
    role: GlassRole = GlassRole.CARD,
    dark: Boolean = false,
    ground: Color = Color(0xFFA3B9B5),
    accent: Color = Color(0xFF72A5BE),
    sky: Color = Color(0xFFBDCFC6),
    bodyAlpha: Float = 0f
): Modifier {
    val host = LocalGlassHost.current
    val realtime = host?.realtime == true
    val recipe = remember(emphasis, role, dark, realtime, ground, accent, sky) {
        recipeFor(emphasis, dark, realtime, ground, accent, sky)
    }
    val hazeStyle = recipe.style
    val sourceState = if (hazeStyle != null) host?.sourceState else null
    val bodyBrush = Brush.verticalGradient(
        listOf(
            ground.copy(alpha = bodyAlpha),
            accent.copy(alpha = (bodyAlpha + 0.08f).coerceAtMost(0.94f))
        )
    )
    return this
        .shadow(
            elevation = recipe.elevation,
            shape = shape,
            clip = false,
            ambientColor = recipe.shadow,
            spotColor = recipe.shadow
        )
        .clip(shape)
        .then(
            if (hazeStyle != null && sourceState != null) {
                Modifier.hazeEffect(state = sourceState, style = hazeStyle) {
                    inputScale = HazeInputScale.Default
                    if (role == GlassRole.BAR) {
                        mask = Brush.verticalGradient(
                            0f to Color.White,
                            0.62f to Color.White.copy(alpha = 0.55f),
                            1f to Color.Transparent
                        )
                    }
                }
            } else {
                Modifier.background(recipe.frost, shape)
            }
        )
        .then(
            if (bodyAlpha > 0.001f) Modifier.background(bodyBrush, shape) else Modifier
        )
        .background(recipe.sheen, shape)
        .border(recipe.rimWidth, recipe.rim, shape)
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    emphasis: GlassEmphasis = GlassEmphasis.REGULAR,
    dark: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .glassMaterial(emphasis = emphasis, dark = dark)
            .padding(18.dp),
        content = content
    )
}

/** 让编译器真的去实例化这些组合函数，避免探针退化成"只检查了 import" */
@Composable
fun ProbeScreen() {
    val host = rememberGlassHost()
    Box(Modifier.fillMaxSize().glassSource(host)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalGlassHost provides host) {
            GlassSurface(emphasis = GlassEmphasis.REGULAR) {}
            GlassSurface(emphasis = GlassEmphasis.THIN, dark = true) {}
            Box(
                Modifier
                    .glassMaterial(shape = GlassShapes.bar, role = GlassRole.BAR, bodyAlpha = 0.8f)
                    .padding(12.dp)
            )
        }
    }
}
