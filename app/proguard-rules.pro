# ===== 简衣 WeatherOutfit 混淆规则 =====

# Gson 序列化的数据模型不能被混淆（依赖字段名反射）
-keep class com.jianyi.outfit.data.remote.** { *; }
-keep class com.jianyi.outfit.data.model.** { *; }

# Retrofit / OkHttp 通用规则
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepattributes Signature
-keepattributes *Annotation*
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# 协程与 Google Play Services 无用警告
-dontwarn kotlinx.coroutines.**
-dontwarn com.google.android.gms.**
