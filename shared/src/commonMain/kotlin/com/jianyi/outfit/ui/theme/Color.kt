package com.jianyi.outfit.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 色彩规范：全 App 不超过 3 种主色。
 * - 主题色 1 种：莫兰迪蓝
 * - 状态色 2 种：高温浅橙 / 低温浅蓝（仅用于提示）
 */
val MorandiBlue = Color(0xFF6B82A6)        // 主题色：莫兰迪蓝
val HighTempOrange = Color(0xFFF5A67A)     // 高温提示色：浅橙
val LowTempBlue = Color(0xFF7EB3D5)        // 低温提示色：浅蓝

/* ============ 浅色模式 ============ */
val LightBackground = Color(0xFFF8F9FB)
val LightCard = Color(0xFFEFF1F5)          // 浅色卡片底色（比背景浅 5% 左右）
val LightOnBackground = Color(0xFF1A1C1F)
val LightSurfaceVariant = Color(0xFFE7EAF0)
val LightOnSurfaceVariant = Color(0xFF44474D)
val LightPrimaryContainer = Color(0xFFD7E0EE)
val LightOnPrimaryContainer = Color(0xFF24344D)
val LightSecondary = Color(0xFF8494AC)
val LightOutline = Color(0xFF74777F)

/* ============ 暗黑模式（遵循「避免纯黑」原则） ============ */
val DarkBackground = Color(0xFF121212)     // 深色底色
val DarkOnBackground = Color(0xFFE0E0E0)   // 主要文字
val DarkCard = Color(0xFF1E1E1E)           // 卡片底色
val DarkSurfaceVariant = Color(0xFF262626)
val DarkOnSurfaceVariant = Color(0xFFB9BCC2)
val DarkPrimary = Color(0xFFA7BCDA)        // 暗黑模式下的主题色（提亮保证对比度）
val DarkOnPrimary = Color(0xFF16283F)
val DarkPrimaryContainer = Color(0xFF2A3A55)
val DarkOnPrimaryContainer = Color(0xFFD7E0EE)
val DarkOutline = Color(0xFF8E9199)

/* ============ 背景遮罩 ============
 * 液态玻璃的材质参数（本体色 / 受光 / 描边 / 投影）不在这里定义。
 * 它们必须从「当前风景」推导，见 ui/glass/Glass.kt 的 bodyColors()：
 * 中性灰或纯白的固定底色压在蓝调夜景上只会得到一块泥，这是上一版被否掉的直接原因。
 */
/** 背景遮罩：保证玻璃上的文字在任何风景下都有足够对比度 */
val ScrimLightTop = Color(0x24FFFFFF)
val ScrimLightBottom = Color(0x66FFFFFF)
val ScrimDarkTop = Color(0x40000000)
val ScrimDarkBottom = Color(0xA8000000)
