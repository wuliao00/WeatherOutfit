package com.jianyi.outfit.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 工厂单例：统一超时配置与客户端复用。
 * OkHttpClient 全局仅构建一次（连接池 / 线程池复用）；
 * baseUrl 支持用户自定义（设置页自填接口地址），服务实例按地址缓存。
 */
object RetrofitClient {

    /** 默认接口基础地址（apihz.cn 中国气象局数据） */
    const val DEFAULT_BASE_URL = "https://cn.apihz.cn/"

    /** 全局共享的 OkHttpClient（连接池与线程池复用，避免每次请求重建） */
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** 创建（或复用）指定基础地址的天气接口服务实例 */
    fun create(baseUrl: String = DEFAULT_BASE_URL): WeatherApiService {
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(url)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }
}
