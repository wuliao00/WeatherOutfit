package com.jianyi.outfit.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jianyi.outfit.WeatherOutfitApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机自启接收器：对齐每日推送的调度时刻。
 *
 * WorkManager 任务本身已持久化，重启后会自动恢复，无需在此重新创建；
 * 这里主要用于：
 * 1. 用户修改过推送时刻时，按 DataStore 中最新设定重新对齐初始延迟；
 * 2. 兜底校验开关状态（开启则确保任务在排，关闭则不做处理）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as WeatherOutfitApp
                val prefs = app.container.settingsRepository.preferences.first()
                if (prefs.dailyPushEnabled) {
                    DailyPushScheduler.ensureScheduled(context, prefs.dailyPushHour)
                }
            } catch (e: Exception) {
                // 开机对齐失败静默处理：WorkManager 持久化任务仍按原时刻执行
            } finally {
                pendingResult.finish()
            }
        }
    }
}
