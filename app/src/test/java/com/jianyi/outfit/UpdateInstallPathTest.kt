package com.jianyi.outfit

import com.jianyi.outfit.data.InstallResult
import com.jianyi.outfit.data.update.UPDATE_DIR_NAME
import com.jianyi.outfit.update.precheckInstallPath
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 层间契约：`DownloadEvent.Success(path)` → `install(String)` 这条接缝只允许装
 * `cacheDir/update/` 之下的文件。
 *
 * 评审的原话是"下载器清洗文件名只是**策略**，守卫要放在**知道 FileProvider root 的那一层**"。
 * 之所以测的是 [precheckInstallPath] 而不是 [com.jianyi.outfit.update.AndroidUpdateGateway.install]，
 * 是因为本项目的 app 测试是普通 JUnit4、没有 Robolectric，构造不出 Context（见 LocationBudgetTest
 * 里同样口径的说明）—— 于是守卫被抽成不碰平台 API 的纯函数，`install()` 只负责"先问它、
 * 它拒了就原样把 Failed 交出去"。`install()` 那三行接线靠读 diff 与真机确认。
 *
 * 不做的后果不是崩，是**误诊**：越界路径会走到 `FileProvider.getUriForFile` 抛
 * IllegalArgumentException，被 catch 折成 Failed 之后 UI 文案是"写入失败，请清理手机存储后重试" ——
 * 症状、日志、文案三者在说三件不同的事，而单测一条都不红。
 */
class UpdateInstallPathTest {

    private val cacheDir = Files.createTempDirectory("jianyi-install-guard").toFile()
    private val updateRoot = File(cacheDir, UPDATE_DIR_NAME)

    private fun apkIn(dir: File, name: String = "jianyi-2.2.0.apk"): File {
        dir.mkdirs()
        return File(dir, name).apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) }
    }

    @Test fun a_package_under_the_update_root_is_allowed_through() {
        val apk = apkIn(updateRoot)
        assertNull("下载器正常落盘的那个路径必须放行：${apk.absolutePath}", precheckInstallPath(apk.path, cacheDir))
    }

    /**
     * 越界 ⇒ Failed，而且**不抛**。
     *
     * 这里先在被拒的位置真放一个存在的文件：过去那道判据只有 `file.exists()`，
     * 一个真实存在的包就会被一路放行到 FileProvider —— 红的理由只能是"路径不在 root 里"。
     */
    @Test fun a_path_outside_the_update_root_is_rejected_without_throwing() {
        val elsewhere = apkIn(cacheDir, "installed-backup.apk")
        assertTrue("前提不成立：那个文件必须真的存在", elsewhere.isFile)
        assertEquals(InstallResult.Failed, precheckInstallPath(elsewhere.path, cacheDir))
    }

    /** `..` 必须在**解析之后**再比：直接比字符串的话看着在 root 里、实际在 root 外 */
    @Test fun dot_dot_cannot_walk_out_of_the_root() {
        val outside = apkIn(cacheDir, "secret.apk")
        assertTrue("前提不成立：被瞄上的那个文件必须真的存在", outside.isFile)
        val sneaky = File(updateRoot, ".." + File.separator + "secret.apk")
        assertEquals("带 .. 的路径要按解析结果拒", InstallResult.Failed, precheckInstallPath(sneaky.path, cacheDir))
    }

    /** 前缀撞上的兄弟目录不算 root：拼完 root 还要补一个分隔符 */
    @Test fun a_sibling_directory_with_the_same_prefix_is_not_the_root() {
        val apk = apkIn(File(cacheDir, "$UPDATE_DIR_NAME-evil"))
        assertEquals(InstallResult.Failed, precheckInstallPath(apk.path, cacheDir))
    }

    /** root 自己、不存在的文件、以及 root 里的子目录都不许被当成安装包 */
    @Test fun directories_and_missing_files_are_rejected() {
        assertEquals("目录不是包", InstallResult.Failed, precheckInstallPath(updateRoot.path, cacheDir))
        assertEquals(
            "下都没下下来的包不该走到 FileProvider",
            InstallResult.Failed,
            precheckInstallPath(File(updateRoot, "not-there.apk").path, cacheDir)
        )
        val subDir = File(updateRoot, "sub").apply { mkdirs() }
        assertEquals(InstallResult.Failed, precheckInstallPath(subDir.path, cacheDir))
    }

    /**
     * 目录名是三个文件的共同契约：ApkDownloader 的落盘位置（shared 的 [UPDATE_DIR_NAME]）、
     * FileProvider 的 root（`file_paths.xml` 里那个 `path="update/"`）、以及上面这道守卫。
     *
     * 前两者现在共用同一个常量，所以只剩"xml 有没有跟着改"这一种漂移 —— 而它的症状是
     * 真机上 `install()` 失败、单测一条都不红（台账里 Task 7/8 各记过一次）。
     * 读真文件的用例只能建在 app 的 JVM 测试里：commonTest 没有读文件的 API，
     * 而 iOS 模拟器沙箱里也没有仓库工作树（同 UpdateManifestFileTest 的理由）。
     */
    @Test fun the_fileprovider_config_covers_the_downloaders_directory() {
        val xml = File(repoRoot(), "app/src/main/res/xml/file_paths.xml")
        assertTrue("找不到 $xml —— 测试工作目录异常，这条锁就没了", xml.isFile)
        val text = xml.readText()
        assertTrue(
            "file_paths.xml 必须正好覆盖 cacheDir/$UPDATE_DIR_NAME/，否则 FileProvider 拒给 URI 而单测全绿：$text",
            text.contains("path=\"$UPDATE_DIR_NAME/\"")
        )
        // 同一层里不许出现"图省事"的配法：root-path 等于把整个可读文件系统交给安装器进程，
        // 而 path="." 的 cache-path 会把 Room 库的副本也变成可授权范围（照样装成功，同时静默扩权）
        assertTrue("file_paths.xml 不许出现 root-path", !text.contains("<root-path"))
        assertTrue("cache-path 的 path 不许写成 \".\"（那等于整个 cacheDir）", !text.contains("path=\".\""))
    }

    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        error("找不到仓库根（没有 settings.gradle.kts），测试工作目录异常：${File("").absolutePath}")
    }
}
