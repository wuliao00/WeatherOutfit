package com.jianyi.outfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.jianyi.outfit.ui.components.DisclaimerDialog
import com.jianyi.outfit.ui.navigation.AppNavHost
import com.jianyi.outfit.ui.theme.WeatherOutfitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 唯一 Activity：启用边到边布局，全部页面由 Compose Navigation 承载。
 * 首次启动（未勾选「不再提示」）时展示免责声明弹窗，覆盖于导航之上。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeatherOutfitTheme {
                val app = application as WeatherOutfitApp
                val scope = rememberCoroutineScope()
                var showDisclaimer by remember { mutableStateOf(false) }

                // 启动时读取一次确认状态（未确认过即弹出，不阻塞页面加载）
                LaunchedEffect(Unit) {
                    if (!app.container.settingsRepository.disclaimerAccepted.first()) {
                        showDisclaimer = true
                    }
                }

                AppNavHost()

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
        }
    }
}
