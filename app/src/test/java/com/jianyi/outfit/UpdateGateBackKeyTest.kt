package com.jianyi.outfit

import androidx.activity.OnBackPressedDispatcher
import com.jianyi.outfit.data.update.UpdateManifest
import com.jianyi.outfit.ui.update.DownloadFailure
import com.jianyi.outfit.ui.update.UpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 门禁与返回键。这里必须同时钉住两半，只测一半会留下真实事故：
 *
 * 1. **门禁下按不动**。返回键不吞，"不可跳过"就只是视觉上的 —— 用户按一次返回就回到原页面
 *    继续用旧版，强更等于没做。
 * 2. **非门禁下要按得动**。下面那四条用真实 [OnBackPressedDispatcher] 观察"返回键有没有走到
 *    兜底"，而不是只断言一个布尔。拦截类回调最常见的事故不是拦不住，而是**常驻开启**：
 *    装完更新、门禁撤了，返回键还在吞，整个 App 只能靠杀进程退出。
 *    所以 callback 必须随状态开/关，而"关"这件事只有真的按一次才看得见。
 *
 * 判据复用 Task 6 的 `gateHoldsPage`（门禁层用它决定要不要盖遮罩、吃点击），
 * 因此这张表同时是一条跨层锁：任何一边单独改判据 —— 出现"卡片还按着你、返回键却能退"
 * 或"卡片放开了、返回键还在吞" —— 这里就红。
 */
class UpdateGateBackKeyTest {

    private val m = UpdateManifest(9, "2.2.0", 9, "https://gitee.com/x.apk")

    // ---- 判据本身 ----

    @Test
    fun gate_blocks_back() {
        assertTrue(shouldBlockBack(UpdateUiState.Gate(m)))
    }

    /**
     * 这就是 Task 5 把 `forced` 塞进 Downloading 的理由：门禁下点了下载，卡片变成
     * Downloading，此时按返回若能退，门禁就被它要防的那个动作本身绕过。
     */
    @Test
    fun forced_download_blocks_back() {
        assertTrue(shouldBlockBack(UpdateUiState.Downloading(m, 1L, 2L, forced = true)))
    }

    /** 可跳过那一档的下载不能吞：它本来就可跳过，吞了返回键等于替用户做了"立即更新"的决定 */
    @Test
    fun voluntary_download_does_not_block_back() {
        assertFalse(shouldBlockBack(UpdateUiState.Downloading(m, 1L, 2L, forced = false)))
    }

    /**
     * 失败态**放开**返回键，连 forced 也放开。理由有两层，都不是随手定的：
     * - 计划的 Goal 写的就是"任何失败都能退出而不锁死用户"；
     * - `gateHoldsPage` 在 Failed 那一态本来就不拦点击（UpdateCopy.kt 里那条注释点明了
     *   "与 gateIsForced 刻意差一个分支"），只吞返回键的净效果是：页面照常能点，
     *   唯独退不出去 —— 把"失败可退"换成"必须杀进程"。
     * 强制那一档失败时卡片仍然给「重试」与「复制下载链接」，逃生口在那里，不在返回键上。
     */
    @Test
    fun failed_releases_back_so_the_user_can_leave() {
        assertFalse(shouldBlockBack(UpdateUiState.Failed(m, DownloadFailure.Network, forced = true)))
        assertFalse(shouldBlockBack(UpdateUiState.Failed(m, DownloadFailure.Io, forced = false)))
    }

    @Test
    fun optional_card_and_hidden_do_not_block_back() {
        assertFalse(shouldBlockBack(UpdateUiState.OptionalCard(m)))
        assertFalse(shouldBlockBack(UpdateUiState.Hidden))
    }

    // ---- 接到真实 dispatcher 上：返回键到底通不通 ----

    /**
     * 真的 dispatcher + 真的回调。`fellThrough` 就是"系统兜底有没有被调到"：
     * 未被吞的返回键一定会走到它（在 Activity 里就是 finish/回到上一层），
     * 被吞掉的不会。测的是接线，不是我自己重写一遍分发逻辑。
     */
    private class Harness {
        var fellThrough = 0
            private set

        private val dispatcher = OnBackPressedDispatcher(Runnable { fellThrough++ })

        val callback = UpdateBackKeyCallback()

        init {
            dispatcher.addCallback(callback)
        }

        /** 生产里 `sync` 由 onCreate 的 `state.collect` 触发，这里按状态机给出的同一序列调用它 */
        fun press(state: UpdateUiState) {
            callback.sync(state)
            dispatcher.onBackPressed()
        }
    }

    @Test
    fun back_is_swallowed_while_the_gate_holds() {
        val h = Harness()
        h.press(UpdateUiState.Gate(m))
        assertEquals("门禁挂着时返回键必须被吃掉", 0, h.fellThrough)
        assertTrue("门禁挂着时回调必须是开启的", h.callback.isEnabled)
    }

    /**
     * 这条是本文件最承重的一条（也是"只测门禁下按不动"那种半成品写法唯一抓不到的）：
     * 门禁撤掉之后返回键必须立刻恢复，否则用户装完更新就再也退不出这个 App。
     */
    @Test
    fun back_reaches_the_system_again_once_the_gate_lifts() {
        val h = Harness()
        h.press(UpdateUiState.Gate(m))
        assertEquals(0, h.fellThrough)

        // 安装页弹出去了（VM 把状态退回 Hidden）
        h.press(UpdateUiState.Hidden)
        assertEquals("门禁撤了返回键还在吞 —— App 退不出去", 1, h.fellThrough)
        assertFalse("状态离开门禁后回调必须关掉", h.callback.isEnabled)
    }

    /** 强制下载中吞、失败后放开：同一条回调随状态开关 */
    @Test
    fun forced_download_blocks_and_a_failure_releases() {
        val h = Harness()
        h.press(UpdateUiState.Downloading(m, 1L, 2L, forced = true))
        assertEquals("门禁下载中途按返回不许绕过门禁", 0, h.fellThrough)

        h.press(UpdateUiState.Failed(m, DownloadFailure.NoSpace, forced = true))
        assertEquals("一直下不下来时必须让人走", 1, h.fellThrough)
    }

    /** 非门禁的两种状态一次都不该吞：可跳过卡片与它自己的下载中 */
    @Test
    fun non_gate_states_never_swallow_back() {
        val h = Harness()
        h.press(UpdateUiState.OptionalCard(m))
        h.press(UpdateUiState.Downloading(m, 1L, 2L, forced = false))
        assertEquals("非门禁状态下返回键必须照常放行", 2, h.fellThrough)
    }
}
