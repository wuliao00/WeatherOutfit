package com.jianyi.outfit.ui.update

import com.jianyi.outfit.data.AppVersion
import com.jianyi.outfit.data.update.UpdateVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 设置页副标题的六档文案。
 *
 * 文案在这里测而不是在 Compose 树里测：shared 没有 Robolectric、也没有截图测试，
 * 而这一层承重的全部是"哪句话在说谎"，与布局无关。
 * 布局那一侧的约束（不许 maxLines 把整句裁成省略号）由
 * `worst_case_load_stays_inside_one_line_on_a_narrow_screen` 先钉住长度，
 * 具体排版仍要到真机看 —— 台账里首页降级条那条教训针对的是
 * "a11y 树里文本完整、只有裁图才发现被裁"。
 */
class UpdateSettingsCopyTest {

    private val v = AppVersion(9, "2.2.0")

    /**
     * 六档：还没查过 / 检查中 / 已是最新 / 可跳过 / 门禁 / 无法确认。
     * 「检查中」压过结论（见 checking_overrides_a_stale_verdict），所以这里只列一个 verdict 配它。
     */
    private fun allTiers(): List<Pair<String, String>> = listOf(
        "还没查过" to UpdateSettingsCopy.entrySubtitle(v, null, false),
        "检查中" to UpdateSettingsCopy.entrySubtitle(v, null, true),
        "已是最新" to UpdateSettingsCopy.entrySubtitle(v, UpdateVerdict.UpToDate, false),
        "可跳过" to UpdateSettingsCopy.entrySubtitle(v, UpdateVerdict.Optional, false),
        "门禁" to UpdateSettingsCopy.entrySubtitle(v, UpdateVerdict.Forced, false),
        "无法确认" to UpdateSettingsCopy.entrySubtitle(v, UpdateVerdict.Unreachable, false)
    )

    /**
     * 分不出来的两档等于没写。尤其是「还没查过」与「检查失败」：
     * 计划里那版把两者合成同一句"当前版本 X"，读的人无从知道是没查还是查不到。
     */
    @Test fun every_tier_is_its_own_sentence() {
        val texts = allTiers().map { it.second }
        assertEquals(texts.size, texts.distinct().size, "有其中两档写成同一句话：$texts")
    }

    /**
     * **只有"查过、确实没更新"那一档许说"最新"。**
     * 这条是整个文件里最值钱的断言：失败被写成成功之后，用户不会再去点第二次，
     * 而"所有人永远收不到更新"这件事在日志里也是安静的。
     */
    @Test fun only_up_to_date_claims_being_latest() {
        for ((name, text) in allTiers()) {
            if (name == "已是最新") continue
            assertFalse(text.contains("最新"), "$name 这一档不许声称是最新：$text")
        }
        assertTrue(
            UpdateSettingsCopy.entrySubtitle(v, UpdateVerdict.UpToDate, false).contains("最新"),
            "查过、确实没有更新时就得明说，否则用户以为还没查"
        )
    }

    @Test fun entry_shows_current_version_when_never_checked() {
        assertEquals("当前版本 2.2.0（还没查过）", UpdateSettingsCopy.entrySubtitle(v, null, false))
    }

    @Test fun entry_marks_up_to_date() {
        assertEquals(
            "当前版本 2.2.0（已是最新）",
            UpdateSettingsCopy.entrySubtitle(v, UpdateVerdict.UpToDate, false)
        )
    }

    /** 门禁状态下副标题不能写"已是最新"，否则与首页卡片自相矛盾 */
    @Test fun entry_marks_update_needed_for_forced() {
        val text = UpdateSettingsCopy.entrySubtitle(v, UpdateVerdict.Forced, false)
        assertTrue(text.contains("需要更新"), text)
        assertFalse(text.contains("可跳过"), "门禁那一档不许给出「随时可以不理」的暗示：$text")
    }

    @Test fun entry_marks_optional_as_skippable() {
        val text = UpdateSettingsCopy.entrySubtitle(v, UpdateVerdict.Optional, false)
        assertTrue(text.contains("发现新版本") && text.contains("可跳过"), text)
    }

    /**
     * 安全约束的那一条：门禁挂着时点「检查更新」而恰好断网，
     * 状态机不会解除门禁（UpdateViewModel 里那两条粘滞用例钉住那半），
     * 这一行则必须落在"无法确认"，**不许**显示"已是最新版本"。
     */
    @Test fun a_failed_check_under_a_still_holding_gate_says_it_cannot_confirm() {
        val text = UpdateSettingsCopy.entrySubtitle(v, UpdateVerdict.Unreachable, false)
        assertTrue(text.contains("无法确认"), "检查失败要说失败：$text")
        assertFalse(text.contains("已是最新"), "把失败伪装成成功是最难发现的那种错：$text")
    }

    /**
     * 检查进行中时，屏幕上那句旧结论已经不可信了 —— 再写"已是最新"就是在报一个
     * 正在被重查的答案。所以这一档压过 lastVerdict。
     */
    @Test fun checking_overrides_a_stale_verdict() {
        for (last in listOf(null, UpdateVerdict.UpToDate, UpdateVerdict.Forced)) {
            val text = UpdateSettingsCopy.entrySubtitle(v, last, true)
            assertTrue(text.contains("正在检查"), "last=$last 时没报出「正在检查」：$text")
            assertFalse(text.contains("最新"), "结果还没回来不许沿用旧结论（last=$last）：$text")
        }
    }

    /** 六档都带着装机版本号：切状态时不会让"我装的是什么"这半句消失 */
    @Test fun every_tier_carries_the_installed_version_name() {
        for ((name, text) in allTiers()) {
            assertTrue(text.contains(v.versionName), "$name 这一档没写版本号：$text")
        }
    }

    /**
     * 最坏内容负载。这行没有 maxLines、也不许加 —— 超长时它换行，
     * 而换行之前那串省略号是本项目真踩过的坑（动作提示被裁掉、a11y 树里文本却完整）。
     * 这里钉的是"文案自己别把长度用光"：真实版本名 + 最长那一档仍要在窄屏一行内放得下。
     */
    @Test fun worst_case_load_stays_inside_one_line_on_a_narrow_screen() {
        val longest = allTiers().maxBy { it.second.length }
        assertTrue(
            longest.second.length <= 24,
            "副标题最长一档 ${longest.second.length} 字（${longest.first}）超过窄屏一行：${longest.second}"
        )
        // 纯函数自己绝不省略：长版本名必须原样出现，裁不裁是排版层的事
        val longName = UpdateSettingsCopy.entrySubtitle(
            AppVersion(9, "10.10.10-beta.1"),
            UpdateVerdict.Unreachable,
            false
        )
        assertTrue(longName.contains("10.10.10-beta.1"), longName)
    }

    /** 省略号只属于"正在检查"那一档（它是唯一表示"还没完"的符号） */
    @Test fun only_the_checking_tier_uses_an_ellipsis() {
        for ((name, text) in allTiers()) {
            assertEquals(
                name == "检查中",
                text.contains("…"),
                "$name 这一档的省略号不该出现成这样：$text"
            )
        }
    }
}
