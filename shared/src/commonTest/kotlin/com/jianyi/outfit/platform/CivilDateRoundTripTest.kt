package com.jianyi.outfit.platform

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * epoch day ↔ 年月日 的往返测试（commonTest ⇒ Android 与 iOS 各跑一遍）。
 *
 * 锚点用可独立核算的公历日期，不让实现自己算出来的数当黄金值——
 * 与 PlatformDateAlignmentTest 同一套做法。
 */
class CivilDateRoundTripTest {

    @Test
    fun civil_from_epoch_day_matches_auditable_anchors() {
        assertEquals(CivilDate(1970, 1, 1), civilFromEpochDay(0L), "纪元原点")
        assertEquals(CivilDate(1969, 12, 31), civilFromEpochDay(-1L), "原点前一天")
        assertEquals(CivilDate(2000, 1, 1), civilFromEpochDay(10957L))
        assertEquals(CivilDate(2024, 2, 29), civilFromEpochDay(19782L), "闰年 2 月 29 日")
        assertEquals(CivilDate(2026, 10, 5), civilFromEpochDay(20731L))
    }

    @Test
    fun round_trip_is_identity_over_nine_decades() {
        // 1950-01-01 到 2049-12-31 逐日往返，含 25 个闰日
        var e = epochDayFromCivil(1950, 1, 1)
        val end = epochDayFromCivil(2049, 12, 31)
        var n = 0
        while (e <= end) {
            val c = civilFromEpochDay(e)
            assertEquals(e, epochDayFromCivil(c.year, c.month, c.day), "往返不一致于 $c")
            n++
            e++
        }
        assertEquals(36525, n, "100 年 = 36525 天（25 个闰日）")
    }

    @Test
    fun month_and_day_are_1_based_and_in_range() {
        var e = epochDayFromCivil(2026, 1, 1)
        while (e <= epochDayFromCivil(2026, 12, 31)) {
            val c = civilFromEpochDay(e)
            assert(c.month in 1..12) { "月份应 1~12，实到 ${c.month}" }
            assert(c.day in 1..31) { "日应 1~31，实到 ${c.day}" }
            assertEquals(e, epochDayFromCivil(c.year, c.month, c.day))
            e++
        }
    }

    @Test
    fun today_civil_date_agrees_with_platform_epoch_day() {
        val c = todayCivilDate()
        assertEquals(todayEpochDay(), epochDayFromCivil(c.year, c.month, c.day), "两端同源")
    }
}
