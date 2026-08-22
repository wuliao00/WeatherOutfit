package com.jianyi.outfit.engine

import com.jianyi.outfit.data.model.Gender
import com.jianyi.outfit.data.model.OutfitPlan
import com.jianyi.outfit.data.model.OutfitRecommendation
import com.jianyi.outfit.data.model.StylePreference
import com.jianyi.outfit.data.model.ToleranceLevel
import com.jianyi.outfit.data.model.UserPreferences
import com.jianyi.outfit.data.model.WeatherNow
import kotlin.math.roundToInt

/**
 * 穿搭推荐引擎：基于温度、湿度、紫外线、风力四个维度综合计算。
 *
 * 规则表（以体感温度为主维度，多条件叠加时合并推荐，冲突时以温度为主）：
 * - 温度 ≥ 28℃      → 短袖、短裤、防晒帽
 * - 20 ~ 27℃        → 薄款长袖、长裤
 * - 10 ~ 19℃        → 卫衣、外套、长裤
 * - 温度 ≤ 9℃       → 羽绒服、围巾、手套
 * - 紫外线指数 ≥ 5   → 防晒衣、墨镜
 * - 风力 ≥ 4 级      → 防风外套
 * - 湿度 ≥ 80%       → 透气面料、雨具
 *
 * 引擎为纯 Kotlin 实现（不依赖 Android 框架），便于单元测试与后续扩展调整。
 */
object OutfitRecommendationEngine {

    /** 三大场景常量 */
    const val SCENE_COMMUTE = "通勤"
    const val SCENE_OUTDOOR = "户外"
    const val SCENE_CASUAL = "休闲"

    /** 生成穿搭推荐 */
    fun recommend(weather: WeatherNow, prefs: UserPreferences): OutfitRecommendation {
        // 1. 以体感温度为基础，并按用户耐寒/耐热程度修正：
        //    偏低（怕冷怕热）→ 体感 -2℃，更早进入保暖档，倾向穿得更多；
        //    偏高（耐受力强）→ 体感 +2℃，更晚进入保暖档，倾向穿得更少
        val feelsLike = weather.feelsLike ?: weather.temperature
        val toleranceOffset = when (prefs.coldHeatTolerance) {
            ToleranceLevel.LOW -> -2.0
            ToleranceLevel.HIGH -> 2.0
            ToleranceLevel.MEDIUM -> 0.0
        }
        val effectiveTemp = feelsLike + toleranceOffset

        // 2. 温度主维度：确定基础单品与温度区间文案
        val baseItems = temperatureBucketItems(effectiveTemp)
        val tempRange = temperatureBucketRange(effectiveTemp)

        // 3. 辅助维度：紫外线 / 风力 / 湿度 / 降水 叠加推荐
        val extras = mutableListOf<String>()
        if (weather.uvIndex >= 5) extras += listOf("防晒衣", "墨镜")
        if (parseWindScale(weather.windScaleText, weather.windSpeedMs) >= 4) extras += "防风外套"
        if ((weather.humidity ?: 0) >= 80) extras += listOf("透气面料单品", "折叠雨伞")
        val rainy = hasPrecipitation(weather)
        if (rainy && "折叠雨伞" !in extras) extras += "折叠雨伞"

        // 4. 合并去重得到基础清单
        val allItems = (baseItems + extras).distinct()

        // 5. 出行提醒
        val reminders = mutableListOf<String>()
        val dayHigh = weather.dayHigh
        val dayLow = weather.dayLow
        if (dayHigh != null && dayLow != null && dayHigh - dayLow >= 8) {
            reminders += "早晚温差约 ${dayHigh.roundToInt()}℃ → ${dayLow.roundToInt()}℃，建议随身带一件外套"
        }
        if (weather.uvIndex >= 5) reminders += "紫外线较强，长时间在外注意防晒"
        if (parseWindScale(weather.windScaleText, weather.windSpeedMs) >= 4) reminders += "风力较大，出行优先选择防风外套"
        if (rainy) reminders += "今天有降水，记得带伞"
        weather.alarms.forEach { reminders += it.title }

        // 6. 一句话摘要（首页卡片展示）
        val summary = "${weather.condition}，体感 ${feelsLike.roundToInt()}℃，推荐 ${allItems.take(3).joinToString("、")}" +
            if (allItems.size > 3) "等 ${allItems.size} 件单品" else ""

        // 7. 生成三场景方案
        val commute = OutfitPlan(SCENE_COMMUTE, tempRange, commuteItems(allItems), commuteTip(effectiveTemp))
        val outdoor = OutfitPlan(SCENE_OUTDOOR, tempRange, outdoorItems(allItems, weather), outdoorTip(weather))
        val casual = OutfitPlan(SCENE_CASUAL, tempRange, casualItems(allItems, prefs.gender), casualTip(effectiveTemp))

        // 8. 按用户常用风格调整场景排序（常用风格排首位）
        val plans = when (prefs.style) {
            StylePreference.COMMUTE -> listOf(commute, casual, outdoor)
            StylePreference.CASUAL -> listOf(casual, commute, outdoor)
            StylePreference.MINIMALIST -> listOf(commute, casual, outdoor)
        }

        return OutfitRecommendation(summary = summary, plans = plans, reminders = reminders)
    }

    /* ============ 温度分档 ============ */

    /** 温度分档基础单品 */
    private fun temperatureBucketItems(temp: Double): List<String> = when {
        temp >= 28 -> listOf("短袖", "短裤", "防晒帽")
        temp >= 20 -> listOf("薄款长袖", "长裤")
        temp >= 10 -> listOf("卫衣", "薄外套", "长裤")
        else -> listOf("羽绒服", "围巾", "手套")
    }

    /** 温度分档区间文案 */
    private fun temperatureBucketRange(temp: Double): String = when {
        temp >= 28 -> "28℃ 以上"
        temp >= 20 -> "20 ~ 27℃"
        temp >= 10 -> "10 ~ 19℃"
        else -> "9℃ 以下"
    }

    /* ============ 场景化改造 ============ */

    /** 通勤：偏挺括面料，方便穿脱应对室内外温差 */
    private fun commuteItems(items: List<String>): List<String> {
        val mapping = mapOf(
            "短袖" to "短袖衬衫", "短裤" to "九分西裤", "薄款长袖" to "挺括衬衫",
            "长裤" to "通勤长裤", "卫衣" to "卫衣 + 休闲西装", "薄外套" to "薄西装外套",
            "羽绒服" to "长款羽绒服"
        )
        return (items.mapNotNull { mapping[it] }.ifEmpty { items } + listOf("方便穿脱的外套"))
            .distinct()
    }

    private fun commuteTip(temp: Double): String =
        if (temp in 10.0..27.0) "通勤场景建议选择挺括面料，地铁与写字楼温差大，外套方便穿脱最重要。"
        else "通勤场景以简洁利落为主，按清单叠穿即可。"

    /** 户外：强调防晒防风，面料速干透气 */
    private fun outdoorItems(items: List<String>, weather: WeatherNow): List<String> {
        val mapping = mapOf(
            "长裤" to "速干长裤", "短裤" to "速干短裤", "薄外套" to "防风薄外套",
            "防晒衣" to "防晒皮肤衣"
        )
        val base = items.mapNotNull { mapping[it] }.ifEmpty { items }
        val additions = buildList {
            add("运动鞋")
            if (weather.uvIndex >= 5) add("遮阳帽")
        }
        return (base + additions).distinct()
    }

    private fun outdoorTip(weather: WeatherNow): String = when {
        weather.uvIndex >= 5 -> "户外活动紫外线较强，防晒衣与遮阳帽必备，避开正午时段。"
        hasPrecipitation(weather) -> "今天有降水，户外活动请安排雨具并避开积水路段。"
        else -> "户外活动选择速干透气面料，活动前后注意及时增减衣物。"
    }

    /** 休闲：舒适宽松优先，可按性别补充细节单品 */
    private fun casualItems(items: List<String>, gender: Gender): List<String> {
        val base = items + "舒适休闲鞋"
        val extra = when (gender) {
            Gender.FEMALE -> when {
                items.contains("薄款长袖") -> listOf("薄款针织开衫")
                items.contains("羽绒服") -> listOf("加绒打底裤（可选）")
                else -> emptyList()
            }
            else -> emptyList()
        }
        return (base + extra).distinct()
    }

    private fun casualTip(temp: Double): String =
        if (temp >= 28) "高温天休闲出行以透气棉麻为主，浅色系更凉爽。"
        else "休闲场景以宽松舒适为主，按清单自由组合即可。"

    /* ============ 工具方法（对 Repository 开放） ============ */

    /**
     * 紫外线指数估算。
     * 接口未提供紫外线字段，按“天气现象 + 时间段”估算：
     * 晴 7 / 多云 4~5 / 阴 2 / 雨雪 1，夜间归零，早晚减半。
     */
    fun estimateUvIndex(condition: String, hour: Int): Int {
        val base = when {
            condition.contains("雨") || condition.contains("雪") || condition.contains("雷") -> 1
            condition.contains("阴") -> 2
            condition.contains("多云") -> 5
            condition.contains("晴") -> 7
            else -> 3
        }
        val factor = when (hour) {
            in 0..5, in 19..23 -> 0.0   // 夜间无紫外线
            in 6..8, in 17..18 -> 0.5   // 清晨/傍晚紫外线较弱
            else -> 1.0
        }
        return (base * factor).roundToInt().coerceIn(0, 11)
    }

    /** 紫外线等级描述 */
    fun uvLevelText(uvIndex: Int): String = when (uvIndex) {
        in 0..2 -> "最弱"
        in 3..4 -> "弱"
        in 5..6 -> "中等"
        in 7..9 -> "强"
        else -> "很强"
    }

    /**
     * 解析风力等级：优先解析文字（如“3~4级”取上限），取不到时按风速（m/s）换算。
     */
    fun parseWindScale(scaleText: String?, windSpeedMs: Double?): Int {
        scaleText?.let { text ->
            if (text.contains("微风")) return 2
            val numbers = Regex("\\d+").findAll(text).mapNotNull { it.value.toIntOrNull() }.toList()
            if (numbers.isNotEmpty()) return numbers.max()
        }
        windSpeedMs?.let { speed -> return msToBeaufort(speed) }
        return 2
    }

    /** 风速（m/s）换算蒲福风级 */
    private fun msToBeaufort(speed: Double): Int {
        val thresholds = doubleArrayOf(0.3, 1.6, 3.4, 5.5, 8.0, 10.8, 13.9, 17.2, 20.8, 24.5, 28.5, 32.7)
        return thresholds.indexOfFirst { speed < it }.let { if (it == -1) 12 else it }
    }

    /** 是否存在降水（天气现象含雨/雪，或当前降水量大于 0） */
    fun hasPrecipitation(weather: WeatherNow): Boolean =
        weather.condition.contains("雨") ||
            weather.condition.contains("雪") ||
            (weather.precipitation ?: 0.0) > 0.0
}
