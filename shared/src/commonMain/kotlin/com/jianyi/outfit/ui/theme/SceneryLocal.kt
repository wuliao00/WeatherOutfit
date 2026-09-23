package com.jianyi.outfit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.jianyi.outfit.ui.scenery.Scenery

/** 当前生效的风景，供任意组件取用强调色 / 判断明暗（原在 app 的 Theme.kt，随玻璃层一起进 shared） */
val LocalScenery = staticCompositionLocalOf { Scenery.DEFAULT }

/** 便捷读取：当前风景是否为暗调（组件据此决定玻璃底色与文字色） */
@Composable
fun sceneryIsDark(): Boolean = LocalScenery.current.dark ||
    isSystemInDarkTheme()
