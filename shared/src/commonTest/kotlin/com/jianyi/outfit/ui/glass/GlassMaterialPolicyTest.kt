package com.jianyi.outfit.ui.glass

import com.jianyi.outfit.data.model.GlassQuality
import com.jianyi.outfit.data.model.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 玻璃材质的「什么时候该糊、糊多透」策略测试。
 *
 * 这一版界面是被推翻重做的：上一版默认走静态着色，着色本身又叠了三层白
 * （本体 0.56 + 上缘高光 0.52 + 1px 描边 0.62），叠完接近不透明，屏幕上就是一排
 * 灰色圆角板。所以这里钉的不只是函数返回值，还有那条「实时模糊路径上绝不再叠白」的
 * 数值红线 —— 谁把 tintAlpha 调回 0.5 以上，这条测试就会红。
 *
 * 从 app 的 JVM 测试搬进 commonTest：realtimeBlurFor 是 shared 的 internal，
 * 且这组真值表在 iOS 上同样生效。
 */
class GlassMaterialPolicyTest {

    /* ============ 档位 x 角色 x 硬件能力 ============ */

    @Test
    fun `performance tier never blurs anything even on capable hardware`() {
        for (role in GlassRole.entries) {
            assertFalse(
                realtimeBlurFor(GlassQuality.PERFORMANCE, role, canBlur = true),
                "$role should stay static on PERFORMANCE"
            )
        }
    }

    @Test
    fun `balanced tier blurs floating bars but not the many cards`() {
        assertTrue(realtimeBlurFor(GlassQuality.BALANCED, GlassRole.BAR, canBlur = true))
        assertFalse(realtimeBlurFor(GlassQuality.BALANCED, GlassRole.CARD, canBlur = true))
    }

    @Test
    fun `realtime tier blurs both`() {
        for (role in GlassRole.entries) {
            assertTrue(
                realtimeBlurFor(GlassQuality.REALTIME, role, canBlur = true),
                "$role should blur on REALTIME"
            )
        }
    }

    /** Android 11 及以下没有 RenderEffect：选了全实时也只能整体降级，不能白屏也不能崩 */
    @Test
    fun `hardware without RenderEffect degrades every tier to static`() {
        for (quality in GlassQuality.entries) {
            for (role in GlassRole.entries) {
                assertFalse(
                    realtimeBlurFor(quality, role, canBlur = false),
                    "$quality/$role must not claim realtime without RenderEffect"
                )
            }
        }
    }

    /* ============ 默认值 ============ */

    @Test
    fun `default glass quality is the transparent one`() {
        assertEquals(GlassQuality.REALTIME, UserPreferences().glassQuality)
        // 老用户 DataStore 里没有这个键，读出来是 null：必须落到同一个默认值，
        // 否则升级后他们看到的还是那块灰板子
        assertEquals(GlassQuality.REALTIME, GlassQuality.safe(null))
        assertEquals(GlassQuality.REALTIME, GlassQuality.safe("NOT_A_TIER"))
    }

    @Test
    fun `every tier has a distinct label and a description for the settings page`() {
        val labels = GlassQuality.entries.map { it.label }
        assertEquals(labels.size, labels.distinct().size)
        GlassQuality.entries.forEach {
            assertTrue(it.desc.isNotBlank(), "${it.name} needs a subtitle")
        }
    }

    /* ============ 数值红线 ============ */

    /** 越厚的玻璃越不透、糊得越开；顺序反了层次就没了 */
    @Test
    fun `emphasis ordering is monotonic`() {
        val ordered = listOf(
            GlassEmphasis.ULTRA_THIN,
            GlassEmphasis.THIN,
            GlassEmphasis.REGULAR,
            GlassEmphasis.THICK
        )
        assertEquals(ordered.size, ordered.distinct().size)
        for (i in 1 until ordered.size) {
            val lo = ordered[i - 1]
            val hi = ordered[i]
            assertTrue(lo.tintAlpha < hi.tintAlpha, "tint")
            assertTrue(lo.frostAlpha < hi.frostAlpha, "frost")
            assertTrue(lo.blur < hi.blur, "blur")
            assertTrue(lo.elevation < hi.elevation, "elevation")
        }
    }

    /**
     * 有真实模糊兜底时，着色必须远弱于静态着色 —— 通透感就来自这个差值。
     * 0.30 是上一版 REGULAR 的一半：再往上叠就等于把糊好的背景重新盖白。
     */
    @Test
    fun `realtime tint stays far lighter than the static fallback`() {
        GlassEmphasis.entries.forEach {
            assertTrue(
                it.tintAlpha <= 0.30f,
                "${it.name}: realtime tint must stay under the milk ceiling"
            )
            assertTrue(
                it.tintAlpha < it.frostAlpha * 0.5f,
                "${it.name}: realtime tint must be clearly lighter than frost"
            )
        }
    }

    /** 描边是「方向性的弱受光」，不是等宽高亮环；超过 0.25 就重新变成贴纸 */
    @Test
    fun `border stays a rim light rather than a sticker outline`() {
        GlassEmphasis.entries.forEach {
            assertTrue(it.borderAlpha in 0.05f..0.25f, "${it.name} border too strong")
            assertTrue(it.rimAlpha in 0.05f..0.35f, "${it.name} rim too strong")
        }
    }
}
