package com.jianyi.outfit

import com.jianyi.outfit.util.FrameRate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 帧率申请里唯一那部分是纯函数，就只测它。
 *
 * `setRequestedFrameRate` 本身在 JVM 上无法验证（要真机 + dumpsys），
 * 但「挑出来的那个数字对不对」是可以钉住的：`Display.Mode.refreshRate`
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
}
