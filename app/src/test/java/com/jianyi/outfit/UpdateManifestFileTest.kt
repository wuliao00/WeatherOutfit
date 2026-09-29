package com.jianyi.outfit

import com.jianyi.outfit.data.update.UpdateManifestParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 这条测试存在的唯一理由：清单是**手写的**，而手写文件不会跟着代码一起改。
 * "把 versionCode 从 8 抬到 9 却忘了改 update.json" 的表现是所有人永远收不到更新，
 * 单元测试全绿、CI 全绿、只有真机升级时才暴露 —— 所以必须有一条用例去读那个真文件。
 *
 * 放在 app 的 JVM 测试而不是 commonTest：commonTest 没有读文件的 API，
 * 且 iOS 模拟器的沙箱里根本没有仓库工作树。
 * 相应地，`app/build.gradle.kts` 必须把 update.json 登记成这条测试任务的输入，
 * 否则只改清单不改代码时任务会判 UP-TO-DATE / 在 CI 里 FROM-CACHE，锁会静默失效。
 *
 * **这里刻意不重复 `isSane()`**：`manifest` 之所以非 null，就是因为解析器已经逐条查过
 * `versionCode > 0`、https、`.apk`、`versionName` 非空、sha256 是 64 位小写十六进制。
 * 再断言一遍不会多抓一个 bug，只会把"清单读不到"以外的原因伪装成通过（原来那五条
 * 与 `declared_sha256_is_full_hex_or_absent` 就是这么删掉的；哈希形状的断言归
 * `UpdateManifestTest.malformed_sha256_is_invalid`）。留下的每条都必须**只有这份文件**
 * 才能回答：它是否与 BuildConfig 一致、URL 里的 tag 段是否跟着版本走、
 * 以及 min 是否被写到了 latest 之上（解析器不钳制，见下）。
 */
class UpdateManifestFileTest {

    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        error("找不到仓库根（没有 settings.gradle.kts），测试工作目录异常：${File("").absolutePath}")
    }

    private val manifest by lazy {
        val file = File(repoRoot(), "update.json")
        assertTrue("仓库根缺 update.json：$file", file.exists())
        val text = file.readText()
        val parsed = UpdateManifestParser.parse(text)
        assertNotNull("update.json 解析失败，整份清单必须能读：\n$text", parsed)
        parsed!!
    }

    /**
     * 真正决定门禁的是 versionCode，不是 versionName：T2 的 `decideUpdate` 第一条判据就是
     * `currentVersionCode >= manifest.versionCode ⇒ UpToDate`。清单停在 8 而 app 抬到 9 时，
     * 所有人永远"已是最新"、测试全绿、零异常 —— 只钉 versionName 的那半把锁抓不到它。
     * 两个字段各一条用例，失败信息直接指出是谁没跟着走。
     */
    @Test fun declared_version_code_matches_build_config() {
        assertEquals(BuildConfig.VERSION_CODE, manifest.versionCode)
    }

    /** 与 app 的 build.gradle.kts 对齐：清单声明的 versionName 必须就是已配置的版本 */
    @Test fun declared_version_name_matches_build_config() {
        assertEquals(BuildConfig.VERSION_NAME, manifest.versionName)
    }

    /** 清单里的 tag 段必须跟着 versionName 走，否则 HEAD 探包永远探到一个不存在的附件 */
    @Test fun apk_url_tag_matches_version_name() {
        assertTrue(
            "apkUrl 应含 /download/v${manifest.versionName}/，实际：${manifest.apkUrl}",
            manifest.apkUrl.contains("/download/v${manifest.versionName}/")
        )
    }

    /**
     * 原来这里是一堆 `versionCode > 0` / `startsWith("https://")` / `endsWith(".apk")`，
     * 已删（理由见类注释）。留下的这条是 `isSane()` **不管**、而手写文件真会写错的：
     * min 高于 latest。解析器刻意不钳制（`UpdateManifestTest.min_above_latest_still_parses`
     * 钉的就是"原样透出"），所以 min=12、versionCode=9 这种笔误能一路走到判定层，
     * 变成"所有人都被硬拦"。
     */
    @Test fun min_supported_code_is_not_above_declared_version_code() {
        assertTrue(
            "minSupportedVersionCode(${manifest.minSupportedCode}) 不得高于 versionCode(${manifest.versionCode})：" +
                "解析器不钳制，判定层会把它当成对所有人生效的硬门禁",
            manifest.minSupportedCode <= manifest.versionCode
        )
    }
}
