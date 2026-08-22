package com.jianyi.outfit

import android.app.Application
import com.jianyi.outfit.di.AppContainer
import com.jianyi.outfit.notification.Notifier

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
    }
}
