package com.jianyi.outfit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 字体层级。
 *
 * 只调字重与字号，不引入自定义字体文件：中文字体子集化后仍有几 MB，
 * 而这个 App 的首屏目标是「打开即成」，不值得为字形付那点体积与解码时间。
 * 系统字体在 Android 12+ 的 Roboto/思源黑体已经够克制。
 *
 * 注意每个子样式都要写全 `X.copy(...)`：在 `Typography.copy { }` 里裸写 `copy(...)`
 * 会解析到外层 Typography 的 copy，编译期直接报类型不匹配。
 */
private val BaseTypography = Typography()

val AppTypography = BaseTypography.copy(
    // 超大号温度数字：细一档的字重在风景上更透气，也不会和卡片标题抢权重
    displayLarge = BaseTypography.displayLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 88.sp,
        lineHeight = 92.sp,
        letterSpacing = (-2).sp
    ),
    displayMedium = BaseTypography.displayMedium.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1).sp
    ),
    headlineMedium = BaseTypography.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium = BaseTypography.titleMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = BaseTypography.bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(lineHeight = 20.sp),
    // 数字用等宽，温度/湿度变化时不会左右抖动
    labelLarge = BaseTypography.labelLarge.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp
    )
)

/** 圆角体系：整体比改造前更圆，玻璃需要大圆角才像「吹制」出来的 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

/** 行高对齐策略：避免中文在小字号下顶部被切 */
val AlignFullLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)
