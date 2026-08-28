package com.jianyi.outfit.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 每日推送调度器：基于 WorkManager 的持久化周期任务。
 * 相比 AlarmManager：任务随系统持久化，设备重启 / 应用被杀后自动恢复，
 * 无需手动重新注册；网络请求也由 Worker 自身完成，不受前台限制。
 */
object DailyPushScheduler {

    /** WorkManager 唯一任务名 */
    private const val WORK_NAME = "daily_outfit_push"

    /** 周期：每 24 小时一次 */
    private const val PERIOD_DAYS = 1L

    /**
     * 注册（或按新的初始延迟对齐）每日推送任务。
     * 使用 CANCEL_AND_REENQUEUE：修改推送时刻后以新时刻重新起算周期。
     * 幂等：重复调用只会对齐时刻，不产生重复任务。
     */
    fun ensureScheduled(context: Context, pushHour: Int) {
        val request = PeriodicWorkRequestBuilder<DailyOutfitWorker>(PERIOD_DAYS, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis(pushHour), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    /** 取消每日推送任务 */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * 计算距下一个「今日/明日 pushHour 点整」的毫秒数。
     * 时刻已过（或就是现在）则顺延到明天，保证首次触发即对齐用户设定时刻。
     */
    fun initialDelayMillis(pushHour: Int, now: Calendar = Calendar.getInstance()): Long {
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, pushHour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return next.timeInMillis - now.timeInMillis
    }
}
