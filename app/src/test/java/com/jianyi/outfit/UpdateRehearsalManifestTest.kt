package com.jianyi.outfit

import com.jianyi.outfit.data.update.UPDATE_MANIFEST_URL
import com.jianyi.outfit.data.update.UpdateManifest
import com.jianyi.outfit.data.update.UpdateManifestParser
import com.jianyi.outfit.data.update.UpdateVerdict
import com.jianyi.outfit.data.update.decideUpdate
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 仓库根那份 `update.test.json` —— 门禁的端到端演练清单 —— 的自校验。
 *
 * ## 为什么单独一条测试类而不是往 UpdateManifestFileTest 里加方法
 * 那个类被钉成"改了版本号忘了改清单"的锁，台账里明确要求动它之前先动清单。
 * 这里的三条判的是另一件事：**演练路径本身还成不成立**。两个文件、两种失效形状，
 * 混在一个类里以后，"哪条红了该改哪个文件"又要靠人记。
 *
 * ## 为什么这条路需要锁
 * `minSupportedVersionCode` 保持 1（没有存量用户），所以生产数据上硬门禁永远不会出现。
 * 演练清单是唯一能让门禁 UI 在合并前跑起来的东西 —— 而它同样是**手写**的 JSON。
 * 它一旦写坏（解析失败、或者 min 不再高于装机版本），症状是"装了 debug 包去验门禁，
 * 结果界面上什么都不显示"，看起来像门禁功能没做，实际是那份清单废了。
 * 这正好是本项目反复付钱的那一类：静默降级成"一切正常/一切为空"。
 *
 * 三条都用真实判定函数 [decideUpdate]，不在测试里另写一遍版本比较 ——
 * 比较只许存在于判定层一处（见 UpdateDecision.kt）。
 */
class UpdateRehearsalManifestTest {

    /**
     * 与 UpdateManifestFileTest 里那份逐字同逻辑。刻意复制而不是抽公共 helper：
     * 那个文件是已被钉住的锁，为了省 8 行去改它不值。
     */
    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        error("找不到仓库根（没有 settings.gradle.kts），测试工作目录异常：${File("").absolutePath}")
    }

    private fun manifest(fileName: String): UpdateManifest {
        val file = File(repoRoot(), fileName)
        assertTrue("$fileName 必须存在于仓库根：$file", file.exists())
        val text = file.readText()
        val parsed = UpdateManifestParser.parse(text)
        assertNotNull("$fileName 解析失败（整份清单会被判无效 ⇒ 演练时界面上什么都没有）：\n$text", parsed)
        return parsed!!
    }

    /**
     * 演练清单必须**真的**能把装了本包的设备判成硬门禁。
     * 这条红了通常意味着 app 的 versionCode 已经抬到 99 以上 —— 那就把演练清单的两个 99
     * 一起抬上去，别改这条断言（断言的是"它能拦住我们"，不是"数值刚好是 99"）。
     */
    @Test fun the_rehearsal_manifest_gates_the_installed_build() {
        val rehearsal = manifest("update.test.json")
        val decision = decideUpdate(rehearsal, BuildConfig.VERSION_CODE, apkReachable = true)
        assertEquals(
            "演练清单对装机 versionCode=${BuildConfig.VERSION_CODE} 判的不是 Forced ⇒ 门禁不会出现，" +
                "整条端到端路径等于没验过（$rehearsal）",
            UpdateVerdict.Forced,
            decision.verdict
        )
        assertNotNull("Forced 必须带清单，否则状态机那一侧会 fail-open 成 Hidden", decision.manifest)
    }

    /**
     * 反面对照，也是这条测试类真正的用处：**生产清单不许拦得住装机的这个包**。
     * 有人为了让门禁出现而把 `minSupportedVersionCode` 抬到 99（或者干脆把演练内容粘进
     * update.json）的话，这里立刻红 —— 那种改动一旦推到 main，就是对每一台旧设备发硬门禁，
     * 而 apkUrl 指向的还是别人仓库的附件。
     */
    @Test fun the_production_manifest_does_not_gate_the_installed_build() {
        val production = manifest("update.json")
        val decision = decideUpdate(production, BuildConfig.VERSION_CODE, apkReachable = true)
        assertEquals(
            "生产清单对装机 versionCode=${BuildConfig.VERSION_CODE} 判了 ${decision.verdict}：" +
                "要么 minSupportedVersionCode 被抬上去了（那是给真设备发门禁，要单独授权），" +
                "要么清单版本落后于本包（那是所有人收不到更新）。演练请走 update.test.json",
            UpdateVerdict.UpToDate,
            decision.verdict
        )
    }

    /**
     * 默认地址必须还是生产那份文件。覆盖只可能来自 `-PupdateManifestUrl=`（debug 才有字段值），
     * 把常量本身改成 update.test.json 的话，release 包会去读一份第三方仓库的门禁清单 ——
     * 那不是"演练"，那是对所有设备发一次硬门禁。
     */
    @Test fun the_default_manifest_url_points_at_the_production_file() {
        assertTrue(
            "UPDATE_MANIFEST_URL 必须以 /update.json 结尾，实际：$UPDATE_MANIFEST_URL",
            UPDATE_MANIFEST_URL.endsWith("/update.json")
        )
    }
}
