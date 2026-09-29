package com.jianyi.outfit.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.jianyi.outfit.data.LocalAppDependencies
import com.jianyi.outfit.ui.glass.GlassEmphasis
import com.jianyi.outfit.ui.glass.GlassPill
import com.jianyi.outfit.ui.glass.GlassShapes
import com.jianyi.outfit.ui.glass.GlassSurface
import com.jianyi.outfit.ui.theme.LocalScenery
import kotlinx.coroutines.launch

/**
 * 更新门禁 / 可跳过提示那一层。挂在 RootScreen 的 Box 里、`content()` **之后**，
 * 所以它盖在活动导航栈里的所有页面与弹层之上。
 *
 * **一句诚实的边界**（评审记录，别把这层的能力说满）：Compose 的 `Dialog`/`Popup` 是**独立的
 * window**，不属于本层的 composition 树，所以这一层的遮罩盖不住它们。今天这条链上没有 Dialog
 * 就够了 —— 门禁仍然在 Activity 这一层拦着返回键与点击，Dialog 挡不住的是"看得见"，
 * 不是"绕得过去"。以后若给更新流程加 Dialog，要按 window 层级重新评估这句话。
 *
 * 三条硬规矩：
 * 1. **门禁不提供任何关闭入口**。按钮集合由 [gateActions] 决定并被用例钉住；
 *    返回键拦截在 Task 8（判据复用 [gateHoldsPage]，两处不许各写一份），这一层不"顺手"加一颗关闭按钮。
 * 2. **整页都是半透明玻璃**：卡片走 `GlassSurface`、按钮走现成的 `GlassPill`。
 *    仓库里没有 `GlassButton`，`GlassShapes` 里也没有 `pill` 那一档（只有
 *    card/inner/chip/bar/sheet/circle），所以这里既不自造第二颗按钮、也不新增形状常量。
 * 3. **文案一律不写 `maxLines = 1`**。本仓库 2026-09-25 刚踩过：首页降级条把
 *    "点此改用 GPS"这句唯一的动作提示裁成了省略号，无障碍树里文本是完整的、只有裁图看得出。
 *    窄屏 + 长中文失败原因宁可折行，所以这一层一个 maxLines 都没有；
 *    长度真不受控的只有作者写的更新说明，那一块改成可滚动而不是裁掉。
 */
@Composable
fun UpdateGateLayer(vm: UpdateViewModel) {
    val state by vm.state.collectAsState()
    val deps = LocalAppDependencies.current
    val clipboard = LocalClipboardManager.current
    val uiScope = rememberCoroutineScope()
    val dark = LocalScenery.current.dark || isSystemInDarkTheme()
    val barrierSource = remember { MutableInteractionSource() }

    /**
     * 冷启动检查由这里发起，而不是容器构造时、Application 里、或某个页面的 LaunchedEffect。
     *
     * 全计划原本没有任何一处调用 `checkOnce()`：VM 造好、接缝接上、测试全绿，
     * 真机上门禁**永远不出现**，因为唯一会触发它的东西是设置页那个手动按钮。
     * 挂在这一层的理由是它恰好只在 RootScreen 挂载期间存在一次，
     * 天然就是"每次冷启动一次"的作用域；`checkOnce` 自身幂等（checkedThisLaunch），
     * 下面那次 early return 和此后每次重组都不会再打一遍网络。
     */
    LaunchedEffect(Unit) { vm.checkOnce() }

    val manifest = gateManifest(state) ?: return
    val url = vm.apkUrl()
    // 没有地址就不摆"复制链接"：按下去复制出一个空串，等于把用户丢进浏览器里撞 404。
    // 这条过滤**必须在纯函数里**（gateActions 的 hasUrl 参数）而不是在这里 .filterNot：
    // 那样用例断言的列表和这里画的列表是两份，"按住整页 + 零按钮"就藏在接缝里。
    val hasUrl = url != null
    val actions = gateActions(state, hasUrl)
    val holdsPage = gateHoldsPage(state, hasUrl)
    val download = gateDownloadView(state)

    Box(
        modifier = if (holdsPage) {
            // 门禁必须真的吃掉点击。一层只有颜色、没有点击处理的遮罩只是装饰：
            // 用户可以透过它点到底下的页面继续用旧版 —— 那正是门禁要防的那件事。
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = GATE_SCRIM_ALPHA))
                .clickable(interactionSource = barrierSource, indication = null, onClick = {})
        } else {
            // 可跳过那一档刻意不挡页面：卡片浮着，不理会也能继续用。
            // 这同时是"非强制的下载失败只给重试、不给按下去没反应的以后再说"的前提。
            Modifier.fillMaxSize()
        },
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp),
            shape = GlassShapes.card,
            // 整页最上面的一块玻璃，取现成档位里最厚的一档：它要压在活动页面之上，
            // 还要保证那几句中文在任何风景下都读得清。不自己发明数值。
            emphasis = GlassEmphasis.THICK,
            dark = dark,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(22.dp)
        ) {
            Text(
                text = gateHeadline(state),
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
                // 更新说明是作者写的、长度不受控，所以让它自己滚，而不是裁掉或把卡片撑破：
                // 卡片被顶出屏幕的后果是按钮看不见，那比少读两行说明严重得多
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .heightIn(max = NOTES_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState())
                )
            }

            when (val s = state) {
                is UpdateUiState.Downloading -> DownloadBody(download)

                is UpdateUiState.Failed -> Text(
                    text = UpdateCopy.failureText(s.reason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )

                is UpdateUiState.Gate -> Text(
                    text = "不更新将无法继续使用，以免旧版本读到错误数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> Unit
            }

            if (actions.isNotEmpty()) {
                ActionRow(actions = actions, dark = dark) { action ->
                    when (action) {
                        // 下载与重试是同一条路：VM 里有 downloadJob 防重入，双击不会并发起两条
                        GateAction.Update, GateAction.Retry -> vm.startDownload()

                        // 这颗按钮只会出现在可跳过那一档（见 gateActions 与那条 sweep 用例）
                        GateAction.Later -> vm.dismissOptional()

                        GateAction.CopyLink -> {
                            val link = url ?: return@ActionRow
                            // CMP 1.8 的剪贴板写入是 suspend（iOS 那份实现必须是异步的），
                            // 所以起协程；写成 runBlocking 会把整条重组链挂住
                            uiScope.launch { clipboard.setText(AnnotatedString(link)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 进度那一块。读的唯一来源是 [gateDownloadView] —— 也就是 `state.done` 与 `state.total`。
 *
 * Task 5 那边有用例保证"下载器发了 Progress 就一定会落到状态上"，这一层负责
 * "落到状态上就一定会画出来"：ratio 为负走不确定态，永不画 0% 死条。
 */
@Composable
private fun ColumnScope.DownloadBody(view: DownloadView?) {
    if (view == null) return
    if (view.ratio >= 0f) {
        LinearProgressIndicator(
            progress = { view.ratio },
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    if (view.label.isNotEmpty()) {
        Text(
            text = view.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 按钮行。摆哪几颗、什么顺序全部来自 gateActions，这里一个字都不判 */
@Composable
private fun ColumnScope.ActionRow(
    actions: List<GateAction>,
    dark: Boolean,
    onAction: (GateAction) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (action in actions) {
            GlassPill(
                label = action.label,
                dark = dark,
                // 正向动作染成 primary：门禁那一档只有一颗能点，
                // 别让用户在两颗同色玻璃里猜哪颗才是"更新"
                contentColor = if (action == GateAction.Update || action == GateAction.Retry) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                // fill = false：宽度只被等分上限夹住，字多就自己折行，
                // 既不裁字也不会把三颗胶囊挤出卡片
                modifier = Modifier.weight(1f, fill = false),
                onClick = { onAction(action) }
            )
        }
    }
}

/**
 * 门禁遮罩的不透明度。
 *
 * 计划定的 0.55f：低到还能看出背后有内容（不然就是一块不透的白板子，那是这个项目的丑），
 * 高到让那一页的按钮看起来不像还能点。
 */
private const val GATE_SCRIM_ALPHA = 0.55f

/** 更新说明最多占这么高，超了自己滚 —— 大致是窄屏上四行 bodySmall，不是随手挑的 */
private val NOTES_MAX_HEIGHT = 96.dp
