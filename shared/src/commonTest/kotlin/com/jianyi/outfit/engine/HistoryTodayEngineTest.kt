package com.jianyi.outfit.engine

import com.jianyi.outfit.data.model.HistoricalEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 「历史上的今天」引擎测试。
 *
 * 按 Ruling 2 只测 `parse` 这条纯函数路径，fixture 全部内联：Compose 的资源提供器在 JVM 单测里
 * 可见性不确定，把它写进断言只会得到「本机红、CI 绿、没人说得清为什么」的结果。
 * `ensureLoaded` 比 `parse` 多出来的那一步（读资源）本身没有可断言的行为，就不为它写测试。
 *
 * 全年 366 天的**内容完整性**不在这里管，那是 `tools/validate_today_dataset.py` 的活
 * （Task 7 已跑：1098 条 / 366 天 / 每日恰 3 条 / 无残留外部源痕迹）。计划原文里那个穷举 366 天
 * 的用例因此不写。这里钉的是**取数行为**：倒序、截断、缺日、非法日、畸形 JSON。
 *
 * fixture 的字段名与顺序照正式数据集（m/d/y/t/s），事件内容则是编的——史实归数据集和校验脚本，
 * 测试只负责形状。用例会改动引擎的单例状态，所以每个走 `eventsFor` 的用例都先自己 load 一份
 * fixture，不依赖用例顺序。
 */
class HistoryTodayEngineTest {

    /* ============ parse：纯函数，键为 month*100+day ============ */

    /** 钉住「同日取到 3 条、按年份由近及远」：输入故意打乱年份顺序 */
    @Test
    fun parses_a_day_into_events_ordered_from_the_most_recent_year() {
        val index = HistoryTodayEngine.parse(
            jsonOf(
                row(10, 5, 1962, 1, "旧事甲。"),
                row(10, 5, 2011, 1, "近事乙。"),
                row(10, 5, 1998, 2, "中间丙。"),
                row(1, 1, 1995, 3, "另一天的丁。")
            )
        )
        assertEquals(listOf(101, 1005), index.keys.sorted(), "键必须是 month*100+day")
        assertEquals(
            listOf(
                HistoricalEvent(year = 2011, summary = "近事乙。", type = 1),
                HistoricalEvent(year = 1998, summary = "中间丙。", type = 2),
                HistoricalEvent(year = 1962, summary = "旧事甲。", type = 1)
            ),
            index[1005], "输入顺序被打乱也要按年份由近及远排"
        )
    }

    /** 钉住「同日超过上限时截断到 3 条，且截掉的是最旧的那几条」（卡片只显示 3 条） */
    @Test
    fun a_day_over_the_per_day_limit_keeps_the_newest_and_drops_the_oldest() {
        val years = listOf(1879, 1979, 1990, 2005, 2015)
        val index = HistoryTodayEngine.parse(
            jsonOf(*years.map { row(3, 14, it, 1, "事件$it。") }.toTypedArray())
        )
        val kept = index[314].orEmpty()
        assertEquals(HistoricalEvent.PER_DAY_LIMIT, kept.size, "超出每日上限必须截断")
        assertEquals(listOf(2015, 2005, 1990), kept.map { it.year }, "截的是最旧的，不是最新的")
    }

    /** 钉住 `t` 原样透传到 HistoricalEvent.type（UI 侧不用它分支，但模型要有） */
    @Test
    fun the_t_field_passes_through_into_event_type() {
        val index = HistoryTodayEngine.parse(
            jsonOf(
                row(6, 1, 2001, 1, "事件句。"),
                row(6, 1, 1950, 2, "出生句。"),
                row(6, 1, 1900, 3, "逝世句。")
            )
        )
        assertEquals(listOf(1, 2, 3), index[601].orEmpty().map { it.type }, "1/2/3 逐条原样落到 type")
    }

    /** 钉住非法日期不进索引；闰日 2/29 是真实日期，必须留下来（数据集覆盖 366 天） */
    @Test
    fun impossible_dates_never_enter_the_index_while_valid_days_survive() {
        val index = HistoryTodayEngine.parse(
            jsonOf(
                row(2, 30, 2000, 1, "不存在的二月三十。"),
                row(13, 1, 2000, 1, "不存在的十三月。"),
                row(4, 31, 2000, 1, "四月只有三十天。"),
                row(0, 1, 2000, 1, "零月。"),
                row(2, 29, 2000, 1, "闰日是真的。"),
                row(12, 31, 2000, 1, "年末。")
            )
        )
        assertEquals(listOf(229, 1231), index.keys.sorted(), "非法月日被丢弃，2/29 与 12/31 必须保留")
    }

    /** 钉住畸形 JSON 不崩、得到空索引：每一条都单独跑一遍，任何一种抛出来就红 */
    @Test
    fun malformed_json_yields_an_empty_index_and_never_throws() {
        val cases = listOf(
            "空串" to "",
            "纯空白" to "   ",
            "根本不是 JSON" to "历史上的今天：<a href='#'>外部网页片段</a>",
            "整体是对象而不是数组" to """{"m":1,"d":1,"y":1999,"t":1,"s":"只有孤零零一条。"}""",
            "少字段" to """[{"m":1,"d":1,"y":1999}]""",
            "字段类型错" to """[{"m":1,"d":1,"y":"一九九九","t":1,"s":"年份写成了汉字。"}]""",
            "数组中途截断" to """[{"m":1,"d":1,"y":1999,"t":1,"s":"好的"}, {"m":1,"d":2,"y":2020,"t":1,"s":"缺右括号""",
            "空数组" to "[]"
        )
        for ((label, json) in cases) {
            val index = HistoryTodayEngine.parse(json)
            assertTrue(index.isEmpty(), "$label：应得到空索引，实到 $index")
        }
    }

    /* ============ eventsFor：走索引的读出口 ============ */

    /** 钉住「缺某日 → 空列表、不抛」：UI 侧据此整卡不渲染（spec 7.1） */
    @Test
    fun a_day_the_dataset_does_not_cover_returns_an_empty_list() {
        HistoryTodayEngine.load(jsonOf(row(10, 5, 2011, 1, "近事乙。")))
        assertEquals(1, HistoryTodayEngine.eventsFor(10, 5).size, "先确认索引确实装上了")
        assertEquals(emptyList(), HistoryTodayEngine.eventsFor(7, 20), "数据集没覆盖的日子给空列表，不抛")
    }

    /** 钉住「非法日期 → 空列表、不抛」：越界入参由调用方负责，引擎只负责不炸 */
    @Test
    fun an_out_of_range_date_returns_an_empty_list_instead_of_throwing() {
        HistoryTodayEngine.load(jsonOf(row(2, 28, 1900, 1, "旧事甲。")))
        for ((m, d) in listOf(2 to 30, 13 to 1, 0 to 1, 1 to 32)) {
            assertEquals(emptyList(), HistoryTodayEngine.eventsFor(m, d), "$m/$d 是非法日期，不该抛")
        }
    }

    /**
     * 钉住畸形输入不留半构建索引：整份索引要么都在、要么整份不在。
     *
     * 逐行边解边塞的实现会在这里留下「10/5 有两条」这种半份状态——真机上表现是有些天正常、
     * 有些天少一条，没人会把它和一次解析失败联系起来。所以这里先装一份好的、再喂一条中途畸形的，
     * 断言好的那份被整体换掉而不是被留下半截。
     */
    @Test
    fun a_failed_load_leaves_no_half_built_index() {
        HistoryTodayEngine.load(
            jsonOf(row(10, 5, 2011, 1, "近事乙。"), row(10, 5, 1962, 1, "旧事甲。"))
        )
        assertEquals(2, HistoryTodayEngine.eventsFor(10, 5).size, "先确认整份索引在")
        HistoryTodayEngine.load("""[{"m":10,"d":5,"y":1999,"t":1,"s":"好的"}, {"m":10,"d":5}]""")
        assertEquals(emptyList(), HistoryTodayEngine.eventsFor(10, 5), "畸形输入后不该留下半截索引")
    }

    /**
     * 钉住载入闸门与「索引是否为空」解耦。
     *
     * 计划原文用 `if (index.isEmpty())` 当「还没加载」的判据：一次失败后索引就是空的，
     * 于是每次进首页都重新读一遍资源、重新失败一次（顺带重复刷日志）。这里把闸门单独记，
     * 失败也算「试过」，所以空索引不再触发重试。
     */
    @Test
    fun a_failed_load_does_not_reopen_the_load_gate() {
        HistoryTodayEngine.load("")
        assertTrue(HistoryTodayEngine.hasAttemptedToLoad, "失败也算尝试过，否则 ensureLoaded 会反复重读资源")
        assertTrue(HistoryTodayEngine.eventsFor(10, 5).isEmpty(), "失败后索引是空的，不是半份")
    }

    /* ============ fixture 工具 ============ */

    /** 一行事件，字段名与顺序照正式数据集（m/d/y/t/s） */
    private fun row(m: Int, d: Int, y: Int, t: Int, s: String): String =
        """{"m":$m,"d":$d,"y":$y,"t":$t,"s":"$s"}"""

    private fun jsonOf(vararg rows: String): String = rows.joinToString(prefix = "[", postfix = "]")
}
