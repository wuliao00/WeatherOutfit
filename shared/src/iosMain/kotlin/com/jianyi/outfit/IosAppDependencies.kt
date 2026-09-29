package com.jianyi.outfit

import com.jianyi.outfit.data.AppDependencies
import com.jianyi.outfit.data.AppUpdateGateway
import com.jianyi.outfit.data.AppVersion
import com.jianyi.outfit.data.CitySelection
import com.jianyi.outfit.data.ExtremeAlerter
import com.jianyi.outfit.data.HighFrameRateApi
import com.jianyi.outfit.data.LocationProvider
import com.jianyi.outfit.data.NotificationGate
import com.jianyi.outfit.data.PushScheduler
import com.jianyi.outfit.data.local.WeatherCacheDatabase
import com.jianyi.outfit.data.local.buildAppDatabase
import com.jianyi.outfit.data.local.buildWeatherCacheDatabase
import com.jianyi.outfit.data.repository.CityRepository
import com.jianyi.outfit.data.repository.CityRepositoryImpl
import com.jianyi.outfit.data.repository.NSUserDefaultsPreferenceBackend
import com.jianyi.outfit.data.repository.OutfitTemplateRepository
import com.jianyi.outfit.data.repository.OutfitTemplateRepositoryImpl
import com.jianyi.outfit.data.repository.PreferenceBackend
import com.jianyi.outfit.data.repository.SettingsRepository
import com.jianyi.outfit.data.repository.SettingsRepositoryImpl
import com.jianyi.outfit.data.repository.WeatherRepository
import com.jianyi.outfit.data.repository.WeatherRepositoryImpl
import com.jianyi.outfit.platform.IosExtremeAlerter
import com.jianyi.outfit.platform.IosHighFrameRate
import com.jianyi.outfit.platform.IosLocationProvider
import com.jianyi.outfit.platform.IosNotificationGate
import com.jianyi.outfit.platform.IosPushScheduler
import com.jianyi.outfit.platform.IosUpdateGateway
import com.jianyi.outfit.ui.scenery.SceneryController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * iOS 侧的依赖容器，与 Android 的 AppContainer 对位：实现同一套 AppDependencies，
 * 页面与 ViewModel 拿到的东西两边完全一样。
 *
 * 三条与 Android 不同的事实，都写在这里而不是藏在实现里：
 *
 * 1. **没有内置的 API 凭证**。Android 有 BuildConfig 兜底（仓库内置一对演示 key），
 *    iOS 没有对应的生成物，于是 defaultApiId / defaultApiKey 传空 ——
 *    第一次启动必须在设置页自填 apihz 的 id/key 才能取到天气。
 *    这不是缺陷，是"别人的 key 不该打进别人的 app"这条老规矩的必然结果。
 * 2. 数据库文件名与目录布局沿用 commonMain 那份（Documents/jianyi.db 等），
 *    开库时显式给了 bundled SQLite 驱动，理由见 DatabaseBuilders.ios.kt。
 * 3. 每日推送与极端天气预警都走本地通知，语义见 IosCapabilities 里的注释。
 */
class IosAppDependencies : AppDependencies {

    private val appDatabase = buildAppDatabase()
    private val cacheDatabase: WeatherCacheDatabase = buildWeatherCacheDatabase()

    /** 偏好落盘：NSUserDefaults（单个带类型标签的 JSON） */
    private val preferenceBackend: PreferenceBackend = NSUserDefaultsPreferenceBackend()

    override val settingsRepository: SettingsRepository = SettingsRepositoryImpl(preferenceBackend)

    override val weatherRepository: WeatherRepository = WeatherRepositoryImpl(
        credentials = settingsRepository.apiCredentials,
        cacheDao = cacheDatabase.weatherCacheDao(),
        defaultApiId = "",
        defaultApiKey = ""
    )

    override val cityRepository: CityRepository = CityRepositoryImpl(appDatabase.cityDao())

    override val templateRepository: OutfitTemplateRepository =
        OutfitTemplateRepositoryImpl(appDatabase.outfitTemplateDao())

    override val pushScheduler: PushScheduler = IosPushScheduler()

    override val locationProvider: LocationProvider = IosLocationProvider()

    override val notificationGate: NotificationGate = IosNotificationGate()

    override val extremeAlerter: ExtremeAlerter = IosExtremeAlerter()

    /**
     * 占位值，不是"iOS 上的真实版本"：updateGateway.supported 恒 false，
     * 门禁与设置页入口整块不渲染，判定链根本走不到读这个字段的那一步。
     *
     * 写成常量而不是去解析 Info.plist 的 CFBundleVersion，是为了不在这条永远走不到的
     * 路上引入新的平台代码。将来若真要做 TestFlight 引导，再换成读 plist。
     */
    override val appVersion: AppVersion = AppVersion(versionCode = 1, versionName = "0.0.0")

    /** iOS 不提供应用内更新（App Store 规则），详见 [IosUpdateGateway] */
    override val updateGateway: AppUpdateGateway = IosUpdateGateway()

    override val highFrameRate: HighFrameRateApi = IosHighFrameRate

    override val sceneryController: SceneryController = SceneryController()

    /** 与 Android 侧同一条映射：页面只要省市两个字段，不该感知 Room 的行类型 */
    override val currentCity: Flow<CitySelection?> =
        cityRepository.currentCity.map { entity ->
            entity?.let { CitySelection(it.province, it.city) }
        }
}
