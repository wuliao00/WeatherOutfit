package com.jianyi.outfit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.jianyi.outfit.data.AppDependencies
import com.jianyi.outfit.data.model.UserPreferences
import com.jianyi.outfit.ui.city.CityScreen
import com.jianyi.outfit.ui.city.CityViewModel
import com.jianyi.outfit.ui.components.DisclaimerDialog
import com.jianyi.outfit.ui.detail.OutfitDetailScreen
import com.jianyi.outfit.ui.detail.OutfitDetailViewModel
import com.jianyi.outfit.ui.home.HomeScreen
import com.jianyi.outfit.ui.home.HomeViewModel
import com.jianyi.outfit.ui.root.RootScreen
import com.jianyi.outfit.ui.settings.SettingsScreen
import com.jianyi.outfit.ui.settings.SettingsViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * iOS 入口：Swift 侧调 `MainViewControllerKt.MainViewController()` 拿到一个
 * UIViewController，直接当 window.rootViewController 用。
 *
 * ⚠ 这份只经过 **iosArm64 编译**验证（CI 的 :shared iOS job），没在真机或模拟器上
 * 跑过：本机是 Windows，Kotlin/Native 不交叉编译 iOS，也没有 Mac 可装。
 *
 * 入口刻意做到最小：手写四值路由 + 四页装配，不引导航库。JetBrains 的
 * navigation 在 iOS 上能编，但上一轮已经因为"iOS klib 存在、commonMain 却解析不到
 * 里面的类型"踩过一次（SavedStateHandle），这里不再引入同类变量。
 */
@OptIn(ExperimentalForeignApi::class)
fun MainViewController(): UIViewController = ComposeUIViewController { IosApp() }

/**
 * 应用根：依赖容器、偏好流、路由与须知弹窗。
 *
 * 容器 remember 建一次：它持有两个 Room 库与偏好后端，反复重建等于重复开库。
 * Swift 侧只在启动时取一次 rootViewController，所以这条 composition 与应用同生命周期。
 */
@Composable
fun IosApp() {
    val deps = remember { IosAppDependencies() }
    val controller = deps.sceneryController
    val prefs by deps.settingsRepository.preferences.collectAsState(UserPreferences())

    LaunchedEffect(Unit) {
        // Coil3 不自带网络栈：不挂 fetcher 的话网络图**静默不加载**（不报错也不回调失败，
        // Android 那边踩过一次，所以在 WeatherOutfitApp 里挂了 OkHttp 引擎）。
        // iOS 这边对位挂 Ktor 的 Darwin 引擎。图标拉不到时页面会自动收起那一块，不留空洞。
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context).components { add(KtorNetworkFetcherFactory()) }.build()
        }
    }

    LaunchedEffect(prefs) { controller.onPreferences(prefs) }

    val router = remember { IosRouter() }

    RootScreen(deps = deps, controller = controller, prefs = prefs) {
        Pages(deps = deps, router = router)
        DisclaimerLayer(deps)
    }
}

/** 四页与转场。路由状态由 [IosRouter] 持有 */
@Composable
private fun Pages(deps: AppDependencies, router: IosRouter) {
    val homeVm = remember(deps) { HomeViewModel(deps) }
    val cityVm = remember(deps) { CityViewModel(deps) }
    val settingsVm = remember(deps) { SettingsViewModel(deps) }
    // 详情页 VM 与一条具体天气绑定（构造参数就是缓存 key），所以按 key 现建
    val detailKey = router.detailKey
    val detailVm = if (detailKey == null) null else remember(detailKey) {
        OutfitDetailViewModel(deps, detailKey)
    }

    AnimatedContent(
        targetState = router.current,
        transitionSpec = {
            // 与 Android 那套转场同一组参数：进场略慢要让人看清层次，退场要利落
            (slideInHorizontally(tween(ENTER_MS)) { it / 14 } +
                scaleIn(tween(ENTER_MS), 0.94f) + fadeIn(tween(ENTER_MS)))
                togetherWith
                (slideOutHorizontally(tween(EXIT_MS)) { -it / 22 } +
                    scaleOut(tween(EXIT_MS), 0.96f) + fadeOut(tween(EXIT_MS)))
        }
    ) { route ->
        when (route) {
            IosRoute.HOME -> HomeScreen(
                onNavigateToDetail = { cacheKey -> router.openDetail(cacheKey) },
                onNavigateToCity = { router.go(IosRoute.CITY) },
                onNavigateToSettings = { router.go(IosRoute.SETTINGS) },
                viewModel = homeVm
            )

            IosRoute.DETAIL -> if (detailVm != null) {
                OutfitDetailScreen(onBack = { router.back() }, viewModel = detailVm)
            }

            IosRoute.CITY -> CityScreen(onBack = { router.back() }, viewModel = cityVm)

            IosRoute.SETTINGS -> SettingsScreen(onBack = { router.back() }, viewModel = settingsVm)
        }
    }
}

/** 首次启动的免责声明；"未勾选不再提示就下次再弹"的语义与 Android 一致 */
@Composable
private fun DisclaimerLayer(deps: AppDependencies) {
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!deps.settingsRepository.disclaimerAccepted.first()) show = true
    }

    if (show) {
        DisclaimerDialog(
            onDismiss = { dontShowAgain ->
                show = false
                if (dontShowAgain) scope.launch { deps.settingsRepository.setDisclaimerAccepted(true) }
            }
        )
    }
}

/** 页面标识：手写四值枚举，不引导航库 */
enum class IosRoute { HOME, DETAIL, CITY, SETTINGS }

/**
 * 极简路由：一个栈 + 详情页的缓存 key。
 * 字段都是 mutableStateOf，改了就让上面的 AnimatedContent 换页。
 */
class IosRouter {
    var current by mutableStateOf(IosRoute.HOME)
        private set

    /** 详情页要展示哪条缓存天气；离开详情页就清空 */
    var detailKey by mutableStateOf<String?>(null)
        private set

    private val stack = mutableListOf(IosRoute.HOME)

    fun go(route: IosRoute) {
        stack += route
        current = route
    }

    fun openDetail(cacheKey: String) {
        detailKey = cacheKey
        go(IosRoute.DETAIL)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        detailKey = null
        current = stack.last()
    }
}

private const val ENTER_MS = 340
private const val EXIT_MS = 260
