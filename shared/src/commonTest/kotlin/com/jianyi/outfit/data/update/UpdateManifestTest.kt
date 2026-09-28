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
        assertEquals(9, m?.minSupportedCode)
        assertEquals(24115200L, m?.sizeBytes)
    }

    /** 可选字段缺失要能解，且解成 null —— CI 首次出包之前哈希就是拿不到的 */
    @Test fun optional_fields_may_be_absent() {
        val m = UpdateManifestParser.parse(
            """{"versionCode":9,"versionName":"2.2.0","minSupportedVersionCode":1,"apkUrl":"https://gitee.com/x.apk"}"""
        )
        assertEquals(1, m?.minSupportedCode)
        assertNull(m?.sha256)
        assertNull(m?.sizeBytes)
        assertNull(m?.notes)
    }

    @Test fun unknown_fields_are_ignored() {
        val m = UpdateManifestParser.parse(
            """{"versionCode":9,"versionName":"2.2.0","minSupportedVersionCode":1,"apkUrl":"https://gitee.com/x.apk","future_field":42}"""
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
                """{"versionCode":"9","versionName":"2.2.0","minSupportedVersionCode":1,"apkUrl":"https://gitee.com/x.apk"}"""
            )
        )
    }

    @Test fun missing_required_field_is_invalid() {
        assertNull(UpdateManifestParser.parse("""{"versionName":"2.2.0","minSupportedVersionCode":1,"apkUrl":"https://gitee.com/x.apk"}"""))
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
        val base = """{"versionCode":9,"versionName":"2.2.0","minSupportedVersionCode":1,"apkUrl":"https://gitee.com/x.apk","sha256":""""
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
        assertEquals(12, m?.minSupportedCode)
    }
}
