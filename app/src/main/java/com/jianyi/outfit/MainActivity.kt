package com.jianyi.outfit

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.jianyi.outfit.data.model.UserPreferences
import com.jianyi.outfit.ui.components.DisclaimerDialog
import com.jianyi.outfit.ui.navigation.AppNavHost
import com.jianyi.outfit.ui.root.RootScreen
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
                FrameRate.request(view, prefs.highFrameRateEnabled)
                WindowInsetsControllerCompat(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }

            RootScreen(controller = controller, prefs = prefs) {
                AppNavHost()
                DisclaimerLayer(app)
            }
        }
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
