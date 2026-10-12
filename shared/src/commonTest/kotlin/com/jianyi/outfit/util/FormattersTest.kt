package com.jianyi.outfit.util

import com.jianyi.outfit.data.model.Horoscope
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 推送正文的组装。
 *
 * 钉两件事：没填生日时**不许**出现星座那一行（不能拿当天太阳星座顶上），
 * 填了则第二行必须冠星座名——用户要一眼看出那不是天气数据。
 */
class FormattersTest {

    private fun mine() = Horoscope(
        constellation = "双鱼",
        overall = "今天适合把想法先写下来再动手。",
        love = "",
        career = "",
        wealth = "",
        luckyColor = "米白",
        luckyNumber = 4,
        signWindow = "2月19日–3月20日",
        isMine = true
    )

    @Test
    fun 没填生日时推送只有穿搭那一句() {
        assertEquals("晴，适合薄外套。", Formatters.dailyPushText("晴，适合薄外套。", null))
    }

    @Test
    fun 填了生日时第二行带星座名与整体那句() {
        assertEquals(
            "晴，适合薄外套。\n双鱼座 · 今天适合把想法先写下来再动手。",
            Formatters.dailyPushText("晴，适合薄外套。", mine())
        )
    }

    /**
     * 预报日期要同时容忍 “-” 与 “/”。
     *
     * 接口 day=7 的逐日日期给的是 “2026/10/13”，而标签/短日期历史上只认短横线：
     * 只认一种的后果不是报错，是把整串原样画到卡片上、在窄列里被挤掉（真机实测显示成 “2026/1”）。
     */
    @Test
    fun 预报日期容忍斜杠分隔() {
        assertEquals("1/1", Formatters.forecastDateShort("2099/01/01"))
        assertEquals("1/1", Formatters.forecastDateShort("2099-01-01"))
        assertEquals(
            Formatters.forecastDayLabel("2099-01-01"),
            Formatters.forecastDayLabel("2099/01/01"),
            "同一日期两种分隔符必须得到同一个标签"
        )
    }
}
