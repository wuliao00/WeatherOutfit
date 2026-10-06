package com.jianyi.outfit.engine

import com.jianyi.outfit.data.model.AlmanacDay
import com.jianyi.outfit.platform.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 黄历引擎测试。
 *
 * 优先测**不变量**而非具体字符串：干支纪日是严格 60 周期、节气只在当日命中——
 * 这些不随 tyme4kt 版本改文案而失效。具体值只在能独立核算的地方钉死
 * （冬至/夏至、冲煞两锚点均有外部黄历逐字对照）。
 *
 * ## 断言依据：tyme4kt 1.5.0 实测登记表
 *
 * 登记表出自 Task 1 勘察，原始输出只存在于被 gitignore 的临时目录；为避免清理工作区
 * 后丢失，摘录于此，让断言与它的实测依据一起随代码留存。锚点为
 * 2026-10-05 / 2026-12-22 / 2027-01-01 / 2027-02-06：
 *
 * | 字段（实测 API） | 2026-10-05 | 2026-12-22 | 2027-01-01 | 2027-02-06 |
 * |---|---|---|---|---|
 * | 农历日名 getName()（是日名，不含月） | 廿五 | 十四 | 廿四 | 初一 |
 * | 农历整写 toString()（农历年+月+日；lunarDateText 采用） | 农历丙午年八月廿五 | 农历丙午年十一月十四 | 农历丙午年十一月廿四 | 农历丁未年正月初一 |
 * | 日干支 LunarDay.getSixtyCycle() | 壬子 | 庚午 | 庚辰 | 丙辰 |
 * | 三柱 SixtyCycleDay.toString() | 丙午年丁酉月壬子日 | 丙午年庚子月庚午日 | 丙午年庚子月庚辰日 | 丁未年壬寅月丙辰日 |
 * | 农历年 getName() | 农历丙午年 | 农历丙午年 | 农历丙午年 | 农历丁未年 |
 * | 建除 duty（单字，无「:吉/凶」后缀） | 平 | 破 | 定 | 满 |
 * | 黄道黑道十二神 twelveStar | 司命 | 司命 | 天牢 | 金匮 |
 * | 二十八宿 twentyEightStar | 毕 | 室 | 鬼 | 氐 |
 * | 月相 phase | 残月 | 盈凸月 | 残月 | 新月 |
 * | 宜 recommends（裸名，无「:吉」后缀） | 祭祀,沐浴,修饰垣墙,平治道涂,馀事勿取 | 破屋,坏垣,治病,馀事勿取 | 嫁娶,冠笄,祭祀,祈福,求嗣,雕刻,开光,安香,出行,入学,修造,动土,竖柱,上梁,盖屋,起基,安门,出火,移徙,入宅,掘井,造畜稠,安葬,破土,除服,成服 | 嫁娶,冠笄,纳采,出行,会亲友,上梁,安机械,安床,牧养,畋猎,祭祀,祈福,开光,修造,安门,盖屋,起基 |
 * | 忌 avoids（裸名，无「:凶」后缀） | 斋醮,嫁娶,移徙,出行,上梁,入宅 | 移徙,入宅 | 开市,纳采,订盟,作灶,造庙,造船,经络 | 入宅,作灶,治病,安葬,移徙 |
 * | 节日 getName()（非节日 null） | null | 冬至节 | null | 春节 |
 * | 星座（两字短名，无「座」后缀） | 天秤 | 摩羯 | 摩羯 | 水瓶 |
 *
 * Task 4 追加勘察（Task 1 未覆盖的节气/年柱/生肖/冲煞与越界行为）：
 *
 * | 字段（实测 API） | 2026-10-05 | 2026-12-22 | 2026-06-21 | 2027-02-04 / 02-05 |
 * |---|---|---|---|---|
 * | 最近节气 getTerm() / 距节气天数 getTermDay().getDayIndex() | 秋分 / 12 | 冬至 / 0 | 夏至 / 0 | 立春 / 0、1 |
 * | 年柱 SixtyCycleDay.getYear()（立春换年；仅登记，引擎不用） | 丙午/马 | 丙午/马 | 丙午/马 | 丁未/羊（农历年仍丙午，02-06 春节才换） |
 * | 农历年干支 LunarYear.getSixtyCycle()（春节换年，引擎采用） | 丙午/马 | 丙午/马 | 丙午/马 | 丙午/马（02-06 春节起丁未/羊） |
 * | 冲煞 冲{冲柱生肖}({冲柱干支})煞{日支煞方} | 冲马(丙午)煞南 | 冲鼠(甲子)煞北 | 冲猴(庚申)煞北 | 冲猴(戊申)煞北 / 冲鸡(己酉)煞西 |
 *
 * 其它实测要点：
 * - lunarDateText 取 LunarDay.toString()（实测「农历丙午年八月廿五」，年+月+日整写）：
 *   LunarDay.getName() 只有日名（「廿五」，1.5.0 sources 为 NAMES[day-1]），单独用它卡面会缺月名；
 *   toString() 内的农历年与 ganzhiYear 同源（LunarYear.getSixtyCycle()），两字段口径不会分叉。
 * - 宜/忌条目（List<Taboo>）的 toString() = getName()，全锚点合计实测 2~26 项、逐日不定，
 *   所以引擎一律截断到 AlmanacDay.TABOO_DISPLAY_LIMIT。
 * - 冲煞两锚点与便民查询网（wannianrili.bmcx.com）逐字一致：「冲马 （丙午）煞南」
 *   与「冲鼠 （甲子）煞北」（外部黄历在括号前多一个空格）；冲柱干支 = 日柱在六十甲子
 *   表上退 6 位（壬子→丙午），不是简单按 +6 走六十甲子（那会得到戊午）。
 *   模型 KDoc 的示例「冲马(丙午)煞南」与实测格式一致。
 * - 年柱/生肖取农历年干支（**春节换年**，民俗/大众口径；LunarYear.getSixtyCycle()），
 *   不取 SixtyCycleDay.getYear()（立春换年，命理/术数口径，本 App 不做命理）：春节可
 *   晚于立春（2027 立春 02-04、春节 02-06），立春换年会让这两天的干支年/生肖先于
 *   农历年切换。分歧窗口由 year_pillar_switches_at_lunar_new_year_not_at_lichun 钉死。
 * - 越界日期（2 月 30 日 / 13 月 / 公元 0 年 / 999999 年）在 SolarDay 构造期即抛
 *   IllegalArgumentException，引擎入口 runCatching 收敛为 null。
 */
class AlmanacEngineTest {

    @Test
    fun produces_full_record_for_today() {
        val a = AlmanacEngine.of(CivilDate(2026, 10, 5))
        assertNotNull(a, "合法日期不该返回 null")
        // 农历日期是「年+月+日」整写（实测「农历丙午年八月廿五」）；只要日名（「廿五」）是产品缺陷，
        // 完整的防退化断言见 lunar_date_text_carries_month_and_day_not_bare_day_name
        assertEquals("农历丙午年八月廿五", a!!.lunarDateText, "农历日期缺月名则卡面分不清哪个月")
        assertTrue(a.ganzhiDay.length in 2..4, "干支纪日应为两字，实到 ${a.ganzhiDay}")
        assertTrue(a.zodiac.length == 1, "生肖应为单字，实到 ${a.zodiac}")
        assertTrue(a.moonPhase.isNotBlank())
        assertTrue(a.duty.isNotBlank())
        assertTrue(AlmanacDay.SOURCE_LABEL.isNotBlank(), "来源标注常量不得为空，否则角标会渲染成空壳")
    }

    @Test
    fun lunar_date_text_carries_month_and_day_not_bare_day_name() {
        // lunarDateText 用 LunarDay.toString()（农历年+月+日，实测见登记表），不用 getName()：
        // 1.5.0 的 getName() 实测只回日名（「廿五」），卡面缺月名就分不清哪个月。
        // 两个锚点钉死整写；再显式守卫「不是裸日名」——来源哪天被改回 getName()，这里立刻红。
        val oct5 = assertNotNull(AlmanacEngine.of(CivilDate(2026, 10, 5)))
        val dec22 = assertNotNull(AlmanacEngine.of(CivilDate(2026, 12, 22)))
        assertEquals("农历丙午年八月廿五", oct5.lunarDateText)
        assertEquals("农历丙午年十一月十四", dec22.lunarDateText)
        assertTrue(oct5.lunarDateText.contains("八月"), "农历日期缺月名：${oct5.lunarDateText}")
        assertTrue(dec22.lunarDateText.contains("十一月"), "农历日期缺月名：${dec22.lunarDateText}")
        assertNotEquals("廿五", oct5.lunarDateText, "农历日期不能退化成裸日名")
        assertNotEquals("十四", dec22.lunarDateText, "农历日期不能退化成裸日名")
    }

    @Test
    fun sixty_cycle_day_advances_one_per_day() {
        // 干支纪日是严格 60 循环：相邻两天在序列里必须相邻（模 60）。
        // 只断言「两天不同」不够——跳过一天也会不同，必须把日干支落到甲子表的索引上验 +1。
        // 这条不需要外部权威日期当黄金值，所以比"查表比对"更抗腐化。
        val base = assertNotNull(AlmanacEngine.of(CivilDate(2026, 1, 1)))
        var prev = assertNotNull(JIA_ZI_INDEX[base.ganzhiDay], "干支不在甲子表内：${base.ganzhiDay}")
        var d = CivilDate(2026, 1, 1)
        // 走到 3 月 30 日共 89 天（> 60），(prev + 1) mod 60 的回绕因此必然被执行到
        while (d.day < 31 || d.month == 1) {
            d = nextDay(d)
            val a = assertNotNull(AlmanacEngine.of(d), "$d 不该返回 null")
            val cur = assertNotNull(JIA_ZI_INDEX[a.ganzhiDay], "干支不在甲子表内：${a.ganzhiDay}（$d）")
            assertEquals((prev + 1) % 60, cur, "干支纪日应逐日 +1（mod 60），$d 实到 ${a.ganzhiDay}")
            prev = cur
        }
    }

    @Test
    fun taboo_lists_are_resolved_and_within_display_limit() {
        val a = AlmanacEngine.of(CivilDate(2026, 10, 5))!!
        assertTrue(a.recommends.size <= AlmanacDay.TABOO_DISPLAY_LIMIT)
        assertTrue(a.avoids.size <= AlmanacDay.TABOO_DISPLAY_LIMIT)
        assertTrue(a.recommends.none { it.isBlank() }, "宜不得含空项")
        assertTrue(a.avoids.none { it.isBlank() }, "忌不得含空项")
        assertTrue(a.recommends.intersect(a.avoids.toSet()).isEmpty(), "同一事项不应既宜又忌")
    }

    @Test
    fun jieqi_and_festival_are_null_when_absent_and_set_on_known_days() {
        // 冬至/夏至是能独立核算的节气锚点：2026-12-22 为冬至、2026-06-21 为夏至
        // （登记表实测，12-22 与便民查询网 2026-12 月页一致），相邻日必须落空。
        val winter = AlmanacEngine.of(CivilDate(2026, 12, 22))
        assertNotNull(winter)
        assertEquals("冬至", winter!!.jieqi, "2026-12-22 应为冬至，实到 ${winter.jieqi}")
        assertEquals("冬至节", winter.festival, "冬至当天的农历节日应为冬至节，实到 ${winter.festival}")
        assertEquals("夏至", AlmanacEngine.of(CivilDate(2026, 6, 21))!!.jieqi, "2026-06-21 应为夏至")
        assertNull(AlmanacEngine.of(CivilDate(2026, 12, 21))!!.jieqi, "12-21 距大雪 14 天，不是节气日")
        assertNull(AlmanacEngine.of(CivilDate(2026, 12, 23))!!.jieqi, "冬至后一天不再是节气日")
        assertNull(AlmanacEngine.of(CivilDate(2026, 6, 22))!!.jieqi, "夏至后一天不再是节气日")
        assertNull(AlmanacEngine.of(CivilDate(2026, 10, 5))!!.jieqi, "普通日期无节气")
        assertNull(AlmanacEngine.of(CivilDate(2026, 10, 5))!!.festival, "普通日期无节日")
    }

    @Test
    fun chong_sha_is_built_from_day_pillar_in_measured_format() {
        // 冲煞 = 冲{六冲方位生肖}({日柱在甲子表退 6 位的干支})煞{日支的煞方}。
        // 两锚点与便民查询网 2026-10-05 / 2026-12-22 两页逐字一致（外部黄历在括号前多一个空格）。
        assertEquals("冲马(丙午)煞南", AlmanacEngine.of(CivilDate(2026, 10, 5))!!.chongSha)
        assertEquals("冲鼠(甲子)煞北", AlmanacEngine.of(CivilDate(2026, 12, 22))!!.chongSha)
    }

    @Test
    fun year_pillar_switches_at_lunar_new_year_not_at_lichun() {
        // 口径钉死：干支年/生肖随**春节**换（民俗口径），不随立春换（命理口径）。
        // 2027 立春（02-04）早于春节（02-06），这两天是两种口径的分歧窗口：
        // 立春口径已给出丁未/羊，本引擎取春节口径，仍为丙午/马，春节当天才换。
        val lichun = assertNotNull(AlmanacEngine.of(CivilDate(2027, 2, 4)), "2027-02-04 立春日")
        assertEquals("丙午", lichun.ganzhiYear, "立春不换年：02-04 仍是丙午（立春口径会得到丁未）")
        assertEquals("马", lichun.zodiac, "立春不换生肖：02-04 仍是马（立春口径会得到羊）")
        val cnyEve = assertNotNull(AlmanacEngine.of(CivilDate(2027, 2, 5)), "2027-02-05 除夕")
        assertEquals("丙午", cnyEve.ganzhiYear, "春节没到，02-05 仍是丙午")
        assertEquals("马", cnyEve.zodiac)
        val cny = assertNotNull(AlmanacEngine.of(CivilDate(2027, 2, 6)), "2027-02-06 春节")
        assertEquals("丁未", cny.ganzhiYear, "春节当天干支年才换丁未")
        assertEquals("羊", cny.zodiac, "春节当天生肖才换羊")
    }

    @Test
    /** 非法/越界日期一律收敛为 null，绝不让首页崩（spec 第 7 节第 5 条） */
    fun out_of_range_dates_return_null_instead_of_throwing() {
        assertNull(AlmanacEngine.of(CivilDate(2026, 2, 30)), "2 月 30 日")
        assertNull(AlmanacEngine.of(CivilDate(2026, 13, 1)), "13 月")
        assertNull(AlmanacEngine.of(CivilDate(0, 1, 1)), "公元 0 年")
        assertNull(AlmanacEngine.of(CivilDate(999999, 1, 1)), "超大年份")
    }

    @Test
    fun known_leap_day_is_accepted() {
        assertNotNull(AlmanacEngine.of(CivilDate(2028, 2, 29)), "闰年 2 月 29 日必须可用")
    }

    private fun nextDay(d: CivilDate): CivilDate {
        val mdays = intArrayOf(31, if ((d.year % 4 == 0 && d.year % 100 != 0) || d.year % 400 == 0) 29 else 28,
            31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        return if (d.day < mdays[d.month - 1]) CivilDate(d.year, d.month, d.day + 1)
        else if (d.month < 12) CivilDate(d.year, d.month + 1, 1)
        else CivilDate(d.year + 1, 1, 1)
    }

    private companion object {
        private const val STEMS = "甲乙丙丁戊己庚辛壬癸"
        private const val BRANCHES = "子丑寅卯辰巳午未申酉戌亥"

        /** 六十甲子表：第 i 项 = 天干 i%10 + 地支 i%12（干支同步前进，60 一循环），与 tyme4kt 的六十甲子序一致 */
        private val JIA_ZI_INDEX: Map<String, Int> = (0 until 60).associate { i ->
            "${STEMS[i % 10]}${BRANCHES[i % 12]}" to i
        }
    }
}
