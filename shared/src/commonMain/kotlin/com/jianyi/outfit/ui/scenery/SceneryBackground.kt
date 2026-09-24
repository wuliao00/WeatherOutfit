package com.jianyi.outfit.ui.scenery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import org.jetbrains.compose.resources.painterResource
import com.jianyi.outfit.ui.glass.GlassHost
import com.jianyi.outfit.ui.glass.glassSource
import com.jianyi.outfit.ui.theme.LocalReduceMotion
import com.jianyi.outfit.ui.theme.MotionSpecs
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 全屏风景背景层。
 *
 * 同时做三件事，且都要不掉帧：
 * 1. 换景。用户横滑 → 空间式滑动切换（你「走」到了另一片风景）；
 *    天气自动换 → 时间式交叉淡入。两条路径共用同一个 progress 状态机。
 * 2. 视差。背景按滚动量的 0.35 倍反向移动，形成前景/背景的纵深差。
 * 3. 呼吸。约 26 秒一个来回的极缓慢缩放漂移，让静态照片有空气感。
 *
 * 所有位移与缩放只写进 graphicsLayer 的 lambda：每帧更新变换矩阵即可，
 * 既不重新记录 DisplayList，也不触发重组，这是 120Hz 下稳住帧的关键。
 */

/** 换景方式 */
private enum class Transition { SLIDE, FADE }

@Composable
fun SceneryBackground(
    scenery: Scenery,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    /**
     * 滚动量提供者（px）。
     *
     * 故意传 lambda 而不是 Float：视差每帧都要读最新滚动量，
     * 如果作为普通参数传入，就会让本组件跟着滚动每帧重组一次。
     * 传 lambda 后只有 graphicsLayer 内部读它，重组被挡在外面。
     */
    scrollPx: () -> Float = { 0f },
    parallaxEnabled: Boolean = true,
    breathing: Boolean = true,
    swipeEnabled: Boolean = true,
    hazeHost: GlassHost? = null,
    onSceneryChange: (Scenery) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    /**
     * 容器宽度（像素）。横滑比例与整页位移都以它为单位。
     *
     * 不用 `LocalConfiguration.screenWidthDp`：那个 compositionLocal 依赖 Android 的
     * Configuration，Compose Multiplatform 没有它（本地编译发现不了，只有 iOS 编译会报）。
     * 改成量真实布局尺寸，顺带修掉一个既有偏差：屏幕宽 ≠ 背景层宽（有 inset 时不等）。
     */
    val widthPx = remember { mutableFloatStateOf(1f) }

    /** 当前稳定展示的一张 */
    var shown by remember { mutableStateOf(scenery) }
    /** 正在进入的一张；null 表示没有进行中的切换 */
    var incoming by remember { mutableStateOf<Scenery?>(null) }
    var mode by remember { mutableStateOf(Transition.FADE) }
    var direction by remember { mutableIntStateOf(1) }
    val progress = remember { Animatable(0f) }
    var dragPx by remember { mutableFloatStateOf(0f) }

    /**
     * 切换收口。
     *
     * 顺序很重要：必须先扶正 shown、清掉 incoming，再把 progress 归零。
     * 绘制端用 [effectiveProgress]（incoming 为空时强制按 0 处理）兜底，
     * 否则归零那一帧会先把刚换好的图判成「已滑出屏幕」，露出空白或旧图闪回。
     */
    val commit = {
        incoming?.let { next ->
            shown = next
            incoming = null
            dragPx = 0f
            scope.launch { progress.snapTo(0f) }
        }
    }

    // 外部（天气/设置）换景：淡入
    LaunchedEffect(scenery) {
        if (scenery == shown || incoming == scenery) return@LaunchedEffect
        direction = directionBetween(shown, scenery)
        mode = Transition.FADE
        incoming = scenery
        progress.animateTo(
            1f,
            tween(if (reduceMotion) 0 else MotionSpecs.SCENERY_CROSSFADE_MS)
        )
        commit()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { widthPx.floatValue = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                translationY =
                    if (reduceMotion || !parallaxEnabled) 0f else -scrollPx() * 0.35f
                // 预放大 10%：视差与呼吸都会把画面推开，不预留就会露出边缘
                scaleX = 1.10f
                scaleY = 1.10f
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .breathingLayer(enabled = breathing && !reduceMotion)
            .then(
                if (swipeEnabled && !reduceMotion) {
                    Modifier.pointerInput(shown) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val ratio = abs(dragPx) / widthPx.floatValue
                                scope.launch {
                                    if (ratio < 0.30f) {
                                        // 没拖够：弹回，取消换景
                                        progress.animateTo(0f, MotionSpecs.follow())
                                        incoming = null
                                        dragPx = 0f
                                    } else {
                                        progress.animateTo(1f, MotionSpecs.settle())
                                        commit()
                                        onSceneryChange(shown)
                                    }
                                }
                            }
                        ) { _, dragAmount ->
                            // 逐帧写入手指位移：手指停，画面就停
                            val next = dragPx + dragAmount
                            dragPx = next
                            val dir = if (next < 0f) 1 else -1
                            if (dir != direction) {
                                direction = dir
                                incoming = neighborOf(shown, dir)
                                mode = Transition.SLIDE
                            } else if (incoming == null) {
                                incoming = neighborOf(shown, dir)
                                mode = Transition.SLIDE
                            }
                            val p = (abs(next) / widthPx.floatValue).coerceIn(0f, 1f)
                            scope.launch { progress.snapTo(p) }
                        }
                    }
                } else Modifier
            )
    ) {
        /**
         * 模糊源单独成层，且这一层的内容平时完全静止。
         *
         * 真机实测：把视差/呼吸的 graphicsLayer 和 hazeSource 放在同一个节点上时，
         * 呼吸动画每帧都会让源失效，Haze 于是每帧重新抓全屏 + 重新高斯模糊，
         * 页面静止不动也能掉到 22ms/帧。拆成「外层只做变换、内层才是源」之后，
         * 源内容不变，模糊结果被缓存复用，只有真正换景时才重算一次。
         */
        Box(
            Modifier
                .matchParentSize()
                .glassSource(hazeHost)
        ) {
            // 进度同样只在 lambda 里读：横滑换景时手指每动一下都会刷新 progress，
            // 如果在组合期取，就等于「拖动过程中每帧重组两张全屏图」，最容易卡的时机偏偏最重
            SceneryImage(
                scenery = shown,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        applyTransition(
                            effectiveProgress(incoming, progress),
                            direction, widthPx.floatValue, mode, isLeaving = true
                        )
                    }
            )

            incoming?.let { next ->
                SceneryImage(
                    scenery = next,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            applyTransition(
                                effectiveProgress(incoming, progress),
                                direction, widthPx.floatValue, mode, isLeaving = false
                            )
                        }
                )
            }

            // 遮罩：让玻璃卡片上的文字在任何风景下都够对比度
            Box(
                Modifier
                    .matchParentSize()
                    .background(scrimBrush(shown, dark))
            )
        }
    }
}

/**
 * 切换进度。
 *
 * incoming 为空时强制按 0 处理 —— 这是收口那一帧不闪旧图的关键，
 * 因为扶正 shown 与归零 progress 无法真正原子完成，中间必然有一帧
 * 「shown 已是新图、progress 还是 1」，按 1 会把新图判成「已滑出屏幕」。
 */
private fun effectiveProgress(
    incoming: Scenery?,
    progress: Animatable<Float, AnimationVector1D>
): Float = if (incoming == null) 0f else progress.value

/** 单张风景图。painterResource 自带位图缓存，切回上一张时不会重新解码 */
@Composable
private fun SceneryImage(scenery: Scenery, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(scenery.resId),
        contentDescription = scenery.label,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize()
    )
}

/**
 * 把 0..1 的进度翻译成当前这一层的变换。
 *
 * @param isLeaving true = 正在退场的旧图，false = 正在进场的新图
 */
private fun GraphicsLayerScope.applyTransition(
    progress: Float,
    direction: Int,
    widthPx: Float,
    mode: Transition,
    isLeaving: Boolean
) {
    when (mode) {
        Transition.FADE -> {
            alpha = if (isLeaving) 1f - progress else progress
        }
        Transition.SLIDE -> {
            alpha = 1f
            translationX = if (isLeaving) {
                -progress * widthPx * direction
            } else {
                (1f - progress) * widthPx * direction
            }
            if (!isLeaving) {
                // 进场方从 94% 推到 100%，一点镜头推近的液态感
                val k = 0.94f + 0.06f * progress
                scaleX = k
                scaleY = k
            }
        }
    }
}

/** 遮罩：顶部压一点保状态栏可读，底部压更多给正文让路 */
private fun scrimBrush(scenery: Scenery, dark: Boolean): Brush {
    val deep = dark || scenery.dark
    val base = if (deep) Color.Black else Color.White
    return if (deep) {
        Brush.verticalGradient(
            0f to base.copy(alpha = 0.36f),
            0.42f to base.copy(alpha = 0.16f),
            1f to base.copy(alpha = 0.54f)
        )
    } else {
        Brush.verticalGradient(
            0f to base.copy(alpha = 0.28f),
            0.45f to base.copy(alpha = 0.06f),
            1f to base.copy(alpha = 0.36f)
        )
    }
}

/**
 * 呼吸漂移：26 秒一个来回的正弦缩放 + 微平移。
 *
 * 用正弦而不是线性往复，是因为线性在折返点速度突变，肉眼能看到「顿一下」。
 *
 * ⚠ 相位必须在 graphicsLayer 的 lambda 里读，绝不能在组合期用 `by` 取值。
 * 之前写成 `val phase by transition.animateFloat(...)` 再在外面算 wave，
 * 等于每帧重组整个背景子树（两张全屏位图 + 遮罩 + 所有 modifier 重新构造），
 * 真机实测静止时也要 23ms/帧 —— 60Hz 屏上直接掉到 43fps。
 * 改成 lambda 内读取后，每帧只是改一次变换矩阵。
 */
@Composable
private fun Modifier.breathingLayer(enabled: Boolean): Modifier {
    if (!enabled) return this
    val transition = rememberInfiniteTransition(label = "breathing")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing)),
        label = "breathPhase"
    )
    return graphicsLayer {
        val wave = sin(phase.value * 2.0 * PI).toFloat()
        scaleX = 1f + 0.012f * wave
        scaleY = 1f + 0.012f * wave
        translationY = wave * 7f
    }
}

/* ============ 邻居计算 ============ */

private fun indexOf(scenery: Scenery): Int = Scenery.ordered.indexOf(scenery)

/** 沿 dir 方向取下一张，循环 */
private fun neighborOf(current: Scenery, dir: Int): Scenery {
    val n = Scenery.ordered.size
    return Scenery.ordered[((indexOf(current) + dir) % n + n) % n]
}

/** 程序化换景时决定从哪边进场：按列表顺序就近进入 */
private fun directionBetween(from: Scenery, to: Scenery): Int {
    val n = Scenery.ordered.size
    val d = indexOf(to) - indexOf(from)
    return when {
        d == 0 -> 1
        abs(d) <= n / 2 -> if (d > 0) 1 else -1
        else -> if (d > 0) -1 else 1
    }
}
