# ===== 简衣 WeatherOutfit 混淆规则 =====

# Gson 序列化的数据模型不能被混淆（依赖字段名反射）
-keep class com.jianyi.outfit.data.remote.** { *; }
-keep class com.jianyi.outfit.data.model.** { *; }

# 更新清单那一层：同类的"按名 keep 数据模型"，但**理由和上面两条不一样，写清楚免得后人照抄错的前提**。
#
# 上面那两条防的是 Gson 的字段名反射。`UpdateManifest` 用的是 kotlinx.serialization，而它是
# **反射无关**的：元素名由 @Serializable 编译插件写进生成的 `UpdateManifest$$serializer`，
# 当字符串常量用。所以"R8 把清单模型改名 ⇒ parse() 返回 null ⇒ 没人收到更新"这条症状对它**不成立**
# —— 本轮实测过反例：摘掉下面这一行重出 release 包，mapping 里
#   com.jianyi.outfit.data.update.UpdateManifest          -> a3.x
#   com.jianyi.outfit.data.update.UpdateManifest$$serializer -> a3.v
# 两个名字都改了，而 kotlinx 自带的 consumer 规则（kotlinx-serialization-common.pro）
# 确实在合并进 R8 的配置里。
#
# 那这条 keep 买的是什么：保险，不是修复。防的是将来有人把清单模型改交给反射式编解码、
# 或者 R8 把 Companion.serializer() / 构造函数 shrink 掉那一类漂移，成本是零。
# 真正**没有证据**的是 release 这一侧本身 —— debug 包不混淆，而 README 第九节的真机清单跑的是
# assembleDebug 产物，所以那条链上任何 release 专属的故障今天都看不见。把这件事变成证据的动作
# 是 README 第九节新加的那一项（装一次 release 产物读 logcat -s JianyiUpdate），不是这一行。
-keep class com.jianyi.outfit.data.update.** { *; }

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
