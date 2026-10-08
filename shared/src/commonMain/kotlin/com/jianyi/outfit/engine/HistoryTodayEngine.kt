package com.jianyi.outfit.engine

import com.jianyi.outfit.data.model.HistoricalEvent
import com.jianyi.outfit.shared.res.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 「历史上的今天」取数引擎。
 *
 * 数据集（Task 7，`shared/src/commonMain/composeResources/files/today_in_history.json`）
 * 打进包、离线读，所以没有网络失败态也没有缓存层——spec 3.1：能离线确定的东西不走网络。
 *
 * 拆成两层是 Ruling 2 定的：[parse] 是纯函数，内联 fixture 就能把它测透；[ensureLoaded] 只比它
 * 多做一件事——把资源字节换成一串文本。Compose 的资源提供器在 JVM 单测里可见性不确定，
 * 把读资源这一步写进断言只会得到「本机红、CI 绿、没人说得清为什么」的结果，所以它不留单测，
 * 全年 366 天的内容完整性由 `tools/validate_today_dataset.py` 兜（Task 7 已跑）。
 */
object HistoryTodayEngine {

    /**
     * 数据集在 composeResources 下的路径。
     *
     * CMP 1.8.2 不给 `files/` 生成类型化访问器（生成的 `Res` 只有 drawable/string/array/plurals/font
     * 四个 object，加一个按路径读的 `Res.readBytes`），所以计划原文里的
     * `readResourceBytes(Res.files.today_in_history)` 在这个版本压根不存在——`Res.files` 未生成。
     */
    private const val RESOURCE_PATH = "files/today_in_history.json"

    /**
     * 只开 `ignoreUnknownKeys`：数据集以后多一个字段不该让整张卡消失。
     *
     * 刻意不开 `isLenient` / `coerceInputValues`：那两个会把「y 写成字符串」这类 schema 漂移
     * 静默咽掉（回退成默认值 0），产出的正是本引擎唯一不接受的东西——一份半真半假的索引。
     */
    private val datasetJson = Json { ignoreUnknownKeys = true }

    @Serializable
    private class Row(val m: Int, val d: Int, val y: Int, val t: Int, val s: String)

    /** month*100+day → 当日事件，已按年份倒序、每日截断到 [HistoricalEvent.PER_DAY_LIMIT] 条 */
    private var index: Map<Int, List<HistoricalEvent>> = emptyMap()

    /**
     * 载入闸门，与「索引是否为空」分开记（[index] 为空既可能是没试过、也可能是试了但整份不可用）。
     *
     * 计划原文用 `if (index.isEmpty())` 当「还没加载」的判据，于是一次失败会永久停在空索引上，
     * 每次进首页都重读一遍资源、重新失败一次。闸门单独记，失败也算「试过」。
     */
    private var attempted = false

    /** 载入闸门状态，只为把「失败不重试」这条钉进单测（见 [ensureLoaded] 的注释） */
    internal val hasAttemptedToLoad: Boolean
        get() = attempted

    /** 读入并建立索引。重复调用幂等 */
    suspend fun ensureLoaded() {
        if (attempted) return
        // 读不到资源等于喂空串：走同一条装载路径，得到空索引，不留第二个分支
        val text = runCatching { Res.readBytes(RESOURCE_PATH).decodeToString() }.getOrDefault("")
        load(text)
    }

    /**
     * 把一段 JSON 装进索引，同时关闭载入闸门。
     *
     * 生产路径只经由 [ensureLoaded]；这个入口存在是为了让 [eventsFor] 的读出口和
     * 「畸形输入不留半构建索引 / 失败不重开闸门」这两条状态行为，能在不碰 Compose 资源的
     * 前提下被单测驱动（Ruling 2）。装载逻辑只有一份，测试驱动的那条和生产那条不会分叉。
     */
    internal fun load(json: String) {
        attempted = true
        index = parse(json)   // 整体替换，不逐行往里塞，所以不存在半构建状态
    }

    /**
     * JSON → 索引。**纯函数**：不碰资源、不写状态、任何输入都不抛。
     *
     * 结果要么整份有效、要么整份为空，没有中间态：数据集是打进包的内容资产，一条行外字符就让它
     * 整个不可用，表现是「这张卡今天全天不出现」——这种问题一眼能看见，也比逐行容错留下的
     * 「某月少了 20 天」那种要等到那天才暴露的静默缺口好归因（spec 第 7 节第 5 条）。
     *
     * 月日不合法的行单独丢弃而不是判整个输入作废：数据集里 2/30 这种笔误只该让那一天没卡。
     * 二月按 29 天判——数据集覆盖 366 天含闰日，闰不闰由内容侧决定，这里不再按年份算一遍。
     */
    fun parse(json: String): Map<Int, List<HistoricalEvent>> = runCatching {
        datasetJson.decodeFromString<List<Row>>(json)
            .filter { it.m in 1..12 && it.d in 1..MDAYS[it.m - 1] }
            .sortedByDescending { it.y }
            .groupBy { it.m * 100 + it.d }
            .mapValues { (_, day) ->
                day.take(HistoricalEvent.PER_DAY_LIMIT)
                    .map { HistoricalEvent(year = it.y, summary = it.s, type = it.t) }
            }
    }.getOrDefault(emptyMap())

    /**
     * 当日事件，按年份由近及远。
     *
     * 缺数据（含非法日期、含载入失败）返回空列表而不是 null 也不抛：UI 侧据此整卡不渲染，
     * 不留一张写着「暂无数据」的空壳（spec 7.1）。
     */
    fun eventsFor(month: Int, day: Int): List<HistoricalEvent> =
        index[month * 100 + day].orEmpty()

    private val MDAYS = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
}
