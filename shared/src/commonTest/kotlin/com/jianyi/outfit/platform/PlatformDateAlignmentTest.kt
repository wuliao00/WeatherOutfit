package com.jianyi.outfit.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 日期算术的对齐测试（commonTest ⇒ Android 与 iOS 两端各跑一遍）。
 *
 * 为什么放公共测试而不是某一端：`epochDayFromCivil` 与 `weekdayNameFromEpochDay`
 * 是纯数学（Howard Hinnant 的 days_from_civil），而 `todayEpochDay()` /
 * `currentDayOfYear()` / `currentMonth()` 是问操作系统的。两端要给出**同一个**
 * epoch day，「今天/明天/后天」和每日轮换选景才不会错一天 ——
 * 错一天的表现是不崩溃、只是偶尔不对，最难被发现的那类。
 *
 * 锚点用可独立核算的公历日期，不用实现自己算出来的数当黄金值。
 */
class PlatformDateAlignmentTest {

    @Test
    fun epoch_day_matches_anchors_auditable_by_hand() {
        assertEquals(0L, epochDayFromCivil(1970, 1, 1), "纪元原点")
        assertEquals(-1L, epochDayFromCivil(1969, 12, 31), "原点前一天")
        // 1970→2000 共 30 年、7 个闰日：30*365+7 = 10957
        assertEquals(10957L, epochDayFromCivil(2000, 1, 1))
        // 2000-01-01 是星期六（可查日历核对），10957 % 7 推不出这个，必须靠公式
        assertEquals("星期六", weekdayNameFromEpochDay(10957L))
        // 2024-01-01 = 10957 + 24*365 + 6 闰日
        assertEquals(19723L, epochDayFromCivil(2024, 1, 1))
        assertEquals(19782L, epochDayFromCivil(2024, 2, 29), "闰年 2 月 29 日")
        assertEquals("星期四", weekdayNameFromEpochDay(19782L), "2024-02-29 是星期四")
        assertEquals("星期三", weekdayNameFromEpochDay(epochDayFromCivil(1969, 12, 31)))
    }

    @Test
    fun epoch_day_is_contiguous_across_month_and_year_edges() {
        assertEquals(1L + epochDayFromCivil(2026, 8, 31), epochDayFromCivil(2026, 9, 1))
        assertEquals(1L + epochDayFromCivil(2026, 12, 31), epochDayFromCivil(2027, 1, 1))
        assertEquals(
            1L + epochDayFromCivil(2028, 2, 28),
            epochDayFromCivil(2028, 2, 29),
            "闰年 2 月有 29 日"
        )
        assertEquals(
            1L + epochDayFromCivil(2027, 2, 28),
            epochDayFromCivil(2027, 3, 1),
            "平年 2 月 28 日的次日是 3 月 1 日"
        )
    }

    @Test
    fun weekday_names_cover_seven_days_and_wrap_negatively() {
        val week = (0L..6L).map { weekdayNameFromEpochDay(it) }
        // epoch day 0 是星期四，所以从它开始连排七天
        assertEquals(
            listOf("星期四", "星期五", "星期六", "星期日", "星期一", "星期二", "星期三"),
            week
        )
        // 周期向前回退也要落在同一张表里（epoch day 为负时取模必须修正）
        assertEquals(week[6], weekdayNameFromEpochDay(-1L))
        assertEquals(week[0], weekdayNameFromEpochDay(7L))
    }

    @Test
    fun platform_today_values_are_in_their_declared_ranges() {
        val month = currentMonth()
        assertTrue(month in 1..12, "currentMonth 约定两端都返回 1~12，实到 $month")

        val doy = currentDayOfYear()
        assertTrue(doy in 1..366, "currentDayOfYear 应在 1~366，实到 $doy")

        val hour = currentHourOfDay()
        assertTrue(hour in 0..23, "currentHourOfDay 应是本地 0~23，实到 $hour")

        // epoch day 与"当年第几天"要同源：同一天各自问一次操作系统，
        // 结果必须能互相推出（iOS 用 NSCalendar、Android 用 java.time，两边实现不同）
        val today = todayEpochDay()
        assertTrue(today > 20_000L, "今天应晚于 2025-05（epoch day 20000），实到 $today")
        assertEquals(doy, (today - epochDayFromCivil(yearOfToday(), 1, 1) + 1).toInt())
    }

    /**
     * 用「今天的 epoch day」反推年份：从 1 月 1 日开始逐年加天数，
     * 直到 today 落在该年区间内。纯算术，不碰平台 API，
     * 这样它才能与 currentDayOfYear() 形成**互相校验**而不是同源自证。
     */
    private fun yearOfToday(): Int {
        val today = todayEpochDay()
        var year = 1970
        while (epochDayFromCivil(year + 1, 1, 1) <= today) year++
        return year
    }
}
