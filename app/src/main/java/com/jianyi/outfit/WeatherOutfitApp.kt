package com.jianyi.outfit

import android.app.Application
import com.jianyi.outfit.di.AppContainer
import com.jianyi.outfit.notification.DailyPushScheduler
import com.jianyi.outfit.notification.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 应用入口：初始化依赖容器与通知渠道。
 */
class WeatherOutfitApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.ensureChannels(this)
        // 自愈对齐：WorkManager 任务已持久化，此处仅在开关开启时
        // 按 DataStore 最新设定重新对齐推送时刻（幂等，不产生重复任务）
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val prefs = container.settingsRepository.preferences.first()
            if (prefs.dailyPushEnabled) {
                DailyPushScheduler.ensureScheduled(this@WeatherOutfitApp, prefs.dailyPushHour)
            }
        }
    }
}
