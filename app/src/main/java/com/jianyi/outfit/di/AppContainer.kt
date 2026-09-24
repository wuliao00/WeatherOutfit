package com.jianyi.outfit.di

import android.content.Context
import com.jianyi.outfit.data.AppDependencies
import com.jianyi.outfit.data.CitySelection
import com.jianyi.outfit.data.ExtremeAlerter
import com.jianyi.outfit.data.HighFrameRateApi
import com.jianyi.outfit.data.LocationProvider
import com.jianyi.outfit.data.NotificationGate
import com.jianyi.outfit.data.PushScheduler
import com.jianyi.outfit.data.local.AppDatabase
import com.jianyi.outfit.data.local.WeatherCacheDatabase
import com.jianyi.outfit.data.local.buildAppDatabase
import com.jianyi.outfit.data.local.buildWeatherCacheDatabase
import com.jianyi.outfit.data.repository.CityRepository
import com.jianyi.outfit.data.repository.CityRepositoryImpl
import com.jianyi.outfit.data.repository.OutfitTemplateRepository
import com.jianyi.outfit.data.repository.OutfitTemplateRepositoryImpl
import com.jianyi.outfit.data.repository.SettingsRepository
import com.jianyi.outfit.data.repository.SettingsRepositoryImpl
import com.jianyi.outfit.data.repository.WeatherRepository
import com.jianyi.outfit.data.repository.WeatherRepositoryImpl
import com.jianyi.outfit.notification.DailyPushScheduler
import com.jianyi.outfit.notification.Notifier
import com.jianyi.outfit.util.ActivityHolder
import com.jianyi.outfit.util.AndroidHighFrameRate
import com.jianyi.outfit.util.AndroidLocationProvider
import com.jianyi.outfit.util.AndroidNotificationGate
import com.jianyi.outfit.util.LocationUtil
import com.jianyi.outfit.ui.scenery.SceneryController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 手动依赖容器（项目体量小，不引入 Hilt 等框架）。
 * 由 Application 持有，全 app 单例。
 * 字段类型为仓库接口，ViewModel 只依赖抽象，便于 JVM 单测替换实现。
 *
 * 实现 shared 的 AppDependencies：shared 里的 ViewModel（设置/详情）只认这个接口，
 * 不再拿 Application 转 AppContainer——这是 ViewModel 跨端的 DI 接缝。
 */
class AppContainer(context: Context) : AppDependencies {

    /**
     * Room 业务库：历史城市、穿搭模板。
     * 库定义与开库函数都在 shared（Room 2.7 起是 KMP 库），androidMain 那份
     * buildAppDatabase(context) 与升级前逐字同参数，数据目录不变。
     */
    val database: AppDatabase = buildAppDatabase(context)

    /** 天气缓存库（独立文件 weather_cache.db，备份规则按文件排除） */
    private val cacheDatabase: WeatherCacheDatabase = buildWeatherCacheDatabase(context)

    /** 设置仓库（Preferences DataStore）：需先于天气仓库创建，供其读取凭证 */
    override val settingsRepository: SettingsRepository =
        SettingsRepositoryImpl(context.applicationContext)

    /** 天气数据仓库：凭证取值「用户自填优先，否则 BuildConfig 默认」
     *  不再需要 gson —— 序列化随网络层一起搬进了 shared（kotlinx.serialization） */
    override val weatherRepository: WeatherRepository = WeatherRepositoryImpl(
        credentials = settingsRepository.apiCredentials,
        cacheDao = cacheDatabase.weatherCacheDao()
    )

    /** 城市仓库 */
    val cityRepository: CityRepository = CityRepositoryImpl(database.cityDao())

    /** 穿搭模板仓库 */
    override val templateRepository: OutfitTemplateRepository =
        OutfitTemplateRepositoryImpl(database.outfitTemplateDao())

    /** 每日推送调度能力：Android 侧包装 WorkManager 静态工具 */
    override val pushScheduler: PushScheduler = object : PushScheduler {
        override fun ensureScheduled(pushHour: Int) =
            DailyPushScheduler.ensureScheduled(context.applicationContext, pushHour)

        override fun cancel() = DailyPushScheduler.cancel(context.applicationContext)
    }

    /** 定位工具 */
    val locationUtil: LocationUtil = LocationUtil(context.applicationContext)

    /** 当前可弹窗的 Activity；MainActivity 在 onCreate 挂、onDestroy 摘 */
    val activities: ActivityHolder = ActivityHolder()

    /**
     * 定位能力（shared 的 ViewModel 与页面只认这个接口）。
     * 声明在 locationUtil / activities 之后：属性初始化按书写顺序执行，
     * 提前引用会拿到尚未赋值的对象。
     */
    override val locationProvider: LocationProvider =
        AndroidLocationProvider(context.applicationContext, locationUtil, activities)

    /** 通知授权（Android 13 起才是运行时权限） */
    override val notificationGate: NotificationGate =
        AndroidNotificationGate(context.applicationContext, activities)

    /** 高帧率状态视图：转发给 FrameRate，设置页因此不必 import Android-only 工具 */
    override val highFrameRate: HighFrameRateApi = AndroidHighFrameRate

    /** 极端天气预警通知：包装 NotificationCompat 的静态工具 */
    override val extremeAlerter: ExtremeAlerter = object : ExtremeAlerter {
        override fun show(title: String, text: String) =
            Notifier.showExtremeAlert(context.applicationContext, title, text)
    }

    /**
     * 风景背景控制器（全应用单例）。
     * 背景必须跨页面连续，所以不能放进各页的 ViewModel。
     *
     * 注意这里不放天气快照：详情页统一按导航携带的缓存 key 从仓库读，
     * 全 app 不留可变单例，避免 null / 旧值竞态。
     */
    override val sceneryController: SceneryController = SceneryController()

    /**
     * 当前城市的跨端最小视图。
     *
     * CityRepository 本身留在 app（它的签名带 Room 的 CityEntity），
     * 这里映射成 CitySelection 只暴露 shared 真正需要的两个字段，
     * 免得 Room 类型顺着接口渗进跨端代码。
     */
    override val currentCity: Flow<CitySelection?> =
        cityRepository.currentCity.map { entity ->
            entity?.let { CitySelection(it.province, it.city) }
        }

}
