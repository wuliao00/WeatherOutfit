package com.jianyi.outfit.data.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateDecisionTest {

    private fun manifest(
        latest: Int,
        min: Int,
        url: String = "https://gitee.com/wuliao11541/WeatherOutfit/releases/download/v2.2.0/jianyi-2.2.0.apk"
    ) = UpdateManifest(latest, "2.2.0", min, url)

    /** 清单缺失 ⇒ Unreachable，且不带 manifest（UI 因此拿不到任何东西可显示） */
    @Test fun absent_manifest_is_unreachable() {
        val d = decideUpdate(null, currentVersionCode = 8, apkReachable = false)
        assertEquals(UpdateVerdict.Unreachable, d.verdict)
        assertEquals(null, d.manifest)
    }

    @Test fun same_version_is_up_to_date() {
        assertEquals(UpdateVerdict.UpToDate, decideUpdate(manifest(9, 9), 9, true).verdict)
    }

    /** 装了比清单更新的包（内测/手动装的），也必须 UpToDate，不能提示"去更新"到一个更旧的版本 */
    @Test fun newer_than_manifest_is_up_to_date() {
        assertEquals(UpdateVerdict.UpToDate, decideUpdate(manifest(9, 9), 12, true).verdict)
    }

    @Test fun below_min_with_package_available_is_forced() {
        assertEquals(UpdateVerdict.Forced, decideUpdate(manifest(9, 9), 8, true).verdict)
    }

    /**
     * 门禁的硬前提：包真的能下。
     * 清单已提交、CI 还没把 APK 传上去 —— 这个窗口期如果拦人，
     * 用户就被锁在一个下载不到的包前面，正是强更最容易把 App 变砖的形态。
     */
    @Test fun below_min_without_package_available_degrades_to_optional() {
        assertEquals(
            UpdateVerdict.Optional,
            decideUpdate(manifest(9, 9), 8, apkReachable = false).verdict
        )
    }

    /** 高于最低线、低于最新版：只弹可跳过提示 */
    @Test fun between_min_and_latest_is_optional() {
        assertEquals(UpdateVerdict.Optional, decideUpdate(manifest(9, 5), 8, true).verdict)
    }

    /**
     * minSupported 写成比 latest 还大 = 手滑。
     * 解析器**原样透出 12**（见 `UpdateManifestTest.min_above_latest_still_parses`，它不该偷偷改数据），
     * 钳位发生在这里：`decideUpdate` 用 `minOf` 把 effective min 钳到 latest，
     * 于是结果是"照常拦老版本"，而不是"更强硬地连最新版的人一起拦"。
     */
    @Test fun min_above_latest_is_clamped() {
        assertEquals(UpdateVerdict.Forced, decideUpdate(manifest(9, 12), 8, true).verdict)
        assertEquals(UpdateVerdict.UpToDate, decideUpdate(manifest(9, 12), 9, true).verdict)
    }

    /** 判定必须是无副作用的纯函数：同一组输入两次结果一致，且不改动入参 */
    @Test fun decide_is_pure() {
        val m = manifest(9, 9)
        assertEquals(decideUpdate(m, 8, true), decideUpdate(m, 8, true))
    }

    @Test fun decision_carries_the_manifest_for_ui() {
        val m = manifest(9, 9)
        assertEquals(m, decideUpdate(m, 8, true).manifest)
    }

    /**
     * 探针谓词与判定**穷举等价**：当且仅当探针结论会改变 verdict，才需要发那次 HEAD。
     *
     * 为什么这条比"看代码觉得显然"重要：`needsApkProbe` 存在的唯一理由就是不许出现第二处阈值比较，
     * 而它一旦和 `decideUpdate` 的分支漂移，症状是"该探时没探 ⇒ Forced 静默变 Optional"，
     * 没有日志、没有异常、任何单条 verdict 用例也都还是绿的。
     * 穷举 (latest, min, current) 全部组合，把"漂移"这件事变成编译期就不可能存在。
     */
    @Test fun probe_is_needed_exactly_when_the_verdict_depends_on_it() {
        for (latest in 1..10) {
            for (min in 1..10) {
                for (current in 0..11) {
                    val m = manifest(latest, min)
                    val verdictIfReachable = decideUpdate(m, current, true).verdict
                    val verdictIfUnreachable = decideUpdate(m, current, false).verdict
                    val dependsOnProbe = verdictIfReachable != verdictIfUnreachable
                    assertEquals(
                        dependsOnProbe,
                        needsApkProbe(m, current),
                        "latest=$latest min=$min current=$current：reachable→$verdictIfReachable、" +
                            "不可达→$verdictIfUnreachable，但 needsApkProbe=" +
                            needsApkProbe(m, current)
                    )
                }
            }
        }
    }

    /** 钳位后的阈值同样作用于谓词：min=12 高于 latest=9 时，装最新版的人不该被探一次 */
    @Test fun probe_predicate_uses_the_clamped_min() {
        assertTrue(needsApkProbe(manifest(9, 12), 8))
        assertFalse(needsApkProbe(manifest(9, 12), 9))
        assertFalse(needsApkProbe(manifest(9, 12), 12))
    }
}
