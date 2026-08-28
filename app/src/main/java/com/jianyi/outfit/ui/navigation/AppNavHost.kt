package com.jianyi.outfit.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jianyi.outfit.ui.city.CityScreen
import com.jianyi.outfit.ui.detail.DETAIL_ARG_CACHE_KEY
import com.jianyi.outfit.ui.detail.OutfitDetailScreen
import com.jianyi.outfit.ui.home.HomeScreen
import com.jianyi.outfit.ui.settings.SettingsScreen

/**
 * 导航图：首页 / 穿搭详情 / 城市管理 / 设置 四个页面。
 * 转场动画：淡入淡出 + 轻微位移，时长 300ms。
 */
object Routes {
    const val HOME = "home"

    /** 穿搭详情：携带天气缓存 key，详情页据此从仓库读取首页刚加载的天气 */
    const val DETAIL = "detail?$DETAIL_ARG_CACHE_KEY={$DETAIL_ARG_CACHE_KEY}"
    const val CITY = "city"
    const val SETTINGS = "settings"
}

/** 构造详情页路由：cacheKey 经 URL 编码（key 含 “|” 与中文） */
fun detailRoute(cacheKey: String): String =
    "detail?$DETAIL_ARG_CACHE_KEY=${Uri.encode(cacheKey)}"

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
                onNavigateToDetail = { cacheKey ->
                    navController.navigate(detailRoute(cacheKey))
                },
                onNavigateToCity = { navController.navigate(Routes.CITY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument(DETAIL_ARG_CACHE_KEY) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) {
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
