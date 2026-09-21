package com.jianyi.outfit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.jianyi.outfit.ui.scenery.Scenery

/**
 * 主题：深浅色不再只看系统设置，而是「系统深色 或 当前风景是暗色」二者取一。
 *
 * 这一步是全 App 可读性的地基：星野、林间、烟雨这几张本身就是暗调，
 * 如果还按系统浅色渲染，玻璃上的深灰文字会直接糊进背景里。
 * 让风景决定前景色，才能保证任何一张背景上文字对比度都够。
 */
private val LightColorScheme = lightColorScheme(
    primary = MorandiBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightBackground,
    onSurface = LightOnBackground,
    surfaceContainer = LightCard,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline
)

/** 暗黑模式：深色底、卡片带一点蓝而不是纯黑，避免在夜景上出现死黑块 */
private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = LowTempBlue,
    onSecondary = androidx.compose.ui.graphics.Color(0xFF103049),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkBackground,
    onSurface = DarkOnBackground,
    surfaceContainer = DarkCard,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline
)

/** 当前生效的风景，供任意组件取用强调色 / 判断明暗 */
val LocalScenery = staticCompositionLocalOf { Scenery.DEFAULT }

@Composable
fun WeatherOutfitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    scenery: Scenery = Scenery.DEFAULT,
    content: @Composable () -> Unit
) {
    // 暗调风景强制走深色配色，保证玻璃上的文字始终看得清
    val useDark = darkTheme || scenery.dark
    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalScenery provides scenery,
        LocalReduceMotion provides rememberReduceMotion()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

/** 便捷读取：当前风景是否为暗调（组件据此决定玻璃底色与文字色） */
@Composable
fun sceneryIsDark(): Boolean = LocalScenery.current.dark ||
    androidx.compose.foundation.isSystemInDarkTheme()
