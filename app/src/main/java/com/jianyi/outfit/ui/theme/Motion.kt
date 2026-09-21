package com.jianyi.outfit.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 动效规范：全部以「弹簧」为主，因为弹簧才有速度概念，
 * 手指拖动时把位移直接写进 Animatable 才能做到完全跟手；
 * 固定时长的 tween 在手指停下后还会继续走完，观感就是「慢半拍」。
 */
object MotionSpecs {

    /** 跟手位移：高刚度 + 轻微回弹，手指停即画面停 */
    fun <T : Any> follow() = spring<T>(
        dampingRatio = 0.68f,
        stiffness = 1200f
    )

    /** 按压反馈：几乎不回弹，压得下去、弹得回来 */
    fun <T : Any> press() = spring<T>(
        dampingRatio = 0.5f,
        stiffness = 1600f
    )

    /** 落位/吸附：无过冲，用于折叠头部、底部面板归位 */
    fun <T : Any> settle() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 600f
    )

    /** 入场：允许一次轻微弹跳，用于卡片浮现 */
    fun <T : Any> reveal() = spring<T>(
        dampingRatio = 0.74f,
        stiffness = 520f
    )

    /** 淡入淡出统一时长（背景交叉切换用，稍长更柔和） */
    const val FADE_MS = 260
    const val SCENERY_CROSSFADE_MS = 900

    val EaseOut = LinearOutSlowInEasing
    val EaseInOut: Easing = FastOutSlowInEasing

    fun fade(milliseconds: Int = FADE_MS) = tween<Float>(milliseconds, easing = EaseInOut)
}

/**
 * 系统「移除动画」开关（设置 → 无障碍 → 动画时长缩放 = 0）。
 * 为 true 时页面仍可用，只是所有位移/缩放退化为直接赋值，尊重用户偏好也省电。
 */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}

/** 动画时长缩放到 0 时，用 Instant 语义：直接跳到目标值 */
fun Animatable<Float, *>.isSettledAt(target: Float) = value == target
