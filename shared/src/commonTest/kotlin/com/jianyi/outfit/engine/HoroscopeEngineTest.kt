package com.jianyi.outfit.engine

import com.jianyi.outfit.data.model.Horoscope
import com.jianyi.outfit.platform.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 星座引擎测试。
 *
 * 这类"按日轮换的本地文案池"最典型的两个失效是：
 * ① 刷新一次结果就变（用户立刻识破是随机的）；② 某星座漏配 → 那天卡片消失。
 * 两条都用测试钉死，而不是靠肉眼翻日历。
 *
 * 还有一条更隐蔽的：星座名对不上。tyme4kt 的 Constellation.getName() 实测是两位短名
 * （「天秤」而非「天秤座」，见 AlmanacEngineTest 顶部的登记表），SIGN_ORDER 里多写一个
 * 「座」字，indexOf 就恒为 -1，整年每天都返回 null、卡片静默消失。
 * every_sign_of_the_year_has_a_horoscope 逐日走满一整年，正是这条的守卫。
 */
class HoroscopeEngineTest {

    @Test
    fun same_day_same_sign_is_deterministic() {
        val a = HoroscopeEngine.of(CivilDate(2026, 10, 5))!!
        val b = HoroscopeEngine.of(CivilDate(2026, 10, 5))!!
        assertEquals(a, b, "同一天同星座必须给出同样结果")
    }

    @Test
    fun every_sign_of_the_year_has_a_horoscope() {
        // 一整年逐日走，收集出现过的星座，必须凑满 12 个且没有一天返回 null
        val seen = mutableSetOf<String>()
        var d = CivilDate(2026, 1, 1)
        var n = 0
        while (d.year == 2026) {
            val h = HoroscopeEngine.of(d)
            assertNotNull(h, "${d.year}-${d.month}-${d.day} 返回 null，说明有星座没配上文案池")
            seen += h!!.constellation
            assertTrue(Horoscope.SOURCE_LABEL.isNotBlank(), "来源标注常量不得为空")
            assertTrue(h.overall.isNotBlank() && h.love.isNotBlank() &&
                h.career.isNotBlank() && h.wealth.isNotBlank())
            assertTrue(h.luckyNumber in 1..9, "幸运数字应 1~9，实到 ${h.luckyNumber}")
            d = next(d); n++
        }
        assertEquals(365, n)
        assertEquals(12, seen.size, "12 星座全覆盖，实到 ${seen.sorted()}")
    }

    @Test
    fun consecutive_days_do_not_repeat_the_same_text() {
        val today = HoroscopeEngine.of(CivilDate(2026, 10, 5))!!
        val tomorrow = HoroscopeEngine.of(CivilDate(2026, 10, 6))!!
        if (today.constellation == tomorrow.constellation) {
            assertNotEquals(today.overall, tomorrow.overall, "同一星座相邻两天不该同句")
        }
    }

    @Test
    fun texts_are_neutral_no_exclamation_or_gossip_tone() {
        // 已确认默认：中性陈述，不带幽默/口语；长度是排版约束——卡片窄，
        // 14~24 字（含句末标点，按 String.length 计）是内容规范定下的区间。
        // 逐日走经过每张卡片的每条文案（k 每天 +1 轮转），全年即可覆盖池内全部条目。
        var d = CivilDate(2026, 1, 1)
        while (d.year == 2026) {
            val h = HoroscopeEngine.of(d)!!
            for (s in listOf(h.overall, h.love, h.career, h.wealth)) {
                assertTrue("！" !in s && "!" !in s, "文案含感叹号：$s")
                assertTrue("宜忌" !in s, "文案不该混进黄历措辞：$s")
                assertTrue(s.length in 14..24, "文案应在 14~24 字（含句末标点），实到 ${s.length} 字：$s")
            }
            d = next(d)
        }
    }

    private fun next(c: CivilDate): CivilDate {
        val mdays = intArrayOf(31, if ((c.year % 4 == 0 && c.year % 100 != 0) || c.year % 400 == 0) 29 else 28,
            31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        return if (c.day < mdays[c.month - 1]) CivilDate(c.year, c.month, c.day + 1)
        else if (c.month < 12) CivilDate(c.year, c.month + 1, 1)
        else CivilDate(c.year + 1, 1, 1)
    }
}
