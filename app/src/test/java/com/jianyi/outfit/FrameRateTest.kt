package com.jianyi.outfit

import com.jianyi.outfit.util.FrameRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 帧率申请里能脱离真机验证的那部分是纯函数，就只测它们。
 *
 * `setRequestedFrameRate` / `preferredDisplayModeId` 本身在 JVM 上无法验证（要真机 + dumpsys），
 * 但「挑出来的那个档位对不对」是可以钉住的：`Display.Mode.refreshRate`
 * 在虚拟屏、异常驱动或某些 OEM 实现上真会给 0 / NaN / 离谱值，
 * 而这些值一旦原样发出去，等于**根本没申请**——且不会有任何报错，
 * 只会表现为"高帧率开关开了但没变化"，最难查。
 */
class FrameRateTest {

    @Test
    fun `picks the highest plausible rate`() {
        assertEquals(120f, FrameRate.highestOf(listOf(60f, 90f, 120f)), 0.001f)
        // 顺序无关
        assertEquals(120f, FrameRate.highestOf(listOf(120f, 60f, 90f)), 0.001f)
        // 同一分辨率下的多个档位，取顶档
        assertEquals(144f, FrameRate.highestOf(listOf(60f, 144f, 120f)), 0.001f)
    }

    @Test
    fun `unusable rates are skipped rather than requested`() {
        assertEquals(90f, FrameRate.highestOf(listOf(0f, 90f)), 0.001f)
        assertEquals(90f, FrameRate.highestOf(listOf(-1f, 90f)), 0.001f)
        assertEquals(90f, FrameRate.highestOf(listOf(Float.NaN, 90f)), 0.001f)
        assertEquals(90f, FrameRate.highestOf(listOf(Float.POSITIVE_INFINITY, 90f)), 0.001f)
    }

    /**
     * 负数哨兵（CATEGORY_HIGH = -4f 等）绝不能被当成"一个档位"挑出来 ——
     * 那是发给另一个通道的值，混进精确值里会让整次申请失效。
     */
    @Test
    fun `category sentinels are never treated as a rate`() {
        assertEquals(0f, FrameRate.highestOf(listOf(-4f, -1f, -2f, -3f)), 0.001f)
    }

    /** 超过 240Hz 不像能申请到的档位，宁可退回类别通道 */
    @Test
    fun `absurd rates are rejected`() {
        assertEquals(0f, FrameRate.highestOf(listOf(1000f)), 0.001f)
        assertEquals(120f, FrameRate.highestOf(listOf(1000f, 120f)), 0.001f)
    }

    @Test
    fun `empty candidate list yields zero so the caller falls back`() {
        assertEquals(0f, FrameRate.highestOf(emptyList()), 0.001f)
    }

    /**
     * 锁档位要的是**模式 id**，不只是 Hz —— id 挑错会连带把分辨率一起换掉，
     * 所以这里按 PLB110 的真实档位表（1256x2760 与 1080x2374 各三档）钉住行为。
     */
    private val plb110Modes = listOf(
        FrameRate.ModeSpec(1, 1256, 2760, 120.00001f),
        FrameRate.ModeSpec(2, 1256, 2760, 90.0f),
        FrameRate.ModeSpec(3, 1256, 2760, 60.0f),
        FrameRate.ModeSpec(4, 1080, 2374, 120.00001f),
        FrameRate.ModeSpec(5, 1080, 2374, 90.0f),
        FrameRate.ModeSpec(6, 1080, 2374, 60.0f)
    )

    @Test
    fun `locks the top mode at the current resolution`() {
        val current = FrameRate.ModeSpec(2, 1256, 2760, 90.0f)
        val peak = FrameRate.peakMode(plb110Modes, current)
        assertEquals(1, peak?.id)
        assertEquals(120.00001f, peak?.hz ?: 0f, 0.001f)
    }

    /** 降分辨率跑的机型：当前是 1080x2374 时不能去锁 1256x2760 那一档 */
    @Test
    fun `never crosses resolution`() {
        val current = FrameRate.ModeSpec(5, 1080, 2374, 90.0f)
        assertEquals(4, FrameRate.peakMode(plb110Modes, current)?.id)
    }

    /** 已经站在顶档上也要能挑出来（幂等），否则切回来时会以为"没有更高的档" */
    @Test
    fun `current mode being the peak still returns it`() {
        val current = FrameRate.ModeSpec(1, 1256, 2760, 120.00001f)
        assertEquals(1, FrameRate.peakMode(plb110Modes, current)?.id)
    }

    @Test
    fun `unusable modes are not lockable`() {
        val current = FrameRate.ModeSpec(2, 1256, 2760, 90.0f)
        val junk = listOf(
            FrameRate.ModeSpec(1, 1256, 2760, 0f),
            FrameRate.ModeSpec(2, 1256, 2760, Float.NaN),
            FrameRate.ModeSpec(3, 1256, 2760, -4f),
            FrameRate.ModeSpec(4, 1256, 2760, 1000f)
        )
        assertNull(FrameRate.peakMode(junk, current))
        assertNull(FrameRate.peakMode(emptyList(), current))
    }

    /** display 还没 attach 时 current 为 null：宁可不锁，也不锁一个猜出来的 id */
    @Test
    fun `no current mode means no lock`() {
        assertNull(FrameRate.peakMode(plb110Modes, null))
    }

    /** 同 Hz 重复档位取 id 大的那个，保证选择是确定的（否则每次重组可能换档） */
    @Test
    fun `tie on hz resolves to a deterministic mode`() {
        val dup = listOf(
            FrameRate.ModeSpec(7, 1256, 2760, 120f),
            FrameRate.ModeSpec(9, 1256, 2760, 120f),
            FrameRate.ModeSpec(8, 1256, 2760, 120f)
        )
        assertEquals(9, FrameRate.peakMode(dup, dup[0])?.id)
    }
}
