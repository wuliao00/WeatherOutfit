package com.jianyi.outfit.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jianyi.outfit.ui.theme.MotionSpecs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 跟手交互基础件。
 *
 * 共同约定：所有位移都写进 [Animatable]，并且只在 graphicsLayer / draw lambda 里读取。
 * 这样每帧只是重新执行 lambda（走 GPU 变换矩阵），不触发 Compose 重组，
 * 120Hz 下才有预算同时跑模糊和视差。
 */

/**
 * 按压跟手缩放：手指按下立刻开始缩小、抬起立刻弹回。
 *
 * 用弹簧而不是固定时长 tween，是因为弹簧保留初速度——快速连点时动画会从
 * 当前速度接着走，不会出现「按第二下时还在等第一下播完」的迟滞感。
 */
@Composable
fun Modifier.pressFollow(
    pressedScale: Float = 0.972f,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val target = if (enabled && pressed) pressedScale else 1f
    val anim = remember { Animatable(1f) }
    LaunchedEffect(target) {
        anim.animateTo(target, MotionSpecs.press())
    }
    return graphicsLayer {
        scaleX = anim.value
        scaleY = anim.value
    }
}

/**
 * 头部折叠控制器：把滚动变成一个 0..1 的连续进度，供标题缩放、背景视差、
 * 玻璃浓度共用同一个源，保证多个元素动得完全同步。
 *
 * 拖拽过程中逐帧 snapTo —— 手指走多少、画面动多少，这才是「跟手」；
 * 只有在需要归位时才交给弹簧收敛。
 */
@Stable
class CollapseController internal constructor(
    private val rangePx: Float,
    private val scope: CoroutineScope
) {
    private val anim = Animatable(0f)

    /** 0 = 完全展开，1 = 完全折叠。只应在 graphicsLayer / draw lambda 中读取 */
    val progress: Float get() = (anim.value / rangePx).coerceIn(0f, 1f)

    /** 已折叠的像素距离，视差直接用它换算 */
    val offsetPx: Float get() = anim.value

    /**
     * 交给 `Modifier.nestedScroll(...)` 的连接。
     *
     * 上滑时优先吃掉位移用于折叠头部（onPreScroll），下滑时先让列表回到顶部、
     * 剩余位移再来展开头部（onPostScroll）——与系统协作式滚动的一致预期。
     */
    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy >= 0f) return Offset.Zero
            val before = anim.value
            val after = (before - dy).coerceAtMost(rangePx)   // dy 为负，-dy 即上滑量
            if (after == before) return Offset.Zero
            setImmediately(after)
            return Offset(0f, -(after - before))
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            val dy = available.y
            if (dy <= 0f) return Offset.Zero
            val before = anim.value
            val after = (before - dy).coerceAtLeast(0f)
            if (after == before) return Offset.Zero
            setImmediately(after)
            return Offset(0f, before - after)
        }
    }

    private fun setImmediately(value: Float) {
        // snapTo 会取消进行中的 animateTo，保证拖拽时不被残留动画「抢方向盘」
        scope.launch { anim.snapTo(value) }
    }

    /** 跳到指定进度（用于旋转/恢复状态） */
    fun snapTo(progress: Float) = setImmediately((progress * rangePx).coerceIn(0f, rangePx))

    /** 弹性展开回初始态 */
    fun expand() {
        scope.launch { anim.animateTo(0f, MotionSpecs.settle()) }
    }
}

/**
 * @param range 从完全展开到完全折叠所需的滚动距离
 */
@Composable
fun rememberCollapseController(range: Dp = 96.dp): CollapseController {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val rangePx = with(density) { range.toPx() }
    return remember(rangePx, scope) { CollapseController(rangePx, scope) }
}
