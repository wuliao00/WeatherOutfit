package com.jianyi.outfit.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jianyi.outfit.ui.city.CityScreen
import com.jianyi.outfit.ui.detail.OutfitDetailScreen
import com.jianyi.outfit.ui.home.HomeScreen
import com.jianyi.outfit.ui.settings.SettingsScreen

/**
 * 导航图：首页 / 穿搭详情 / 城市管理 / 设置 四个页面。
 * 转场动画：淡入淡出 + 轻微位移，时长 300ms。
 */
object Routes {
    const val HOME = "home"
    const val DETAIL = "detail"
    const val CITY = "city"
    const val SETTINGS = "settings"
}

private const val TRANSITION_MS = 300

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            fadeIn(tween(TRANSITION_MS)) + slideInVertically(tween(TRANSITION_MS)) { it / 20 }
        },
        exitTransition = { fadeOut(tween(TRANSITION_MS)) },
        popEnterTransition = { fadeIn(tween(TRANSITION_MS)) },
        popExitTransition = {
            fadeOut(tween(TRANSITION_MS)) + slideOutVertically(tween(TRANSITION_MS)) { it / 20 }
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
            OutfitDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.CITY) {
            CityScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
