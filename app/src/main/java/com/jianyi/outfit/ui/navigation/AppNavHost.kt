package com.jianyi.outfit.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jianyi.outfit.di.AppViewModelProvider
import com.jianyi.outfit.ui.city.CityScreen
import com.jianyi.outfit.ui.detail.DETAIL_ARG_CACHE_KEY
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

    /** 穿搭详情：携带天气缓存 key，详情页据此从仓库读取首页刚加载的天气 */
    const val DETAIL = "detail?$DETAIL_ARG_CACHE_KEY={$DETAIL_ARG_CACHE_KEY}"
    const val CITY = "city"
    const val SETTINGS = "settings"
}

/** 构造详情页路由：cacheKey 经 URL 编码（key 含 “|” 与中文） */
fun detailRoute(cacheKey: String): String =
    "detail?$DETAIL_ARG_CACHE_KEY=${Uri.encode(cacheKey)}"

/** 进场略慢于退场：进场要让人看清层次，退场要利落 */
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
                onBack = { navController.popBackStack() },
                viewModel = viewModel(factory = AppViewModelProvider.Factory)
            )
        }
        composable(Routes.CITY) {
            CityScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
