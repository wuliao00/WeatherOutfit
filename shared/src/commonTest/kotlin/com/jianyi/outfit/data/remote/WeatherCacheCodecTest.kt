package com.jianyi.outfit.data.remote

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 天气缓存格式的"钉子"测试。
 *
 * 缓存是落盘格式：老用户机器上已经有旧版（Gson）写进去的行。格式一变，
 * 表现不是报错，而是"断网时没有旧数据可回退"，极难归因 —— 所以这里用
 * 手写的 JSON 样本把两种历史格式钉死，而不是靠 encode 生成（那样测的
 * 只是自身往返，格式漂移测不出来）。
 *
 * 这组测试在 commonTest，意味着将来它们同样跑在 iOS target 上（CI 编译验证）。
 */
class WeatherCacheCodecTest {

    companion object {
        /**
         * 旧版 Gson 写入的普通端点缓存行（按接口真实形态手写：字符串数字、
         * 冗余字段、缺省 null —— 这些都是 apihz 响应里出现过的形态）。
         */
        const val BARE_GSON_STYLE = """
            {"code":200,"msg":"","guo":"中国","sheng":"广东省","shi":"中山市",
             "name":"中山市","weather1":"多云","weather2":"晴",
             "wd1":"31","wd2":"24","winddirection1":"东南风","windleve1":"3~4级",
             "weather1img":"https://example.com/d01.png",
             "lon":"113.39","lat":"22.52","uptime":"2026-09-22 12:00:29",
             "future_field":{"x":1},"another":null,
             "nowinfo":{"precipitation":0.0,"temperature":29.5,"pressure":1005,
                        "humidity":68,"windDirection":"东南风","windDirectionDegree":135,
                        "windSpeed":3.4,"windScale":"微风","feelst":32.1,
                        "uptime":"2026-09-22 12:00:29"},
             "alarm":[{"id":"1","title":"高温黄色预警","signaltype":"高温",
                       "signallevel":"黄色","effective":"2026-09-22 09:00:00"}]}
        """

        /** 旧版 Gson 写入的经纬度端点缓存行（带 type 信封） */
        const val LATLON_GSON_STYLE = """
            {"type":"latlon","data":{"code":200,"msg":"","weather":"多云",
             "temp":302.65,"temph":29.5,"pressure":1005,"humidity":68,
             "visibility":12000,"speed":3.4,"deg":135,"clouds":60,"country":"CN",
             "sunrise":1758500000,"sunset":1758544000,"name":"Zhongshan",
             "dt":1758516029}}
        """
    }

    /* ============ 历史格式必须一直能读 ============ */

    /** 老版本（Gson 时代）写入的裸 WeatherResponse 缓存行必须能原样解出 */
    @Test
    fun `legacy bare cache row still decodes`() {
        val back = assertIs<WeatherResponse>(WeatherCacheCodec.decode(BARE_GSON_STYLE))
        assertEquals(200, back.code)
        assertEquals("多云", back.weather1)
        assertEquals("31", back.wd1)
        assertEquals(29.5, back.nowinfo?.temperature)
        assertEquals(68, back.nowinfo?.humidity)
        assertEquals("高温黄色预警", back.alarm?.first()?.title)
        assertTrue(back.isSuccess)
    }

    /** 老版本写入的 type 信封行必须按 LatLon DTO 解 */
    @Test
    fun `legacy latlon envelope still decodes`() {
        val back = assertIs<LatLonWeatherResponse>(WeatherCacheCodec.decode(LATLON_GSON_STYLE))
        assertEquals(200, back.code)
        assertEquals("多云", back.weather)
        assertEquals(29.5, back.temph)
        assertEquals(3.4, back.speed)
        assertEquals("Zhongshan", back.name)
    }

    /* ============ Gson 行为差异点，逐条钉住 ============ */

    /** apihz 的数字字段时而是字符串：Gson 会转，kotlinx 靠 isLenient 对齐 */
    @Test
    fun `string numbers coerce like gson did`() {
        val back = assertIs<WeatherResponse>(
            WeatherCacheCodec.decode("""{"code":200,"nowinfo":{"temperature":"29.5","humidity":"68"}}""")
        )
        assertEquals(29.5, back.nowinfo?.temperature)
        assertEquals(68, back.nowinfo?.humidity)
    }

    /** Gson 对缺失 int 填 0：kotlinx 靠默认值对齐；0 != 200，走失败分支 */
    @Test
    fun `missing code falls back to 0 and fails success check`() {
        val back = WeatherCacheCodec.decode("""{"weather1":"晴"}""")
        assertNotNull(back)
        assertFalse(back.isSuccess)
    }

    /** 接口哪天加字段不能让查询整个崩掉（Gson 忽略未知字段） */
    @Test
    fun `unknown fields are ignored like gson did`() {
        val back = assertIs<WeatherResponse>(
            WeatherCacheCodec.decode("""{"code":200,"weather1":"晴","future_field":{"x":1}}""")
        )
        assertEquals("晴", back.weather1)
    }

    /* ============ 缓存是尽力而为的回退：脏数据当没有，绝不抛 ============ */

    @Test
    fun `corrupt cache is treated as no cache never a crash`() {
        assertNull(WeatherCacheCodec.decode(""))
        assertNull(WeatherCacheCodec.decode("not json"))
        assertNull(WeatherCacheCodec.decode("""{"code":200,"nowinfo":"""))
        assertNull(WeatherCacheCodec.decode("""{"type":"latlon","data":"oops"}"""))
        assertNull(WeatherCacheCodec.decode("""{"type":"latlon"}"""))
    }

    /* ============ 写入格式（新写出的行要能被旧版读回，见 app 侧对拍测试） ============ */

    /** 裸格式往返：不套信封，且整对象逐字段还原 */
    @Test
    fun `bare round trip keeps identity`() {
        val response = WeatherResponse(
            code = 200, msg = "", guo = "中国", sheng = "广东省", shi = "中山市",
            name = "中山市", weather1 = "多云", weather2 = "晴",
            wd1 = "31", wd2 = "24", winddirection1 = "东南风", windleve1 = "3~4级",
            uptime = "2026-09-22 12:00:29",
            nowinfo = NowInfo(
                temperature = 29.5, humidity = 68, windScale = "微风",
                feelst = 32.1, windSpeed = 3.4
            ),
            alarm = listOf(
                AlarmItem(title = "高温黄色预警", signaltype = "高温", signallevel = "黄色")
            )
        )
        val payload = WeatherCacheCodec.encode(response)
        assertFalse(payload.contains("\"type\""), "裸格式不能带 type 信封")
        val back = assertIs<WeatherResponse>(WeatherCacheCodec.decode(payload))
        assertEquals(response, back)
    }

    /** 信封格式往返：type 标记 + data 内层 */
    @Test
    fun `latlon round trip keeps the type wrapper`() {
        val response = LatLonWeatherResponse(
            code = 200, weather = "多云", temp = 302.65, temph = 29.5,
            pressure = 1005, humidity = 68, speed = 3.4, deg = 135,
            name = "Zhongshan", dt = 1758516029
        )
        val payload = WeatherCacheCodec.encode(response, tag = "latlon")
        val root = weatherJson.parseToJsonElement(payload).jsonObject
        assertEquals("latlon", root["type"]?.jsonPrimitive?.content)
        val back = assertIs<LatLonWeatherResponse>(WeatherCacheCodec.decode(payload))
        assertEquals(response, back)
    }
}
