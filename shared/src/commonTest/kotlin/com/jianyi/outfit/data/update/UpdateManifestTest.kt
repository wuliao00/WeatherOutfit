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
     * 整个模块里最值钱的一条：null 版本号必须判无效，而不是"看起来能用"。
     *
     * 天气那边故意开了 isLenient + coerceInputValues（接口哪天多返回字段、
     * 数字时而是字符串，不能因此整次查询失败）。清单**绝不能**沿用那套配置：
     * coerceInputValues 的语义是"类型对不上或给了 null 就回退到默认值"，
     * 而 Int 的回退值就是 0 —— versionCode 一旦是 null 或缺失，
     * 会被悄悄折成 0，于是 current(8) >= 0 恒成立 ⇒ 判 UpToDate ⇒
     * 用户永远收不到更新，且没有任何报错。宽松解析在清单这里造出的不是崩溃，
     * 是**永久静默失效**。
     *
     * 现在没开那个开关，所以 null 在解码那步直接抛 ⇒ parse 返回 null ⇒
     * 调用方降级成 Unreachable。这条断言锁的就是"绝不能为了少报错而开开关"。
     */
    @Test fun null_version_code_is_invalid_not_coerced_to_zero() {
        assertNull(
            UpdateManifestParser.parse(
                """{"versionCode":null,"versionName":"2.2.0","minSupportedVersionCode":1,"apkUrl":"https://gitee.com/x.apk"}"""
            )
        )
        assertNull(
            UpdateManifestParser.parse(
                """{"versionCode":9,"versionName":"2.2.0","minSupportedVersionCode":null,"apkUrl":"https://gitee.com/x.apk"}"""
            )
        )
    }

    /**
     * 0 与负数同样是错清单，而且正是"被折成 0"那一故障的最终形态，
     * 所以这道闸必须由语义层（isSane）把住：versionCode=0 会让
     * `current >= 0` 对**任何**已装版本成立 ⇒ 全员判 UpToDate、永远不再更新。
     */
    @Test fun zero_or_negative_version_code_is_invalid() {
        assertNull(
            UpdateManifestParser.parse(
                """{"versionCode":0,"versionName":"2.2.0","minSupportedVersionCode":1,"apkUrl":"https://gitee.com/x.apk"}"""
            )
        )
        assertNull(
            UpdateManifestParser.parse(
                """{"versionCode":-1,"versionName":"2.2.0","minSupportedVersionCode":1,"apkUrl":"https://gitee.com/x.apk"}"""
            )
        )
        assertNull(
            UpdateManifestParser.parse(
                """{"versionCode":9,"versionName":"2.2.0","minSupportedVersionCode":0,"apkUrl":"https://gitee.com/x.apk"}"""
            )
        )
    }

    /**
     * 带引号的数字**会被接受**，这是刻意的，不是漏网，别再为此加结构校验。
     *
     * 计划文档原本断言"严格 Json 会拒掉 `"versionCode":"9"`"，实测不成立：
     * kotlinx.serialization 的整型解码对带引号的数字在严格模式下照样读出 9，
     * 而 Json 的配置项里只有"更松"的开关，没有"更严"的开关能关掉这条宽容。
     * 曾据此在 parse() 前加过一道手写 JSON 树检查，已按裁决删除：
     * 它要防的坑是 null/缺失被折成 0（上面两条测的就是这个），
     * 而 "9" 解成 9 是无害的；手写结构校验既会误伤以后合法的新写法，
     * 也是长期维护负担（它还得跟着 @SerialName 改）。
     * 真正的防线 = 不开 coerceInputValues + isSane() 的 `<= 0` 闸，不是拒引号。
     */
    @Test fun quoted_number_is_accepted_by_design() {
        val m = UpdateManifestParser.parse(
            """{"versionCode":"9","versionName":"2.2.0","minSupportedVersionCode":1,"apkUrl":"https://gitee.com/x.apk"}"""
        )
        assertEquals(9, m?.versionCode)
        assertEquals(1, m?.minSupportedCode)
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
