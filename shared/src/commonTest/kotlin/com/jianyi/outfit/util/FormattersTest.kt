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
}
