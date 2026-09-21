package com.jianyi.outfit.ui.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jianyi.outfit.data.model.GlassQuality
import com.jianyi.outfit.ui.motion.pressFollow
import com.jianyi.outfit.ui.scenery.Scenery
import com.jianyi.outfit.ui.theme.LocalScenery
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * 液态玻璃层（第二版）。
 *
 * ## 第一版为什么丑
 * 第一版把三层「白」叠在了一起：静态磨砂底（白 0.56）+ 上缘高光（白 0.52 铺满大半张卡）
 * + 一圈等宽 1px 白描边（0.62）。叠完实际不透明度接近 0.9，背景糊成什么根本看不见，
 * 于是屏幕上是一排排中性灰圆角板子加贴纸轮廓 —— 那是 2016 年的深色卡片，不是玻璃。
 * 深色风景下更糟：中性灰 #1B2129 压在蓝调夜景上只会得到「泥」。
 *
 * ## 这一版的原则
 * 1. **只有一层着色**。颜色进 [HazeStyle] 的 tint，实时模糊路径上绝不再 `background()` 叠白。
 * 2. **玻璃本体从风景取色**，不用中性灰。卡片上半截偏亮、下半截偏冷，看起来像光穿过了厚介质，
 *    而不是像贴了张半透明纸。
 * 3. **高光只出现在边缘**。上缘一道受光、下缘一道回光，中间完全透明 —— 不再有横贯卡片的光带。
 * 4. **描边是方向性的、且很弱**。左上受光最亮、往下迅速消失、右下只回一点，
 *    alpha 从 0.62 降到 0.1~0.22。等宽亮环是「贴纸感」的唯一来源。
 * 5. **模糊半径分浓度**：越厚的玻璃把背景化得越开，这本身就是层次感的来源。
 *
 * ## 只有一个模糊源，而且这是对的
 * 整屏风景图是唯一的源。试过给「滚动内容」再挂一个源、让顶栏糊正在滚过去的卡片，
 * 真机上不成立：`hazeEffect` 是在自己这块区域上**另画一份**糊过的源纹理，
 * 底下那层原始内容照画不误 —— 浮层本身是半透的，所以糊过的一份盖不住清楚的一份，
 * 文字该冲突还是冲突，只是多花一份全屏离屏缓冲。
 * 结论：浮层要压住滚过来的内容靠的是**本体色**（见 [glassBodyBrush]），不是第二份模糊。
 * 单一源会被 Haze 缓存成同一张模糊纹理，所有玻璃共用，模糊成本与玻璃数量无关。
 */

/**
 * 玻璃角色：不决定采样哪个源，只决定「当前性能档位下允不允许实时模糊」。
 */
enum class GlassRole {
    /** 浮在风景上的卡片：数量多 */
    CARD,

    /** 顶栏 / 底部浮层：面积小且只有一个，模糊还带向下渐隐 */
    BAR
}

/**
 * 玻璃圆角体系。
 *
 * 比第一版整体收紧一档：1256x2760 / 3.5 倍密的屏上 28dp 圆角会显得「胖」，
 * 而且大圆角 + 强描边一起用就是儿童 App 质感。
 */
object GlassShapes {
    val card: Shape = RoundedCornerShape(24.dp)
    val inner: Shape = RoundedCornerShape(16.dp)
    val chip: Shape = RoundedCornerShape(13.dp)
    val bar: Shape = RoundedCornerShape(22.dp)
    val sheet: Shape = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )
    val circle: Shape = CircleShape
}

/**
 * 玻璃浓度。
 *
 * @param blur 模糊半径：越厚化得越开
 * @param frostAlpha 无实时模糊时本体色的不透明度（没有模糊撑着，必须靠着色保证可读）
 * @param tintAlpha 有实时模糊时 tint 的不透明度（比 frost 低一个量级，通透感全靠这个差）
 * @param rimAlpha 上下边缘受光强度
 * @param borderAlpha 描边强度
 */
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

/** 实时模糊的硬件门槛：Android 12 才有 RenderEffect */
internal val supportsRealtimeBlur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * 档位 x 角色 x 硬件能力 -> 该不该走实时模糊。
 *
 * 抽成纯函数并把 `canBlur` 作为入参，是因为真机上的 `Build.VERSION.SDK_INT` 在 JVM 单测里
 * 永远是 0（android.jar 桩类），不这样拆就没法把「PERFORMANCE 一律不糊」「均衡档只糊浮层」
 * 这张真值表钉住 —— 而它一旦被人改错，表现就是「玻璃又变回灰板子」，正是被否掉的那版。
 */
internal fun realtimeBlurFor(
    quality: GlassQuality,
    role: GlassRole,
    canBlur: Boolean
): Boolean {
    if (!canBlur) return false
    return when (role) {
        GlassRole.CARD -> quality == GlassQuality.REALTIME
        GlassRole.BAR -> quality != GlassQuality.PERFORMANCE
    }
}

/**
 * 玻璃宿主：持有唯一的模糊源与当前档位，由根节点创建并经 [LocalGlassHost] 下发。
 */
@Stable
class GlassHost(
    val sourceState: HazeState,
    val quality: GlassQuality
) {
    /** 该角色在当前档位下是否走实时模糊；false 表示退回静态着色 */
    fun realtimeFor(role: GlassRole): Boolean =
        realtimeBlurFor(quality, role, supportsRealtimeBlur)

    /** 有没有任何东西会去采样风景源；都没有就别挂源，省一份全屏离屏缓冲 */
    val anyRealtime: Boolean
        get() = realtimeFor(GlassRole.BAR) || realtimeFor(GlassRole.CARD)
}

val LocalGlassHost = compositionLocalOf<GlassHost?> { null }

@Composable
fun rememberGlassHost(quality: GlassQuality): GlassHost = remember(quality) {
    GlassHost(HazeState(), quality)
}

/**
 * 把风景层登记为唯一的模糊源。
 *
 * PERFORMANCE 档或系统不支持实时模糊时，没有组件会去采样它 —— 挂源等于白让系统
 * 多维护一份全屏离屏缓冲，所以直接不挂。
 */
fun Modifier.glassSource(host: GlassHost?): Modifier =
    if (host == null || !host.anyRealtime) this else hazeSource(host.sourceState)

/* ============ 材质配方 ============ */

/**
 * 一次算全所有画笔。
 *
 * 之所以「配方」而不是直接一串 Modifier：本体色要同时喂给 Haze 的 tint、静态着色、
 * 描边和投影，四处必须是同一个颜色族，否则卡片边缘会出现色差线。
 */
@Immutable
private class Recipe(
    val style: HazeStyle?,
    val frost: Brush,
    val sheen: Brush,
    val rim: Brush,
    val rimWidth: Dp,
    val shadow: Color,
    val elevation: Dp,
    val blur: Dp
)

/** 玻璃本体色：从中性基调往风景的 ground / accent 上偏 */
private fun bodyColors(scenery: Scenery, dark: Boolean): Pair<Color, Color> {
    val base = if (dark) Color(0xFF090F16) else Color(0xFFFFFFFF)
    // 上半：主要受光面，往风景中段偏；深色下偏得多一点，避免整块死黑
    val top = lerp(base, scenery.ground, if (dark) 0.40f else 0.20f)
    // 下半：光穿过厚玻璃后的偏色，取风景的强调色
    val bottom = lerp(base, scenery.accent, if (dark) 0.30f else 0.14f)
    return top to bottom
}

private fun recipeFor(
    emphasis: GlassEmphasis,
    role: GlassRole,
    dark: Boolean,
    realtime: Boolean,
    scenery: Scenery
): Recipe {
    val (top, bottom) = bodyColors(scenery, dark)
    val blur = emphasis.blur

    // 描边：左上最亮 -> 迅速消失 -> 右下回一点。方向性是关键，等宽环必丑。
    val ba = emphasis.borderAlpha
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = ba),
        0.38f to Color.White.copy(alpha = ba * 0.10f),
        0.72f to Color.White.copy(alpha = ba * 0.16f),
        1f to Color.White.copy(alpha = ba * 0.46f)
    )

    // 受光只贴在上下两条边上，中间完全透明
    val ra = emphasis.rimAlpha
    val returnEdge = if (dark) Color.White else Color(0xFF262D36)
    val sheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = ra),
        0.055f to Color.White.copy(alpha = ra * 0.34f),
        0.17f to Color.Transparent,
        0.85f to Color.Transparent,
        1f to returnEdge.copy(alpha = ra * if (dark) 0.28f else 0.42f)
    )

    val shadow = lerp(Color(0xFF03070C), scenery.ground, 0.18f)
        .copy(alpha = if (dark) 0.40f else 0.18f)

    val style = if (realtime) {
        HazeStyle(
            scenery.sky,
            HazeTint(
                Brush.verticalGradient(
                    listOf(
                        top.copy(alpha = emphasis.tintAlpha),
                        bottom.copy(alpha = emphasis.tintAlpha * 0.72f)
                    )
                ),
                BlendMode.SrcOver
            ),
            blur,
            0.022f
        )
    } else {
        null
    }

    // 静态着色：没有模糊兜底，靠风景色渐变把背景「压」住，同时保住通透感
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
        shadow = shadow,
        elevation = emphasis.elevation,
        blur = blur
    )
}

/* ============ 组件 ============ */

/**
 * 浮层的「本体色」：顶栏这类浮层要压住滚到它底下的正文，靠的是这一层，不是第二份模糊。
 *
 * 为什么不靠「再去糊一层滚动内容」：`hazeEffect` 只是在自己这块区域另画一份糊过的源纹理，
 * 底下那层原始内容照画不误 —— 浮层本身是半透的，糊过的一份盖不住清楚的那份，
 * 真机上标题照样和顶栏文字叠在一起，白白多花一份全屏离屏缓冲。
 *
 * @param alpha 这一层的最终不透明度，直接给值而不是乘在 [GlassEmphasis.frostAlpha] 上：
 *   浮层要多「实」是跟手感和可读性一起权衡出来的，不该被浓度档绑架。
 *   真机实测 THIN 的 0.44 挡不住正文，要 0.8 上下才干净。
 *   颜色仍从风景取、下缘比上缘更实，所以它是「同一块玻璃变厚了」，
 *   而不是「在玻璃上又贴了一张灰膜」——后者正是上一版被否掉的样子。
 */
@Composable
private fun bodyBrush(dark: Boolean, alpha: Float): Brush {
    val (top, bottom) = bodyColors(LocalScenery.current, dark)
    return Brush.verticalGradient(
        listOf(
            top.copy(alpha = alpha),
            bottom.copy(alpha = (alpha + 0.08f).coerceAtMost(0.94f))
        )
    )
}

/**
 * 玻璃材质修饰符版：需要在 Box 里自由分层（例如顶栏只让背景渐显、文字保持全亮）
 * 时用这个，而不是整个 [GlassSurface]。
 *
 * @param bodyAlpha 本体色最终不透明度，0 表示纯玻璃。只有会被内容滚到下面的浮层才需要给值。
 *
 * 修饰符顺序有讲究：shadow 必须在 clip 之前（阴影要落在形状外），
 * 模糊 / 本体色必须在 clip 之后（不能糊出圆角外），受光与描边最后压顶。
 */
@Composable
fun Modifier.glassMaterial(
    shape: Shape = GlassShapes.card,
    emphasis: GlassEmphasis = GlassEmphasis.REGULAR,
    role: GlassRole = GlassRole.CARD,
    dark: Boolean = false,
    bodyAlpha: Float = 0f
): Modifier {
    val host = LocalGlassHost.current
    val scenery = LocalScenery.current
    val realtime = host?.realtimeFor(role) == true
    val recipe = remember(emphasis, role, dark, realtime, scenery) {
        recipeFor(emphasis, role, dark, realtime, scenery)
    }
    val hazeStyle = recipe.style
    val sourceState = if (hazeStyle != null) host?.sourceState else null
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
                    // 降采样输入纹理：120Hz 下这一步直接决定糊不糊得起
                    inputScale = HazeInputScale.Default
                    // 浮层的模糊向下渐隐，滚过去的文字才不会「撞」在一条硬边上
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
            // 本体色夹在模糊与受光之间：模糊在下（被它压住）、描边与上缘受光在上（不被盖掉）
            if (bodyAlpha > 0.001f) {
                Modifier.background(bodyBrush(dark, bodyAlpha), shape)
            } else {
                Modifier
            }
        )
        .background(recipe.sheen, shape)
        .border(recipe.rimWidth, recipe.rim, shape)
}

/**
 * 玻璃面板：所有「浮在风景上的卡片」都走它，改一处即可全局调整质感。
 *
 * 内容槽是 **ColumnScope** 而不是 BoxScope —— 玻璃卡片里放的都是纵向堆的
 * 标题 / 正文 / 提醒行，用 BoxScope 的话多个兄弟节点会全部叠在左上角互相压字。
 * 真机上就是靠截图才发现的这个问题，别改回 BoxScope。
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
    contentPadding: PaddingValues = PaddingValues(18.dp),
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

/**
 * 小尺寸玻璃（图标按钮、胶囊）的统一配方。
 *
 * 这类元件永远走静态着色：它们要么在顶栏/卡片内部（再开一层实时模糊只是把父层已经糊过的
 * 东西又糊一遍），要么数量多到足以让模糊成本失控。小元件略微实心在 iOS 上也是同款处理。
 */
@Composable
private fun Modifier.miniGlass(
    shape: Shape,
    dark: Boolean,
    emphasis: GlassEmphasis = GlassEmphasis.THIN
): Modifier {
    val scenery = LocalScenery.current
    val recipe = remember(dark, emphasis, scenery) {
        recipeFor(emphasis, GlassRole.CARD, dark, realtime = false, scenery = scenery)
    }
    return this
        .shadow(
            elevation = recipe.elevation,
            shape = shape,
            clip = false,
            ambientColor = recipe.shadow,
            spotColor = recipe.shadow
        )
        .clip(shape)
        .background(recipe.frost, shape)
        .background(recipe.sheen, shape)
        .border(recipe.rimWidth, recipe.rim, shape)
}

/** 圆形玻璃图标按钮：顶栏、悬浮操作 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = Color.Unspecified,
    dark: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    // 这里不能把 Unspecified 直接丢给 Icon：玻璃层是普通 Column/Box，
    // 没有 Surface 提供 LocalContentColor，未指定色会退成纯黑，深色风景下等于隐形。
    val iconTint = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .size(size)
            .miniGlass(CircleShape, dark, GlassEmphasis.ULTRA_THIN)
            .pressFollow(pressedScale = 0.9f, interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(size * 0.44f)
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
            .miniGlass(shape, dark)
            .then(
                if (onClick != null) {
                    Modifier
                        .pressFollow(pressedScale = 0.94f, interactionSource = interaction)
                        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else Modifier
            )
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (contentColor != Color.Unspecified) {
                contentColor
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

/** 玻璃内部分隔线：两端淡出，避免在透明底上出现硬切边 */
@Composable
fun GlassDivider(modifier: Modifier = Modifier, dark: Boolean = false) {
    val base = if (dark) Color.White else Color(0xFF262D36)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(base.copy(alpha = 0f), base.copy(alpha = 0.22f), base.copy(alpha = 0f))
                )
            )
    )
}
