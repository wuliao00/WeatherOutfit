package com.jianyi.outfit

import com.jianyi.outfit.data.model.ToleranceLevel
import com.jianyi.outfit.data.model.UserPreferences
import com.jianyi.outfit.data.model.WeatherAlarm
import com.jianyi.outfit.data.model.WeatherNow
import com.jianyi.outfit.engine.OutfitRecommendationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 穿搭推荐引擎单元测试：覆盖温度分档、辅助维度叠加与偏好修正。
 */
class OutfitRecommendationEngineTest {

    private fun weather(
        temperature: Double = 26.0,
        feelsLike: Double? = temperature,
        humidity: Int? = 50,
        condition: String = "多云",
        uvIndex: Int = 3,
        windScaleText: String? = "微风",
        windSpeedMs: Double? = 1.0,
        dayHigh: Double? = null,
        dayLow: Double? = null,
        alarms: List<WeatherAlarm> = emptyList()
    ) = WeatherNow(
        province = "四川",
        city = "成都",
        temperature = temperature,
        feelsLike = feelsLike,
        humidity = humidity,
        condition = condition,
        iconUrl = null,
        windDirection = "南风",
        windScaleText = windScaleText,
        windSpeedMs = windSpeedMs,
        precipitation = 0.0,
        dayHigh = dayHigh,
        dayLow = dayLow,
        uvIndex = uvIndex,
        uvLevel = OutfitRecommendationEngine.uvLevelText(uvIndex),
        updateTime = "2026-08-22 12:00:00",
        alarms = alarms
    )

    @Test
    fun `高温推荐短袖短裤`() {
        val result = OutfitRecommendationEngine.recommend(weather(temperature = 30.0), UserPreferences())
        val casual = result.plans.first { it.scene == OutfitRecommendationEngine.SCENE_CASUAL }
        assertTrue(casual.items.contains("短袖"))
        assertTrue(casual.items.contains("短裤"))
    }

    @Test
    fun `寒冷推荐羽绒服围巾手套`() {
        val result = OutfitRecommendationEngine.recommend(weather(temperature = 2.0), UserPreferences())
        val items = result.plans.flatMap { it.items }
        assertTrue(items.contains("羽绒服"))
        assertTrue(items.contains("围巾"))
    }

    @Test
    fun `强紫外线叠加防晒单品`() {
        val result = OutfitRecommendationEngine.recommend(
            weather(temperature = 26.0, uvIndex = 7),
            UserPreferences()
        )
        val items = result.plans.flatMap { it.items }
        assertTrue(items.contains("防晒衣"))
        assertTrue(items.contains("墨镜"))
        assertTrue(result.reminders.any { it.contains("紫外线") })
    }

    @Test
    fun `大风叠加防风外套`() {
        val result = OutfitRecommendationEngine.recommend(
            weather(temperature = 22.0, windScaleText = "5~6级"),
            UserPreferences()
        )
        assertTrue(result.plans.flatMap { it.items }.contains("防风外套"))
    }

    @Test
    fun `高湿度叠加透气与雨具`() {
        val result = OutfitRecommendationEngine.recommend(
            weather(temperature = 22.0, humidity = 85),
            UserPreferences()
        )
        val items = result.plans.flatMap { it.items }
        assertTrue(items.contains("透气面料单品"))
        assertTrue(items.contains("折叠雨伞"))
    }

    @Test
    fun `怕冷人群体感修正后穿得更多`() {
        // 体感 21.5℃：中等 → 20~27 档（薄款长袖）；
        // 怕冷人群 -2℃ → 19.5℃ 落入 10~19 档（卫衣 + 外套，穿得更多）
        // 注：通勤/户外场景会改写单品名称（如“薄款长袖”→“挺括衬衫”），
        // 断言基础分档单品需使用保持原名不变的休闲场景。
        val medium = OutfitRecommendationEngine.recommend(
            weather(temperature = 21.5), UserPreferences()
        )
        val lowTolerance = OutfitRecommendationEngine.recommend(
            weather(temperature = 21.5),
            UserPreferences(coldHeatTolerance = ToleranceLevel.LOW)
        )
        val mediumCasual = medium.plans.first { it.scene == OutfitRecommendationEngine.SCENE_CASUAL }
        val lowCasual = lowTolerance.plans.first { it.scene == OutfitRecommendationEngine.SCENE_CASUAL }
        assertTrue(mediumCasual.items.contains("薄款长袖"))
        assertTrue(lowCasual.items.contains("卫衣"))
    }

    @Test
    fun `预警进入出行提醒`() {
        val alarm = WeatherAlarm("暴雨黄色预警", "暴雨", "黄色", "2026-08-22")
        val result = OutfitRecommendationEngine.recommend(
            weather(temperature = 22.0, condition = "大雨", alarms = listOf(alarm)),
            UserPreferences()
        )
        assertTrue(result.reminders.contains("暴雨黄色预警"))
    }

    @Test
    fun `风力文字解析取上限`() {
        assertEquals(4, OutfitRecommendationEngine.parseWindScale("3~4级", null))
        assertEquals(2, OutfitRecommendationEngine.parseWindScale("微风", null))
        assertEquals(5, OutfitRecommendationEngine.parseWindScale(null, 10.0))
    }

    @Test
    fun `紫外线估算_晴午最高_夜间为零`() {
        assertEquals(7, OutfitRecommendationEngine.estimateUvIndex("晴", 12))
        assertEquals(0, OutfitRecommendationEngine.estimateUvIndex("晴", 22))
        assertEquals(1, OutfitRecommendationEngine.estimateUvIndex("雷阵雨", 12))
    }
}
