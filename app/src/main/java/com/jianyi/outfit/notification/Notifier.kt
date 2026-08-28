package com.jianyi.outfit.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.jianyi.outfit.R

/**
 * 通知工具：渠道管理与通知展示。
 * 每日推送的定时调度已迁移至 DailyPushScheduler（WorkManager）。
 */
object Notifier {

    /** 每日穿搭推送渠道 */
    private const val CHANNEL_DAILY = "daily_outfit"

    /** 极端天气预警渠道 */
    private const val CHANNEL_ALERT = "extreme_alert"

    /** 每日推送通知 id */
    private const val NOTIFY_ID_DAILY = 1001
    private const val NOTIFY_ID_ALERT = 1002

    /** 创建通知渠道（minSdk 26，无需版本判断） */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DAILY,
                context.getString(R.string.channel_daily),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.channel_daily_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                context.getString(R.string.channel_alert),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.channel_alert_desc) }
        )
    }

    /** 展示每日穿搭推送 */
    fun showDaily(context: Context, title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        notify(context, NOTIFY_ID_DAILY, notification)
    }

    /** 展示极端天气预警 */
    fun showExtremeAlert(context: Context, title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notify(context, NOTIFY_ID_ALERT, notification)
    }

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }
}
