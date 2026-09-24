package com.jianyi.outfit

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
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
 *
 * 同时是 Coil3 的 ImageLoader 工厂：Coil3 不再自带网络栈，
 * 这里显式挂 OkHttp fetcher。不显式装配的话，网络图会**静默不加载**
 * （不报错、只留一块空白），真机截图才能发现。
 */
class WeatherOutfitApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .build()

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
