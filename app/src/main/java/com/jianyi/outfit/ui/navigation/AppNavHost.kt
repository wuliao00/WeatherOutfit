package com.jianyi.outfit.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jianyi.outfit.ui.city.CityScreen
import com.jianyi.outfit.ui.detail.OutfitDetailScreen
import com.jianyi.outfit.ui.home.HomeScreen
import com.jianyi.outfit.ui.settings.SettingsScreen

/**
 * 导航图：首页 / 穿搭详情 / 城市管理 / 设置。
 *
 * 转场用「横向推移 + 轻微缩放 + 淡入淡出」的组合：
 * 纯淡入淡出会让页面像在同一平面替换，加上位移与缩放才有前后层次，
 * 也和背景视差的方向感一致。缩放幅度刻意很小（0.94），
 * 再大就会在低端机上看到明显的边缘抖动。
 */
object Routes {
    const val HOME = "home"
    const val DETAIL = "detail"
    const val CITY = "city"
    const val SETTINGS = "settings"
}

private const val ENTER_MS = 340
private const val EXIT_MS = 260

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            slideInHorizontally(tween(ENTER_MS)) { it / 14 } +
                scaleIn(tween(ENTER_MS), 0.94f) + fadeIn(tween(ENTER_MS))
        },
        exitTransition = {
            slideOutHorizontally(tween(EXIT_MS)) { -it / 22 } +
                scaleOut(tween(EXIT_MS), 0.96f) + fadeOut(tween(EXIT_MS))
        },
        popEnterTransition = {
            slideInHorizontally(tween(ENTER_MS)) { -it / 18 } + fadeIn(tween(ENTER_MS))
        },
        popExitTransition = {
            slideOutHorizontally(tween(EXIT_MS)) { it } + fadeOut(tween(EXIT_MS))
        }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToDetail = { navController.navigate(Routes.DETAIL) },
                onNavigateToCity = { navController.navigate(Routes.CITY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.DETAIL) {
            OutfitDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CITY) {
            CityScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
