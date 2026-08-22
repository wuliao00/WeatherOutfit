package com.jianyi.outfit.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jianyi.outfit.R
import java.util.Calendar

/**
 * 通知工具：渠道管理、通知展示、每日推送的定时调度。
 */
object Notifier {

    /** 每日穿搭推送渠道 */
    private const val CHANNEL_DAILY = "daily_outfit"

    /** 极端天气预警渠道 */
    private const val CHANNEL_ALERT = "extreme_alert"

    /** 每日推送通知 id 与定时请求码 */
    private const val NOTIFY_ID_DAILY = 1001
    private const val NOTIFY_ID_ALERT = 1002
    private const val REQUEST_DAILY = 2001

    /** 每日推送时间：早上 8 点 */
    private const val DAILY_HOUR = 8

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
            .setSmallIcon(android.R.drawable.ic_menu_day)
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
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notify(context, NOTIFY_ID_ALERT, notification)
    }

    /** 注册每日 8 点的定时推送（重复闹钟，低功耗非精确策略） */
    fun scheduleDaily(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_DAILY, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = nextDailyTriggerAt()
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, triggerAt, AlarmManager.INTERVAL_DAY, pendingIntent
        )
    }

    /** 取消每日推送 */
    fun cancelDaily(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_DAILY, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /** 计算下一个“早上 8 点”的时间戳（已过今天 8 点则取明天） */
    private fun nextDailyTriggerAt(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, DAILY_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }
}
