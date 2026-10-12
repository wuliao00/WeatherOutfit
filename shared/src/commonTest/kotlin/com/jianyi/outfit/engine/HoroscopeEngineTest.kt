package com.jianyi.outfit.engine

import com.jianyi.outfit.data.model.Horoscope
import com.jianyi.outfit.data.today.HoroscopePool
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

    /**
     * 文案池形状守卫。
     *
     * 引擎按 `(signIndex*31 + doy) % 池子长度` 取条目，读的时候用 `getOrNull ?: return null`
     * ——所以某个星座被改成不足 6 条时，表现是那 1/6 的日子**整卡静默消失**，其余用例照样全绿。
     * 唯一能提前发现它的就是这条长度断言。
     *
     * 刻意写成「≥6 且同一维度内 12 个星座等长」而不是「==6」：池子加长是好事（周期自动变长，
     * 见 HoroscopeEngine 文件头），但同一维度里长短不一会让某些星座的日子取不到条目。
     */
    @Test
    fun pools_are_uniform_and_never_shrink_below_six() {
        val pools = mapOf(
            "overall" to HoroscopePool.overall,
            "love" to HoroscopePool.love,
            "career" to HoroscopePool.career,
            "wealth" to HoroscopePool.wealth
        )
        for ((label, pool) in pools) {
            assertEquals(12, pool.size, "$label 应覆盖 12 星座，实到 ${pool.size}")
            val sizes = pool.values.map { it.size }.toSet()
            assertEquals(1, sizes.size, "$label 的 12 个星座池长度不一致：$sizes")
            val n = sizes.single()
            assertTrue(n >= 6, "$label 每池至少 6 条（少于此会让部分日子整卡消失），实到 $n")
            for ((sign, entries) in pool) {
                assertTrue(entries.all { it.isNotBlank() }, "$label/$sign 存在空条目")
            }
        }
        assertEquals(12, HoroscopePool.colors.size, "幸运色应覆盖 12 星座")
    }

    /**
     * 轮回周期＝池子长度（文件头那条取舍的可执行版本）。
     *
     * 走满一个周期，断言周期内不重复、第 池长 天回到同一槽位。谁把索引改成带跳步
     * （旧版幸运数就是 `doy*3` 那种），这条会红。
     */
    @Test
    fun each_text_dimension_cycles_exactly_at_pool_size() {
        val size = HoroscopePool.overall.getValue("白羊").size
        var d = CivilDate(2026, 3, 1)
        val seen = mutableListOf<String>()
        repeat(size) { seen += HoroscopeEngine.of(d)!!.overall; d = next(d) }
        assertEquals(size, seen.toSet().size, "一个周期内不该重复：$seen")
        assertEquals(seen.first(), HoroscopeEngine.of(d)!!.overall, "第 $size 天应回到同一槽位")
    }

    /**
     * 幸运线：幸运色 12 天一轮、幸运数 9 天一轮。
     *
     * 旧版幸运数是 `(… + doy*3) % 9 + 1`——模 9 下每步跨 3，实际只有 3 天一轮，
     * 把整卡完全重复周期从 36 天压到 12 天（用户一周内就能撞见同一张卡）。
     */
    @Test
    fun lucky_line_cycles_are_twelve_and_nine_days() {
        var d = CivilDate(2026, 3, 1)
        val colors = mutableListOf<String>()
        val numbers = mutableListOf<Int>()
        repeat(12) {
            val h = HoroscopeEngine.of(d)!!
            colors += h.luckyColor
            numbers += h.luckyNumber
            d = next(d)
        }
        assertEquals(12, colors.toSet().size, "幸运色应 12 天不重复：$colors")
        assertEquals(9, numbers.take(9).toSet().size, "幸运数应 9 天不重复：${numbers.take(9)}")
        assertEquals(numbers.first(), plusDays(CivilDate(2026, 3, 1), 9).let { HoroscopeEngine.of(it)!!.luckyNumber },
            "第 9 天应回到第 1 天的幸运数")
    }

    /**
     * 同一星座窗口内（最长 31 天）整卡逐字不重复。
     *
     * 三个周期分别是 6（四类文案）、12（幸运色）、9（幸运数），lcm = 36 > 31，
     * 所以一个星座在其全部日子里都不会看到整卡重复；旧版 lcm(6,12,3)=12 < 31，
     * 同一个星座窗口里必然出现两次一模一样的卡。
     */
    @Test
    fun no_identical_card_within_one_sign_window() {
        var d = CivilDate(2026, 1, 1)
        var cards = mutableListOf<com.jianyi.outfit.data.model.Horoscope>()
        var lastSign = ""
        while (d.year == 2026) {
            val h = HoroscopeEngine.of(d)!!
            if (h.constellation != lastSign) {
                cards = mutableListOf()
                lastSign = h.constellation
            }
            assertTrue(cards.none { it == h }, "同一星座窗口内出现整卡重复：$h（$d）")
            cards += h
            d = next(d)
        }
    }

    private fun plusDays(c: CivilDate, n: Int): CivilDate {
        var d = c
        repeat(n) { d = next(d) }
        return d
    }

    private fun next(c: CivilDate): CivilDate {
        val mdays = intArrayOf(31, if ((c.year % 4 == 0 && c.year % 100 != 0) || c.year % 400 == 0) 29 else 28,
            31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        return if (c.day < mdays[c.month - 1]) CivilDate(c.year, c.month, c.day + 1)
        else if (c.month < 12) CivilDate(c.year, c.month + 1, 1)
        else CivilDate(c.year + 1, 1, 1)
    }
}
