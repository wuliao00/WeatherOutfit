package com.jianyi.outfit.data.model

/**
 * 今日三卡的只读领域模型。
 *
 * 与天气模型分文件放：这三块是「今日」域，字段会随内容源演进而天气域不会，
 * 混在 Models.kt 里会让两个互不相干的变更理由抢同一个文件。
 *
 * 来源文案只以各模型 companion 里的 SOURCE_LABEL 常量暴露、由 UI 直接读，
 * 不进构造参数 —— 没有构造位点能塞进自编或错配的文案，
 * 「不把估算包装成官方数据」这条规矩由编译器而非纪律保证。
 */

/** 农历黄历一日 */
data class AlmanacDay(
    val lunarDateText: String,
    val ganzhiYear: String,
    val ganzhiDay: String,
    val zodiac: String,
    /** 当日节气名；非节气日取 null，UI 侧不显示该字段而非写"无" */
    val jieqi: String?,
    /** 宜。已截断到展示上限 */
    val recommends: List<String>,
    /** 忌。已截断到展示上限 */
    val avoids: List<String>,
    /** 建除十二值，如「建」「除」 */
    val duty: String,
    /** 冲煞描述，如「冲鼠(午)煞北」 */
    val chongSha: String,
    /** 月相，如「朔」「望」 */
    val moonPhase: String,
    val festival: String?
) {
    companion object {
        /** 宜/忌各最多展示几项。黄历原文动辄十余项，全铺与极简调性冲突 */
        const val TABOO_DISPLAY_LIMIT = 3

        const val SOURCE_LABEL = "按传统历法推算"
    }
}

/** 历史上的今天单条事件 */
data class HistoricalEvent(
    val year: Int,
    /** 事件描述，本项目独立撰写（见 spec 3.2） */
    val summary: String,
    /** 1=事件 2=出生 3=逝世 */
    val type: Int
) {
    companion object {
        const val SOURCE_LABEL = "史料整理，未逐条核实"
        const val PER_DAY_LIMIT = 3
    }
}

/** 星座运势一日 */
data class Horoscope(
    /** 星座名，如「天秤座」 */
    val constellation: String,
    val overall: String,
    val love: String,
    val career: String,
    val wealth: String,
    val luckyColor: String,
    val luckyNumber: Int
) {
    companion object {
        const val SOURCE_LABEL = "娱乐内容，非预测"
    }
}
