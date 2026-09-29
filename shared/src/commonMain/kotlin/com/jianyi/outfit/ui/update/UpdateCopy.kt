package com.jianyi.outfit.ui.update

import com.jianyi.outfit.data.AppVersion
import com.jianyi.outfit.data.update.UpdateManifest

/**
 * 门禁层的全部判据与文案，一律写成 `UpdateUiState -> 决策` 的纯函数。
 *
 * ## 为什么这一层的东西要抽出来测
 * shared 里没有 Robolectric、也没有截图测试，`UpdateGateLayer` 那几十行 composable
 * 在本机一行都跑不到。于是"门禁能不能被关掉""VM 发下来的 Progress 看不看得见"
 * 这两件最承重的事，如果只存在于 composable 里的 `if`，就**只能靠真机盯屏幕发现**。
 * 这个仓库为同一形状的缺陷付过两次钱：
 * - 首页降级条的动作提示被 `maxLines = 1` 裁成省略号，无障碍树里文本是完整的，只有裁图看得出；
 * - 下载接缝原本只有成功/失败两个终态，结果 24MB 的下载全程一根 0% 死条，
 *   而算 `progressOf` 的测试和测状态跳转的测试**都全绿**（见 UpdateViewModelTest 里那三条 Progress 用例）。
 * 判据放到这里来测，UI 那一层只许照着画，不许自己再判一次。
 *
 * ## 三条判据刻意不合并
 * [gateIsForced]（文案分量 + 有没有关闭入口）、[UpdateUiState.blocksUser]（要不要按住整页）、
 * [gateActions]（摆哪几颗按钮）合起来看着像"是不是门禁"这一件事，其实是三件：
 * 合并前两条会让下载失败之后仍然按住用户（下不下来、又走不掉）；
 * 合并后两条会给 Failed 摆一颗按下去毫无反应的"以后再说"（VM 的 dismissOptional
 * 只认 OptionalCard 那一态）。每种合并都有一个具体症状，所以分开写、分开测。
 */

/** [UpdateCopy.progressOf] 与 [DownloadView.ratio] 的"不确定态"返回值：不是 0，是没人知道比例 */
const val PROGRESS_INDETERMINATE: Float = -1f

/** 进度条要画成什么样。ratio 为 [PROGRESS_INDETERMINATE] 时不许画带比例的条 */
data class DownloadView(val ratio: Float, val label: String)

/** 卡片上的动作。列表顺序就是渲染顺序，所以这一份列表本身就是布局 */
enum class GateAction {
    /** 开始下载。门禁与可跳过卡片都有它 */
    Update,

    /** 失败之后再来一次 */
    Retry,

    /** 唯一的关闭入口 —— 只属于可跳过那一档，出现在门禁那一档就等于没有门禁 */
    Later,

    /** 逃生口：把 apkUrl 复制到剪贴板。它不关闭任何东西，所以门禁那一档也可以给 */
    CopyLink
}

/** 按钮上的字。长度上限见 UpdateCopyTest —— 窄屏 + 中文，六个字是一行的安全线 */
val GateAction.label: String
    get() = when (this) {
        GateAction.Update -> "立即更新"
        GateAction.Retry -> "重试"
        GateAction.Later -> "以后再说"
        GateAction.CopyLink -> "复制下载链接"
    }

/** 这一态在文案上是不是"门禁那一套"：标题要说"需要更新"，且没有任何关闭入口 */
fun gateIsForced(state: UpdateUiState): Boolean = when (state) {
    is UpdateUiState.Gate -> true
    is UpdateUiState.Downloading -> state.forced
    is UpdateUiState.Failed -> state.forced
    else -> false
}

/**
 * 这一态要不要把用户按住：遮罩、吃掉点击、吞返回键，**三件事共用这一个判据**。
 *
 * 与 [gateIsForced] 刻意差一个分支：Failed 不拦 —— 一直下不下来的时候必须让人走，
 * 否则这个功能自己把它要保护的"任何失败都能退出"给反噬了（spec §7.1，**这是"失败逃生"**）。
 * Task 8 的返回键判据应当复用这个函数：两处各写一份 when 的结果，
 * 就是"返回键能退、卡片还按着你"或者"卡片放开了、返回键还在吞"。
 *
 * `hasUrl` 这一维是评审补的：门禁下载中这一档摆的**只有**「复制下载链接」，
 * 所以连地址都没有时，按住整页就等于"一块吃点击、零按钮"的膜 —— 用户只能杀进程。
 * 那一态此刻没有任何可完成的动作，放开页面才是"不许锁死用户"的写法。
 *
 * ## 为什么这个参数没有默认值（2026-09-30 终审修复轮）
 * 从前它写作 `hasUrl: Boolean = true`，而两个调用点用法不一样：门禁层传真值，
 * 返回键那一侧（`shouldBlockBack`）用默认 —— 那正是本函数上一版（`gateHoldsPage`）的注释
 * 点名禁止的"两处各写一份"，只不过漂的是**输入**而不是判据。今天这条漂移不可达
 * （`apkUrl` 为空时状态是 Hidden），但不可达的重复正是以后长出错的地方。
 * 去掉默认值之后，"返回键那一侧偷偷按 true 算"在类型上就不存在了：
 * 两个调用点都必须显式交出自己那份 `hasUrl`，而它们交的是同一个表达式 `vm.apkUrl() != null`。
 */
fun UpdateUiState.blocksUser(hasUrl: Boolean): Boolean = when (this) {
    is UpdateUiState.Gate -> true
    is UpdateUiState.Downloading -> forced && hasUrl

    // Failed 放开是刻意的（spec §7.1）：下不下来必须能走。
    // 别把 Launched 也照这一条放开 —— 安装页弹出去之后状态是 Gate 不是 Failed，
    // 那一态的语义是"成功之后绕过"，与"失败逃生"不是一类，见 UpdateViewModel.onDownloaded。
    else -> false
}

/** 卡片要画的那份清单。Hidden 没有清单，于是整层直接不出现 */
fun gateManifest(state: UpdateUiState): UpdateManifest? = when (state) {
    is UpdateUiState.OptionalCard -> state.manifest
    is UpdateUiState.Gate -> state.manifest
    is UpdateUiState.Downloading -> state.manifest
    is UpdateUiState.Failed -> state.manifest
    else -> null
}

/**
 * 标题。门禁中途（下载中、下载失败）不许换回"发现新版本" ——
 * 同一张卡片上一句写"需要更新"、一句写可以随时跳过，用户不知道该信哪句。
 */
fun gateHeadline(state: UpdateUiState): String =
    if (gateIsForced(state)) "需要更新到新版本" else "发现新版本"

/**
 * 进度条的三种真相，输入只有 `done` 与 `total` 这两个可空字段：
 *
 * 1. `done == null`：一条进度都没收到 ⇒ 不确定态，而且**不许写任何"已下载"字样**；
 * 2. `done != null && total == null`：清单没声明大小 ⇒ 仍然不确定态，只报已下载字节；
 * 3. 两者都有 ⇒ 画 `done / total` 的真实比例。
 *
 * 为什么第 1 条要单独分出来：VM 进 Downloading 的第一帧就是 done=null，
 * 而计划原本在这里写的是 `sizeText(done ?: 0L, total)` —— 那会渲染成
 * "0.0 MB / 23.0 MB"，是同一根 0% 死条的文字版。
 */
fun gateDownloadView(state: UpdateUiState): DownloadView? = when (state) {
    is UpdateUiState.Downloading -> DownloadView(
        ratio = state.done?.let { UpdateCopy.progressOf(it, state.total) } ?: PROGRESS_INDETERMINATE,
        label = state.done?.let { UpdateCopy.sizeText(it, state.total) }
            ?: UpdateCopy.totalText(state.total)
    )

    else -> null
}

/**
 * 摆哪几颗按钮。三条规矩都各有一条用例钉着：
 * 门禁那一档永远不给 Later；下载中不给"立即更新"（VM 里有 downloadJob 防重入，
 * 摆出来是颗空按钮）；非强制的失败只给重试，因为那一档本来就不按住整页。
 *
 * ## 为什么 `hasUrl` 是参数而不是调用方的 `.filterNot`
 * 评审抓到的正是这个错位：这一层曾经只管"按状态该有哪几颗"，
 * 而"没有地址就别摆复制链接"那半条过滤写在 `UpdateGateLayer` 里。
 * 于是**用例断言的列表和 UI 画的列表是两个东西** ——
 * `apkUrl` 为空且处于 `Downloading(forced=true)` 时，遮罩按住整页而按钮一颗不剩
 * （那一档本来就只有「复制下载链接」这一颗）。把过滤收回参数里，
 * 那条 sweep 用例跑的就是画出来的那一份，漂移不再可能。
 */
fun gateActions(state: UpdateUiState, hasUrl: Boolean): List<GateAction> = when (state) {
    is UpdateUiState.Gate -> listOf(GateAction.Update)
    is UpdateUiState.OptionalCard -> listOf(GateAction.Update, GateAction.Later)

    // 门禁下载中：唯一给的是复制链接 —— 24MB 的路上不能一个出口都没有，
    // 而复制链接不关闭任何东西，所以它不违反"门禁没有关闭入口"。
    // 没有地址时一颗都不给：那种情况下 blocksUser 也必须跟着放开（见那个函数）。
    is UpdateUiState.Downloading -> when {
        !state.forced -> emptyList()
        hasUrl -> listOf(GateAction.CopyLink)
        else -> emptyList()
    }

    is UpdateUiState.Failed -> when {
        !state.forced -> listOf(GateAction.Retry)
        hasUrl -> listOf(GateAction.Retry, GateAction.CopyLink)
        else -> listOf(GateAction.Retry)
    }

    UpdateUiState.Hidden -> emptyList()
}

/** 进度与容量文案。单独成对象是因为它有一堆"最坏负载"要测：24MB 的包、无 sizeBytes 的清单。 */
object UpdateCopy {

    private const val MB = 1024.0 * 1024.0

    /**
     * 已下 / 总量。清单没声明 sizeBytes（或声明成了 0）时返回 [PROGRESS_INDETERMINATE]，
     * 而不是 0f —— 返回 0 会被画成一根"0% 且永远不动"的条，比没有进度条更让人以为卡死。
     */
    fun progressOf(done: Long, total: Long?): Float {
        if (total == null || total <= 0L) return PROGRESS_INDETERMINATE
        return (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * "11.4 MB / 23.0 MB"；没有 total 时只说已下多少，绝不编一个百分比出来。
     *
     * `total <= 0` 与 `total == null` 走同一句话（评审 Minor）：清单把 sizeBytes 写成 0
     * 也是一种"不知道总量"，而 [progressOf] 早就把它当不知道（返回 -1 走不确定态）。
     * 两边不一致的后果是那根条不涨、文字却印"11.4 MB / 0.0 MB" —— 一句话自己否认自己，
     * 用户只会认为程序卡死。
     */
    fun sizeText(done: Long, total: Long?): String = if (total == null || total <= 0L) {
        if (done <= 0L) "" else "已下载 ${mb(done)}"
    } else {
        "${mb(done)} / ${mb(total)}"
    }

    /** 一条进度都没收到时唯一能说的关于大小的话 —— 总量是清单声明的，不是"已经下了 0" */
    fun totalText(total: Long?): String = if (total == null || total <= 0L) "" else "共 ${mb(total)}"

    private fun mb(bytes: Long): String {
        val value = bytes / MB
        // 不用 String.format（commonMain 里没有这个），手动留一位小数并补零
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
