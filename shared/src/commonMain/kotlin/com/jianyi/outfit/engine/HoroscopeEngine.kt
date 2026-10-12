package com.jianyi.outfit.engine

import com.jianyi.outfit.data.model.Horoscope
import com.jianyi.outfit.data.today.HoroscopePool
import com.jianyi.outfit.platform.CivilDate
import com.tyme.solar.SolarDay

/**
 * 星座引擎。
 *
 * 星座名由 tyme4kt 的 Constellation 本地算出（太阳星座按公历日期），
 * 运势取自本地池：不存在可信外部源（spec 2.4），所以不接网络——
 * 与 AlmanacEngine 同一立场，纯 Kotlin、日期从外面传进来，可直接单测。
 *
 * 注意：Constellation.getName() 实测是**两位短名、无「座」后缀**（「天秤」而非「天秤座」，
 * 登记表见 AlmanacEngineTest）。SIGN_ORDER 必须逐字对齐这个写法，错一个字 indexOf 恒为 -1，
 * 星座卡全年静默不显示——HoroscopeEngineTest 的全年遍历就是这条的守卫。
 *
 * 选取用 (星座序号 * 31 + 年内日序) % 池子长度：同一天同星座结果固定，相邻日必然不同。
 *
 * **模数取各自池子的实际长度，不是全局常量**——池子加长时该类的轮回周期自动变长（周期恒
 * 等于池长），也不会出现「多出来的条目永远取不到」（原来写死 6，池子涨到 12 就有一半是死代码）。
 *
 * 已知取舍：每类池子各 6 句，所以**同一星座同一分类的句子 6 天一轮回**。这个周期由文案体量
 * 决定，索引再怎么换都改不动；要拉长只能加句子（清单见 docs/content/horoscope-style.md）。
 * 幸运线是另一回事：幸运色 12 天一轮、幸运数 9 天一轮，所以**整卡（含幸运线）的完全重复
 * 周期是 lcm(6,12,9)=36 天**。旧版幸运数写的是 doy*3，模 9 后每步跨 3，实际只有 3 天一轮，
 * 把整卡周期压到了 12 天——已改成每步跨 1。
 */
object HoroscopeEngine {

    /** @return 当日星座内容；日期非法或星座名不在表内时返回 null（交给 UI 不渲染该卡） */
    fun of(date: CivilDate): Horoscope? {
        val solar = runCatching { SolarDay(date.year, date.month, date.day) }.getOrNull()
            ?: return null
        // 名字用 getName()：1.5.0 的 Constellation 没有 .name 属性（Task 1 实测）
        val name = runCatching { solar.getConstellation().getName() }.getOrNull() ?: return null

        val signIndex = SIGN_ORDER.indexOf(name)
        if (signIndex < 0) return null

        val doy = dayOfYear(date)

        val colors = HoroscopePool.colors
        if (colors.isEmpty()) return null

        return Horoscope(
            constellation = name,
            overall = pick(HoroscopePool.overall[name], signIndex, doy) ?: return null,
            love = pick(HoroscopePool.love[name], signIndex, doy) ?: return null,
            career = pick(HoroscopePool.career[name], signIndex, doy) ?: return null,
            wealth = pick(HoroscopePool.wealth[name], signIndex, doy) ?: return null,
            luckyColor = colors[(signIndex + doy) % colors.size],
            // 每步跨 1（原来是 doy*3，模 9 后实际 3 天一轮）：同一步长才配得上「9 天不重复」
            luckyNumber = (signIndex * 7 + doy) % 9 + 1,
            signWindow = windowOf(date, name)
        )
    }

    /**
     * 取当日条目：`(星座序号 × 31 + 年内日序) % 池子长度`。
     *
     * 模数用各自池子的长度（见文件头），池子为空或星座不在表里时返回 null，由调用方放弃整卡。
     * 31 是随便挑的步长，作用是让 12 个星座在同一天落在不同槽位。
     */
    private fun pick(pool: List<String>?, signIndex: Int, doy: Int): String? =
        pool?.takeIf { it.isNotEmpty() }?.get((signIndex * 31 + doy) % pool.size)

    /**
     * 星座区间，形如「9月23日–10月23日」。
     *
     * tyme4kt 1.5.0 的 `Constellation` **没有**起止日期 API（只有 getName/next/fromName/fromIndex），
     * 所以从当天前后扫库自己的边界：一直走到 `getConstellation()` 换人为止。这样显示的区间与
     * 卡上那个星座**永远自洽**，不依赖任何"通用星座表"——实测 tyme4kt 的天秤是 9月23日–10月23日，
     * 而通用表写的是到 10月22日，照抄通用表就和卡上的星座对不上了。
     *
     * 双向各最多走 40 天（真值 30~32 天），是防越界数据的护栏，不是业务上限。
     */
    private fun windowOf(date: CivilDate, sign: String): String {
        var first = date
        var guard = 0
        while (guard++ < 40) {
            val prev = plusDays(first, -1)
            if (signNameOf(prev) != sign) break
            first = prev
        }
        var last = date
        guard = 0
        while (guard++ < 40) {
            val next = plusDays(last, 1)
            if (signNameOf(next) != sign) break
            last = next
        }
        return "${first.month}月${first.day}日–${last.month}月${last.day}日"
    }

    private fun signNameOf(c: CivilDate): String? =
        runCatching { SolarDay(c.year, c.month, c.day).getConstellation().getName() }.getOrNull()

    /** 与 tyme4kt 1.5.0 Constellation.NAMES 逐字对齐（两位短名，无「座」后缀） */
    private val SIGN_ORDER = listOf(
        "白羊", "金牛", "双子", "巨蟹", "狮子", "处女",
        "天秤", "天蝎", "射手", "摩羯", "水瓶", "双鱼"
    )

    private fun dayOfYear(c: CivilDate): Int {
        val mdays = monthDays(c.year)
        return (1 until c.month).sumOf { mdays[it - 1] } + c.day
    }

    private fun monthDays(year: Int): IntArray =
        intArrayOf(31, if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28,
            31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    /** 日期加减（跨月跨年），只用于扫星座边界，不做通用日期库 */
    private fun plusDays(c: CivilDate, delta: Int): CivilDate {
        var y = c.year
        var m = c.month
        var d = c.day + delta
        while (d < 1) {
            m--
            if (m < 1) { m = 12; y-- }
            d += monthDays(y)[m - 1]
        }
        while (d > monthDays(y)[m - 1]) {
            d -= monthDays(y)[m - 1]
            m++
            if (m > 12) { m = 1; y++ }
        }
        return CivilDate(y, m, d)
    }
}
