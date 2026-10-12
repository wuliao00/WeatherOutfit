package com.jianyi.outfit.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jianyi.outfit.WeatherOutfitApp
import com.jianyi.outfit.engine.HoroscopeEngine
import com.jianyi.outfit.engine.OutfitRecommendationEngine
import com.jianyi.outfit.platform.todayCivilDate
import com.jianyi.outfit.util.Formatters
import kotlinx.coroutines.flow.first

/**
 * 每日穿搭推送 Worker：
 * 取当前城市天气（无则回退 IP 定位）→ 推荐引擎生成建议 → 发通知。
 * 由 DailyPushScheduler 以 24 小时周期调度，初始延迟对齐用户设定的推送时刻。
 *
 * 失败时按 WorkManager 退避策略重试（有限次数），避免公共凭证被持续打爆。
 */
class DailyOutfitWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as WeatherOutfitApp).container
        return try {
            val prefs = container.settingsRepository.preferences.first()
            // 优先使用当前城市，没有则回退 IP 自动定位
            val current = container.cityRepository.currentCity.first()
            val result = if (current != null) {
                container.weatherRepository.byAddress(current.province, current.city)
            } else {
                container.weatherRepository.byIp()
            }
            val weather = result.getOrNull()
                ?: return retryOrFail()
            val recommendation = OutfitRecommendationEngine.recommend(weather, prefs)
            // 填过生日才带星座那一句，而且用的是「我的星座」（用户填的那个），
            // 不是当天太阳所在的星座 —— 后者对用户没有意义，写进推送是误导。
            // 引擎是纯函数、离线，推送因此不额外依赖任何网络
            val mine = if (prefs.hasBirthday) {
                HoroscopeEngine.signForBirthday(prefs.birthMonth, prefs.birthDay)
                    ?.let { HoroscopeEngine.of(todayCivilDate(), it) }
            } else {
                null
            }
            Notifier.showDaily(
                applicationContext,
                title = "今日穿搭建议 · ${weather.city}",
                text = Formatters.dailyPushText(recommendation.summary, mine)
            )
            Result.success()
        } catch (e: Exception) {
            // 网络/接口异常：有限退避重试，超限放弃本轮推送，不打扰用户
            retryOrFail()
        }
    }

    /** 未超过最大尝试次数则退避重试，否则放弃本轮推送 */
    private fun retryOrFail(): Result =
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()

    companion object {
        /** 单轮最大尝试次数（含首次），防止公共凭证高峰期无限重试 */
        const val MAX_ATTEMPTS = 3
    }
}
