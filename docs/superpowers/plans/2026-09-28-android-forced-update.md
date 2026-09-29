# 安卓强制更新（Gitee 源）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让简衣 Android 端在冷启动时从 Gitee 读版本清单，普通新版本弹可跳过提示、破坏性版本硬门禁，App 内下载 APK 并拉起系统安装页，任何失败都能退出而不锁死用户。

**Architecture:** 清单解析、判定、状态机、UI 全部进 `shared/commonMain`（KMP），网络与哈希用可注入接缝以便纯 JVM 测试；只有"落盘下载"和"拉起安装"留在 app 模块（Android 独有），iOS 侧 `AppUpdateGateway.supported = false` 让入口整体不出现。发布走 Gitee Release 附件 + 仓库根 `update.json`（raw 域免鉴权可读，已实测）。

**Tech Stack:** Kotlin 2.1.0、KMP、Compose Multiplatform 1.8.2、Ktor 3.0.3、kotlinx-serialization 1.7.3、coroutines 1.9.0、Room 2.7.2、JUnit4（app 侧）、kotlin.test（shared 侧）、GitHub Actions。

**Spec:** `docs/superpowers/specs/2026-09-28-android-forced-update-design.md`

## Global Constraints

- 仓库根：`C:/Users/Administrator/Desktop/gh-optimize/WeatherOutfit`；包名 `com.jianyi.outfit`；`shared` 与 `app` 用同一套包名，app 侧零 import 改动。
- 构建环境（本机 Windows）：`GRADLE_USER_HOME=E:/dev/gradle-home`、`JAVA_HOME=E:/dev/jdk`、`sdk.dir=E:/dev/android-sdk`。**不要**把 gradle 输出管道到 `tail`（会缓冲全部输出）。
- 改完 Kotlin 必跑 `py tools/kotlin_lint.py <files>`：它抓 commonMain 里的 JVM 泄漏（`java.*` / `android.*` / `System.*` / **`BuildConfig`**）和块注释闭合后的游离 ` * ` 续行。
- `commonMain` 禁止出现 `BuildConfig`、`java.*`、`android.*`、`System.*`（`androidx.*` 合法）。当前版本号只能由 app 侧注入。
- 依赖版本一律走 `gradle/libs.versions.toml`，不在模块里写字面量版本。
- 提交信息用中文，正文必须写"为什么"，尤其写清踩过的坑与被推翻的假设。
- 两个远端都要推：`origin`=GitHub `wuliao00/WeatherOutfit`，`gitee`= `git@gitee.com:wuliao11541/WeatherOutfit.git`。
- 真机：vivo V2156A（Android 11）与 OPPO PLB110（Android 15）。**绝不卸载** `com.jianyi.outfit`（机上那份 Room 2.6.1 时代建的库是"升级不丢数据"的唯一证据）；装包一律 `adb install -r -t`。
- 设备前台若属于别的运行中会话（`com.studykit`、`com.wuliao00.genglingng` 等），立刻停手，把设备侧结论标为未验证。
- 清单解析必须**严格**：不允许 `coerceInputValues` / `isLenient`（理由见 Task 1）。
- 门禁的硬前提：`apkUrl` HEAD 探包成功（2xx）才允许 `Forced`；清单无效/拉取失败/探包失败一律不拦。
- **版本号比较只许出现在 `UpdateDecision.kt` 一个文件里**（`decideUpdate` 与 `needsApkProbe` 共用
  `effectiveMinSupportedCode()`）。任何 Task 都不许在 UI/VM/仓库里再写一次 `>= versionCode` ——
  两处漂移的症状是"该探时没探 ⇒ Forced 静默变 Optional"，零日志零红测。
- 测试写法两条硬约定（Task 3 实测踩过）：
  1. 调 suspend 函数（`UpdateChecker.check`、`ApkDownloader.download`、任何 VM 方法）的用例必须
     `@Test fun xxx() = runTest { … }`；commonTest 已声明 `kotlinx.coroutines.test`，既有写法参照
     `SettingsRepositoryTest`。直接调用会得到 `should be called only from a coroutine`。
  2. `kotlin.test` 的断言顺序是 **assertTrue(actual, message)**，而 app 模块用 JUnit 的
     `org.junit.Assert.assertTrue(message, condition)` —— 两边互相抄会得
     "None of the following candidates is applicable"。
- commonTest 里不许直接触碰平台 actual 的副作用（`android.util.Log` 在无 Robolectric、
  未配 `isReturnDefaultValues` 的普通 JVM 上是抛 `Stub!` 的桩）；需要观测就注入回调。

---

## 文件结构

**新建（shared，跨端）**
- `shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateManifest.kt` — 数据契约 + 严格解析 + 字段自校验
- `shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateDecision.kt` — 纯判定函数（唯一决定"拦不拦"的地方）
- `shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateHttp.kt` — 网络接缝接口 + Ktor 实现
- `shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateRepository.kt` — 组合 fetch + probe，产出 `UpdateDecision`
- `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/update/UpdateViewModel.kt` — 状态机
- `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/update/UpdateGateLayer.kt` — 门禁/提示卡片 + 下载进度
- `shared/src/iosMain/kotlin/com/jianyi/outfit/platform/IosUpdateGateway.kt` — `supported = false` 空实现

**新建（app，Android 独有）**
- `shared/src/androidMain/kotlin/com/jianyi/outfit/data/update/ApkDownloader.kt` — 流式下载 + sha256 + 重试（放这里是因为 shared 的 Ktor 是 implementation，app 编译类路径上没有 io.ktor）
- `app/src/main/java/com/jianyi/outfit/update/AndroidUpdateGateway.kt` — 权限查询/引导 + FileProvider 安装 Intent
- `app/src/main/res/xml/file_paths.xml` — FileProvider 路径
- `app/src/test/java/com/jianyi/outfit/UpdateManifestFileTest.kt` — 读真实 `update.json` 的自校验
- `update.json`（仓库根）

**修改**
- `shared/src/commonMain/kotlin/com/jianyi/outfit/data/AppDependencies.kt` — 加 `appVersion`、`updateGateway`、`AppVersion`、`InstallResult`
- `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/root/RootScreen.kt:55-61` — 挂 gate 层
- `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/settings/SettingsScreen.kt` — 「检查更新」行
- `shared/src/commonMain/kotlin/com/jianyi/outfit/data/AppUpdateUi.kt`（新建，小）— 版本号/进度文案的纯格式化函数
- `app/src/main/java/com/jianyi/outfit/di/AppContainer.kt` — 注入版本号与 gateway
- `app/src/main/AndroidManifest.xml` — `REQUEST_INSTALL_PACKAGES` + FileProvider
- `app/src/main/java/com/jianyi/outfit/MainActivity.kt` — 冷启动触发一次检查
- `.github/workflows/release.yml`（新建）
- `README.md` — 发布手册一节

---

## Task 1: 清单契约与严格解析器

**Files:**
- Create: `shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateManifest.kt`
- Create: `update.json`（仓库根）
- Test: `shared/src/commonTest/kotlin/com/jianyi/outfit/data/update/UpdateManifestTest.kt`
- Test: `app/src/test/java/com/jianyi/outfit/UpdateManifestFileTest.kt`（读真实 `update.json` 的自校验）

**Interfaces:**
- Consumes: 无
- Produces: `data class UpdateManifest(versionCode: Int, versionName: String, minSupportedVersionCode: Int, apkUrl: String, sha256: String? = null, sizeBytes: Long? = null, notes: String? = null)`；`object UpdateManifestParser { fun parse(text: String?): UpdateManifest? }`（null = 无效/缺失）；`internal val updateJson: kotlinx.serialization.json.Json`

- [ ] **Step 1: 写失败的测试**

`shared/src/commonTest/kotlin/com/jianyi/outfit/data/update/UpdateManifestTest.kt`：

```kotlin
package com.jianyi.outfit.data.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateManifestTest {

    private val valid = """
        {"versionCode":9,"versionName":"2.2.0","minSupportedVersionCode":9,
         "apkUrl":"https://gitee.com/wuliao11541/WeatherOutfit/releases/download/v2.2.0/jianyi-2.2.0.apk",
         "sha256":"a".repeat(0)+"${"a".repeat(64)}","sizeBytes":24115200,"notes":"修 bug"}
    """.trimIndent().replace("\"a\".repeat(0)+", "")

    @Test fun full_manifest_parses() {
        val m = UpdateManifestParser.parse(valid)
        assertEquals(9, m?.versionCode)
        assertEquals("2.2.0", m?.versionName)
        assertEquals(9, m?.minSupportedVersionCode)
        assertEquals(24115200L, m?.sizeBytes)
    }

    /** 可选字段缺失要能解，且解成 null —— CI 首次出包之前哈希就是拿不到的 */
    @Test fun optional_fields_may_be_absent() {
        val m = UpdateManifestParser.parse(
            """{"versionCode":9,"versionName":"2.2.0","minSupportedVersionCode":1,"apkUrl":"https://gitee.com/x.apk"}"""
        )
        assertEquals(1, m?.minSupportedVersionCode)
        assertNull(m?.sha256)
        assertNull(m?.sizeBytes)
        assertNull(m?.notes)
    }

    @Test fun unknown_fields_are_ignored() {
        val m = UpdateManifestParser.parse(
            """{"versionCode":9,"versionName":"2.2.0","minSupportedVersionCode":1,"apkUrl":"u","future_field":42}"""
        )
        assertEquals(9, m?.versionCode)
    }

    /**
     * 这条测试是整个模块里最值钱的三条之一。
     *
     * 天气那边故意开了 isLenient + coerceInputValues（接口哪天多返回字段、
     * 数字时而是字符串，不能因此整份查询失败）。清单**绝不能**沿用那套配置：
     * coerceInputValues 会把 "versionCode":"9"（字符串）或 null 悄悄变成 0，
     * 于是 current(8) >= 0 ⇒ 判 UpToDate ⇒ 用户永远收不到更新，且没有任何报错。
     * 宽松解析在清单这里造出的不是崩溃，是**永久静默失效**。
     */
    @Test fun string_number_is_rejected_not_coerced() {
        assertNull(
            UpdateManifestParser.parse(
                """{"versionCode":"9","versionName":"2.2.0","minSupportedVersionCode":1,"apkUrl":"u"}"""
            )
        )
    }

    @Test fun missing_required_field_is_invalid() {
        assertNull(UpdateManifestParser.parse("""{"versionName":"2.2.0","minSupportedVersionCode":1,"apkUrl":"u"}"""))
    }

    @Test fun null_or_blank_or_non_json_is_invalid() {
        assertNull(UpdateManifestParser.parse(null))
        assertNull(UpdateManifestParser.parse(""))
        assertNull(UpdateManifestParser.parse("<html>404</html>"))
    }

    /** 空数组/空对象能过 JSON 语法，但必填字段是 Int 没有默认值 ⇒ 抛 ⇒ 判无效 */
    @Test fun json_array_is_invalid() {
        assertNull(UpdateManifestParser.parse("[]"))
    }

    /** 哈希要么不写，写了就必须是 64 位小写十六进制：截断的哈希会让校验永远失败 */
    @Test fun malformed_sha256_is_invalid() {
        val base = """{"versionCode":9,"versionName":"2.2.0","minSupportedVersionCode":1,"apkUrl":"u","sha256":""""
        assertNull(UpdateManifestParser.parse(base + "abc\"}"))
        assertNull(UpdateManifestParser.parse(base + ("z".repeat(64)) + "\"}"))
        assertTrue(UpdateManifestParser.parse(base + "a".repeat(64) + "\"}") != null)
    }

    /** 非 https 的 apk 地址直接判无效：明文下载一个可安装包等于把设备交出去 */
    @Test fun non_https_apk_url_is_invalid() {
        assertNull(
            UpdateManifestParser.parse(
                """{"versionCode":9,"versionName":"2.2.0","minSupportedVersionCode":1,"apkUrl":"http://gitee.com/x.apk"}"""
            )
        )
    }

    @Test fun apk_url_must_end_with_apk() {
        assertNull(
            UpdateManifestParser.parse(
                """{"versionCode":9,"versionName":"2.2.0","minSupportedVersionCode":1,"apkUrl":"https://gitee.com/x.zip"}"""
            )
        )
    }

    /** minSupported 高于 latest 是写错，不是"更强硬"：钳到 latest，但仍算有效清单 */
    @Test fun min_above_latest_still_parses() {
        val m = UpdateManifestParser.parse(
            """{"versionCode":9,"versionName":"2.2.0","minSupportedVersionCode":12,"apkUrl":"https://gitee.com/x.apk"}"""
        )
        assertEquals(12, m?.minSupportedVersionCode)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `GRADLE_USER_HOME=E:/dev/gradle-home JAVA_HOME=E:/dev/jdk ./gradlew :shared:testDebugUnitTest --tests "*UpdateManifestTest*"`
Expected: 编译失败（Unresolved reference: UpdateManifest / UpdateManifestParser）

- [ ] **Step 3: 写实现**

`shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateManifest.kt`：

```kotlin
package com.jianyi.outfit.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 清单解析配置。**刻意不复用** [com.jianyi.outfit.data.remote.weatherJson]。
 *
 * 天气那边开了 isLenient + coerceInputValues，因为那个接口多返回字段、
 * 数字时而是字符串 —— 为一个字段让整次查询失败不值得。
 * 同一套宽松用在这里会造出**永久静默失效**：coerceInputValues 会把
 * "versionCode":"9" 或 null 折成 0，于是 current >= 0 恒成立 ⇒ 判 UpToDate，
 * 用户再也收不到更新而没有任何异常。清单是要决定"拦不拦人"的，
 * 它对格式的容忍度必须和天气数据相反。
 *
 * 只留 ignoreUnknownKeys：允许以后往清单里加字段而不打断老版本 App。
 */
internal val updateJson = Json { ignoreUnknownKeys = true }

/**
 * Gitee 上一份可安装版本的声明。放在仓库根 `update.json`，
 * 通过 `https://gitee.com/wuliao11541/WeatherOutfit/raw/main/update.json` 读
 * —— raw 域免鉴权（实测 302 → raw.giteeusercontent.com → 200），
 * 而 `/releases/latest` 的响应形态由 User-Agent 决定（Dalvik UA 拿到的是 HTML），
 * 所以不能用它当清单。
 */
@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    /** 低于这个 versionCode 就硬门禁。与 [versionCode] 相等 = 只留最新版 */
    @SerialName("minSupportedVersionCode") val minSupportedCode: Int,
    /** 必须 https、必须以 .apk 结尾：指向 Gitee Release 附件的人类可读路径 */
    val apkUrl: String,
    /** 可选。CI 首次出包前拿不到真哈希；声明了就必须校验，没声明则跳过校验 */
    val sha256: String? = null,
    /** 可选。缺失时进度条走不确定态 */
    val sizeBytes: Long? = null,
    val notes: String? = null
)

object UpdateManifestParser {

    private const val SHA256_HEX_LENGTH = 64

    /** 任何"读不到/看不懂"都收敛成 null，由调用方降级成 Unreachable —— 不抛异常 */
    fun parse(text: String?): UpdateManifest? {
        if (text.isNullOrBlank()) return null
        val manifest = try {
            updateJson.decodeFromString(UpdateManifest.serializer(), text)
        } catch (e: Exception) {
            return null
        }
        return if (manifest.isSane()) manifest else null
    }

    /**
     * 反序列化成功之后再过一道语义闸。
     *
     * 每一项都是"错了会静默要命"的那类，不是格式洁癖：
     * - 非 https：明文传一个可安装包，中间人可以直接塞恶意 APK；
     * - 非 .apk：拿到的东西装不上，用户点了没反应；
     * - 哈希长度不对：下载永远校验失败，而失败原因看起来像包坏了；
     * - versionCode <= 0 或 sizeBytes 负数：一定是清单写错。
     */
    private fun UpdateManifest.isSane(): Boolean {
        if (versionCode <= 0) return false
        if (minSupportedCode <= 0) return false
        if (versionName.isBlank()) return false
        if (!apkUrl.startsWith("https://")) return false
        if (!apkUrl.endsWith(".apk")) return false
        if (sizeBytes != null && sizeBytes <= 0L) return false
        if (sha256 != null && !sha256.isSha256Hex()) return false
        return true
    }

    private fun String.isSha256Hex(): Boolean =
        length == SHA256_HEX_LENGTH && all { it in '0'..'9' || it in 'a'..'f' }
}
```

- [ ] **Step 4: 建仓库根 `update.json`（当前状态的诚实快照）**

版本号还没提升，所以清单声明的就是现在仓库里的版本（`versionCode = 8`），
`minSupportedVersionCode = 1` 保证**任何**已装用户都落在"不拦"那一侧；
`apkUrl` 指向尚未创建的 v2.2.0 附件，Task 11 提升版本时一并改齐。

```json
{
  "versionCode": 8,
  "versionName": "2.1.2",
  "minSupportedVersionCode": 1,
  "apkUrl": "https://gitee.com/wuliao11541/WeatherOutfit/releases/download/v2.1.2/jianyi-2.1.2.apk",
  "notes": "首个支持应用内更新的版本"
}
```

- [ ] **Step 6: 写"读真实清单文件"的自校验测试（app JVM 测试）**

`app/src/test/java/com/jianyi/outfit/UpdateManifestFileTest.kt`：

```kotlin
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
        assertNotNull("update.json 解析失败，整份清单必须能读：\n$text", UpdateManifestParser.parse(text))!!
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
```

> Step 6 与 Step 3 的 `declared_version_matches_build_config` 在**版本号提升前**会失败（`update.json` 写 2.1.2、`BuildConfig.VERSION_NAME` 也是 2.1.2 ⇒ 通过）。Task 11 提升版本时必须同步改清单，这条测试就是那把锁。若某次只想改代码不想改清单，就一起改 `versionName`，不要删这条断言。

- [ ] **Step 7: 跑 lint 与全部测试确认通过**

Run: `py tools/kotlin_lint.py shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateManifest.kt app/src/test/java/com/jianyi/outfit/UpdateManifestFileTest.kt`
Expected: `0 problem(s)`
Run: `./gradlew :shared:testDebugUnitTest --tests "*UpdateManifestTest*" :app:testDebugUnitTest --tests "*UpdateManifestFileTest*"`
Expected: BUILD SUCCESSFUL，11 + 4 条 0 失败

- [ ] **Step 8: 提交**

```bash
git add update.json shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateManifest.kt shared/src/commonTest/kotlin/com/jianyi/outfit/data/update/UpdateManifestTest.kt app/src/test/java/com/jianyi/outfit/UpdateManifestFileTest.kt
git commit -m "feat(update): 版本清单契约、严格解析器，以及读真实清单文件的自校验"
```

---

## Task 2: 判定纯函数（拦不拦，只在这一处决定）

**Files:**
- Create: `shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateDecision.kt`
- Test: `shared/src/commonTest/kotlin/com/jianyi/outfit/data/update/UpdateDecisionTest.kt`

**Interfaces:**
- Consumes: `UpdateManifest`（Task 1）
- Produces: `enum class UpdateVerdict { Unreachable, UpToDate, Optional, Forced }`；`data class UpdateDecision(val verdict: UpdateVerdict, val manifest: UpdateManifest?)`；`fun decideUpdate(manifest: UpdateManifest?, currentVersionCode: Int, apkReachable: Boolean): UpdateDecision`

- [ ] **Step 1: 写失败的测试**

`shared/src/commonTest/kotlin/com/jianyi/outfit/data/update/UpdateDecisionTest.kt`：

```kotlin
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

    /** minSupported 写成比 latest 还大 = 手滑，钳到 latest 而不是"更强硬地拦所有人" */
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
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :shared:testDebugUnitTest --tests "*UpdateDecisionTest*"`
Expected: 编译失败（Unresolved reference: decideUpdate）

- [ ] **Step 3: 写实现**

`shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateDecision.kt`：

```kotlin
package com.jianyi.outfit.data.update

/**
 * 判定结果。**整个功能只有这一个函数决定"拦不拦人"**，
 * UI、ViewModel、下载器都不允许自己再比较版本号 ——
 * 两处各比一次就有一处会漏掉 apkReachable 那条降级。
 */
enum class UpdateVerdict {
    /** 拿不到 / 看不懂清单，或包下不到 —— 什么都不显示，照常使用 */
    Unreachable,

    /** 已经是最新（或更新） */
    UpToDate,

    /** 有新版本，可跳过 */
    Optional,

    /** 低于最低可用版本，且包确实能下 —— 硬门禁 */
    Forced
}

data class UpdateDecision(val verdict: UpdateVerdict, val manifest: UpdateManifest?)

/**
 * 生效的最低可用版本：作者把 min 写成比 latest 还大时钳到 latest。
 * 这个表达式只能有一份 —— `decideUpdate` 与 `needsApkProbe` 都读它。
 */
private fun UpdateManifest.effectiveMinSupportedCode(): Int = minOf(minSupportedCode, versionCode)

/** 要不要为这份清单发那次 HEAD（挂起侧专用，见下方修订说明） */
fun needsApkProbe(manifest: UpdateManifest, currentVersionCode: Int): Boolean =
    currentVersionCode < manifest.effectiveMinSupportedCode()

/**
 * @param manifest 解析成功的清单；null 表示读不到或看不懂
 * @param currentVersionCode 装机包的 versionCode，由 app 侧注入（commonMain 不许碰 BuildConfig）
 * @param apkReachable 对 manifest.apkUrl 发 HEAD、跟完重定向后是否 2xx；
 *   传之前先问 needsApkProbe
 */
fun decideUpdate(
    manifest: UpdateManifest?,
    currentVersionCode: Int,
    apkReachable: Boolean
): UpdateDecision {
    val latest = manifest ?: return UpdateDecision(UpdateVerdict.Unreachable, null)
    if (currentVersionCode >= latest.versionCode) {
        return UpdateDecision(UpdateVerdict.UpToDate, latest)
    }
    // 写错的 minSupported（比 latest 还大）钳到 latest：
    // 不钳的话它会拦掉所有人，包括已经装了最新版的人。
    val minSupported = latest.effectiveMinSupportedCode()
    val verdict =
        if (currentVersionCode >= minSupported) UpdateVerdict.Optional
        else if (apkReachable) UpdateVerdict.Forced
        else UpdateVerdict.Optional
    return UpdateDecision(verdict, latest)
}
```

> **修订（Task 3 期间）**：第三个参数**保持** `apkReachable: Boolean`，但新增
> `fun needsApkProbe(manifest: UpdateManifest, currentVersionCode: Int): Boolean`，
> 与 `decideUpdate` 共用同一私有扩展 `effectiveMinSupportedCode()`（= `minOf(minSupportedCode, versionCode)`）。
>
> 起因：仓库层原来按计划自己抄了一遍 `currentVersionCode >= manifest.versionCode` 来"跳过探包"，
> 于是同一个判定语义散在两个文件 —— 任一处漂移的后果是"判定层想拦而探针没跑"，
> 门禁静默退化成 Optional，且与 `UpdateDecision.kt` 自称的"只有这一个函数决定拦不拦人"矛盾。
> 第一版修法是把参数改成惰性的 `probeApk: () -> Boolean`，**编译器直接拒绝**：
> `headOk` 是挂起函数，非挂起的函数类型里调它会得到
> `Suspension functions can only be called within coroutine body`；
> 把类型改成 `suspend () -> Boolean` 又必须把 `decideUpdate` 变成挂起函数。协程测试支持其实**已经有了**
> （commonTest 已声明 `kotlinx.coroutines.test`，既有 `SettingsRepositoryTest` 就是 `= runTest {}`），
> 所以那不是阻碍；否决它的真实理由是：只有纯值函数才配得上表驱动穷举，
> 挂起版一条条 `runTest` 会把最强那条锁换成最弱那条。
>
> 最终形态：纯函数不变，谓词单独导出给挂起侧用，阈值只有一份。
> 正确性不靠"读代码觉得显然"，由 `UpdateDecisionTest.probe_is_needed_exactly_when_the_verdict_depends_on_it`
> 穷举 `latest 1..10 × min 1..10 × current 0..11`，断言
> **"当且仅当 `apkReachable` 真值会改变 verdict"⇔"needsApkProbe 为 true"**。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :shared:testDebugUnitTest --tests "*UpdateDecisionTest*"`
Expected: BUILD SUCCESSFUL，9 条 0 失败

- [ ] **Step 5: 提交**

```bash
git add shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateDecision.kt shared/src/commonTest/kotlin/com/jianyi/outfit/data/update/UpdateDecisionTest.kt
git commit -m "feat(update): 判定纯函数，含\"包下不到就不拦\"的降级"
```

---

## Task 3: 网络接缝与 UpdateRepository

**Files:**
- Create: `shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateHttp.kt`
- Create: `shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateLog.kt`（`internal expect fun logUpdateWarning`）
- Create: `shared/src/androidMain/kotlin/com/jianyi/outfit/data/update/UpdateLog.android.kt`（`Log.w`）
- Create: `shared/src/iosMain/kotlin/com/jianyi/outfit/data/update/UpdateLog.ios.kt`（`NSLog`）
- Test: `shared/src/commonTest/kotlin/com/jianyi/outfit/data/update/UpdateRepositoryTest.kt`

**Interfaces（Task 3 修复轮后的最终形态，后面所有 Task 以这段为准）:**
- Consumes: `UpdateManifestParser`（Task 1）、`decideUpdate` + `needsApkProbe`（Task 2）、`httpClientEngine()`（`data/remote`，同模块 internal 直接可见）
- Produces: `sealed interface ManifestFetch { Body(text) | HttpStatus(code) | NetworkFailed(reason) }`；
  `interface UpdateHttp { suspend fun fetchManifest(url: String): ManifestFetch; suspend fun headOk(url: String): Boolean }`
  （**不再有 `getText(url): String?`** —— 把 403/404/5xx 和离线折成同一个 null，正好抹掉了这条功能最怕的那类永久静默失效）；
  `class KtorUpdateHttp : UpdateHttp`；`const val UPDATE_MANIFEST_URL: String`；
  `class UpdateRepository(http: UpdateHttp, manifestUrl: String = UPDATE_MANIFEST_URL, logWarning: (String) -> Unit = ::logUpdateWarning) : UpdateChecker`；
  `interface UpdateChecker { suspend fun check(currentVersionCode: Int): UpdateDecision }`
  —— `UpdateChecker.check` 的签名**没变**，Task 5 的假实现照旧可用。
- 报警三态：离线**不报**；非 2xx 报（带状态码与地址）；解析失败报（带地址）；
  该拦却探包失败也报（带 apkUrl）—— 后者是"作者想拦人而系统决定不拦"的唯一留痕。

- [ ] **Step 1: 写失败的测试（用假 UpdateHttp，不引 MockEngine）**

`shared/src/commonTest/kotlin/com/jianyi/outfit/data/update/UpdateRepositoryTest.kt`：

> **本 Task 已实现（提交 `552f5f3`），下面这段是历史文本，与实际文件不一致处以仓库里的实际文件为准。**
> 实际版本与这段的差异：① 每条调 `check(...)` 的用例都 `= runTest { ... }`（suspend 函数不能直接调）；
> ② `assertTrue` 是 `(actual, message)` 顺序，不是 app 模块 JUnit 的 `(message, actual)`；
> ③ `garbage_body_is_unreachable` 扩成 `unreadable_body_is_unreachable_and_warns`（要断言报警真的发出且带地址），
> 并新增 `http_failure_…_stays_silent`（离线不许报警）；
> ④ 新增 `optional_verdict_does_not_probe` / `default_manifest_url_is_used_when_not_overridden` /
> `manifest_url_is_the_gitee_raw_endpoint_not_the_releases_api`；
> ⑤ 告警通过构造参数 `logWarning` 注入（commonTest 里碰不得 android actual 的 `Log`）。

```kotlin
package com.jianyi.outfit.data.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 假实现而不是 Ktor MockEngine：接缝本身就一层，再加一个测试依赖只是多一个会坏的零件 */
private class FakeHttp(
    private val text: String? = null,
    private val reachable: Boolean = true
) : UpdateHttp {
    var getTextCalls = 0
    var headCalls = 0
    var headUrl: String? = null
    override suspend fun getText(url: String): String? { getTextCalls++; return text }
    override suspend fun headOk(url: String): Boolean { headCalls++; headUrl = url; return reachable }
}

private const val MANIFEST =
    """{"versionCode":9,"versionName":"2.2.0","minSupportedVersionCode":9,"apkUrl":"https://gitee.com/x.apk"}"""

class UpdateRepositoryTest {

    @Test fun reachable_package_and_forced_verdict() {
        val d = UpdateRepository(FakeHttp(MANIFEST, true)).check(8)
        assertEquals(UpdateVerdict.Forced, d.verdict)
    }

    @Test fun http_failure_is_unreachable_and_skips_probe() {
        val http = FakeHttp(null, true)
        val d = UpdateRepository(http).check(8)
        assertEquals(UpdateVerdict.Unreachable, d.verdict)
        // 清单都没拿到就不该再发 HEAD：省一次请求，也避免"探了个寂寞"
        assertEquals(0, http.headCalls)
    }

    @Test fun garbage_body_is_unreachable() {
        assertEquals(
            UpdateVerdict.Unreachable,
            UpdateRepository(FakeHttp("<html>404</html>", true)).check(8).verdict
        )
    }

    /** up-to-date 时不必探包 */
    @Test fun up_to_date_skips_probe() {
        val http = FakeHttp(MANIFEST, true)
        assertEquals(UpdateVerdict.UpToDate, UpdateRepository(http).check(9).verdict)
        assertEquals(0, http.headCalls)
    }

    /** 探包失败 ⇒ 降级 Optional（门禁的硬前提在 decideUpdate 里，这里只负责传对话） */
    @Test fun unreachable_package_degrades_to_optional() {
        assertEquals(
            UpdateVerdict.Optional,
            UpdateRepository(FakeHttp(MANIFEST, reachable = false)).check(8).verdict
        )
    }

    @Test fun probe_url_is_the_manifest_apk_url() {
        val http = FakeHttp(MANIFEST, true)
        UpdateRepository(http).check(8)
        assertEquals("https://gitee.com/x.apk", http.headUrl)
    }

    /** 默认走 UPDATE_MANIFEST_URL，调用方不必每次传 */
    @Test fun default_url_is_the_gitee_raw_manifest() {
        val http = FakeHttp(MANIFEST, true)
        UpdateRepository(http).check(9)
        assertTrue(http.getTextCalls == 1)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :shared:testDebugUnitTest --tests "*UpdateRepositoryTest*"`
Expected: 编译失败（Unresolved reference: UpdateRepository / UpdateHttp）

- [ ] **Step 3: 写接缝与 Ktor 实现**

`shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateHttp.kt`：

```kotlin
package com.jianyi.outfit.data.update

import com.jianyi.outfit.data.remote.httpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/** 读清单与探包的两个动作。抽成接口是为了让判定逻辑能在普通 JVM 里测。 */
interface UpdateHttp {
    /** 正文；非 2xx、超时、连接失败一律 null（不抛） */
    suspend fun getText(url: String): String?

    /** HEAD 跟完重定向后是否落在 2xx。401（签名过期）与 404（tag 不存在）都算 false */
    suspend fun headOk(url: String): Boolean
}

/** 清单地址。走 raw 域：实测免鉴权（302 → raw.giteeusercontent.com → 200）且支持 Range。 */
const val UPDATE_MANIFEST_URL =
    "https://gitee.com/wuliao11541/WeatherOutfit/raw/main/update.json"

class KtorUpdateHttp : UpdateHttp {

    private val client = HttpClient(httpClientEngine()) {
        install(HttpTimeout) {
            connectTimeoutMillis = 8_000
            requestTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
        followRedirects = true
        // 自己判状态码：404 是"清单还没放"的正常情形，不该走异常路径
        expectSuccess = false
    }

    override suspend fun getText(url: String): String? = try {
        val response: HttpResponse = client.get(url)
        if (response.status.isSuccess()) response.bodyAsText() else null
    } catch (e: Exception) {
        null
    }

    override suspend fun headOk(url: String): Boolean = try {
        client.head(url).status.isSuccess()
    } catch (e: Exception) {
        false
    }
}
```

> 注：`newHttpClientEngine()` 是本 Task 对 `WeatherApiClient.kt` 里 `internal expect fun httpClientEngine()` 的公开包装（见 Step 4），因为 `internal` 跨不出 `data.remote` 包。

- [ ] **Step 4: 把引擎工厂暴露给 update 包**

修改 `shared/src/commonMain/kotlin/com/jianyi/outfit/data/remote/WeatherApiClient.kt`，在 `internal expect fun httpClientEngine()` 下方加：

```kotlin
/** 给同模块其它网络使用者（清单拉取等）复用的引擎工厂。 */
internal fun newHttpClientEngine(): HttpClientEngine = httpClientEngine()
```

- [ ] **Step 5: 写仓库层**

`shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateRepository.kt`：

```kotlin
package com.jianyi.outfit.data.update

/** ViewModel 只认这个接口，好让状态机能用假实现跑完全部跳转。 */
interface UpdateChecker {
    suspend fun check(currentVersionCode: Int): UpdateDecision
}

/**
 * 把"读清单 → 判要不要提示 → 确认包真能下"串成一次调用。
 *
 * 三个次序都是刻意的：
 * 1. 清单读不到（离线、404）直接 Unreachable 且**不发 HEAD** —— 没有 apkUrl 可探，探了是浪费；
 * 2. 正文到了但解析失败 ⇒ 报一条 warning。这是唯一的观测通道：清单里一个拼错会让
 *    `parse()` 返回 null，于是"永远没有人收到更新"，而测试、CI、UI 全绿（Task 1 裁决）；
 * 3. **版本比较只写在 `decideUpdate` / `needsApkProbe` 里** —— 本类一度自己抄了一遍 `>=`
 *    来省掉探包，于是同一个决定"拦不拦人"的比较存在于两个文件；两处一旦漂移，
 *    结果就是判定层想拦而探针没跑，门禁静默退化成 Optional，正是整个功能最防的那件事。
 *    这里只问谓词，不出现任何阈值。
 */
class UpdateRepository(
    private val http: UpdateHttp,
    private val manifestUrl: String = UPDATE_MANIFEST_URL,
    private val logWarning: (String) -> Unit = ::logUpdateWarning
) : UpdateChecker {

    override suspend fun check(currentVersionCode: Int): UpdateDecision {
        val body = http.getText(manifestUrl)
        if (body == null) return UpdateDecision(UpdateVerdict.Unreachable, null)

        val manifest = UpdateManifestParser.parse(body)
        if (manifest == null) {
            logWarning(
                "更新清单读到了但解析失败：$manifestUrl ⇒ 对所有版本判 Unreachable，" +
                    "也就是没有人会收到更新。检查 versionCode / minSupportedVersionCode / apkUrl " +
                    "是否齐全，以及 apkUrl 是否 https 且以 .apk 结尾"
            )
            return UpdateDecision(UpdateVerdict.Unreachable, null)
        }
        // 只有 verdict 真的依赖探针结论时才发这次 HEAD；谓词与判定共用同一阈值，
        // 所以"少发一次请求"和"不会漏发"不可能互相矛盾。
        val reachable =
            if (needsApkProbe(manifest, currentVersionCode)) http.headOk(manifest.apkUrl) else false
        return decideUpdate(manifest, currentVersionCode, reachable)
    }
}
```

`shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateLog.kt`：

```kotlin
package com.jianyi.outfit.data.update

/**
 * 清单故障的观测通道。commonMain 不许引 `android.util.Log`，也不能 `println`
 * （iOS 侧 println 不进系统日志，等于没有）。
 *
 * 只在"读到了但看不懂"时用；离线是正常态，不该产日志。
 */
internal expect fun logUpdateWarning(message: String)
```

`shared/src/androidMain/kotlin/com/jianyi/outfit/data/update/UpdateLog.android.kt`：

```kotlin
package com.jianyi.outfit.data.update

import android.util.Log

/**
 * `adb logcat -s JianyiUpdate` 是这条故障在真机上唯一的可见方式。
 * tag 独立成一个，方便从满屏日志里捞出来。
 */
internal actual fun logUpdateWarning(message: String) {
    Log.w("JianyiUpdate", message)
}
```

`shared/src/iosMain/kotlin/com/jianyi/outfit/data/update/UpdateLog.ios.kt`：

```kotlin
package com.jianyi.outfit.data.update

import platform.Foundation.NSLog

/**
 * 用 `%@` 而不是把 message 直接当格式串：清单地址/正文片段里出现 `%` 时，
 * 裸串会被 NSLog 当格式说明符解析而打错。
 */
internal actual fun logUpdateWarning(message: String) {
    NSLog("JianyiUpdate: %@", message)
}
```

- [ ] **Step 6: 跑 lint 与测试**

Run: `py tools/kotlin_lint.py shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateHttp.kt shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateRepository.kt shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/UpdateLog.kt shared/src/commonMain/kotlin/com/jianyi/outfit/data/remote/WeatherApiClient.kt`
Expected: `0 problem(s)`（`android.util.Log` 与 `platform.Foundation` 都在 actual 侧，lint 只禁 commonMain）
Run: `./gradlew :shared:testDebugUnitTest --tests "*UpdateRepositoryTest*" --tests "*UpdateDecisionTest*"`
Expected: BUILD SUCCESSFUL，11 + 9 条 0 失败

- [ ] **Step 7: 提交**

```bash
git add shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/ shared/src/commonMain/kotlin/com/jianyi/outfit/data/remote/WeatherApiClient.kt shared/src/commonTest/kotlin/com/jianyi/outfit/data/update/UpdateRepositoryTest.kt
git commit -m "feat(update): 清单拉取与探包接缝，判定逻辑可在 JVM 里测"
```

---

## Task 4: 能力接口扩面（含 iOS 空实现与版本号注入）

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jianyi/outfit/data/AppDependencies.kt`
- Create: `shared/src/iosMain/kotlin/com/jianyi/outfit/platform/IosUpdateGateway.kt`
- Modify: `shared/src/iosMain/kotlin/com/jianyi/outfit/IosAppDependencies.kt`（**不在 platform/ 下**，实现 `AppDependencies` 的 iOS 容器就这一个文件，第 46 行）
- Create: `app/src/main/java/com/jianyi/outfit/update/AndroidUpdateGateway.kt`（本 Task 只建骨架，Task 8 填实现）
- Modify: `app/src/main/java/com/jianyi/outfit/di/AppContainer.kt`

**Interfaces:**
- Consumes: 无新依赖
- Produces: `data class AppVersion(val versionCode: Int, val versionName: String)`；`enum class InstallResult { Launched, PermissionMissing, Failed }`；`interface AppUpdateGateway { val supported: Boolean; fun hasInstallPermission(): Boolean; fun requestInstallPermission(); fun install(apkPath: String): InstallResult }`；`AppDependencies.appVersion: AppVersion`、`AppDependencies.updateGateway: AppUpdateGateway`

- [ ] **Step 1: 在 AppDependencies 里加接口与字段**

在 `interface AppDependencies` 内 `extremeAlerter` 之后加：

```kotlin
    /** 装机包版本（Android 由 BuildConfig 注入；commonMain 不许直接碰 BuildConfig） */
    val appVersion: AppVersion

    /** 应用内更新能力；iOS 恒 supported=false，UI 据此整块不显示 */
    val updateGateway: AppUpdateGateway
```

文件末尾追加：

```kotlin
/** 装机包版本。versionName 给人看，versionCode 给判定用。 */
data class AppVersion(val versionCode: Int, val versionName: String)

/** 拉起系统安装页的结果。分开三种是因为 UI 要说的话完全不同。 */
enum class InstallResult {
    /** 安装页已弹出 */
    Launched,

    /** 还没授予"安装未知应用"，调用方应接着 requestInstallPermission() */
    PermissionMissing,

    /** 弹不出来（FileProvider 路径不对、URI 被拒等） */
    Failed
}

/**
 * 应用内更新的平台能力。
 *
 * 刻意不含下载：下载是 Ktor + 落盘，跨端都能写，放在 shared；
 * 这里只收"只有系统能给的东西"——权限状态、权限引导、拉起安装页。
 *
 * iOS 侧 supported=false 而不是抛异常：这个功能在 iOS 上不是"坏了"，
 * 是规则不允许（App Store 应用不能自建更新通道）。
 */
interface AppUpdateGateway {
    val supported: Boolean

    /** 无需权限的平台上恒 true */
    fun hasInstallPermission(): Boolean

    /** 跳到系统设置里的"安装未知应用"页；已经在设置页里就不必自查 */
    fun requestInstallPermission()

    fun install(apkPath: String): InstallResult
}
```

- [ ] **Step 2: iOS 空实现**

`shared/src/iosMain/kotlin/com/jianyi/outfit/platform/IosUpdateGateway.kt`：

```kotlin
package com.jianyi.outfit.platform

import com.jianyi.outfit.data.AppUpdateGateway
import com.jianyi.outfit.data.InstallResult

/**
 * iOS 不提供应用内更新：App Store 规则不允许自建更新通道，
 * 引导去商店也只能用 `itms-apps://`，而这个包现在根本不在商店里。
 *
 * supported=false 会让 gate 层与设置页入口整块不渲染，
 * 所以这里的方法在 iOS 上永远不该被调用 —— 仍然给出确定返回值而不是抛异常，
 * 免得将来有人加一条调用路径就把 App 弄崩。
 */
class IosUpdateGateway : AppUpdateGateway {
    override val supported: Boolean = false
    override fun hasInstallPermission(): Boolean = false
    override fun requestInstallPermission() = Unit
    override fun install(apkPath: String): InstallResult = InstallResult.Failed
}
```

- [ ] **Step 3: Android 骨架**

`app/src/main/java/com/jianyi/outfit/update/AndroidUpdateGateway.kt`：

```kotlin
package com.jianyi.outfit.update

import android.content.Context
import com.jianyi.outfit.data.AppUpdateGateway
import com.jianyi.outfit.data.InstallResult

/**
 * Android 侧实现。本 Task 只放能编译过的最小骨架（supported 恒 true、
 * install 走 Failed），Task 8 补 FileProvider 与安装 Intent ——
 * 先把接口面钉住，让 shared 的 UI/VM 能并行推进。
 */
class AndroidUpdateGateway(private val context: Context) : AppUpdateGateway {

    override val supported: Boolean = true

    /**
     * 本包 minSdk=26，而"安装未知应用"这套权限正是 26(O) 引入的 ——
     * 所以 `SDK_INT < O` 那种兼容分支在这里恒为 false，是不可能执行到的死码，不写。
     */
    override fun hasInstallPermission(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    override fun requestInstallPermission() = Unit

    override fun install(apkPath: String): InstallResult = InstallResult.Failed
}
```

- [ ] **Step 4: AppContainer 注入**

修改 `app/src/main/java/com/jianyi/outfit/di/AppContainer.kt`，在 `extremeAlerter` 定义之后加（注意放在 `activities` 之后，属性按书写顺序初始化）：

```kotlin
    /** 装机包版本：BuildConfig 是 Android 产物，只能在这里读出去给 shared 用 */
    override val appVersion: AppVersion =
        AppVersion(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)

    /** 应用内更新的平台能力（下载在 shared，权限与安装页在这里） */
    override val updateGateway: AppUpdateGateway =
        AndroidUpdateGateway(context.applicationContext)
```

并在 import 区补 `com.jianyi.outfit.data.AppUpdateGateway`、`com.jianyi.outfit.data.AppVersion`、`com.jianyi.outfit.update.AndroidUpdateGateway`。

- [ ] **Step 5: iOS 依赖容器接上**

修改 `shared/src/iosMain/kotlin/com/jianyi/outfit/IosAppDependencies.kt`（**真实路径不在 `platform/` 下，也不在 `MainViewController.kt` 里**；该类在第 46 行 `class IosAppDependencies : AppDependencies`），加：

```kotlin
    override val appVersion: AppVersion = AppVersion(versionCode = 1, versionName = "0.0.0")
    override val updateGateway: AppUpdateGateway = IosUpdateGateway()
```

> `appVersion` 在 iOS 上是占位值：iOS 不显示任何更新入口（`supported=false`），
> 判定链根本不会走到。写 1/"0.0.0" 而不是去解析 Info.plist，是为了不在这条路上
> 引入新的平台代码 —— 若将来要做 TestFlight 引导，再换成读 CFBundleVersion。

- [ ] **Step 6: 跑全量测试确认没打断既有 78 条**

Run: `./gradlew :app:testDebugUnitTest :shared:testDebugUnitTest`
Expected: BUILD SUCCESSFUL；app 51 + shared 27+（新增用例）全过

- [ ] **Step 7: 提交**

```bash
git add shared/src/commonMain/kotlin/com/jianyi/outfit/data/AppDependencies.kt shared/src/iosMain/kotlin/com/jianyi/outfit/platform/IosUpdateGateway.kt shared/src/iosMain/kotlin/com/jianyi/outfit/IosAppDependencies.kt app/src/main/java/com/jianyi/outfit/update/ app/src/main/java/com/jianyi/outfit/di/AppContainer.kt
git commit -m "feat(update): AppUpdateGateway 能力接口，iOS 空实现，版本号由 app 注入"
```

---

## Task 5: UpdateViewModel 状态机

**Files:**
- Create: `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/update/UpdateViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/update/DownloadState.kt`
- Test: `shared/src/commonTest/kotlin/com/jianyi/outfit/ui/update/UpdateViewModelTest.kt`

> **修订（Task 5 实现后，Task 6/7 派发前必读）**
> 1. `DownloadState.kt` 没有单独建：`DownloadFailure`/`DownloadEvent`/`ApkInstaller` 三个声明都放在
>    `UpdateViewModel.kt` 里，**包名不变**（`com.jianyi.outfit.ui.update`），所以 T6/T7/T8 按包导入不受影响。
> 2. **下载接缝从 `Flow<Success|Failure>` 改成了 `Flow<Progress|Success|Failure>`。**
>    原因是实现者发现并要求裁决的一处"每一层各自都没错、拼起来是死的"：
>    接缝只回终态 ⇒ VM 里 `Downloading.done` 再也没有任何东西赋非 null 值 ⇒
>    24MB 的下载全程一根 0% 死条，而 `UpdateCopyTest`（算 `progressOf`/`sizeText`）全绿、
>    VM 的终态跳转用例也全绿。**Task 7 的 `ApkDownloader` 必须真的发 Progress**
>    （每 flush 1–2 次发一条即可，别每个字节发一条），Task 6 的进度条必须显示 `state.done`。
>    两侧各有用例钉住：`progress_event_moves_the_bar`、
>    `progress_with_unknown_total_keeps_the_bar_indeterminate`。
> 3. VM 多了第 6 个构造参数 `logWarning`（默认 `::logUpdateWarning`）：
>    `runCheck` 现在 try/catch 住 `checker.check`，抛出时按 Unreachable 处理、不拦人、留痕。
>    理由：`UpdateChecker` 是接口，容器那层是 `SupervisorJob`（不吞异常），
>    少这个 catch 就是"冷启动路径上崩 App"。用例 `a_throwing_checker_fails_open_instead_of_crashing`
>    同时是这个 catch 的红绿证明（摘掉 try/catch 它就红）。

**Interfaces:**
- Consumes: `UpdateRepository.check`（Task 3）、`AppUpdateGateway` / `AppVersion`（Task 4）
- Produces: `sealed interface UpdateUiState { Hidden; OptionalCard(manifest); Gate(manifest); Downloading(manifest, done: Long?, total: Long?, forced: Boolean); Failed(manifest, reason: DownloadFailure, forced: Boolean) }`；`enum class DownloadFailure { Network, ChecksumMismatch, Io, NoSpace }`；`sealed interface DownloadEvent { Progress(done: Long, total: Long?); Success(path: String); Failure(reason: DownloadFailure) }`；`interface ApkInstaller { fun download(manifest: UpdateManifest): Flow<DownloadEvent> }`；`class UpdateViewModel(appVersion: AppVersion, checker: UpdateChecker, installer: ApkInstaller, gateway: AppUpdateGateway, scope: CoroutineScope, logWarning: (String) -> Unit = ::logUpdateWarning) { val state: StateFlow<UpdateUiState>; val lastVerdict: StateFlow<UpdateVerdict?>; fun checkOnce(); fun forceCheck(); fun dismissOptional(); fun startDownload(); fun retry(); fun apkUrl(): String? }`；`object UnsupportedInstaller : ApkInstaller`；`fun newUpdateViewModel(...): UpdateViewModel`

- [ ] **Step 1: 定义下载接缝与进度类型（shared 侧，app 侧实现）**

`shared/src/commonMain/kotlin/com/jianyi/outfit/ui/update/DownloadState.kt`：

```kotlin
package com.jianyi.outfit.ui.update

import com.jianyi.outfit.data.update.UpdateManifest
import kotlinx.coroutines.flow.Flow

/** 下载进度。total 为 null 表示清单没声明 sizeBytes，UI 走不确定态进度条 */
data class DownloadProgress(val doneBytes: Long, val totalBytes: Long?)

enum class DownloadFailure {
    /** 网络中断/超时/HTTP 非 2xx —— 可重试 */
    Network,

    /** 下完的包哈希与清单声明不符：包被改过或传坏了，**不自动重试** */
    ChecksumMismatch,

    /** 写盘失败 */
    Io,

    /** 剩余空间不够装这个包 */
    NoSpace
}

sealed interface DownloadEvent {
    data class Success(val path: String) : DownloadEvent
    data class Failure(val reason: DownloadFailure) : DownloadEvent
}

/**
 * 落盘下载。放在 shared 侧声明成接口、由 app 实现，
 * 是为了让 ViewModel 的状态机能用假实现跑完全部跳转 ——
 * 真机上一装就装上了，状态机里"失败→重试"那几条分支反而永远走不到。
 *
 * 刻意**不含** launchInstall：拉起安装是平台能力，已经在
 * [com.jianyi.outfit.data.AppUpdateGateway.install] 里了，再开一个入口
 * 就会出现"两处都能装、只有一处管权限"。
 */
interface ApkInstaller {
    fun download(manifest: UpdateManifest): Flow<DownloadEvent>
}
```

- [ ] **Step 2: 写失败的测试**

`shared/src/commonTest/kotlin/com/jianyi/outfit/ui/update/UpdateViewModelTest.kt`：

```kotlin
package com.jianyi.outfit.ui.update

import com.jianyi.outfit.data.AppUpdateGateway
import com.jianyi.outfit.data.AppVersion
import com.jianyi.outfit.data.InstallResult
import com.jianyi.outfit.data.update.UpdateDecision
import com.jianyi.outfit.data.update.UpdateManifest
import com.jianyi.outfit.data.update.UpdateVerdict
import com.jianyi.outfit.data.update.UpdateChecker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val MANIFEST = UpdateManifest(
    versionCode = 9, versionName = "2.2.0", minSupportedCode = 9,
    apkUrl = "https://gitee.com/x.apk"
)

/** 假的是 UpdateChecker（接缝），不是 UpdateRepository —— 后者的 check 是 final，覆盖不了 */
private class FakeChecker(private val verdict: UpdateVerdict) : UpdateChecker {
    var calls = 0
    override suspend fun check(currentVersionCode: Int): UpdateDecision {
        calls++
        return UpdateDecision(verdict, if (verdict == UpdateVerdict.Unreachable) null else MANIFEST)
    }
}

private class FakeInstaller(
    private val outcome: DownloadEvent = DownloadEvent.Success("/tmp/x.apk")
) : ApkInstaller {
    var downloads = 0
    override fun download(manifest: UpdateManifest): Flow<DownloadEvent> {
        downloads++
        return flowOf(outcome)
    }
}

private class FakeGateway(
    override val supported: Boolean = true,
    private val installResult: InstallResult = InstallResult.Launched
) : AppUpdateGateway {
    var permissionRequests = 0
    var installedPath: String? = null
    override fun hasInstallPermission() = installResult != InstallResult.PermissionMissing
    override fun requestInstallPermission() { permissionRequests++ }
    override fun install(apkPath: String): InstallResult { installedPath = apkPath; return installResult }
}

private suspend fun checkedVm(
    scope: kotlinx.coroutines.test.TestScope,
    verdict: UpdateVerdict,
    installer: ApkInstaller = FakeInstaller(),
    supported: Boolean = true,
    gateway: AppUpdateGateway = FakeGateway(supported)
): UpdateViewModel = UpdateViewModel(
    appVersion = AppVersion(8, "2.1.2"),
    checker = FakeChecker(verdict),
    installer = installer,
    gateway = gateway,
    scope = scope
).also { it.checkOnce(); scope.runCurrent() }

class UpdateViewModelTest {

    @Test fun hidden_when_nothing_to_show() = runTest {
        assertIs<UpdateUiState.Hidden>(checkedVm(this, UpdateVerdict.UpToDate).state.value)
    }

    @Test fun forced_becomes_gate() = runTest {
        val s = checkedVm(this, UpdateVerdict.Forced).state.value
        assertIs<UpdateUiState.Gate>(s)
        assertEquals(9, s.manifest.versionCode)
    }

    @Test fun optional_becomes_dismissible_card() = runTest {
        val vm = checkedVm(this, UpdateVerdict.Optional)
        assertIs<UpdateUiState.OptionalCard>(vm.state.value)
        vm.dismissOptional()
        assertIs<UpdateUiState.Hidden>(vm.state.value)
    }

    /**
     * 门禁下"跳过"必须无效。这条测试存在的理由：dismiss 逻辑一旦写错，
     * 用户就能绕过破坏性版本继续用旧版 —— 强更直接失效，而且不会有任何报错。
     */
    @Test fun gate_cannot_be_dismissed() = runTest {
        val vm = checkedVm(this, UpdateVerdict.Forced)
        vm.dismissOptional()
        assertIs<UpdateUiState.Gate>(vm.state.value)
    }

    /** 平台不支持 ⇒ 即使判定是 Forced 也不显示任何东西（iOS 走这条） */
    @Test fun unsupported_platform_stays_hidden() = runTest {
        val vm = checkedVm(this, UpdateVerdict.Forced, supported = false)
        assertIs<UpdateUiState.Hidden>(vm.state.value)
    }

    @Test fun start_download_installs_on_success() = runTest {
        val installer = FakeInstaller()
        val gateway = FakeGateway()
        val vm = checkedVm(this, UpdateVerdict.Forced, installer, gateway = gateway)
        vm.startDownload(); runCurrent()
        assertEquals(1, installer.downloads)
        assertEquals("/tmp/x.apk", gateway.installedPath)
    }

    /** 装成功后状态必须回到 Hidden：门禁卡片继续挂着就是"装完了还拦着" */
    @Test fun launched_install_hides_the_gate() = runTest {
        val vm = checkedVm(this, UpdateVerdict.Forced)
        vm.startDownload(); runCurrent()
        assertIs<UpdateUiState.Hidden>(vm.state.value)
    }

    /**
     * 权限没给：仍然算门禁，不能退成可跳过卡片。
     *
     * 修订（Task 3 之后）：原计划这里断言 `OptionalCard`，那等于
     * **"只要我不授权，强更就自动降级成可跳过"** —— 门禁被它要防的那个动作（拒绝授权）绕过。
     * 授权回来后卡片还在，是因为门禁一直挂着，不是因为退了一步。
     */
    @Test fun permission_missing_keeps_the_gate_and_opens_settings() = runTest {
        val gateway = FakeGateway(installResult = InstallResult.PermissionMissing)
        val vm = checkedVm(this, UpdateVerdict.Forced, gateway = gateway)
        vm.startDownload(); runCurrent()
        assertIs<UpdateUiState.Gate>(vm.state.value)
        assertEquals(1, gateway.permissionRequests)
    }

    /** 非强制那一档才可以退成可跳过卡片：它本来就可跳过，退回去能让用户一键继续 */
    @Test fun permission_missing_downgrades_only_a_non_forced_card() = runTest {
        val gateway = FakeGateway(installResult = InstallResult.PermissionMissing)
        val vm = checkedVm(this, UpdateVerdict.Optional, gateway = gateway)
        vm.startDownload(); runCurrent()
        assertIs<UpdateUiState.OptionalCard>(vm.state.value)
        assertEquals(1, gateway.permissionRequests)
    }

    @Test fun download_failure_exposes_reason_and_keeps_gate_context() = runTest {
        val vm = checkedVm(
            this, UpdateVerdict.Forced,
            FakeInstaller(DownloadEvent.Failure(DownloadFailure.ChecksumMismatch))
        )
        vm.startDownload(); runCurrent()
        val s = vm.state.value
        assertIs<UpdateUiState.Failed>(s)
        assertEquals(DownloadFailure.ChecksumMismatch, s.reason)
        assertEquals(true, s.forced, "门禁下的失败必须记得自己是门禁")
    }

    @Test fun retry_reissues_download() = runTest {
        val installer = FakeInstaller(DownloadEvent.Failure(DownloadFailure.Network))
        val vm = checkedVm(this, UpdateVerdict.Forced, installer)
        vm.startDownload(); runCurrent()
        vm.retry(); runCurrent()
        assertEquals(2, installer.downloads)
    }

    /** 冷启动只查一次；手动检查走 forceCheck() 明确复位 */
    @Test fun check_once_is_idempotent_until_forced() = runTest {
        val checker = FakeChecker(UpdateVerdict.UpToDate)
        val vm = UpdateViewModel(AppVersion(8, "2.1.2"), checker, FakeInstaller(), FakeGateway(), this)
        vm.checkOnce(); vm.checkOnce(); runCurrent()
        assertEquals(1, checker.calls)
        vm.forceCheck(); runCurrent()
        assertEquals(2, checker.calls)
    }

    /**
     * 关掉可跳过卡片后，同一冷启动内重复 checkOnce 不该再弹。
     *
     * 修订（Task 3 之后）：原写法是 `vm.forceCheck()` 然后断言 Hidden —— 那是自相矛盾，
     * `forceCheck()` 按实现就会复位 `optionalDismissed`，卡片必然重新出现，这条用例永远红。
     * 拆成两条把两个意图分别钉住（见下一条）。
     */
    @Test fun dismissed_optional_stays_dismissed_within_the_same_launch() = runTest {
        val vm = checkedVm(this, UpdateVerdict.Optional)
        vm.dismissOptional()
        vm.checkOnce(); runCurrent()
        assertIs<UpdateUiState.Hidden>(vm.state.value)
    }

    /**
     * 手动「检查更新」是用户的第二个意图，它必须能重新弹出刚被关掉的卡片。
     *
     * 不复位 `optionalDismissed` 才是真正的缺陷形状：用户点掉卡片之后，
     * 设置页里那个按钮按多少次都毫无反应，而且看起来"已经检查过了"。
     */
    @Test fun force_check_reshows_a_dismissed_optional() = runTest {
        val vm = checkedVm(this, UpdateVerdict.Optional)
        vm.dismissOptional()
        assertIs<UpdateUiState.Hidden>(vm.state.value)
        vm.forceCheck(); runCurrent()
        assertIs<UpdateUiState.OptionalCard>(vm.state.value)
    }
}
```

- [ ] **Step 3: 跑测试确认失败**

Run: `./gradlew :shared:testDebugUnitTest --tests "*UpdateViewModelTest*"`
Expected: 编译失败（Unresolved reference: UpdateViewModel）

- [ ] **Step 4: 写实现**

`shared/src/commonMain/kotlin/com/jianyi/outfit/ui/update/UpdateViewModel.kt`：

```kotlin
package com.jianyi.outfit.ui.update

import com.jianyi.outfit.data.AppUpdateGateway
import com.jianyi.outfit.data.AppVersion
import com.jianyi.outfit.data.update.UpdateChecker
import com.jianyi.outfit.data.update.UpdateManifest
import com.jianyi.outfit.data.update.UpdateVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object Hidden : UpdateUiState
    data class OptionalCard(val manifest: UpdateManifest) : UpdateUiState
    data class Gate(val manifest: UpdateManifest) : UpdateUiState

    /**
     * forced 必须随状态一起带：门禁下点下载之后卡片会变成 Downloading/Failed，
     * 这时"能不能跳过/能不能按返回键"必须还是门禁那一套。
     * 少了这个字段，Task 8 的返回键拦截就会在下载中放行 —— 那等于门禁可绕过。
     */
    data class Downloading(val manifest: UpdateManifest, val done: Long?, val total: Long?, val forced: Boolean) : UpdateUiState
    data class Failed(val manifest: UpdateManifest, val reason: DownloadFailure, val forced: Boolean) : UpdateUiState
}

/**
 * 更新流程的状态机。
 *
 * 构造参数全部注入（不是 deps），这样十二条状态跳转能在普通 JVM 里用假实现跑完。
 * 生命周期是**容器级单例**（挂在 AppContainer 上）而不是各页面 new 一个：
 * 门禁必须跨页面存在，且"这次冷启动已经查过/已经关过"这件事只有一份真相。
 */
class UpdateViewModel(
    private val appVersion: AppVersion,
    private val checker: UpdateChecker,
    private val installer: ApkInstaller,
    private val gateway: AppUpdateGateway,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Hidden)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /**
     * 最近一次判定的结论。设置页副标题要读它 —— UI 状态本身不够：
     * Hidden 既可能是"查过、没更新"，也可能是"没查过/查失败"，
     * 而这三句话在设置页上是三条不同的副标题（见 Task 9）。
     *
     * 用 StateFlow 而不是普通 var：副标题要在检查完成后**自己变**，
     * 普通 var 不触发重组，就会表现为"检查完了那行字还是旧的"。
     */
    private val _lastVerdict = MutableStateFlow<UpdateVerdict?>(null)
    val lastVerdict: StateFlow<UpdateVerdict?> = _lastVerdict.asStateFlow()

    private var checkedThisLaunch = false
    private var optionalDismissed = false
    private var lastManifest: UpdateManifest? = null
    private var forcedThisLaunch = false

    /** 冷启动入口：重复调用是安全的（幂等） */
    fun checkOnce() {
        if (checkedThisLaunch) return
        checkedThisLaunch = true
        runCheck()
    }

    /** 设置页「检查更新」用：复位"这次已查过/已关过"再走同一条路 */
    fun forceCheck() {
        checkedThisLaunch = false
        optionalDismissed = false
        checkOnce()
    }

    private fun runCheck() {
        scope.launch {
            val decision = checker.check(appVersion.versionCode)
            lastManifest = decision.manifest
            _lastVerdict.value = decision.verdict
            if (!gateway.supported) return@launch
            forcedThisLaunch = decision.verdict == UpdateVerdict.Forced
            _state.value = when (decision.verdict) {
                UpdateVerdict.Unreachable, UpdateVerdict.UpToDate -> UpdateUiState.Hidden
                UpdateVerdict.Forced -> UpdateUiState.Gate(decision.manifest!!)
                UpdateVerdict.Optional ->
                    if (optionalDismissed) UpdateUiState.Hidden
                    else UpdateUiState.OptionalCard(decision.manifest!!)
            }
        }
    }

    /** 只有 OptionalCard 能被关掉；Gate 调这个是空操作（见测试） */
    fun dismissOptional() {
        if (_state.value is UpdateUiState.OptionalCard) {
            optionalDismissed = true
            _state.value = UpdateUiState.Hidden
        }
    }

    fun startDownload() {
        val manifest = _state.value.manifestOrNull() ?: lastManifest ?: return
        _state.value = UpdateUiState.Downloading(manifest, done = null, total = manifest.sizeBytes, forced = forcedThisLaunch)
        scope.launch {
            installer.download(manifest).collect { outcome ->
                when (outcome) {
                    is DownloadEvent.Success -> onDownloaded(manifest, outcome.path)
                    is DownloadEvent.Failure ->
                        _state.value = UpdateUiState.Failed(manifest, outcome.reason, forcedThisLaunch)
                }
            }
        }
    }

    private fun onDownloaded(manifest: UpdateManifest, path: String) {
        when (gateway.install(path)) {
            // 装完了必须回到 Hidden：门禁卡片继续挂着就是"装完了还拦着"
            com.jianyi.outfit.data.InstallResult.Launched -> _state.value = UpdateUiState.Hidden
            // 用户没给"安装未知应用"权限：跳系统设置页。
            // **门禁不许退成可跳过卡片** —— 那等于"只要拒绝授权，强更就自动变成可跳过"，
            // 门禁被它要防的动作本身绕过。非强制那一档才退回去，让他回来还能一键继续。
            com.jianyi.outfit.data.InstallResult.PermissionMissing -> {
                _state.value = if (forcedThisLaunch) UpdateUiState.Gate(manifest)
                               else UpdateUiState.OptionalCard(manifest)
                gateway.requestInstallPermission()
            }
            com.jianyi.outfit.data.InstallResult.Failed ->
                _state.value = UpdateUiState.Failed(manifest, DownloadFailure.Io, forcedThisLaunch)
        }
    }

    fun retry() = startDownload()

    /** 逃生口：门禁下一直下不下来时，UI 用这个文本给"复制下载链接" */
    fun apkUrl(): String? = (_state.value.manifestOrNull() ?: lastManifest)?.apkUrl

    private fun UpdateUiState.manifestOrNull(): UpdateManifest? = when (this) {
        is UpdateUiState.OptionalCard -> manifest
        is UpdateUiState.Gate -> manifest
        is UpdateUiState.Downloading -> manifest
        is UpdateUiState.Failed -> manifest
        UpdateUiState.Hidden -> null
    }
}
```

> VM 只暴露 `apkUrl()`，不碰剪贴板：剪贴板是平台 API，VM 在 commonMain 里碰它就得再开一条 expect/actual，而调用点只有一个 Composable。

- [ ] **Step 5: 跑测试确认通过**

Run: `./gradlew :shared:testDebugUnitTest --tests "*UpdateViewModelTest*"`
Expected: BUILD SUCCESSFUL，9 条 0 失败

- [ ] **Step 6: 提交**

```bash
git add shared/src/commonMain/kotlin/com/jianyi/outfit/ui/update/ shared/src/commonTest/kotlin/com/jianyi/outfit/ui/update/
git commit -m "feat(update): 更新状态机，门禁不可跳过与\"包下不到不拦\"两条都用例钉住"
```

---

## Task 6: 门禁层 UI 与挂载

**Files:**
- Create: `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/update/UpdateGateLayer.kt`
- Create: `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/update/UpdateCopy.kt`
- Create: `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/update/UpdateWiring.kt`（两端共享的构造点，防参数顺序漂移）
- Modify: `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/root/RootScreen.kt:55-61`
- Modify: `shared/src/commonMain/kotlin/com/jianyi/outfit/data/AppDependencies.kt`（加 `val updateViewModel: UpdateViewModel`）
- Modify: `app/src/main/java/com/jianyi/outfit/di/AppContainer.kt`（Android 侧构造 VM）
- Modify: `shared/src/iosMain/kotlin/com/jianyi/outfit/IosAppDependencies.kt`（iOS 侧构造 VM；**路径不在 `platform/` 下**）
- Test: `shared/src/commonTest/kotlin/com/jianyi/outfit/ui/update/UpdateCopyTest.kt`

> **这三处 Modify 原本没列进 Files，也没进 Step 的 `git add`**（Task 4 期间实测到同一形状的事故）：
> 症状是本地全绿、提交里少文件，CI 才报 missing override / 未构造 VM；
> 而 iOS 那一份在本机（Windows）连编都编不了，只能靠远端。**派发时按这份清单，别照旧文本。**

**Interfaces:**
- Consumes: `UpdateViewModel` / `UpdateUiState`（Task 5）、`GlassSurface` / `GlassShapes` / `GlassEmphasis`（既有玻璃层）
- Produces: `@Composable fun UpdateGateLayer(vm: UpdateViewModel)`；`object UpdateCopy { fun sizeText(done: Long?, total: Long?): String; fun progressOf(done: Long, total: Long?): Float; fun failureText(reason: DownloadFailure): String; fun versionLine(appVersion: AppVersion, manifest: UpdateManifest): String }`

- [ ] **Step 1: 先写文案/格式的失败测试**

`shared/src/commonTest/kotlin/com/jianyi/outfit/ui/update/UpdateCopyTest.kt`：

```kotlin
package com.jianyi.outfit.ui.update

import com.jianyi.outfit.data.AppVersion
import com.jianyi.outfit.data.update.UpdateManifest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 进度与容量文案单独测，是因为它们是**唯一会在门禁上每帧变**的字符串，
 * 而且"最坏负载"就出在这里：24MB 的包、无 sizeBytes 的清单、
 * 以及中文失败原因塞进窄卡片后被省略号吃掉 —— 这个坑本项目今天刚踩过一次
 * （首页降级条的 CTA 被 maxLines=1 裁掉，a11y 树里文本完整、屏幕上看不见）。
 */
class UpdateCopyTest {

    private val m = UpdateManifest(9, "2.2.0", 9, "https://gitee.com/x.apk")

    @Test fun progress_ratio_uses_declared_total() {
        assertEquals(0.5f, UpdateCopy.progressOf(12_000_000, 24_000_000))
    }

    /** 清单没声明 sizeBytes 时不能算出 NaN 或 0，进度条要走不确定态 */
    @Test fun progress_is_null_when_total_unknown() {
        assertEquals(-1f, UpdateCopy.progressOf(5_000_000, null))
    }

    /** 服务端报的 done 超过 total（重定向重试后偶发）不能画出 130% 的进度条 */
    @Test fun progress_is_clamped() {
        assertEquals(1f, UpdateCopy.progressOf(30_000_000, 24_000_000))
    }

    @Test fun size_text_covers_mb_and_unknown() {
        assertEquals("11.4 MB / 23.0 MB", UpdateCopy.sizeText(11_953_920, 24_115_200))
        assertEquals("已下载 11.4 MB", UpdateCopy.sizeText(11_953_920, null))
        assertEquals("", UpdateCopy.sizeText(0, null))
    }

    @Test fun failure_text_is_actionable_for_every_reason() {
        for (reason in DownloadFailure.entries) {
            val text = UpdateCopy.failureText(reason)
            assertEquals(true, text.isNotBlank(), "$reason 必须给用户一句能照着做的话")
            assertEquals(true, text.length <= 40, "$reason 的文案超过了窄卡片一行能放下的量")
        }
    }

    /** 校验失败的话必须和"网络断了"完全不同 —— 前者重试没用 */
    @Test fun checksum_failure_does_not_suggest_retry() {
        assertEquals(false, UpdateCopy.failureText(DownloadFailure.ChecksumMismatch).contains("重试"))
    }

    @Test fun version_line_shows_both_versions() {
        assertEquals(
            "当前 2.1.2 → 最新 2.2.0",
            UpdateCopy.versionLine(AppVersion(8, "2.1.2"), m)
        )
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :shared:testDebugUnitTest --tests "*UpdateCopyTest*"`
Expected: 编译失败（Unresolved reference: UpdateCopy）

- [ ] **Step 3: 写文案对象**

`shared/src/commonMain/kotlin/com/jianyi/outfit/ui/update/UpdateCopy.kt`：

```kotlin
package com.jianyi.outfit.ui.update

import com.jianyi.outfit.data.AppVersion
import com.jianyi.outfit.data.update.UpdateManifest

/** 进度与失败文案。单独成文件是因为它有一堆"最坏负载"要测。 */
object UpdateCopy {

    private const val MB = 1024.0 * 1024.0

    /** -1f = 不确定态（清单没给 sizeBytes） */
    fun progressOf(done: Long, total: Long?): Float {
        if (total == null || total <= 0L) return -1f
        return (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    fun sizeText(done: Long, total: Long?): String {
        if (total == null) return if (done <= 0L) "" else "已下载 ${mb(done)}"
        return "${mb(done)} / ${mb(total)}"
    }

    private fun mb(bytes: Long): String {
        val value = bytes / MB
        // 不用 String.format（commonMain 里没有），手动留一位小数并补零
        val tenths = (value * 10 + 0.5).toLong()
        return "${tenths / 10}.${tenths % 10} MB"
    }

    /**
     * 每条都要"能照着做"。校验失败那条刻意不提重试：
     * 包本身是坏的，重试只会再下一次坏的，用户以为程序卡住。
     */
    fun failureText(reason: DownloadFailure): String = when (reason) {
        DownloadFailure.Network -> "下载中断，请检查网络后重试"
        DownloadFailure.ChecksumMismatch -> "安装包校验不通过，请稍后再试或联系作者"
        DownloadFailure.Io -> "写入失败，请清理手机存储后重试"
        DownloadFailure.NoSpace -> "存储空间不足，需至少 60 MB"
    }

    fun versionLine(appVersion: AppVersion, manifest: UpdateManifest): String =
        "当前 ${appVersion.versionName} → 最新 ${manifest.versionName}"
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :shared:testDebugUnitTest --tests "*UpdateCopyTest*"`
Expected: BUILD SUCCESSFUL，7 条 0 失败

- [ ] **Step 5: 写门禁层**

`shared/src/commonMain/kotlin/com/jianyi/outfit/ui/update/UpdateGateLayer.kt`：

```kotlin
package com.jianyi.outfit.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jianyi.outfit.data.LocalAppDependencies
import com.jianyi.outfit.ui.glass.GlassEmphasis
import com.jianyi.outfit.ui.glass.GlassShapes
import com.jianyi.outfit.ui.glass.GlassSurface

/**
 * 更新门禁 / 提示层。挂在 RootScreen 的 Box 里、content 之后 ⇒ 永远盖在所有页面之上。
 *
 * 三条硬规矩（都是这个仓库今天踩过或推演出来的）：
 * 1. Gate 不提供任何关闭入口，返回键由宿主 Activity 侧拦（见 Task 8）；
 * 2. 文案一律 maxLines 不限、软换行 —— 窄屏 + 长失败原因时宁可折行，
 *    也不要 maxLines=1 把"点此重试"这种动作提示裁成省略号；
 * 3. 进度未知时不画假进度条（0% 卡死比没有进度条更让人以为坏了）。
 */
@Composable
fun UpdateGateLayer(vm: UpdateViewModel) {
    val state by vm.state.collectAsState()
    val deps = LocalAppDependencies.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()

    /**
     * 冷启动检查由这里发起，而不是容器构造时或 Application 里。
     *
     * 修订（Task 3 之后）：全计划原本没有任何一处调用 `checkOnce()` ——
     * VM 造好、接缝都接上、测试全绿，真机上门禁**永远不出现**，
     * 因为唯一会触发它的东西是设置页那个手动按钮。挂在这一层是因为这一层
     * 恰好只在 RootScreen 挂载时存在一次，天然就是"每次冷启动一次"的作用域；
     * `checkOnce` 自身幂等（`checkedThisLaunch`），重组不会重复打网络。
     */
    LaunchedEffect(Unit) { vm.checkOnce() }

    val manifest = when (state) {
        is UpdateUiState.OptionalCard -> (state as UpdateUiState.OptionalCard).manifest
        is UpdateUiState.Gate -> (state as UpdateUiState.Gate).manifest
        is UpdateUiState.Downloading -> (state as UpdateUiState.Downloading).manifest
        is UpdateUiState.Failed -> (state as UpdateUiState.Failed).manifest
        else -> null
    } ?: return

    val forced = when (val s = state) {
        is UpdateUiState.Gate -> true
        is UpdateUiState.Downloading -> s.forced
        is UpdateUiState.Failed -> s.forced
        else -> false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (forced) MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)
                        else androidx.compose.ui.graphics.Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp),
            shape = GlassShapes.card,
            emphasis = GlassEmphasis.THIN,
            dark = dark,
            contentPadding = PaddingValues(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (forced) "需要更新到新版本" else "发现新版本",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = UpdateCopy.versionLine(deps.appVersion, manifest),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val notes = manifest.notes
                if (!notes.isNullOrBlank()) {
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when (val s = state) {
                    is UpdateUiState.Downloading -> DownloadingBody(s.done, s.total)
                    is UpdateUiState.Failed -> Text(
                        text = UpdateCopy.failureText(s.reason),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> if (forced) Text(
                        text = "不更新将无法继续使用，以免旧版本读到错误数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start
                    )
                }

                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassPill(
                        label = if (state is UpdateUiState.Failed) "重试" else "立即更新",
                        onClick = { vm.startDownload() }
                    )
                    if (!forced) {
                        GlassPill(label = "以后再说", onClick = { vm.dismissOptional() })
                    } else if (state is UpdateUiState.Failed) {
                        // 门禁 + 下载失败：给最后一道出口，否则用户真的没有第二条路
                        GlassPill(
                            label = "复制下载链接",
                            onClick = {
                                vm.apkUrl()?.let { url ->
                                    // CMP 1.8 的 Clipboard.setText 是 suspend，必须起协程；
                                    // 直接调会编译失败，别改成 runBlocking
                                    uiScope.launch { clipboard.setText(AnnotatedString(url)) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadingBody(done: Long?, total: Long?) {
    val ratio = done?.let { UpdateCopy.progressOf(it, total) } ?: -1f
    if (ratio >= 0f) {
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    val line = UpdateCopy.sizeText(done ?: 0L, total)
    if (line.isNotEmpty()) {
        Text(
            text = line,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

> **本文件用到的三个尚未存在的符号，在 Step 5b 里一次补齐**（`GlassButton`、`uiScope`、剪贴板相关 import）。
> 先写出来是为了让读代码的人一眼看到"这里还欠什么"，不要跳过 Step 5b 直接编译。

- [ ] **Step 5b: 补齐玻璃按钮、协程作用域与剪贴板**

- [ ] **Step 5b: 补齐玻璃按钮、协程作用域与剪贴板**

> **本步下方那段 `private fun GlassButton` 代码已作废（Task 3 之后核实）**：仓库里已有
> `GlassPill`，Step 5 的三处按钮已改成直接调它。本 Task 只需要做两件事 ——
> ①`UpdateGateLayer` 开头加 `val uiScope = rememberCoroutineScope()`；
> ②按下面的 import 清单补齐（**注意 `LocalClipboard` 在 CMP 1.8 已废弃，用
> `androidx.compose.ui.platform.LocalClipboardManager`，它的 `setText(AnnotatedString)` 是 suspend**）。
> 不要照抄下面这段玻璃按钮，也不要为它新增 `GlassShapes.pill`。

在同文件底部加玻璃按钮（**已核实：仓库里没有 `GlassButton` / `GlassTextButton`，但有现成的
`GlassPill(label: String, modifier, dark, contentColor, shape = GlassShapes.chip, onClick: (() -> Unit)?)`
—— 见 `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/glass/Glass.kt:517`，它就是这颗胶囊按钮，
直接复用，本 Task 不许再写第二个私有 `GlassButton`，也不许新增形状常量：
`GlassShapes` 只有 `card/inner/chip/bar/sheet/circle`，**没有 `pill`**）：

```kotlin
/**
 * 门禁卡片上的按钮。用 GlassSurface 包一层而不是 M3 的 Button：
 * 整页都是玻璃语言，一个实心 M3 按钮放上去就是"不透的白板子"，
 * 那是这个项目的 UI 红线（见 memory: 用户对液态玻璃的判定标准）。
 */
@Composable
private fun GlassButton(text: String, onClick: () -> Unit) {
    val dark = isSystemInDarkTheme()
    GlassSurface(
        shape = GlassShapes.pill,
        emphasis = GlassEmphasis.ULTRA_THIN,
        dark = dark,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
```

`UpdateGateLayer` 函数开头补一行（剪贴板要起协程）：

```kotlin
    val uiScope = androidx.compose.runtime.rememberCoroutineScope()
```

需要新增的 import：

```kotlin
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.launch
```

并把 Step 5 里内联写的 `androidx.compose.foundation.isSystemInDarkTheme()` 与
`androidx.compose.ui.platform.LocalClipboard.current` 换成短名（有了 import 之后）。

> 若 `GlassShapes.pill` 不存在，先 `grep -n "val pill\|val card" shared/src/commonMain/kotlin/com/jianyi/outfit/ui/glass/*.kt` 看现有形状叫什么，用现成的那个 —— **不要**为了这个按钮新增形状常量。

- [ ] **Step 6: 挂进 RootScreen**

把 `RootScreen.kt` 的 `CompositionLocalProvider(...) { content() }` 改成：

```kotlin
            CompositionLocalProvider(
                LocalGlassHost provides glassHost,
                LocalSceneryController provides controller,
                LocalAppDependencies provides deps
            ) {
                content()
                // 更新门禁：必须在 content 之后，才能盖住所有页面与弹层
                UpdateGateLayer(vm = deps.updateViewModel)
            }
```

并在 `AppDependencies` 里加 `val updateViewModel: UpdateViewModel`，然后**在这一 Task 内**把两端容器接上。

先在 commonMain 加一个共享构造点（否则两端各 new 一次，参数顺序会漂移）：

`shared/src/commonMain/kotlin/com/jianyi/outfit/ui/update/UpdateWiring.kt`：

```kotlin
package com.jianyi.outfit.ui.update

import com.jianyi.outfit.data.AppUpdateGateway
import com.jianyi.outfit.data.AppVersion
import com.jianyi.outfit.data.update.UpdateChecker
import com.jianyi.outfit.data.update.UpdateManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * iOS 用不到下载（gateway.supported=false，门禁永远不显示），
 * 但构造 VM 必须给一个 ApkInstaller —— 给一个明确失败的实现，
 * 而不是可空参数：可空会让"忘了传"变成运行时 NPE，这个则直接说出原因。
 */
object UnsupportedInstaller : ApkInstaller {
    override fun download(manifest: UpdateManifest): Flow<DownloadEvent> =
        flowOf(DownloadEvent.Failure(DownloadFailure.Io))
}

fun newUpdateViewModel(
    appVersion: AppVersion,
    checker: UpdateChecker,
    installer: ApkInstaller,
    gateway: AppUpdateGateway,
    scope: CoroutineScope
) = UpdateViewModel(appVersion, checker, installer, gateway, scope)
```

`app/src/main/java/com/jianyi/outfit/di/AppContainer.kt`（Task 4 已在此加过 `appVersion` / `updateGateway`）：

```kotlin
    /** 更新流程自己的作用域：门禁要活到 Activity 销毁之后重开，不跟任何页面绑 */
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val updateChecker: UpdateChecker = UpdateRepository(KtorUpdateHttp())

    override val updateViewModel: UpdateViewModel = newUpdateViewModel(
        appVersion = appVersion,
        checker = updateChecker,
        installer = ApkDownloader(context.applicationContext),
        gateway = updateGateway,
        scope = updateScope
    )
```

`shared/src/iosMain/.../IosAppDependencies`：

```kotlin
    override val updateViewModel: UpdateViewModel = newUpdateViewModel(
        appVersion = appVersion,
        checker = UpdateRepository(KtorUpdateHttp()),
        installer = UnsupportedInstaller,
        gateway = updateGateway,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    )
```

> `AppDependencies` 同时加 `val updateChecker: UpdateChecker`：设置页的「检查更新」要读它上一次的结果来显示副标题（Task 9），而 VM 只暴露 UI 状态、不暴露 verdict 历史。

- [ ] **Step 7: 跑 lint + 全量测试 + 构建**

Run: `py tools/kotlin_lint.py shared/src/commonMain/kotlin/com/jianyi/outfit/ui/update/UpdateGateLayer.kt shared/src/commonMain/kotlin/com/jianyi/outfit/ui/update/UpdateCopy.kt shared/src/commonMain/kotlin/com/jianyi/outfit/ui/root/RootScreen.kt`
Run: `./gradlew :app:testDebugUnitTest :shared:testDebugUnitTest :app:assembleDebug`
Expected: 全绿，APK 产出

- [ ] **Step 8: 提交**

```bash
git add shared/src/commonMain/kotlin/com/jianyi/outfit/ui/update/ shared/src/commonMain/kotlin/com/jianyi/outfit/ui/root/RootScreen.kt shared/src/commonMain/kotlin/com/jianyi/outfit/data/AppDependencies.kt app/src/main/java/com/jianyi/outfit/di/AppContainer.kt shared/src/iosMain/kotlin/com/jianyi/outfit/IosAppDependencies.kt
git commit -m "feat(update): 门禁层挂在 RootScreen 之上，文案与进度格式有测试"
```

---

## Task 7: APK 下载器（Android）

**Files:**
- Create: `shared/src/androidMain/kotlin/com/jianyi/outfit/data/update/ApkDownloader.kt`
- Test: `shared/src/androidUnitTest/kotlin/com/jianyi/outfit/data/update/ApkDownloaderTest.kt`

**Interfaces:**
- Consumes: `UpdateManifest`、`ApkInstaller` / `DownloadEvent` / `DownloadFailure`（Task 5）、`httpClientEngine()`（Task 3 的 `data/remote/WeatherApiClient.kt`）

> **修订（Task 3 修复轮）**：引擎工厂的真名是 **`httpClientEngine()`**（`internal expect fun`）。
> 计划里一度写作 `newHttpClientEngine()`，那是我为一个**不存在的限制**加的包装 ——
> `internal` 是**模块级**可见性，不是包级（Kotlin 没有"包内可见"这一档），
> 所以 `data.update` 本来就能直接调 `data.remote` 的 internal 声明。别名已删，别再引用它。
- Produces: `class ApkDownloader(context: Context) : ApkInstaller`；`internal fun sha256Of(file: File): String?`；`internal fun shouldRefuseDownload(total: Long?, free: Long): Boolean`；`fun apkFileNameFor(versionName: String): String`

> **实测事实（2026-09-29，第三方 Gitee Release 附件）**：附件地址带 `Range: bytes=0-99` 时
> `foruda.gitee.com` **忽略 Range**，直接开始整文件传输（实测 30 秒推了 26MB / 58MB 才掐断）
> ⇒ **APK 下载没有续传**。因此：不许写"断点续传/从中间偏移继续"的路径，失败就整个重来；
> 进度回调要允许"到 90% 失败后从 0 再来"这种观感（文案不许出现"已恢复上次进度"）；
> `shouldRefuseDownload` 那种"空间不够就别开始"的前置检查因此更重要 —— 下到一半才失败等于白烧用户流量。
> 好消息是同一批实测确认了 HEAD 对附件地址返 **200**（跟完三跳），`headOk` 的机制成立。

> **为什么放 `shared/androidMain` 而不是 app 模块**：shared 里 Ktor 是 `implementation()`（见 `shared/build.gradle.kts`），app 的编译类路径上根本没有 `io.ktor.*` —— 放 app 就得给 app 补两条 ktor 依赖，而下载器要用的引擎工厂 `httpClientEngine()` 本来就是 shared 模块内的 internal 声明。androidMain 里两者都现成，且 `:shared:testDebugUnitTest` 会自动跑 `androidUnitTest`。

- [ ] **Step 1: 写失败的测试（只测纯部分：哈希、空间判定、文件名）**

`shared/src/androidUnitTest/kotlin/com/jianyi/outfit/data/update/ApkDownloaderTest.kt`：

> **派发前必读的两条校正（Task 3 修复轮核实）**：
> 1. **用 `kotlin.test`，不要用 `org.junit.*`**。`shared` 的测试依赖只声明了
>    `kotlin("test")` 与 `kotlinx.coroutines.test`，junit4 是否能靠传递依赖解析出来**没有验证过**；
>    而本仓库所有既有 shared 测试都是 kotlin.test。顺手也避开断言参数顺序那个坑
>    （kotlin.test 是 `assertEquals(expected, actual)`，`assertTrue(actual, message)`）。
> 2. **`shared/src/androidUnitTest/` 这个目录现在不存在**，是新建的。
>    如果 KGP 不认这个 source set 名字，结果不是报错而是**这条测试被静默忽略**：
>    构建绿、0 条新用例。所以跑完后必须验证
>    `shared/build/test-results/testDebugUnitTest/TEST-*ApkDownloaderTest.xml` **存在**且
>    `tests="N"` 的 N ≥ 用例数，不能只看 `BUILD SUCCESSFUL`。

```kotlin
package com.jianyi.outfit.data.update

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 这里测的是"下完之后判断对不对"，不是网络。
 * 哈希判断错的表现是"永远校验失败"或"坏包被当成好包装上"，
 * 后者比前者严重得多，所以它值得一条真算一遍的用例。
 */
class ApkDownloaderTest {

    @Test fun sha256_of_known_bytes() {
        val f = File.createTempFile("apk", ".bin")
        f.writeBytes("abc".toByteArray())
        // 公开测试向量：sha256("abc") = ba7816bf...
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Of(f)
        )
        f.delete()
    }

    @Test fun empty_file_hashes_as_empty_string_input() {
        val f = File.createTempFile("apk", ".bin")
        f.writeBytes(ByteArray(0))
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Of(f)
        )
        f.delete()
    }

    /** 空间不够就别开始下：下到 90% 报 NoSpace 比一开始就拒绝要难看得多 */
    @Test fun refuses_when_free_space_below_2_5x_package() {
        assertTrue(shouldRefuseDownload(total = 24_000_000L, free = 40_000_000L))
        assertFalse(shouldRefuseDownload(total = 24_000_000L, free = 60_000_000L))
        // 不知道包多大时不拦（清单没声明 sizeBytes 是合法情形）
        assertFalse(shouldRefuseDownload(total = null, free = 1_000L))
    }

    /** 文件名带版本号：同版本重下要覆盖，不同版本不能互相踩 */
    @Test fun target_name_includes_version() {
        assertEquals("jianyi-2.2.0.apk", apkFileNameFor("2.2.0"))
    }

    private fun newByteArray(n: Int) = ByteArray(n)
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*ApkDownloaderTest*"`
Expected: 编译失败（Unresolved reference: sha256Of）

- [ ] **Step 3: 写实现**

`shared/src/androidMain/kotlin/com/jianyi/outfit/data/update/ApkDownloader.kt`：

```kotlin
package com.jianyi.outfit.data.update

import android.content.Context
import com.jianyi.outfit.data.remote.httpClientEngine
import com.jianyi.outfit.ui.update.ApkInstaller
import com.jianyi.outfit.ui.update.DownloadFailure
import com.jianyi.outfit.ui.update.DownloadEvent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** 空间系数：包体 + 安装时解包，2.5 倍是经验值，宁可保守 */
private const val SPACE_FACTOR = 2.5

fun apkFileNameFor(versionName: String): String = "jianyi-$versionName.apk"

fun shouldRefuseDownload(total: Long?, free: Long): Boolean =
    total != null && free < total * SPACE_FACTOR

class ApkDownloader(private val context: Context) : ApkInstaller {

    private val client = HttpClient(httpClientEngine()) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            // 24MB 的包：只限连接与单次读，不设总请求超时，否则慢网必被掐断
            socketTimeoutMillis = 30_000
        }
        expectSuccess = false
        followRedirects = true
    }

    override fun download(manifest: UpdateManifest): Flow<DownloadEvent> = flow {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val target = File(dir, apkFileNameFor(manifest.versionName))
        target.delete()

        if (shouldRefuseDownload(manifest.sizeBytes, dir.usableSpace)) {
            emit(DownloadEvent.Failure(DownloadFailure.NoSpace)); return@flow
        }

        // 每次重新走三跳：Gitee 的签名直链 15 分钟过期（401），不能缓存
        val response = try {
            client.get(manifest.apkUrl)
        } catch (e: Exception) {
            emit(DownloadEvent.Failure(DownloadFailure.Network)); return@flow
        }
        if (!response.status.isSuccess()) {
            emit(DownloadEvent.Failure(DownloadFailure.Network)); return@flow
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val written = try {
            FileOutputStream(target).use { out ->
                val channel = response.bodyAsChannel()
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var sinceReport = 0L
                while (true) {
                    val read = channel.readAvailable(buf)
                    if (read <= 0) break
                    digest.update(buf, 0, read)
                    out.write(buf, 0, read)
                    // **进度必须真的发出去**。接缝只有 Success/Failure 两个终态的那版，
                    // 下游 `Downloading.done` 永远是 null ⇒ 24MB 的下载全程一根 0% 死条，
                    // 而算 progressOf/sizeText 的用例、跳终态的用例全都绿 —— 没有任何一层能单独发现。
                    // 每 512KB 一条：够顺眼，又不至于每个 64KB 块都去重组一次 UI。
                    done += read
                    sinceReport += read
                    if (sinceReport >= 512 * 1024) {
                        sinceReport = 0L
                        emit(DownloadEvent.Progress(done, manifest.sizeBytes))
                    }
                }
                target.length()
            }
        } catch (e: Exception) {
            target.delete()
            emit(DownloadEvent.Failure(DownloadFailure.Io)); return@flow
        }

        if (written <= 0L) {
            target.delete()
            emit(DownloadEvent.Failure(DownloadFailure.Network)); return@flow
        }

        val expected = manifest.sha256
        if (expected != null) {
            val actual = digest.digest().toHex()
            if (!actual.equals(expected, ignoreCase = true)) {
                // 删掉坏包：留着它，将来"复用已下载文件"的优化会直接装上坏包
                target.delete()
                emit(DownloadEvent.Failure(DownloadFailure.ChecksumMismatch)); return@flow
            }
        }
        emit(DownloadEvent.Success(target.absolutePath))
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** 单独出来是为了能在 JVM 里用公开向量测（见测试） */
internal fun sha256Of(file: File): String? = try {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buf)
            if (read <= 0) break
            digest.update(buf, 0, read)
        }
    }
    digest.digest().toHex()
} catch (e: Exception) {
    null
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :shared:testDebugUnitTest --tests "*ApkDownloaderTest*"`
Expected: BUILD SUCCESSFUL，4 条 0 失败

> 若 `androidUnitTest` 这个源目录是第一次出现，先确认 `:shared:testDebugUnitTest` 真的跑了它：跑完看 `shared/build/test-results/testDebugUnitTest/` 里有没有 `ApkDownloaderTest.xml`。**没有就是没接上**，别看到 BUILD SUCCESSFUL 就当过了。

- [ ] **Step 5: 提交**

```bash
git add shared/src/androidMain/kotlin/com/jianyi/outfit/data/update/ApkDownloader.kt shared/src/androidUnitTest/kotlin/com/jianyi/outfit/data/update/ApkDownloaderTest.kt
git commit -m "feat(update): APK 流式下载器，边下边算 sha256，坏包立即删"
```

---

## Task 8: 权限、FileProvider 与安装页

**Files:**
- Modify: `app/src/main/java/com/jianyi/outfit/update/AndroidUpdateGateway.kt`
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/jianyi/outfit/MainActivity.kt`
- Test: `app/src/test/java/com/jianyi/outfit/UpdateGateBackKeyTest.kt`

**Interfaces:**
- Consumes: `AppUpdateGateway`（Task 4）、`UpdateViewModel.state`（Task 5）
- Produces: 完整的 `AndroidUpdateGateway.install()`；`internal fun shouldBlockBack(state: UpdateUiState): Boolean`

- [ ] **Step 1: 写返回键的失败测试**

`app/src/test/java/com/jianyi/outfit/UpdateGateBackKeyTest.kt`：

```kotlin
package com.jianyi.outfit

import com.jianyi.outfit.data.update.UpdateManifest
import com.jianyi.outfit.ui.update.DownloadFailure
import com.jianyi.outfit.ui.update.UpdateUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 门禁下返回键必须吞掉 —— 否则"不可跳过"只是视觉上的：
 * 用户按返回就回到原页面继续用旧版，强更等于没做。
 * 但下载失败时不能吞：那时候用户已经点不动任何东西，
 * 吞返回就是彻底困死，必须让他能退到桌面去找别的下载方式。
 */
class UpdateGateBackKeyTest {

    private val m = UpdateManifest(9, "2.2.0", 9, "https://gitee.com/x.apk")

    @Test fun gate_blocks_back() {
        assertTrue(shouldBlockBack(UpdateUiState.Gate(m)))
    }

    @Test fun downloading_from_gate_blocks_back() {
        assertTrue(shouldBlockBack(UpdateUiState.Downloading(m, 1L, 2L, forced = true)))
    }

    @Test fun failed_stops_blocking_so_user_can_leave() {
        assertFalse(shouldBlockBack(UpdateUiState.Failed(m, DownloadFailure.Network, forced = true)))
    }

    @Test fun optional_card_does_not_block_back() {
        assertFalse(shouldBlockBack(UpdateUiState.OptionalCard(m)))
        assertFalse(shouldBlockBack(UpdateUiState.Hidden))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*UpdateGateBackKeyTest*"`
Expected: 编译失败（Unresolved reference: shouldBlockBack；`Downloading` 还没有 `forced` 字段）

- [ ] **Step 3: 确认 `forced` 字段已在位**

Task 5 的 `UpdateUiState.Downloading` / `Failed` 已带 `val forced: Boolean`，VM 用 `forcedThisLaunch` 填。本步只做两件事：跑一次 `./gradlew :shared:testDebugUnitTest --tests "*UpdateViewModelTest*"` 确认没被后续改动打断；若 Task 6 的实现里发现某条路径漏传 `forced`，在这里补上并加一条断言到 VM 测试。

- [ ] **Step 4: 写返回键拦截**

`app/src/main/java/com/jianyi/outfit/MainActivity.kt` 内加（并用 `OnBackPressedCallback`，不要重写已废弃的 `onBackPressed`）：

```kotlin
    /**
     * 门禁期间吞掉返回键。
     * 用 OnBackPressedCallback 而不是 onBackPressed()：后者在 Android 13+ 已废弃，
     * 且预测性返回手势（Android 15）只走 callback 这条路。
     */
    private val updateBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { /* 拦住：门禁期间返回无效 */ }
    }

    private fun shouldBlockBack(state: UpdateUiState): Boolean = when (state) {
        is UpdateUiState.Gate -> true
        is UpdateUiState.Downloading -> state.forced
        else -> false
    }
```

并在 `onCreate` 里注册 + 观察：

```kotlin
        onBackPressedDispatcher.addCallback(this, updateBackCallback)
        lifecycleScope.launch {
            container.updateViewModel.state.collect { updateBackCallback.isEnabled = shouldBlockBack(it) }
        }
```

把 `shouldBlockBack` 提成文件级 `internal fun`（测试要直接调）。

- [ ] **Step 5: FileProvider 与权限声明**

`app/src/main/res/xml/file_paths.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <!-- 只暴露下载目录，不要写 <root-path>：那等于把整个可读文件系统交给安装器 -->
    <cache-path name="updates" path="update/" />
</paths>
```

`AndroidManifest.xml` 应用节点内加：

```xml
    <!-- 应用内更新：Android 8+ 是"安装未知应用"特殊权限，不走 requestPermissions() -->
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

`<application>` 内加：

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 6: 填 install() 实现**

替换 `AndroidUpdateGateway` 的骨架：

```kotlin
    /**
     * 拉起系统安装页。
     *
     * 三处细节都是不写就会在真机上"点了没反应"的：
     * - 必须走 FileProvider：Android 7+ 对 content:// 之外的 file:// URI 会抛
     *   FileUriExposedException，表现就是安装按钮按了什么都没发生；
     * - FLAG_GRANT_READ_URI_PERMISSION 必给，否则安装器读不到那个文件；
     * - 权限没给时**不能**直接 startActivity（会抛 ActivityNotFound 或被系统吞掉），
     *   先回 PermissionMissing 让 UI 去引导设置页。
     */
    override fun install(apkPath: String): InstallResult {
        val file = File(apkPath)
        if (!file.exists()) return InstallResult.Failed
        if (!hasInstallPermission()) return InstallResult.PermissionMissing
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            InstallResult.Launched
        } catch (e: Exception) {
            InstallResult.Failed
        }
    }

    override fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // 部分定制 ROM 没有这个设置页；没有就别硬跳，用户自己会去系统设置
        }
    }
```

补 import：`android.content.Intent`、`android.net.Uri`、`android.provider.Settings`、`androidx.core.content.FileProvider`、`java.io.File`。

- [ ] **Step 7: 跑测试与构建**

Run: `./gradlew :app:testDebugUnitTest --tests "*UpdateGateBackKeyTest*" :app:assembleDebug :app:lint`
Expected: 全绿（lint 必须过，它会抓 `REQUEST_INSTALL_PACKAGES` 相关的清单问题）

- [ ] **Step 8: 提交**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/xml/file_paths.xml app/src/main/java/com/jianyi/outfit/update/AndroidUpdateGateway.kt app/src/main/java/com/jianyi/outfit/MainActivity.kt app/src/test/java/com/jianyi/outfit/UpdateGateBackKeyTest.kt shared/src/commonMain/kotlin/com/jianyi/outfit/ui/update/UpdateViewModel.kt
git commit -m "feat(update): FileProvider 安装页、未知来源权限引导、门禁期吞返回键"
```

---

## Task 9: 设置页入口与手动检查

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/settings/SettingsScreen.kt`
- Test: `shared/src/commonTest/kotlin/com/jianyi/outfit/ui/update/UpdateSettingsCopyTest.kt`

**Interfaces:**
- Consumes: `UpdateViewModel`（Task 5）、`deps.appVersion`（Task 4）
- Produces: `object UpdateSettingsCopy { fun entrySubtitle(appVersion: AppVersion, last: UpdateVerdict?): String }`

- [ ] **Step 1: 写文案测试**

`shared/src/commonTest/kotlin/com/jianyi/outfit/ui/update/UpdateSettingsCopyTest.kt`：

```kotlin
package com.jianyi.outfit.ui.update

import com.jianyi.outfit.data.AppVersion
import com.jianyi.outfit.data.update.UpdateVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateSettingsCopyTest {

    private val v = AppVersion(8, "2.1.2")

    @Test fun entry_shows_current_version_when_never_checked() {
        assertEquals("当前版本 2.1.2", UpdateSettingsCopy.entrySubtitle(v, null))
    }

    @Test fun entry_marks_up_to_date() {
        assertEquals("当前版本 2.1.2（已是最新）", UpdateSettingsCopy.entrySubtitle(v, UpdateVerdict.UpToDate))
    }

    /** 门禁状态下副标题不能写"已是最新"，否则与首页卡片自相矛盾 */
    @Test fun entry_marks_update_needed_for_forced() {
        assertTrue(UpdateSettingsCopy.entrySubtitle(v, UpdateVerdict.Forced).contains("需要更新"))
    }

    /**
     * "查过了、没更新"和"没查成"必须是两句话。
     * 合成一句的话，用户在没网时会看到"已是最新"——那是把失败伪装成正常，
     * 比报错更难发现（本项目今天刚因同类问题查了两小时）。
     */
    @Test fun never_checked_and_failed_check_both_avoid_claiming_latest() {
        for (verdict in listOf(null, UpdateVerdict.Unreachable)) {
            val text = UpdateSettingsCopy.entrySubtitle(v, verdict)
            assertEquals(false, text.contains("最新"), "verdict=$verdict 时不能声称是最新：$text")
        }
    }
}
```

- [ ] **Step 2: 跑测试确认失败** → `./gradlew :shared:testDebugUnitTest --tests "*UpdateSettingsCopyTest*"`（Unresolved reference）

- [ ] **Step 3: 写文案对象**（放在 `UpdateCopy.kt` 同包新文件 `UpdateSettingsCopy.kt`）

```kotlin
package com.jianyi.outfit.ui.update

import com.jianyi.outfit.data.AppVersion
import com.jianyi.outfit.data.update.UpdateVerdict

object UpdateSettingsCopy {

    /**
     * 副标题就是"检查更新"的全部反馈，所以四种结论必须互相分得开：
     * 没查过 / 查失败 → 只报版本号（绝不写"最新"）；
     * 已最新 → 明说；可更新 / 需要更新 → 与首页卡片口径一致。
     */
    fun entrySubtitle(appVersion: AppVersion, last: UpdateVerdict?): String = when (last) {
        null, UpdateVerdict.Unreachable -> "当前版本 ${appVersion.versionName}"
        UpdateVerdict.UpToDate -> "当前版本 ${appVersion.versionName}（已是最新）"
        UpdateVerdict.Optional -> "当前版本 ${appVersion.versionName}（可更新）"
        UpdateVerdict.Forced -> "当前版本 ${appVersion.versionName}（需要更新）"
    }
}
```

- [ ] **Step 4: 加设置项**

先确认这页现有的设置行组件叫什么：`grep -n "private fun Settings\|fun SettingRow\|fun SettingsRow" shared/src/commonMain/kotlin/com/jianyi/outfit/ui/settings/SettingsScreen.kt`。**有就复用它**，不要新开一种行样式。

在既有区块之后、页面末尾之前插入（`updateVm` 从 `LocalAppDependencies` 取）：

```kotlin
                // ===== 应用更新（iOS 上整块不显示：gateway.supported=false）=====
                if (deps.updateGateway.supported) {
                    val updateVm = deps.updateViewModel
                    val lastVerdict by updateVm.lastVerdict.collectAsState()
                    SettingsRow(
                        title = "检查更新",
                        subtitle = UpdateSettingsCopy.entrySubtitle(deps.appVersion, lastVerdict),
                        onClick = { updateVm.forceCheck() }
                    )
                }
```

> 反馈全走副标题，不再叠一条 Snackbar：点了之后那行字自己从「当前版本 2.1.2」变成「（已是最新）」，比弹一条三秒就消失的提示更好读，也省掉"VM 要往设置页的 message 通道里回写"这条跨 VM 耦合。

- [ ] **Step 5: 跑测试 + 构建**

Run: `./gradlew :shared:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`
Expected: 全绿

- [ ] **Step 6: 提交**

```bash
git add shared/src/commonMain/kotlin/com/jianyi/outfit/ui/update/UpdateSettingsCopy.kt shared/src/commonMain/kotlin/com/jianyi/outfit/ui/settings/SettingsScreen.kt shared/src/commonMain/kotlin/com/jianyi/outfit/ui/settings/SettingsViewModel.kt shared/src/commonTest/kotlin/com/jianyi/outfit/ui/update/UpdateSettingsCopyTest.kt
git commit -m "feat(update): 设置页检查更新入口，\"没更新\"与\"检查失败\"分两句说"
```

---

## Task 10: CI 发布与发布手册

**Files:**
- Create: `.github/workflows/release.yml`
- Modify: `README.md`（新增「发布新版本」一节）
- Test: 无自动化测试（这一步的验证是"真跑一次 tag"），但要在计划里写明验证方式

**Interfaces:**
- Consumes: `update.json`（Task 1）、既有签名环境变量机制
- Produces: 一个 tag 触发的发布 job

- [ ] **Step 1: 写 workflow**

`.github/workflows/release.yml`：

```yaml
name: Release

# 只在打 tag 时发布。不用 push main 触发：清单里声明的版本必须与 tag 一致，
# 而 tag 是"这个包真的存在"的唯一凭据 —— HEAD 探包那条护栏就是靠它兜住窗口期的。
on:
  push:
    tags: [ "v*" ]

permissions:
  contents: read

jobs:
  publish:
    name: Build signed APK and publish to Gitee
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }

      - uses: gradle/actions/setup-gradle@v4

      - name: Write local.properties
        env:
          WEATHER_API_ID: ${{ secrets.WEATHER_API_ID }}
          WEATHER_API_KEY: ${{ secrets.WEATHER_API_KEY }}
        run: |
          {
            echo "sdk.dir=${ANDROID_HOME}"
            echo "WEATHER_API_ID=${WEATHER_API_ID:-88888888}"
            echo "WEATHER_API_KEY=${WEATHER_API_KEY:-88888888}"
          } > local.properties

      - name: Require release signing secret
        run: |
          if [ -z "${RELEASE_KEYSTORE_BASE64:-}" ]; then
            echo "::error::未配置 RELEASE_KEYSTORE_BASE64 —— 未签名包不能当更新包发布"; exit 1
          fi
        env: { RELEASE_KEYSTORE_BASE64: ${{ secrets.RELEASE_KEYSTORE_BASE64 }} }

      - name: Decode release keystore
        env: { RELEASE_KEYSTORE_BASE64: ${{ secrets.RELEASE_KEYSTORE_BASE64 }} }
        run: echo "$RELEASE_KEYSTORE_BASE64" | base64 -d > "${{ runner.temp }}/release.jks"

      - name: Assemble signed release
        env:
          RELEASE_KEYSTORE_PATH: ${{ runner.temp }}/release.jks
          RELEASE_KEYSTORE_PASSWORD: ${{ secrets.RELEASE_KEYSTORE_PASSWORD }}
          RELEASE_KEY_ALIAS: ${{ secrets.RELEASE_KEY_ALIAS }}
          RELEASE_KEY_PASSWORD: ${{ secrets.RELEASE_KEY_PASSWORD }}
        run: ./gradlew assembleRelease --stacktrace

      - name: Check tag matches update.json
        run: |
          TAG="${GITHUB_REF_NAME}"
          python3 - "$TAG" update.json <<'PY'
          import json, sys
          tag, path = sys.argv[1], sys.argv[2]
          m = json.load(open(path, encoding="utf-8"))
          url = m["apkUrl"]
          if f"/download/{tag}/" not in url:
              sys.exit(f"update.json 的 apkUrl 不含 /download/{tag}/ —— 清单与本次 tag 不一致")
          if m["versionName"] != tag.lstrip("v"):
              sys.exit(f"update.json versionName={m['versionName']} 与 tag={tag} 不一致")
          print(f"清单与 tag 一致：{m['versionName']} (code {m['versionCode']})")
          PY

      - name: Compute package digest
        id: pkg
        run: |
          APK=app/build/outputs/apk/release/app-release.apk
          test -f "$APK" || { echo "找不到签名产物 $APK"; exit 1; }
          echo "apk=$APK" >> "$GITHUB_OUTPUT"
          echo "sha256=$(sha256sum "$APK" | cut -d' ' -f1)" >> "$GITHUB_OUTPUT"
          echo "size=$(stat -c %s "$APK")" >> "$GITHUB_OUTPUT"

      - name: Cross-check declared sha256 (if declared)
        run: |
          python3 - <<'PY'
          import json, os, sys
          m = json.load(open("update.json", encoding="utf-8"))
          declared, actual = m.get("sha256"), os.environ["ACTUAL"]
          if declared and declared.lower() != actual.lower():
              sys.exit(f"update.json 声明的 sha256 与实际包不一致：\n  {declared}\n  {actual}")
          print("哈希一致" if declared else "清单未声明哈希，跳过比对（请把实际值补进去）")
          print(f"实际 sha256：{actual}")
          print(f"实际大小：{os.environ['SIZE']} 字节")
          PY
        env: { ACTUAL: ${{ steps.pkg.outputs.sha256 }}, SIZE: ${{ steps.pkg.outputs.size }} }

      # 两步式：先建发行版拿 id，再传附件。端点与参数取自 Gitee 官方 OpenAPI 规范
      # （https://gitee.com/api/v5/doc_json），但**本 job 是第一次真实调用**，
      # 失败就按 README「发布手册」走手动上传 —— 功能不依赖这条链路成功。
      #
      # 刻意不用 `if: ${{ env.GITEE_API_TOKEN != '' }}` 这类步骤级条件：
      # 步骤级 if 里 secrets 上下文不可用，而 env 的可见性又依赖"同一个 step 的 env 块"
      # 这种容易踩空的细节（本项目在 PriceLens 上就为此返工过）。
      # 改成把 token 交给 job 级 env、在脚本里自己判断并 exit 0 —— 少一个能骗人的绿勾。
      - name: Publish to Gitee Release
        env:
          GITEE_API_TOKEN: ${{ secrets.GITEE_API_TOKEN }}
          APK_PATH: ${{ steps.pkg.outputs.apk }}
          SHA256: ${{ steps.pkg.outputs.sha256 }}
          SIZE: ${{ steps.pkg.outputs.size }}
        run: |
          set -euo pipefail
          if [ -z "${GITEE_API_TOKEN:-}" ]; then
            echo "::warning::未配置 GITEE_API_TOKEN，本次未自动发布。"
            echo "::warning::请手动在 Gitee 建发行版 $GITHUB_REF_NAME 并上传 $APK_PATH"
            echo "::warning::实际 sha256=$SHA256  size=$SIZE —— 记得补进 update.json"
            exit 0
          fi

          BODY=$(python3 -c "import json;print(json.load(open('update.json',encoding='utf-8')).get('notes',''))")

          curl -sS --fail-with-body -X POST \
            "https://gitee.com/api/v5/repos/wuliao11541/WeatherOutfit/releases" \
            --data-urlencode "access_token=$GITEE_API_TOKEN" \
            --data-urlencode "tag_name=$GITHUB_REF_NAME" \
            --data-urlencode "name=简衣 $GITHUB_REF_NAME" \
            --data-urlencode "body=$BODY" \
            --data-urlencode "target_commitish=$GITHUB_SHA" \
            -o release.json

          RELEASE_ID=$(python3 -c "import json;print(json.load(open('release.json'))['id'])")
          echo "建好发行版 id=$RELEASE_ID"

          curl -sS --fail-with-body -X POST \
            "https://gitee.com/api/v5/repos/wuliao11541/WeatherOutfit/releases/$RELEASE_ID/attach_files" \
            -F "access_token=$GITEE_API_TOKEN" \
            -F "file=@$APK_PATH"

          echo "附件已上传。请把 sha256=$SHA256 补进 update.json 并提交。"

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: jianyi-${{ github.ref_name }}
          path: app/build/outputs/apk/release/app-release.apk
```

- [ ] **Step 2: README 加发布手册**

在 `README.md` 末尾加一节，内容包含：
1. 提升版本号的三处（`app/build.gradle.kts` 的 `versionCode`/`versionName`、`update.json` 的 `versionCode`/`versionName`/`minSupportedVersionCode`/`apkUrl`）；
2. 打 tag：`git tag v2.2.0 && git push origin v2.2.0`；
3. CI 自动发布需要 `GITEE_API_TOKEN`；
4. **手动兜底全流程**：Gitee 网页 → 发行版 → 新建 → tag 填 `v2.2.0` → 上传 `app-release.apk` → 把 CI 日志里的 sha256 补进 `update.json` 并提交；
5. 什么时候该抬 `minSupportedVersionCode`（缓存格式变更、接口凭证语义变更等会让旧版读到错数据的改动），以及抬错会拦掉所有人这件事。

- [ ] **Step 3: 我们自己的第一个 Release 出来后顺手复核探包正例**

**这条已经从阻塞项降级为"顺手复核"** —— 2026-09-29 用**第三方真实存在附件的 Gitee Release**
提前把机制问清了，不必等我们自己出包：

```
curl -I https://gitee.com/beijing-jicang/yichenbao/releases/download/v1.0.34/24-12-22.apk
  → HTTP=200
    FINAL=https://foruda.gitee.com/attach_file/1734866382367626772/24-12-22.apk?token=…&ts=…&attname=24-12-22.apk
```

⇒ **`headOk` 的三跳（`releases/download/<tag>/<file>` → `attach_files/…` → `foruda`）对 HEAD 答 200**，
`Forced` 不会因为我们把探针设计错而永远不触发。当时最怕的形态（"附件地址对 HEAD 返 403/405 ⇒
门禁静默失效、22 条用例全绿"）已被排除。

同时**实测到另一条会影响 Task 7 的事实**：同一个地址带 `Range: bytes=0-99` 时 foruda **忽略 Range**，
直接开始整文件传输（30 秒内推了 26MB / 58MB 才被我主动掐断）⇒ **APK 下载没有续传**。
`ApkDownloader` 因此不许实现"断点续传"路径，失败就整个重来；
进度条要能接受"下到 90% 失败又从 0 开始"这种观感。
（注意证据边界：这是**别的仓库**的附件。我们自己的包上线后仍建议顺手
`curl -I` 一次自己的 `apkUrl` 确认 200 —— 那是确认"附件真的传上去了 + tag 拼对了"，不是确认机制。）

已实测过的其它相关事实：不存在的 tag → 404（干净的负例，不是登录页）；
raw 域 → HEAD 200 且 `Range: bytes=0-99` → 206（清单本身可分段读）。

- [ ] **Step 4: 语法校验**

Run: `py -c "import yaml;d=yaml.safe_load(open('.github/workflows/release.yml',encoding='utf-8'));print(list(d['jobs']));print(len(d['jobs']['publish']['steps']),'steps')"`
Expected: `['publish']` 与步骤数

- [ ] **Step 5: 提交**

```bash
git add .github/workflows/release.yml README.md
git commit -m "ci(release): tag 触发签名构建并发布到 Gitee Release，附手动兜底手册"
```

---

## Task 11: 版本提升、全量验证与真机回归

**Files:**
- Modify: `app/build.gradle.kts`（`versionCode = 9`、`versionName = "2.2.0"`）
- Modify: `update.json`（与上面齐）
- Modify: `CHANGELOG.md`

- [ ] **Step 1: 提升版本并让自校验测试通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*UpdateManifestFileTest*"`
Expected: 通过（`apkUrl` 的 tag 段与 `versionName` 一致）

> `UpdateManifestFileTest` 在 Task 1 就已经建好（`app/src/test/java/com/jianyi/outfit/UpdateManifestFileTest.kt`，
> 现在**只有这 4 条**，别按更早的描述去找 sha256 断言）：从测试工作目录向上找到含 `settings.gradle.kts` 的仓库根，
> 读真实的 `update.json`，逐条钉住
> ①`declared_version_code_matches_build_config`、②`declared_version_name_matches_build_config`
> （这两条就是"改了版本号忘了改清单"的锁：**它变红就改清单，不要改测试**）、
> ③`apk_url_tag_matches_version_name`（tag 段必须跟 `versionName` 走，否则 HEAD 探包永远探一个不存在的附件）、
> ④`min_supported_code_is_not_above_declared_version_code`（`decideUpdate` 会用 `minOf` 把这个笔误静默吸收，
> 别处全都看不出来，只有这条读真文件的用例能抓到）。
>
> 它**不覆盖**的事别再以为覆盖了：不校验 https / `.apk` 后缀 / sha256 长度 —— 那些是
> `UpdateManifestParser.isSane()` 在运行时的语义闸，不是这条仓库测试的职责。
> 另外 `update.json` 已登记成该测试任务的输入（`app/build.gradle.kts` 里 `inputs.file(...)`），
> 所以它不会因为 UP-TO-DATE/FROM-CACHE 而拿着旧清单变绿。

- [ ] **Step 2: 本地全量（等价 CI）**

Run: `GRADLE_USER_HOME=E:/dev/gradle-home JAVA_HOME=E:/dev/jdk ./gradlew lint testDebugUnitTest assembleDebug assembleRelease`
Expected: BUILD SUCCESSFUL；记录测试总数与 0 失败，并核对四个任务都不是 `UP-TO-DATE`/`FROM-CACHE`（缓存命中要在报告里如实说明）

- [ ] **Step 3: 真机回归（两台，`adb install -r -t`，绝不卸载）**

逐条记录"实际看到什么"，不要写"应该没问题"：

1. 清单可达、`minSupported=1` ⇒ 无门禁、无提示（正常路径）。
2. 临时把 `update.json` 的 `minSupportedVersionCode` 改成 99 推到 Gitee ⇒ 冷启动**只弹可跳过提示**（因为 v2.2.0 附件还不存在，HEAD 探包失败）—— 这条是"包下不到就不拦"的正向证据。
3. 手动在 Gitee 建 tag `v2.2.0` 发行版并上传 debug 之外那份签名 APK（或先传任意 `.apk` 占位）⇒ 同一步骤 2 的配置下**出现全屏门禁**。
4. 门禁下按返回键 ⇒ 不退出（Task 8 的正向证据）。
5. 点「立即更新」⇒ 进度条到 100% ⇒ 系统安装页弹出 ⇒ 装成功后版本变为 2.2.0、**数据仍在**（首页显示的还是那行旧缓存）。
6. 断网点更新 ⇒ 失败文案 + 「重试」+「复制下载链接」；OriginOS/ColorOS 的"未知来源"引导页各截一张。
7. 把 `sha256` 改成错值 ⇒ 下载完判 `ChecksumMismatch`，文案不提重试，且 `cacheDir/update/` 下不留文件。
8. 窄屏最坏负载：把 `notes` 换成 80 个汉字，截图确认动作按钮与文案没被裁（这条专门防今天那个 CTA 被省略号吃掉的坑复发）。
9. 设备状态还原：清单改回 `minSupportedVersionCode = 1`，App 回到正常态。

- [ ] **Step 4: 提交并推两个远端**

```bash
git add app/build.gradle.kts update.json CHANGELOG.md
git commit -m "chore(release): 2.2.0 / versionCode 9 —— 首个支持应用内更新的版本"
git push origin feat/app-update && git push gitee feat/app-update
```

---

## 完成判据

全部满足才算这批做完：

1. `lint testDebugUnitTest assembleDebug assembleRelease` 本地全绿，四个任务真执行；
2. 新增测试全部通过，且 `decideUpdate` 的"包下不到不拦"、门禁不可跳过、返回键拦截三条各有一条自动用例钉住；
3. 真机 9 条回归有截图与逐条实际观察（不能只写"已验证"）；
4. 一次真实的"清单改了 → 门禁出现 → 装成功 → 数据仍在"闭环跑通；
5. 两个远端都有这批提交；`main` 是否合入等用户决定。
