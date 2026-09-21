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
    val alarms: List<WeatherAlarm>, // 生效中的气象预警
    /* ---- 以下字段仅经纬度端点提供，缺省为 null，UI 需自行降级显示 ---- */
    val pressureHpa: Int? = null,   // 气压（hPa）
    val visibilityM: Int? = null,   // 能见度（米）
    val cloudCover: Int? = null,    // 云量（%）
    val sunriseAt: Long? = null,    // 日出时间戳（秒）
    val sunsetAt: Long? = null      // 日落时间戳（秒）
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

/* ============ 视觉与性能偏好 ============ */

/** 背景风景的选取方式 */
enum class SceneryMode(val label: String) {
    /** 跟随实况天气与时段自动换景（默认） */
    AUTO_WEATHER("跟随天气"),

    /** 固定使用用户手选的那一张 */
    FIXED("固定一张"),

    /** 每天自动轮换一张，与天气无关 */
    DAILY_ROTATE("每日轮换");

    companion object {
        fun safe(value: String?) = entries.firstOrNull { it.name == value } ?: AUTO_WEATHER
    }
}

/**
 * 玻璃模糊档位。
 *
 * 真实背景模糊（Android 12+ RenderEffect）每一层都要过一次 GPU，所以把
 * 「好不好看」和「流不流畅」拆开交给用户。默认是 REALTIME —— 液态玻璃本来就是
 * 靠「能看穿背景」成立的，卡片一旦改用静态着色就退化成一块灰色圆角板。
 * 早先在 Android 11 / 60Hz 的测试机上默认给的是 BALANCED，那台机器既没有
 * RenderEffect 也撑不住全屏多图层模糊；现在的支持面变了，默认值跟着变。
 * 系统低于 Android 12 时 [GlassHost] 会自行逐级降级，不会因为选了 REALTIME 而白屏。
 *
 * - REALTIME   ：卡片与顶栏都做实时模糊，最贴近液态玻璃（默认）
 * - BALANCED   ：仅顶栏/浮层实时模糊，卡片用静态着色
 * - PERFORMANCE：全部静态着色，零运行时模糊开销，省电且必然满帧
 */
enum class GlassQuality(val label: String, val desc: String) {
    REALTIME("全实时", "卡片与顶栏都实时采样背景，最通透（推荐）"),
    BALANCED("均衡", "顶栏实时模糊，卡片用静态着色"),
    PERFORMANCE("流畅优先", "全部静态着色，省电且不掉帧");

    companion object {
        fun safe(value: String?) = entries.firstOrNull { it.name == value } ?: REALTIME
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
    val extremeAlertEnabled: Boolean = true,
    /* ---- 视觉与性能 ---- */
    val sceneryMode: SceneryMode = SceneryMode.AUTO_WEATHER,
    /** Scenery.key；sceneryMode = FIXED 时生效，为空表示尚未选择 */
    val sceneryKey: String? = null,
    val glassQuality: GlassQuality = GlassQuality.REALTIME,
    /** 滚动视差：背景随列表反向位移，制造纵深 */
    val parallaxEnabled: Boolean = true,
    /** 呼吸漂移：背景做极缓慢的缩放漂移，画面不「死」 */
    val breathingEnabled: Boolean = true,
    /** 高帧率：向系统申请以最高刷新率档位渲染（帧率类别 API 需 Android 15+） */
    val highFrameRateEnabled: Boolean = true
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

/**
 * 生活指数（穿衣 / 防晒 / 运动 / 洗车 / 感冒）。
 *
 * 注意：接口本身不返回这些指数，全部由本地规则从实况数据推导，
 * 因此 UI 上必须标注「本地估算」，不能让用户误以为是官方指数。
 */
data class LifeIndex(
    val key: String,
    val name: String,
    /** 适宜 / 较适宜 / 一般 / 较不适宜 / 不适宜 */
    val level: String,
    /** 1..5，越大越适宜，用于画点状刻度 */
    val score: Int,
    val advice: String
) {
    companion object {
        /** score → 等级文案 */
        fun levelText(score: Int): String = when (score.coerceIn(1, 5)) {
            1 -> "不适宜"
            2 -> "较不适宜"
            3 -> "一般"
            4 -> "较适宜"
            else -> "适宜"
        }
    }
}

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
