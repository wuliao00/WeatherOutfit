package com.jianyi.outfit.di

import android.content.Context
import com.google.gson.Gson
import com.jianyi.outfit.data.local.AppDatabase
import com.jianyi.outfit.data.local.WeatherCacheDatabase
import com.jianyi.outfit.data.repository.CityRepository
import com.jianyi.outfit.data.repository.CityRepositoryImpl
import com.jianyi.outfit.data.repository.OutfitTemplateRepository
import com.jianyi.outfit.data.repository.OutfitTemplateRepositoryImpl
import com.jianyi.outfit.data.repository.SettingsRepository
import com.jianyi.outfit.data.repository.SettingsRepositoryImpl
import com.jianyi.outfit.data.repository.WeatherRepository
import com.jianyi.outfit.data.repository.WeatherRepositoryImpl
import com.jianyi.outfit.util.LocationUtil

/**
 * 手动依赖容器（项目体量小，不引入 Hilt 等框架）。
 * 由 Application 持有，全 app 单例。
 * 字段类型为仓库接口，ViewModel 只依赖抽象，便于 JVM 单测替换实现。
 */
class AppContainer(context: Context) {

    /** Room 业务库：历史城市、穿搭模板 */
    val database: AppDatabase = AppDatabase.build(context)

    /** 天气缓存库（独立文件 weather_cache.db，备份规则按文件排除） */
    private val cacheDatabase: WeatherCacheDatabase = WeatherCacheDatabase.build(context)

    /** JSON 序列化（缓存与模板清单存储共用） */
    val gson: Gson = Gson()

    /** 设置仓库（Preferences DataStore）：需先于天气仓库创建，供其读取凭证 */
    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(context.applicationContext)

    /** 天气数据仓库：凭证取值「用户自填优先，否则 BuildConfig 默认」 */
    val weatherRepository: WeatherRepository = WeatherRepositoryImpl(
        credentials = settingsRepository.apiCredentials,
        gson = gson,
        cacheDao = cacheDatabase.weatherCacheDao()
    )

    /** 城市仓库 */
    val cityRepository: CityRepository = CityRepositoryImpl(database.cityDao())

    /** 穿搭模板仓库 */
    val templateRepository: OutfitTemplateRepository =
        OutfitTemplateRepositoryImpl(database.outfitTemplateDao(), gson)

    /** 定位工具 */
    val locationUtil: LocationUtil = LocationUtil(context.applicationContext)
}
