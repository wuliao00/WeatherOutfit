package com.jianyi.outfit.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gson ↔ kotlinx.serialization 的**对拍**测试（weather 网络层刚从 Gson 迁走）。
 *
 * shared 侧的 WeatherCacheCodecTest 用手写 JSON 钉格式；这里用真实 Gson
 * 再对拍一遍，把两侧的等价性直接钉在同一个断言里：
 *
 * 1. 同一段旧缓存，Gson 与 kotlinx 解出的对象必须**逐字段相等** ——
 *    否则说明哪一侧的解析策略与旧行为有偏差；
 * 2. 新代码写出的缓存行，Gson 仍要能读回 —— 兜底"用户降级回旧版本 APK"
 *    时缓存不变成垃圾数据。
 *
 * 样本与 shared 侧是各自独立手写的（不共享常量），所以它同时交叉验证了
 * 两个文件里样本本身没有抄错。
 */
class GsonKotlinxParityTest {

    private val gson = Gson()

    private val bareSample = """
        {"code":200,"msg":"","guo":"中国","sheng":"广东省","shi":"中山市",
         "name":"中山市","weather1":"多云","weather2":"晴",
         "wd1":"31","wd2":"24","uptime":"2026-09-22 12:00:29",
         "future_field":{"x":1},"another":null,
         "nowinfo":{"temperature":29.5,"humidity":68,"windScale":"微风","feelst":32.1},
         "alarm":[{"title":"高温黄色预警","signaltype":"高温","signallevel":"黄色"}]}
    """

    private val latlonSample = """
        {"type":"latlon","data":{"code":200,"weather":"多云","temp":302.65,
         "temph":29.5,"humidity":68,"speed":3.4,"deg":135,"name":"Zhongshan",
         "dt":1758516029}}
    """

    /** 同一段旧缓存，两套解析器解出的对象必须相等 */
    @Test
    fun `legacy cache row parses identically under both parsers`() {
        val byGson = gson.fromJson(bareSample, WeatherResponse::class.java)
        val byKotlinx = WeatherCacheCodec.decode(bareSample) as WeatherResponse
        assertEquals(byGson, byKotlinx)

        val latlonRoot = JsonParser.parseString(latlonSample).asJsonObject
        val latlonByGson = gson.fromJson(latlonRoot["data"], LatLonWeatherResponse::class.java)
        val latlonByKotlinx = WeatherCacheCodec.decode(latlonSample) as LatLonWeatherResponse
        assertEquals(latlonByGson, latlonByKotlinx)
    }

    /** apihz 的数字字段时而是字符串：两边都必须转成功且结果一致 */
    @Test
    fun `string numbers parse identically under both parsers`() {
        val sample = """{"code":200,"nowinfo":{"temperature":"29.5","humidity":"68"},"wd1":"31"}"""
        val byGson = gson.fromJson(sample, WeatherResponse::class.java)
        val byKotlinx = WeatherCacheCodec.decode(sample) as WeatherResponse
        assertEquals(byGson, byKotlinx)
        assertEquals(29.5, byKotlinx.nowinfo?.temperature)
        assertEquals(68, byKotlinx.nowinfo?.humidity)
    }

    /** 降级兼容：新代码写出的裸缓存行，旧版 Gson 仍能完整读回 */
    @Test
    fun `newly written bare cache row is still gson readable`() {
        val response = WeatherResponse(
            code = 200, weather1 = "多云", wd1 = "31", wd2 = "24",
            uptime = "2026-09-22 12:00:29",
            nowinfo = NowInfo(temperature = 29.5, humidity = 68, feelst = 32.1),
            alarm = listOf(AlarmItem(title = "高温黄色预警", signaltype = "高温"))
        )
        val payload = WeatherCacheCodec.encode(response)
        val back = gson.fromJson(payload, WeatherResponse::class.java)
        assertEquals(response, back)
    }

    /** 降级兼容：新写出的信封行，旧版 Gson 仍能按 type 取 data 读回 */
    @Test
    fun `newly written latlon envelope is still gson readable`() {
        val response = LatLonWeatherResponse(
            code = 200, weather = "多云", temph = 29.5, temp = 302.65,
            speed = 3.4, deg = 135, name = "Zhongshan", dt = 1758516029
        )
        val payload = WeatherCacheCodec.encode(response, tag = "latlon")
        val root = JsonParser.parseString(payload).asJsonObject
        assertEquals("latlon", root.get("type").asString)
        val back = gson.fromJson(root.get("data"), LatLonWeatherResponse::class.java)
        assertEquals(response, back)
    }

    /** 缺 code 字段：两边都落到 0，都判失败（不会有一边误判成功） */
    @Test
    fun `missing code is 0 on both sides`() {
        val sample = """{"weather1":"晴"}"""
        val byGson = gson.fromJson(sample, WeatherResponse::class.java)
        val byKotlinx = WeatherCacheCodec.decode(sample)
        assertEquals(0, byGson.code)
        assertEquals(0, byKotlinx?.code)
        assertTrue(!byKotlinx!!.isSuccess)
    }
}
