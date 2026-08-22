package com.jianyi.outfit.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 单例工厂：统一基础地址与超时配置。
 */
object RetrofitClient {

    /** 接口基础地址（apihz.cn 中国气象局数据） */
    private const val BASE_URL = "https://cn.apihz.cn/"

    /** 创建天气接口服务实例 */
    fun create(): WeatherApiService {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }
}
