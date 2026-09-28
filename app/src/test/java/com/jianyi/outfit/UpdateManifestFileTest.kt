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

    @Test fun required_fields_present_and_sane() {
        assertTrue(manifest.versionCode > 0)
        assertTrue(manifest.minSupportedCode > 0)
        assertTrue(manifest.versionName.isNotBlank())
        assertTrue(manifest.apkUrl.startsWith("https://"))
        assertTrue(manifest.apkUrl.endsWith(".apk"))
    }

    /** 清单里的 tag 段必须跟着 versionName 走，否则 HEAD 探包永远探到一个不存在的附件 */
    @Test fun apk_url_tag_matches_version_name() {
        assertTrue(
            "apkUrl 应含 /download/v${manifest.versionName}/，实际：${manifest.apkUrl}",
            manifest.apkUrl.contains("/download/v${manifest.versionName}/")
        )
    }

    /** 与 app 的 build.gradle.kts 对齐：清单声明的 versionName 不得低于已配置的版本 */
    @Test fun declared_version_matches_build_config() {
        assertEquals(BuildConfig.VERSION_NAME, manifest.versionName)
    }

    @Test fun declared_sha256_is_full_hex_or_absent() {
        val sha = manifest.sha256 ?: return
        assertTrue("sha256 必须是 64 位小写十六进制，实际长度 ${sha.length}", Regex("^[0-9a-f]{64}$").matches(sha))
    }
}
