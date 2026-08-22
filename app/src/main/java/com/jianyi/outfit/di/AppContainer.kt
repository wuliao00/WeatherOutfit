package com.jianyi.outfit.di

import android.content.Context
import com.google.gson.Gson
import com.jianyi.outfit.BuildConfig
import com.jianyi.outfit.data.local.AppDatabase
import com.jianyi.outfit.data.model.WeatherNow
import com.jianyi.outfit.data.repository.CityRepository
import com.jianyi.outfit.data.repository.OutfitTemplateRepository
import com.jianyi.outfit.data.repository.SettingsRepository
import com.jianyi.outfit.data.repository.WeatherRepository
import com.jianyi.outfit.util.LocationUtil

/**
 * 手动依赖容器（项目体量小，不引入 Hilt 等框架）。
 * 由 Application 持有，全 app 单例。
 */
class AppContainer(context: Context) {

    /** Room 数据库 */
    val database: AppDatabase = AppDatabase.build(context)

    /** JSON 序列化（缓存与模板清单存储共用） */
    val gson: Gson = Gson()

    /** 天气数据仓库（凭证经 BuildConfig 注入） */
    val weatherRepository: WeatherRepository = WeatherRepository(
        apiId = BuildConfig.WEATHER_API_ID,
        apiKey = BuildConfig.WEATHER_API_KEY,
        gson = gson,
        cacheDao = database.weatherCacheDao()
    )

    /** 城市仓库 */
    val cityRepository: CityRepository = CityRepository(database.cityDao())

    /** 设置仓库（Preferences DataStore） */
    val settingsRepository: SettingsRepository = SettingsRepository(context.applicationContext)

    /** 穿搭模板仓库 */
    val templateRepository: OutfitTemplateRepository =
        OutfitTemplateRepository(database.outfitTemplateDao(), gson)

    /** 定位工具 */
    val locationUtil: LocationUtil = LocationUtil(context.applicationContext)

    /**
     * 当前会话的天气快照：
     * 首页加载后供穿搭详情页复用，避免重复请求。
     */
    @Volatile
    var sessionWeather: WeatherNow? = null
}
