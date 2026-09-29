package com.jianyi.outfit.data.update

import kotlin.test.Test
import kotlin.test.assertEquals

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
        assertEquals(UpdateVerdict.Optional, decideUpdate(manifest(9, 9), 8, apkReachable = false).verdict)
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
}
