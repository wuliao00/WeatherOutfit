package com.jianyi.outfit.data.model

/**
 * 天气实况领域模型。
 * UI 层只依赖本文件中的领域模型，不直接接触网络 DTO，便于替换数据源。
 */
data class WeatherNow(
    val province: String,           // 省份，如“四川”
    val city: String,               // 城市，如“成都”
    val temperature: Double,        // 当前气温（℃）
    val feelsLike: Double?,         // 体感温度（℃），接口可能缺失
    val humidity: Int?,             // 相对湿度（%）
    val condition: String,          // 天气现象文字，如“多云”“中雨”
    val iconUrl: String?,           // 天气图标图片地址
    val windDirection: String?,     // 风向文字，如“东南风”
    val windScaleText: String?,     // 风力原始文字，如“3~4级”“微风”
    val windSpeedMs: Double?,       // 风速（m/s）
    val precipitation: Double?,     // 当前降水量（mm）
    val dayHigh: Double?,           // 今日最高温（℃）
    val dayLow: Double?,            // 今日最低温（℃）
    val uvIndex: Int,               // 紫外线指数（接口未提供，按天气现象估算）
    val uvLevel: String,            // 紫外线等级描述，如“中等”
    val updateTime: String,         // 数据更新时间
    val alarms: List<WeatherAlarm>  // 生效中的气象预警
) {
    /** 首页展示用的定位描述 */
    val locationText: String get() = if (province.isNotBlank()) "$province·$city" else city
}

/** 气象预警（台风/暴雨/高温等） */
data class WeatherAlarm(
    val title: String,          // 预警完整标题
    val signalType: String,     // 预警类型，如“暴雨”
    val signalLevel: String,    // 预警级别，如“黄色”
    val effective: String       // 生效时间
)

/** 7 天预报单日条目 */
data class ForecastDay(
    val date: String?,       // 日期
    val condition: String?,  // 天气现象
    val lowTemp: Int?,       // 最低温（℃）
    val highTemp: Int?       // 最高温（℃）
)

/* ============ 用户偏好相关枚举 ============ */

/** 温度单位 */
enum class TempUnit(val label: String) {
    CELSIUS("℃"), FAHRENHEIT("℉");

    companion object {
        fun safe(value: String?) = entries.firstOrNull { it.name == value } ?: CELSIUS
    }
}

/** 风力单位 */
enum class WindUnit(val label: String) {
    BEAUFORT("级"), MS("m/s");

    companion object {
        fun safe(value: String?) = entries.firstOrNull { it.name == value } ?: BEAUFORT
    }
}

/** 耐寒/耐热程度：低 = 对温度敏感（更怕冷怕热），高 = 耐受力强 */
enum class ToleranceLevel(val label: String) {
    LOW("偏低"), MEDIUM("中等"), HIGH("偏高");

    companion object {
        fun safe(value: String?) = entries.firstOrNull { it.name == value } ?: MEDIUM
    }
}

/** 常用穿搭风格 */
enum class StylePreference(val label: String) {
    MINIMALIST("简约"), CASUAL("休闲"), COMMUTE("通勤");

    companion object {
        fun safe(value: String?) = entries.firstOrNull { it.name == value } ?: MINIMALIST
    }
}

/** 性别（影响个别推荐细节） */
enum class Gender(val label: String) {
    MALE("男"), FEMALE("女"), UNKNOWN("保密");

    companion object {
        fun safe(value: String?) = entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

/** 用户偏好（DataStore 持久化） */
data class UserPreferences(
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val windUnit: WindUnit = WindUnit.BEAUFORT,
    val coldHeatTolerance: ToleranceLevel = ToleranceLevel.MEDIUM,
    val style: StylePreference = StylePreference.MINIMALIST,
    val gender: Gender = Gender.UNKNOWN,
    val dailyPushEnabled: Boolean = false,
    val dailyPushHour: Int = DEFAULT_DAILY_PUSH_HOUR,
    val extremeAlertEnabled: Boolean = true
) {
    companion object {
        /** 每日推送默认时刻：早上 8 点 */
        const val DEFAULT_DAILY_PUSH_HOUR = 8
    }
}

/* ============ 穿搭推荐相关模型 ============ */

/** 单场景穿搭方案 */
data class OutfitPlan(
    val scene: String,        // 场景名称：通勤 / 户外 / 休闲
    val tempRange: String,    // 适配温度范围描述
    val items: List<String>,  // 穿搭清单
    val tip: String           // 搭配小贴士
)

/** 推荐结果：一句话摘要 + 三场景方案 + 出行提醒 */
data class OutfitRecommendation(
    val summary: String,
    val plans: List<OutfitPlan>,
    val reminders: List<String>
)

/** 用户自定义穿搭模板（Room 持久化） */
data class CustomOutfitTemplate(
    val id: Long = 0L,
    val name: String,
    val scene: String,          // 通勤 / 户外 / 休闲
    val minTemp: Int,           // 适配最低温（℃）
    val maxTemp: Int,           // 适配最高温（℃）
    val items: List<String>,    // 单品清单
    val tip: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** 适配温度范围展示文案 */
    val tempRangeText: String get() = "${minTemp}℃ ~ ${maxTemp}℃"
}
