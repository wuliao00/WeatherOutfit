package com.jianyi.outfit.ui.root

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.jianyi.outfit.data.AppDependencies
import com.jianyi.outfit.data.LocalAppDependencies
import com.jianyi.outfit.data.model.UserPreferences
import com.jianyi.outfit.ui.glass.LocalGlassHost
import com.jianyi.outfit.ui.glass.rememberGlassHost
import com.jianyi.outfit.ui.scenery.LocalSceneryController
import com.jianyi.outfit.ui.scenery.SceneryBackground
import com.jianyi.outfit.ui.scenery.SceneryController
import com.jianyi.outfit.ui.theme.WeatherOutfitTheme
import com.jianyi.outfit.ui.update.UpdateGateLayer

/**
 * 应用根节点：风景背景 + 玻璃宿主 + 主题，全部页面共享同一份。
 *
 * 背景放在这一层而不是各页面内部，是为了：
 * 1. 跨页面切换时背景不重置，横滑换景的结果在所有页面一致；
 * 2. 只有一个模糊源（sceneryState）需要维护，玻璃卡片的采样成本恒定；
 * 3. 视差读的是全局滚动量，页面之间不会各滚各的。
 *
 * 这里同时是 `LocalAppDependencies` 的注入点：页面要的权限/高帧率等平台能力
 * 在 shared 侧只是接口，实现在 app，统一从这一层交给子树。
 */
@Composable
fun RootScreen(
    deps: AppDependencies,
    controller: SceneryController,
    prefs: UserPreferences,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val scenery = controller.scenery
    val glassHost = rememberGlassHost(prefs.glassQuality)

    WeatherOutfitTheme(darkTheme = systemDark, scenery = scenery) {
        Box(modifier = Modifier.fillMaxSize().background(scenery.sky)) {
            // 底色先铺风景的天空采样色：图片解码完成前不会是白屏或纯色块
            SceneryBackground(
                scenery = scenery,
                dark = systemDark,
                scrollPx = { controller.scrollPx.floatValue },
                parallaxEnabled = prefs.parallaxEnabled,
                breathing = prefs.breathingEnabled,
                hazeHost = glassHost,
                onSceneryChange = { picked -> controller.onUserSelect(picked) }
            )

            CompositionLocalProvider(
                LocalGlassHost provides glassHost,
                LocalSceneryController provides controller,
                LocalAppDependencies provides deps
            ) {
                content()
                // 更新门禁：必须在 content() **之后**，才盖得住所有页面与弹层。
                //
                // 为什么挂这一层而不是各页面各挂一份：门禁的语义是"这次冷启动拦一次人"，
                // 挂在页面上的话，看不看得到门禁就取决于用户停在哪个页面 —— 而拦人的东西
                // 本来就该在页面之上。RootScreen 恰好是每次冷启动存在一次、且包住全部内容
                // 的作用域，冷启动检查（UpdateGateLayer 里那句 checkOnce）也只能由它发起。
                UpdateGateLayer(vm = deps.updateViewModel)
            }
        }
    }
}
