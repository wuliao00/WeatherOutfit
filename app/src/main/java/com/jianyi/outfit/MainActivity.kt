package com.jianyi.outfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.jianyi.outfit.data.model.UserPreferences
import com.jianyi.outfit.ui.components.DisclaimerDialog
import com.jianyi.outfit.ui.navigation.AppNavHost
import com.jianyi.outfit.ui.root.RootScreen
import com.jianyi.outfit.ui.update.UpdateUiState
import com.jianyi.outfit.ui.update.gateHoldsPage
import com.jianyi.outfit.util.FrameRate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 唯一 Activity：边到边 + 高帧率申请 + 系统栏明暗跟随风景，
 * 具体页面全部由 [RootScreen] 里的导航承载。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as WeatherOutfitApp).container
        // 权限弹窗需要 Activity；shared 侧的能力接口通过容器上的 holder 取用
        container.activities.attach(this)

        // 门禁期间的返回键拦截。VM 是容器级单例，所以这里挂的是它的 state，
        // 而不是某个页面自己的状态 —— 两个来源的返回键判据迟早会漂。
        val updateBackCallback = UpdateBackKeyCallback()
        onBackPressedDispatcher.addCallback(this, updateBackCallback)
        lifecycleScope.launch {
            container.updateViewModel.state.collect { updateBackCallback.sync(it) }
        }

        setContent {
            val app = application as WeatherOutfitApp
            val controller = app.container.sceneryController
            val prefs by app.container.settingsRepository.preferences
                .collectAsStateWithLifecycle(initialValue = UserPreferences())

            LaunchedEffect(prefs) { controller.onPreferences(prefs) }

            // 暗调风景会强制深色配色，状态栏图标必须跟着翻成亮色，
            // 否则夜里会出现「深灰图标压在深蓝天空上」这种看不清的组合。
            val dark = isSystemInDarkTheme() || controller.scenery.dark
            val view = LocalView.current
            SideEffect {
                FrameRate.request(window, view, prefs.highFrameRateEnabled)
                WindowInsetsControllerCompat(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }

            RootScreen(deps = app.container, controller = controller, prefs = prefs) {
                AppNavHost()
                DisclaimerLayer(app)
            }
        }
    }

    override fun onDestroy() {
        (application as WeatherOutfitApp).container.activities.detach(this)
        super.onDestroy()
    }
}

/**
 * 门禁期间要不要吞掉返回键。
 *
 * 判据**复用 Task 6 的 `gateHoldsPage`**，不在这再写一份 when。那个函数决定的是
 * "门禁层要不要盖遮罩、吃掉点击"，返回键是同一件事的另一半：两处各写一份的结果
 * 必然是"卡片还按着你、返回键却能退"或者反过来"卡片放开了、返回键还在吞"
 * （UpdateCopy.kt 里 gateHoldsPage 的注释点名的就是这个漂移）。
 *
 * 于是 Failed 不吞，连 forced 也不吞：计划的 Goal 写的就是"任何失败都能退出而不锁死用户"，
 * 而那一态本来也不拦点击，只吞返回键的净效果是"页面照常能用、唯独退不出去"。
 * 强制下载中必须吞，则是 Task 5 把 `forced` 塞进 Downloading 的理由 ——
 * 少了它，下到大一半按一次返回就回到旧版继续用，门禁被它要防的动作本身绕过。
 */
internal fun shouldBlockBack(state: UpdateUiState): Boolean = gateHoldsPage(state)

/**
 * 随状态开/关的返回键回调。
 *
 * 用 [OnBackPressedCallback] 而不是覆写 `onBackPressed()`：后者在 Android 13+ 已废弃，
 * 而预测性返回手势（Android 15，本包 manifest 开了 enableOnBackInvokedCallback）
 * 只走 callback 这条路 —— 用旧写法的后果是在新机上完全不生效，门禁被一次侧滑绕过。
 *
 * **开关必须跟着状态走**，这是本文件最容易写成事故的地方：只测"门禁下按不动"会留下
 * 另一种更坏的形态 —— 装完更新、门禁撤了，回调还常驻开启，整个 App 只能靠杀进程退出。
 * 所以这里既提供 sync()（状态变了就开/关），也被 UpdateGateBackKeyTest 用真实
 * OnBackPressedDispatcher 双向验过：门禁下兜底不被调用，门禁撤了兜底必须被调用。
 */
internal class UpdateBackKeyCallback : OnBackPressedCallback(false) {

    override fun handleOnBackPressed() {
        // 什么都不做就是这一层的语义：返回键被吃掉，门禁页上唯一的出路是卡片里那几颗按钮
    }

    /**
     * 状态 → 开关。onCreate 里的 collect 与单测都走这一个入口，
     * 两边共用同一个判据才不会漂。
     */
    fun sync(state: UpdateUiState) {
        isEnabled = shouldBlockBack(state)
    }
}

/**
 * 首次启动的免责声明层。
 * 覆盖在导航之上，不阻塞页面加载——用户还在看天气时就可以先读完。
 */
@Composable
private fun DisclaimerLayer(app: WeatherOutfitApp) {
    val scope = rememberCoroutineScope()
    var showDisclaimer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!app.container.settingsRepository.disclaimerAccepted.first()) {
            showDisclaimer = true
        }
    }

    if (showDisclaimer) {
        DisclaimerDialog(
            onDismiss = { dontShowAgain ->
                showDisclaimer = false
                if (dontShowAgain) {
                    scope.launch {
                        app.container.settingsRepository.setDisclaimerAccepted(true)
                    }
                }
                // 未勾选「不再提示」：本次可正常使用，下次启动再次弹出
            }
        )
    }
}
