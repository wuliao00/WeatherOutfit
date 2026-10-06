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
 * 选取用 (星座序号 * 31 + 年内日序) 混合后取模，保证同一天同星座结果固定，
 * 而相邻日几乎必然不同——这是这类卡片最容易被用户识破的地方。
 */
object HoroscopeEngine {

    private const val POOL_SIZE = 6

    /** @return 当日星座内容；日期非法或星座名不在表内时返回 null（交给 UI 不渲染该卡） */
    fun of(date: CivilDate): Horoscope? {
        val solar = runCatching { SolarDay(date.year, date.month, date.day) }.getOrNull()
            ?: return null
        // 名字用 getName()：1.5.0 的 Constellation 没有 .name 属性（Task 1 实测）
        val name = runCatching { solar.getConstellation().getName() }.getOrNull() ?: return null

        val signIndex = SIGN_ORDER.indexOf(name)
        if (signIndex < 0) return null

        val doy = dayOfYear(date)
        val k = (signIndex * 31 + doy) % POOL_SIZE

        return Horoscope(
            constellation = name,
            overall = HoroscopePool.overall[name]?.getOrNull(k) ?: return null,
            love = HoroscopePool.love[name]?.getOrNull(k) ?: return null,
            career = HoroscopePool.career[name]?.getOrNull(k) ?: return null,
            wealth = HoroscopePool.wealth[name]?.getOrNull(k) ?: return null,
            luckyColor = HoroscopePool.colors[(signIndex + doy) % HoroscopePool.colors.size],
            luckyNumber = (signIndex * 7 + doy * 3) % 9 + 1
        )
    }

    /** 与 tyme4kt 1.5.0 Constellation.NAMES 逐字对齐（两位短名，无「座」后缀） */
    private val SIGN_ORDER = listOf(
        "白羊", "金牛", "双子", "巨蟹", "狮子", "处女",
        "天秤", "天蝎", "射手", "摩羯", "水瓶", "双鱼"
    )

    private fun dayOfYear(c: CivilDate): Int {
        val mdays = intArrayOf(31, if ((c.year % 4 == 0 && c.year % 100 != 0) || c.year % 400 == 0) 29 else 28,
            31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        return (1 until c.month).sumOf { mdays[it - 1] } + c.day
    }
}
