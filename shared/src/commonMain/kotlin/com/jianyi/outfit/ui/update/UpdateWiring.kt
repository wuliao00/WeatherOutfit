package com.jianyi.outfit.ui.update

import com.jianyi.outfit.data.AppUpdateGateway
import com.jianyi.outfit.data.AppVersion
import com.jianyi.outfit.data.update.UpdateChecker
import com.jianyi.outfit.data.update.UpdateManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * iOS 用不到下载（gateway.supported 恒 false，门禁与卡片永远不显示），
 * 但构造 VM 必须给一个 ApkInstaller —— 给一个明确失败的实现，而不是可空参数：
 * 可空会让"忘了传"变成运行时 NPE，这个则直接说出原因。
 *
 * **Android 侧在 Task 7 落地之前也用它**，表现是点"立即更新"立刻进失败态、
 * 文案是"写入失败，请清理手机存储后重试"。这条替代关系是刻意的：
 * 与其让门禁在包还没下载能力的时候就能被点，不如让它明确报"下不了"。
 */
object UnsupportedInstaller : ApkInstaller {
    override fun download(manifest: UpdateManifest): Flow<DownloadEvent> =
        flowOf(DownloadEvent.Failure(DownloadFailure.Io))
}

/**
 * 两端共享的构造点。
 *
 * 为什么不各容器各 new 一次 UpdateViewModel：那个构造有 5 个位置参数，
 * 其中三个类型都是接口（checker / installer / gateway），换一下顺序编译器一声不响。
 * 参数顺序漂移在这条链上的症状不是编译失败，是"门禁按另一套判定在拦人"这种
 * 只能靠真机复现的问题，所以把它收成一处。
 *
 * 第 6 个参数 `logWarning` 不在这里透传：默认值就是平台那条真实的告警通道，
 * 只有测试会换掉它。
 */
fun newUpdateViewModel(
    appVersion: AppVersion,
    checker: UpdateChecker,
    installer: ApkInstaller,
    gateway: AppUpdateGateway,
    scope: CoroutineScope
) = UpdateViewModel(
    appVersion = appVersion,
    checker = checker,
    installer = installer,
    gateway = gateway,
    scope = scope
)
