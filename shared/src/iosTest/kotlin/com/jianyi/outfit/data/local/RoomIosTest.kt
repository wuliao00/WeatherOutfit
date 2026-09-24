package com.jianyi.outfit.data.local

import com.jianyi.outfit.data.local.dao.CityDao
import com.jianyi.outfit.data.local.entity.OutfitTemplateEntity
import com.jianyi.outfit.data.local.entity.WeatherCacheEntity
import com.jianyi.outfit.platform.currentTimeMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Room 在 iOS 上的**运行**测试 —— 不是编译测试。
 *
 * 这层的意义：iOS 侧此前只证明过"编译器认了"，而 Room 恰恰是编译期看不出问题的库 ——
 * 驱动没挂上（native 的 build() 会 requireNotNull(driver)）、schema 校验不过、
 * 生成的 Impl 找不到、flow 不推送，全都是第一次真正开库时才炸。
 * 跑在 CI 的 iOS 模拟器上才算数。
 *
 * 用例一律用带时间戳的唯一键，避免多次运行之间互相污染（模拟器上的 Documents 目录
 * 里的 jianyi.db 是会留下的）。断言全部顺序执行，不起后台收集器与写入赛跑。
 */
class RoomIosTest {

    private val dao: CityDao get() = database.cityDao()

    @Test
    fun opens_database_and_switch_to_current_city() = runBlocking {
        val city = "测试城${currentTimeMillis()}"
        dao.switchTo(province = "四川", city = city, source = "test", now = currentTimeMillis())

        val current = dao.observeCurrent().first()
        assertNotNull(current, "switchTo 之后应能查到当前城市")
        assertEquals(city, current!!.city)
        assertEquals("四川", current.province)
        assertTrue(current.isCurrent)

        // 同一 (省,市) 再来一次：唯一索引 + 查重必须复用原行，而不是插出第二行
        val beforeId = current.id
        dao.switchTo(province = "四川", city = city, source = "test", now = currentTimeMillis() + 1)
        val again = dao.find("四川", city)
        assertNotNull(again)
        assertEquals(beforeId, again!!.id, "重复切换同一城市不应新增第二行")

        dao.delete(again)
        assertNull(dao.find("四川", city), "删除后应查不到")
    }

    @Test
    fun switching_selection_updates_current_row() = runBlocking {
        val firstCity = "甲城${currentTimeMillis()}"
        val secondCity = "乙城${currentTimeMillis() + 1}"

        dao.switchTo("测试省", firstCity, "test", currentTimeMillis())
        assertEquals(firstCity, dao.observeCurrent().first()?.city)

        dao.switchTo("测试省", secondCity, "test", currentTimeMillis() + 1)
        assertEquals(secondCity, dao.observeCurrent().first()?.city, "不应出现两个当前城市")

        dao.clearCurrent()
        assertNull(dao.observeCurrent().first(), "清除选择后应回到自动定位链")
    }

    @Test
    fun weather_cache_put_get_replace_and_sweep() = runBlocking {
        val cache = cacheDatabase.weatherCacheDao()
        val key = "test|${currentTimeMillis()}"
        val now = currentTimeMillis()

        assertNull(cache.get(key))
        cache.put(WeatherCacheEntity(key, """{"probe":1}""", now))
        val row = cache.get(key)
        assertNotNull(row)
        assertEquals("""{"probe":1}""", row!!.payloadJson, "payload 必须逐字回读（它是接口原始 JSON）")
        assertEquals(now, row.cachedAt)

        // REPLACE 冲突策略：同 key 再写一次应覆盖，而不是报错或多出一行
        cache.put(WeatherCacheEntity(key, """{"probe":2}""", now + 1))
        assertEquals("""{"probe":2}""", cache.get(key)?.payloadJson)

        cache.evict(key)
        assertNull(cache.get(key))

        // 清理只删早于阈值的行：刚写的行不能被带走
        val keep = "keep|${currentTimeMillis()}"
        cache.put(WeatherCacheEntity(keep, "{}", now))
        cache.deleteStale(now - 1_000)
        assertNotNull(cache.get(keep), "deleteStale 不应删掉未过期的行")
        cache.evict(keep)
    }

    /** 模板表：itemsJson 是用户亲手存的清单，落盘读回的字节必须一致 */
    @Test
    fun outfit_template_insert_and_delete() = runBlocking {
        val templates = database.outfitTemplateDao()
        val stamp = currentTimeMillis()
        val id = templates.insert(
            OutfitTemplateEntity(
                name = "模板$stamp",
                scene = "通勤",
                minTemp = 5,
                maxTemp = 12,
                itemsJson = """["卫衣","长裤"]""",
                tip = "早晚温差大",
                createdAt = stamp
            )
        )
        assertTrue(id > 0, "自增主键应大于 0")

        val mine = templates.observeAll().first().first { it.id == id }
        assertEquals("""["卫衣","长裤"]""", mine.itemsJson)
        assertEquals("通勤", mine.scene)

        templates.deleteById(id)
        assertTrue(templates.observeAll().first().none { it.id == id })
    }

    private companion object {
        /**
         * 整份测试共用一个库实例，避免同文件被多实例反复打开。
         * 缓存库这里**持有 Database 本身**而不是只持有 DAO —— Room 的连接归库对象所有，
         * 只留 DAO 的话库实例可能被回收，测试会在中途出现"连接已关闭"这类假故障。
         */
        val database: AppDatabase by lazy { buildAppDatabase() }
        val cacheDatabase: WeatherCacheDatabase by lazy { buildWeatherCacheDatabase() }
    }
}
