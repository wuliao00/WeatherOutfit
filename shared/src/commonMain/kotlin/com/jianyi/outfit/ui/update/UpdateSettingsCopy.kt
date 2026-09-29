package com.jianyi.outfit.ui.update

import com.jianyi.outfit.data.AppVersion
import com.jianyi.outfit.data.update.UpdateVerdict

/**
 * 设置页「检查更新」那一行的副标题 —— 这条入口**全部的反馈都走这一行**。
 *
 * ## 为什么不弹 Snackbar（已裁定的裁决，别再翻）
 * 弹 Snackbar 需要更新状态机往设置页那个 ViewModel 的 message 通道回写，
 * 那是一条跨 VM 的耦合：设置页的 VM 从此要知道更新流程的存在。代价只是"点击没有即时动效"，
 * 而那由下面 `checking` 那一档补上（点下去立刻变"正在检查…"）。
 *
 * ## 六档必须互相分得开，而且"查失败"那一档最要紧
 * 把「还没查过」和「检查失败」写成同一句话，或者把失败写成"已是最新"，
 * 症状都是**把失败伪装成成功**：用户断网点了一下，看到"已是最新版本"，
 * 于是认为没有新版可升 —— 而真实情况是清单根本没读到。这类"静默降级成正常态"
 * 在本功能里已经付过两次钱（清单读不到与包探不到都曾被折成"什么都不显示"的沉默）。
 * 所以：只有 [UpdateVerdict.UpToDate] 那一档许出现"最新"二字，其余五档一个字都不许有，
 * 这条由 `UpdateSettingsCopyTest` 逐档钉住。
 *
 * ## 与"门禁不许被一次手动检查解除"的配合
 * 门禁挂着时点这个按钮恰好断网 ⇒ 判定层给 [UpdateVerdict.Unreachable]，
 * 状态机那边**不放行 Gate→Hidden**（UpdateViewModel 的 forcedThisLaunch 粘滞），
 * 而这一行必须落在"无法确认"那一档 —— 也就是说 UI 说的与门禁还挂着这件事不矛盾：
 * 副标题报的是"这次没查成"，门禁报的是"上一次查成的结论仍然生效"。
 *
 * ## checking 为什么由调用方传进来而不是这里判
 * "正在检查"是一次**过程**，不是结论：`lastVerdict` 在检查期间保持旧值，
 * 而 StateFlow 对相同值去重、再查一次得到同一个结论时根本不会再发，
 * 所以这一维只能来自状态机的 `checking`（见 UpdateViewModel.checking），UI 自己记 flag 复位不了。
 */
object UpdateSettingsCopy {

    /**
     * 一句话同时给出"装的是哪个版本"和"关于更新现在知道到什么程度"。
     *
     * 六档全都以 `当前版本 <versionName>` 开头：这样这一行永远在回答"我装的是什么"，
     * 括号里才是"查的结果"，切换状态时不会有半句话突然消失。
     * `versionName` 由 app 侧注入（见 [AppVersion]），这里不出现任何平台产物。
     */
    fun entrySubtitle(appVersion: AppVersion, last: UpdateVerdict?, checking: Boolean): String {
        val current = "当前版本 ${appVersion.versionName}"
        val suffix = if (checking) {
            "正在检查…"
        } else {
            when (last) {
                // 没查过 ≠ 查了没结果：这一档不许显得像"检查失败"
                null -> "还没查过"

                // 离线、非 2xx、清单看不懂都折在这一档：对用户它们是同一个结果——不知道。
                // 具体成因走 JianyiUpdate 那条日志（观测面在 UpdateRepository），不写进 UI。
                UpdateVerdict.Unreachable -> "无法确认是否有更新"

                UpdateVerdict.UpToDate -> "已是最新"

                // 与首页可跳过卡片同一口径：这一档随时能继续用旧版
                UpdateVerdict.Optional -> "发现新版本，可跳过"

                // 与门禁卡片同一口径；门禁此刻仍然挂在屏幕上，这一行在它后面
                UpdateVerdict.Forced -> "需要更新"
            }
        }
        return "$current（$suffix）"
    }
}
