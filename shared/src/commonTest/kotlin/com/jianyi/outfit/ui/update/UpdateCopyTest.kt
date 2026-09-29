package com.jianyi.outfit.ui.update

import com.jianyi.outfit.data.AppVersion
import com.jianyi.outfit.data.update.UpdateManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 门禁层的纯决策用例：进度条的三种真相、按钮集合、要不要按住整页、每一句文案。
 *
 * 为什么这些判据被抽成函数单独测（而不是写在 composable 里）：shared 没有
 * Robolectric 也没有截图测试，`UpdateGateLayer` 里那几十行 UI 在本机一行都跑不到。
 * 于是"门禁能不能被关掉""VM 发下来的 Progress 看不看得见"这两件最承重的事，
 * 如果只存在于 composable 的 `if` 里，就**只能靠真机盯屏幕发现**。
 * 这个仓库已经为这类缺陷付过两次钱：
 * - 首页降级条的动作提示被 `maxLines = 1` 裁成省略号，a11y 树里文本完整、只有裁图看得出；
 * - 下载接缝原本只有终态，VM 里 `Downloading.done` 再没人赋值，24MB 的下载一路 0% 死条，
 *   而两侧的单测照样全绿（见 UpdateViewModelTest 里那三条 Progress 用例）。
 * 所以判据放在这儿测，UI 那一层只许照着画。
 */
private val MANIFEST = UpdateManifest(
    versionCode = 9,
    versionName = "2.2.0",
    minSupportedCode = 9,
    apkUrl = "https://gitee.com/x.apk"
)

/** 最坏负载之一：24MB 的包（真机上就是这个尺寸在决定一行文案放不放得下） */
private val BIG_MANIFEST = MANIFEST.copy(sizeBytes = 24_115_200L)

private val APP_VERSION = AppVersion(8, "2.1.2")

/** 声明过总大小与没声明的两份，进度条的三种真相由这两个输入决定 */
private const val TOTAL = 24_115_200L

/** 正好一半，能除尽成 0.5f（Float 只有 24 位尾数，随手挑的数会带出一串二进制误差） */
private const val HALF = 12_057_600L

/**
 * "门禁那一套"的全部状态。
 *
 * 刻意包含下载中与下载失败：门禁下点了下载，卡片会变成 Downloading/Failed，
 * 这时候它仍然是门禁 —— 少一条字段（`forced`）就会在下载中途被返回键绕过。
 */
private val GATE_LIKE_STATES = listOf<UpdateUiState>(
    UpdateUiState.Gate(MANIFEST),
    UpdateUiState.Downloading(MANIFEST, done = null, total = TOTAL, forced = true),
    UpdateUiState.Downloading(MANIFEST, done = HALF, total = TOTAL, forced = true),
    UpdateUiState.Failed(MANIFEST, DownloadFailure.Network, forced = true)
)

class UpdateCopyTest {

    // ========== 进度与容量算术 ==========

    @Test fun progress_ratio_uses_declared_total() {
        assertEquals(0.5f, UpdateCopy.progressOf(12_000_000L, 24_000_000L))
    }

    /** 清单没声明 sizeBytes 时不能算出 NaN 或 0 —— 进度条要走不确定态 */
    @Test fun progress_is_undefined_when_total_unknown() {
        assertEquals(-1f, UpdateCopy.progressOf(5_000_000L, null))
        assertEquals(-1f, UpdateCopy.progressOf(5_000_000L, 0L), "total=0 是清单写坏了，不能当成 100%")
    }

    /** 已下超过总量（重定向重试后偶发）不能画出 130% 的进度条 */
    @Test fun progress_is_clamped() {
        assertEquals(1f, UpdateCopy.progressOf(30_000_000L, 24_000_000L))
    }

    @Test fun size_text_covers_mb_and_unknown() {
        assertEquals("11.4 MB / 23.0 MB", UpdateCopy.sizeText(11_953_920L, 24_115_200L))
        assertEquals("已下载 11.4 MB", UpdateCopy.sizeText(11_953_920L, null))
        assertEquals("", UpdateCopy.sizeText(0L, null))
    }

    /**
     * `total == 0` 与"没有 total"必须是同一句话（评审 Minor）。
     *
     * 清单把 sizeBytes 写成 0（或被解析器放过的坏值）也是一种"不知道总量"，
     * 而 [UpdateCopy.progressOf] 早就把它当不知道（返回 -1 走不确定态）。
     * 两边不一致的后果是那根条不涨、文字却印"11.4 MB / 0.0 MB" —— 一句话自己否认自己，
     * 用户只会认为程序卡死。
     */
    @Test fun a_zero_total_is_unknown_not_zero_mb() {
        assertEquals("已下载 11.4 MB", UpdateCopy.sizeText(11_953_920L, 0L), "0 当分母印出来的是一句自相矛盾的话")
        assertEquals("", UpdateCopy.sizeText(0L, 0L))
        assertEquals(
            -1f,
            UpdateCopy.progressOf(11_953_920L, 0L),
            "sizeText 与 progressOf 对 total=0 的口径必须一致：一个走未知、一个走 0%"
        )
    }

    /**
     * 一条进度都没收到时，关于大小唯一能说的一句是"这个包有多大"。
     *
     * 计划原本在这里写的是 `sizeText(done ?: 0L, total)`，那会渲染成
     * "0.0 MB / 23.0 MB" —— 那是同一根 0% 死条的文字版。
     */
    @Test fun total_text_names_only_the_declared_size() {
        assertEquals("共 23.0 MB", UpdateCopy.totalText(TOTAL))
        assertEquals("", UpdateCopy.totalText(null), "清单没给大小就什么都别说")
    }

    // ========== 失败文案 ==========

    @Test fun failure_text_is_actionable_for_every_reason() {
        for (reason in DownloadFailure.entries) {
            val text = UpdateCopy.failureText(reason)
            assertTrue(text.isNotBlank(), "$reason 必须给用户一句能照着做的话")
            assertTrue(text.length <= 40, "$reason 的文案超过了窄卡片一行能放下的量：$text")
        }
    }

    /** 校验失败的话必须和"网络断了"完全不同 —— 前者重试没用 */
    @Test fun checksum_failure_does_not_suggest_retry() {
        assertFalse(UpdateCopy.failureText(DownloadFailure.ChecksumMismatch).contains("重试"))
    }

    @Test fun version_line_shows_both_versions() {
        assertEquals("当前 2.1.2 → 最新 2.2.0", UpdateCopy.versionLine(APP_VERSION, MANIFEST))
    }

    // ========== 进度条的三种真相：Task 5 保证"发得下来"，这里保证"看得见" ==========

    /**
     * done == null（刚进 Downloading、一条 Progress 都还没收到）时既不画比例条、
     * 也不写"已下载 0"。
     */
    @Test fun no_progress_event_yet_keeps_the_bar_indeterminate() {
        val knownTotal = assertIs<DownloadView>(
            gateDownloadView(UpdateUiState.Downloading(BIG_MANIFEST, done = null, total = TOTAL, forced = true))
        )
        assertEquals(-1f, knownTotal.ratio, "还没有进度就不许画带比例的条")
        assertEquals("共 23.0 MB", knownTotal.label)

        val nothingKnown = assertIs<DownloadView>(
            gateDownloadView(UpdateUiState.Downloading(MANIFEST, done = null, total = null, forced = true))
        )
        assertEquals(-1f, nothingKnown.ratio)
        assertEquals("", nothingKnown.label, "连总量都不知道时无话可说，宁可空着也不编一个 0%")
    }

    @Test fun progress_event_with_total_moves_the_bar() {
        val view = assertIs<DownloadView>(
            gateDownloadView(UpdateUiState.Downloading(BIG_MANIFEST, done = HALF, total = TOTAL, forced = true))
        )
        assertEquals(0.5f, view.ratio, "VM 发下来的 done 没落到比例上，就是一根不动的条")
        assertEquals("11.5 MB / 23.0 MB", view.label)
    }

    /** total 缺失时不许编百分比，只能报已下载字节 */
    @Test fun progress_event_without_total_stays_indeterminate_and_shows_bytes() {
        val view = assertIs<DownloadView>(
            gateDownloadView(UpdateUiState.Downloading(MANIFEST, done = 5_000_000L, total = null, forced = true))
        )
        assertEquals(-1f, view.ratio, "没有总量就没有比例，画出来的任何百分比都是编的")
        assertEquals("已下载 4.8 MB", view.label)
    }

    /** 真的下了 0 字节（下载器发了第一条 Progress(0, total)）与"还没收到进度"是两件事 */
    @Test fun a_reported_zero_is_not_the_same_as_no_report_at_all() {
        val view = assertIs<DownloadView>(
            gateDownloadView(UpdateUiState.Downloading(BIG_MANIFEST, done = 0L, total = TOTAL, forced = true))
        )
        assertEquals(0f, view.ratio, "下载器确实报了 0，那就画 0%，这不是假进度条")
        assertEquals("0.0 MB / 23.0 MB", view.label)
    }

    @Test fun only_a_downloading_state_has_a_bar() {
        for (state in listOf<UpdateUiState>(
            UpdateUiState.Hidden,
            UpdateUiState.Gate(MANIFEST),
            UpdateUiState.OptionalCard(MANIFEST),
            UpdateUiState.Failed(MANIFEST, DownloadFailure.Io, forced = false)
        )) {
            assertNull(gateDownloadView(state), "$state 上不许画进度条")
        }
    }

    // ========== 按钮集合：门禁不提供任何关闭入口 ==========

    /**
     * 门禁那一套的任何一个状态都不许出现"以后再说"。
     *
     * 这条是 Task 6 的变异目标：把 Gate 分支也接上关闭入口，红的就是这一条，
     * 而 VM 的 `gate_cannot_be_dismissed` 仍然全绿 —— 因为 VM 那边只是"关掉时不动作"，
     * UI 若真摆出那颗按钮，症状是**用户点了没反应**：门禁的"不可跳过"退化成一次静默失效。
     *
     * 两种 `hasUrl` 都要跑：按钮集合现在由这两个输入共同决定，而"过滤后的那一份
     * 才是画出来的"正是评审抓到的错位。
     */
    @Test fun gate_like_states_never_offer_a_way_to_close() {
        for (state in GATE_LIKE_STATES) {
            for (hasUrl in listOf(true, false)) {
                val actions = gateActions(state, hasUrl)
                assertFalse(
                    actions.contains(GateAction.Later),
                    "${state::class.simpleName}(hasUrl=$hasUrl) 是门禁那一套，摆出关闭入口就等于没有门禁：$actions"
                )
            }
        }
    }

    /**
     * 反方向的两半，合起来才是"门禁不会变成砖"：
     * 1. 有地址时（这是唯一能走到 Downloading 的情形，下载本来就需要 apkUrl），
     *    门禁那一套的每个状态都得有至少一颗能点的按钮；
     * 2. 没地址时，一颗按钮都摆不出来的那个状态，就不许同时把整页按住。
     *
     * 第 2 半是评审 Important 的落点：过去这里断言的是未过滤的列表，而 UI 画的是过滤后的
     * （`gateActions(state).filterNot { it == CopyLink && url == null }`），两份列表不一致 ⇒
     * `Downloading(forced=true)` 只剩「复制下载链接」这一颗，一旦没有地址就是
     * "遮罩按住整页 + 零按钮"，用户只能杀进程。现在过滤在判据里，这条跑的就是画出来的那份。
     */
    @Test fun gate_like_states_always_offer_something_to_press() {
        for (state in GATE_LIKE_STATES) {
            assertTrue(
                gateActions(state, hasUrl = true).isNotEmpty(),
                "$state 一个按钮都没有，用户只能杀进程"
            )
            val drawn = gateActions(state, hasUrl = false)
            if (drawn.isEmpty()) {
                assertFalse(
                    gateHoldsPage(state, hasUrl = false),
                    "${state::class.simpleName} 在没有下载地址时既按住整页又零按钮 —— 只能杀进程"
                )
            }
        }
    }

    /** 遮罩与按钮这两份判据必须一起改：有地址照旧按住，没地址就放开 */
    @Test fun a_forced_download_holds_the_page_only_when_it_can_offer_the_link() {
        val forcedDownloading = UpdateUiState.Downloading(MANIFEST, done = null, total = TOTAL, forced = true)
        assertTrue(gateHoldsPage(forcedDownloading, hasUrl = true))
        assertFalse(gateHoldsPage(forcedDownloading, hasUrl = false))
        // 返回键那一侧（Task 8）只看状态，默认 hasUrl=true，判据不漂
        assertTrue(gateHoldsPage(forcedDownloading))
        assertEquals(listOf(GateAction.CopyLink), gateActions(forcedDownloading, hasUrl = true))
        assertEquals(emptyList(), gateActions(forcedDownloading, hasUrl = false))
    }

    @Test fun optional_card_offers_update_then_skip() {
        assertEquals(
            listOf(GateAction.Update, GateAction.Later),
            gateActions(UpdateUiState.OptionalCard(MANIFEST), hasUrl = true)
        )
    }

    @Test fun failed_gate_offers_retry_and_the_link_escape() {
        assertEquals(
            listOf(GateAction.Retry, GateAction.CopyLink),
            gateActions(UpdateUiState.Failed(MANIFEST, DownloadFailure.Network, forced = true), hasUrl = true)
        )
        // 没有地址时"重试"仍然要给：那一态本来就不按住整页，用户可以走开也可以再试一次
        assertEquals(
            listOf(GateAction.Retry),
            gateActions(UpdateUiState.Failed(MANIFEST, DownloadFailure.Network, forced = true), hasUrl = false)
        )
    }

    /**
     * 非强制的下载失败只给"重试"。
     *
     * 不给"以后再说"是因为 VM 的 dismissOptional() 只认 OptionalCard 那一态，
     * 在这里摆一颗按下去毫无反应的按钮比不摆更坏；而这一档本来就不按住整页
     * （见 an_optional_card_never_holds_the_page），用户不理会它也能继续用。
     */
    @Test fun non_forced_failure_offers_retry_only() {
        assertEquals(
            listOf(GateAction.Retry),
            gateActions(UpdateUiState.Failed(MANIFEST, DownloadFailure.Network, forced = false), hasUrl = true)
        )
    }

    /**
     * 下载进行中不给"立即更新"：VM 里有 `downloadJob` 防重入，摆出来是颗空按钮。
     * 门禁那一档给的是复制链接 —— 24MB 的路上不能一个出口都没有，而复制链接不关闭任何东西。
     */
    @Test fun a_running_download_offers_no_start_button() {
        assertEquals(
            emptyList(),
            gateActions(UpdateUiState.Downloading(MANIFEST, done = null, total = null, forced = false), hasUrl = true)
        )
        assertEquals(
            listOf(GateAction.CopyLink),
            gateActions(UpdateUiState.Downloading(MANIFEST, done = null, total = TOTAL, forced = true), hasUrl = true)
        )
    }

    @Test fun hidden_state_gives_the_layer_nothing_to_draw() {
        assertEquals(emptyList(), gateActions(UpdateUiState.Hidden, hasUrl = true))
        assertFalse(gateHoldsPage(UpdateUiState.Hidden))
        assertNull(gateDownloadView(UpdateUiState.Hidden))
        assertNull(gateManifest(UpdateUiState.Hidden))
    }

    // ========== 谁按住整页（与 Task 8 的返回键同一条判据） ==========

    @Test fun the_gate_and_its_own_download_hold_the_page() {
        assertTrue(gateHoldsPage(UpdateUiState.Gate(MANIFEST)))
        assertTrue(
            gateHoldsPage(UpdateUiState.Downloading(MANIFEST, done = null, total = TOTAL, forced = true)),
            "门禁下载中途放开点击，用户就能在包下完之前去用旧版"
        )
    }

    /**
     * 可跳过那一档不吃点击：卡片浮在页面上，不理会也能继续用。
     * 这同时是"非强制档没给以后再说也不锁人"的前提。
     */
    @Test fun an_optional_card_never_holds_the_page() {
        assertFalse(gateHoldsPage(UpdateUiState.OptionalCard(MANIFEST)))
        assertFalse(gateHoldsPage(UpdateUiState.Downloading(MANIFEST, done = null, total = null, forced = false)))
    }

    /**
     * 下不下来的时候必须放人走 —— 与 Task 8 `shouldBlockBack` 同一条判据。
     * 两处若各写一份 when，就会出现"返回键能退、卡片还按着你"。
     */
    @Test fun a_failed_download_stops_holding_the_page_even_under_a_gate() {
        assertFalse(gateHoldsPage(UpdateUiState.Failed(MANIFEST, DownloadFailure.Network, forced = true)))
    }

    // ========== 标题 ==========

    /** 门禁中途（下载中、失败后）标题不许换成"发现新版本"，否则与"不可跳过"自相矛盾 */
    @Test fun headline_keeps_saying_update_needed_across_the_gate() {
        for (state in GATE_LIKE_STATES) {
            assertTrue(gateHeadline(state).contains("需要更新"), "$state 的标题丢了门禁的分量：${gateHeadline(state)}")
        }
    }

    @Test fun optional_headline_does_not_claim_a_hard_gate() {
        val text = gateHeadline(UpdateUiState.OptionalCard(MANIFEST))
        assertTrue(text.contains("新版本"), text)
        assertFalse(text.contains("需要更新"), "可跳过那一档写成\"需要更新\"就是把提示做成了拦路虎：$text")
    }

    /** 每一张卡片都要有版本行，所以清单必须拿得出来（下载中/失败态也不例外） */
    @Test fun every_visible_state_carries_its_manifest() {
        for (state in listOf<UpdateUiState>(
            UpdateUiState.Gate(MANIFEST),
            UpdateUiState.OptionalCard(MANIFEST),
            UpdateUiState.Downloading(MANIFEST, done = HALF, total = TOTAL, forced = false),
            UpdateUiState.Failed(MANIFEST, DownloadFailure.Io, forced = false)
        )) {
            assertEquals(MANIFEST, gateManifest(state), "$state 拿不到清单就没有 apkUrl，也没有版本行")
        }
    }

    /** 按钮是照着 actions 渲染的，所以每个动作都得有自己的一句短话 */
    @Test fun every_action_has_a_distinct_short_label() {
        val labels = GateAction.entries.map { it.label }
        assertEquals(GateAction.entries.size, labels.distinct().size, "两个动作撞了同一句话：$labels")
        for (label in labels) {
            assertTrue(label.isNotBlank() && label.length <= 6, "按钮字串要短到窄屏放得下：$label")
        }
    }
}
