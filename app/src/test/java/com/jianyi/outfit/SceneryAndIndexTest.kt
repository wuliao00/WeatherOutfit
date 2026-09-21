package com.jianyi.outfit

import com.jianyi.outfit.data.model.LifeIndex
import com.jianyi.outfit.data.model.ToleranceLevel
import com.jianyi.outfit.data.model.UserPreferences
import com.jianyi.outfit.data.model.WeatherAlarm
import com.jianyi.outfit.data.model.WeatherNow
import com.jianyi.outfit.engine.LifeIndexEngine
import com.jianyi.outfit.ui.scenery.Scenery
import com.jianyi.outfit.ui.scenery.SceneryResolver
import com.jianyi.outfit.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自动选景与生活指数的规则测试。
 *
 * 这两块都是「界面看起来对不对，全靠规则本身站不站得住」的逻辑，
 * 而且优先级顺序很容易被后续改动破坏（比如有人把「晴」的判断提到「雨」前面，
 * 雷阵雨的中午就会变成海岸落日），所以必须钉住。
 */
class SceneryAndIndexTest {

    private fun weather(
        temperature: Double = 22.0,
        feelsLike: Double? = temperature,
        humidity: Int? = 60,
        condition: String = "多云",
        uvIndex: Int = 3,
        windScaleText: String? = "微风",
        precipitation: Double? = 0.0,
        dayHigh: Double? = null,
        dayLow: Double? = null
    ) = WeatherNow(
        province = "广东",
        city = "中山",
        temperature = temperature,
        feelsLike = feelsLike,
        humidity = humidity,
        condition = condition,
        iconUrl = null,
        windDirection = "南风",
        windScaleText = windScaleText,
        windSpeedMs = 1.5,
        precipitation = precipitation,
        dayHigh = dayHigh,
        dayLow = dayLow,
        uvIndex = uvIndex,
        uvLevel = "弱",
        updateTime = "2026-09-21 15:20:00",
        alarms = emptyList()
    )

    private fun sceneryFor(
        condition: String,
        temp: Double = 22.0,
        hour: Int = 13,
        wind: Int = 2,
        month: Int = 9
    ) = SceneryResolver.pick(condition, temp, hour, wind, month).scenery

    /* ============ 自动选景 ============ */

    @Test
    fun `降水优先于温度与晴雨判断`() {
        // 34℃ 的雷阵雨：高温会把人误导到「海岸落日」，但下雨必须先看雨
        assertEquals(Scenery.RAIN_HILLS, sceneryFor("雷阵雨", temp = 34.0, hour = 13))
        assertEquals(Scenery.RAIN_HILLS, sceneryFor("中雨"))
    }

    @Test
    fun `夜间一律星野`() {
        assertEquals(Scenery.NIGHT_STARS, sceneryFor("晴", hour = 22))
        assertEquals(Scenery.NIGHT_STARS, sceneryFor("晴", hour = 4))
    }

    @Test
    fun `降雪与极寒走雪原`() {
        assertEquals(Scenery.SNOW_PINE, sceneryFor("中雪"))
        assertEquals(Scenery.SNOW_PINE, sceneryFor("晴", temp = -3.0, hour = 13))
    }

    @Test
    fun `晴天的清晨与傍晚给不同光感`() {
        assertEquals(Scenery.DAWN_RIDGE, sceneryFor("晴", hour = 7))
        assertEquals(Scenery.COAST_SUNSET, sceneryFor("晴", hour = 17))
        assertEquals(Scenery.ALPINE_LAKE, sceneryFor("晴", hour = 13))
    }

    @Test
    fun `大风走秋谷`() {
        assertEquals(Scenery.AUTUMN_VALLEY, sceneryFor("多云", temp = 20.0, wind = 7))
    }

    @Test
    fun `每日轮换在一年内稳定且可回到首张`() {
        val first = Scenery.forDay(1)
        assertEquals(first, Scenery.forDay(1 + Scenery.ordered.size * 3))
        assertTrue(Scenery.ordered.contains(Scenery.forDay(99)))
    }

    @Test
    fun `主题键值可反查且唯一`() {
        assertEquals(Scenery.RAIN_HILLS, Scenery.fromKey("rain_hills"))
        assertEquals(null, Scenery.fromKey("not_a_theme"))
        assertEquals(
            Scenery.ordered.size,
            Scenery.ordered.map { it.key }.distinct().size
        )
    }

    /* ============ 生活指数 ============ */

    @Test
    fun `穿衣指数随体感温度下降`() {
        val hot = LifeIndexEngine.compute(weather(temperature = 33.0), UserPreferences())
            .first { it.key == "clothing" }
        val cold = LifeIndexEngine.compute(weather(temperature = 1.0), UserPreferences())
            .first { it.key == "clothing" }
        assertTrue("高温应更适宜穿得少：${hot.score}", hot.score >= 2)
        assertTrue("低温应更不适宜：${cold.score}", cold.score <= 2)
    }

    @Test
    fun `怕冷人群穿衣指数更低`() {
        val base = LifeIndexEngine.compute(weather(temperature = 12.0), UserPreferences())
            .first { it.key == "clothing" }
        val sensitive = LifeIndexEngine.compute(
            weather(temperature = 12.0),
            UserPreferences(coldHeatTolerance = ToleranceLevel.LOW)
        ).first { it.key == "clothing" }
        assertTrue(
            "同样 12℃，怕冷的人应得到不更高的适宜度（${base.score} vs ${sensitive.score}）",
            sensitive.score <= base.score
        )
    }

    @Test
    fun `强紫外线压低防晒指数`() {
        val low = LifeIndexEngine.compute(weather(uvIndex = 1), UserPreferences())
            .first { it.key == "sunscreen" }
        val high = LifeIndexEngine.compute(weather(uvIndex = 9), UserPreferences())
            .first { it.key == "sunscreen" }
        assertTrue(low.score >= 4)
        assertTrue(high.score <= 2)
    }

    @Test
    fun `有降水时不宜洗车`() {
        val rainy = LifeIndexEngine.compute(
            weather(condition = "小雨", precipitation = 2.0),
            UserPreferences()
        ).first { it.key == "carwash" }
        assertEquals(1, rainy.score)
        assertTrue(rainy.advice.contains("降水"))
    }

    @Test
    fun `大温差抬高感冒风险`() {
        val flat = LifeIndexEngine.compute(
            weather(temperature = 18.0, dayHigh = 20.0, dayLow = 17.0),
            UserPreferences()
        ).first { it.key == "cold" }
        val swing = LifeIndexEngine.compute(
            weather(temperature = 18.0, dayHigh = 27.0, dayLow = 12.0),
            UserPreferences()
        ).first { it.key == "cold" }
        assertTrue(swing.score < flat.score)
    }

    @Test
    fun `指数分值始终落在有效区间`() {
        listOf(
            weather(temperature = -8.0, condition = "暴雪", humidity = 95),
            weather(temperature = 39.0, condition = "晴", uvIndex = 11, humidity = 20),
            weather(temperature = 20.0, condition = "雾", windScaleText = "7~8级")
        ).forEach { w ->
            val indices = LifeIndexEngine.compute(w, UserPreferences())
            assertEquals("五项指数应齐全", 5, indices.size)
            indices.forEach {
                assertTrue("${it.key}=${it.score}", it.score in 1..5)
                assertEquals(LifeIndex.levelText(it.score), it.level)
            }
        }
    }

    @Test
    fun `预警不改变指数但会进提醒`() {
        // 预警由推荐引擎负责呈现，指数引擎不应重复输出同一条信息
        val withAlarm = weather().copy(alarms = listOf(
            WeatherAlarm("暴雨红色预警", "暴雨", "红色", "2026-09-21")
        ))
        val indices = LifeIndexEngine.compute(withAlarm, UserPreferences())
        assertTrue(indices.all { !it.advice.contains("红色预警") })
    }

    /* ============ 格式化 ============ */

    @Test
    fun `华氏换算不截断`() {
        // 0℃ 必须是 32℉，整数版 celsius * 9 / 5 会先截断得到 31
        assertEquals("32℉", Formatters.temp(0.0, com.jianyi.outfit.data.model.TempUnit.FAHRENHEIT))
        assertEquals("-40℉", Formatters.temp(-40.0, com.jianyi.outfit.data.model.TempUnit.FAHRENHEIT))
    }

    @Test
    fun `能见度按量级切换单位`() {
        assertEquals("800 m", Formatters.visibility(800))
        assertEquals("12.5 km", Formatters.visibility(12500))
        assertEquals("--", Formatters.visibility(null))
    }
}
