package com.jianyi.outfit.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jianyi.outfit.WeatherOutfitApp
import com.jianyi.outfit.engine.OutfitRecommendationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 每日穿搭推送接收器：到点后拉取当前城市天气，
 * 用推荐引擎生成一句话建议并发送通知。
 */
class DailyAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as WeatherOutfitApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = app.container
                // 优先使用当前城市，没有则回退 IP 自动定位
                val current = container.cityRepository.currentCity.first()
                val result = if (current != null) {
                    container.weatherRepository.byAddress(current.province, current.city)
                } else {
                    container.weatherRepository.byIp()
                }
                result.getOrNull()?.let { weather ->
                    val prefs = container.settingsRepository.preferences.first()
                    val recommendation = OutfitRecommendationEngine.recommend(weather, prefs)
                    Notifier.showDaily(
                        context,
                        title = "今日穿搭建议 · ${weather.city}",
                        text = recommendation.summary
                    )
                }
            } catch (e: Exception) {
                // 后台推送失败静默处理，不打扰用户
            } finally {
                pendingResult.finish()
            }
        }
    }
}
