package com.jianyi.outfit.engine

import com.jianyi.outfit.data.model.AlmanacDay
import com.jianyi.outfit.platform.CivilDate
import com.tyme.lunar.LunarDay
import com.tyme.solar.SolarDay

/**
 * 黄历推导引擎。
 *
 * 与 LifeIndexEngine 同形：纯 Kotlin、不碰平台 API、日期从外面传进来，
 * 所以能直接单测，也让「今天」这个概念在测试里可固定。
 *
 * 全离线是本设计的核心立场（spec 3.1）：农历/干支/节气/宜忌是确定性历法计算，
 * 走网络等于给一个永不失败的东西引入失败态，还要多背一个凭证与限流源。
 */
object AlmanacEngine {

    /**
     * @return 当日黄历；日期非法或超出库的支持范围时返回 null（不抛，交给 UI 不渲染该卡）
     *
     * 整个推导包在 runCatching 里而不是逐字段兜底：tyme4kt 对越界日期在构造期就抛
     * IllegalArgumentException（实测 2 月 30 日 / 13 月 / 公元 0 年 / 999999 年都一样），
     * 一个入口兜住比每个 getter 各包一层更不容易漏（spec 第 7 节第 5 条）。
     */
    fun of(date: CivilDate): AlmanacDay? = runCatching {
        val lunar = SolarDay(date.year, date.month, date.day).getLunarDay()

        val recommends = lunar.getRecommends().map { it.toString() }.filter { it.isNotBlank() }
        val avoids = lunar.getAvoids().map { it.toString() }.filter { it.isNotBlank() }

        AlmanacDay(
            // 农历日期用 toString()（实测「农历丙午年八月廿五」，年+月+日整写）：getName() 实测只回日名
            // （「廿五」），卡面缺月名就分不清哪个月；toString() 的年与 ganzhiYear 同源
            // （LunarYear.getSixtyCycle()），两字段的换年口径不会分叉。
            lunarDateText = lunar.toString(),
            // 干支年取农历年干支（春节换年），不取立春换年的年柱——理由见 zodiacOf 的注释
            ganzhiYear = lunar.getLunarMonth().getLunarYear().getSixtyCycle().getName(),
            ganzhiDay = lunar.getSixtyCycle().getName(),
            zodiac = zodiacOf(lunar),
            jieqi = jieqiOf(date),
            recommends = recommends.take(AlmanacDay.TABOO_DISPLAY_LIMIT),
            avoids = avoids.take(AlmanacDay.TABOO_DISPLAY_LIMIT),
            duty = lunar.getDuty().getName(),
            chongSha = chongShaOf(lunar),
            moonPhase = lunar.getPhase().getName(),
            festival = lunar.getFestival()?.getName()
        )
    }.getOrNull()

    /* ============ 三个需要拆解到 tyme4kt 原语的字段 ============ */

    /**
     * 生肖：取**干支年**（农历年）地支的生肖（丙午→马），不是日柱。
     *
     * 干支年取农历年干支（**春节换年**，民俗/大众口径；LunarYear.getSixtyCycle()），
     * 不取 SixtyCycleDay.getYear()（立春换年）：立春换年是命理/术数口径，本 App 不做命理。
     * 春节可晚于立春（2027：立春 02-04、春节 02-06），立春换年会让这两天的干支年/生肖
     * 先于农历年切换，与卡面农历日期所属的农历年脱节——分歧窗口由
     * year_pillar_switches_at_lunar_new_year_not_at_lichun 钉死。
     * tyme4kt 的 EarthBranch.getZodiac() 就是子鼠丑牛这张表，不用自己抄一份。
     */
    private fun zodiacOf(lunar: LunarDay): String =
        lunar.getLunarMonth().getLunarYear().getSixtyCycle().getEarthBranch().getZodiac().getName()

    /**
     * 当日节气名：只在恰为节气当天返回，否则 null（spec：非节气日 UI 不显示该字段）。
     *
     * 判据是 SolarTermDay.getDayIndex() == 0——该日距最近节气日的天数，0 即当天。
     * 实测：2026-12-22 冬至 dayIndex=0、2026-06-21 夏至 dayIndex=0，
     * 而 2026-10-05 距秋分 12 天、2026-12-21 距大雪 14 天，都不是节气日。
     */
    private fun jieqiOf(date: CivilDate): String? {
        val termDay = SolarDay(date.year, date.month, date.day).getTermDay()
        return if (termDay.getDayIndex() == 0) termDay.getSolarTerm().getName() else null
    }

    /**
     * 冲煞：「冲{生肖}({干支})煞{方位}」。
     *
     * tyme4kt 1.5.0 没有现成的冲煞串（计划里写的 SixtyCycleDay.getChong() 在实测 API
     * 中不存在），但三个原语都在 EarthBranch 上：getZodiac() 定「冲谁」、
     * getOminous() 定「煞哪方」；括号内的干支是日柱在六十甲子表上**退 6 位**
     * （壬子→丙午，整体 +6 会错成戊午），与便民查询网 2026-10-05 的
     * 「冲马 （丙午）煞南」逐字一致。
     */
    private fun chongShaOf(lunar: LunarDay): String {
        val day = lunar.getSixtyCycle()
        val chong = day.next(-6)
        return "冲${chong.getEarthBranch().getZodiac().getName()}(${chong.getName()})煞${day.getEarthBranch().getOminous().getName()}"
    }
}
