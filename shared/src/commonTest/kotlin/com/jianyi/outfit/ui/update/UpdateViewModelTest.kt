package com.jianyi.outfit.ui.update

import com.jianyi.outfit.data.AppUpdateGateway
import com.jianyi.outfit.data.AppVersion
import com.jianyi.outfit.data.InstallResult
import com.jianyi.outfit.data.update.UpdateChecker
import com.jianyi.outfit.data.update.UpdateDecision
import com.jianyi.outfit.data.update.UpdateManifest
import com.jianyi.outfit.data.update.UpdateVerdict
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * UpdateViewModel 状态机的用例。
 *
 * 全部走注入的假实现（UpdateChecker / ApkInstaller / AppUpdateGateway），
 * 一个平台符号都不碰：真机上一次就装上了，状态机里"失败→重试""拒绝授权"
 * 那几条分支反而永远走不到，而它们恰恰是最不能靠运气验证的几段。
 */
private val MANIFEST = UpdateManifest(
    versionCode = 9,
    versionName = "2.2.0",
    minSupportedCode = 9,
    apkUrl = "https://gitee.com/x.apk"
)

/** 带 sizeBytes 的那份：Downloading 的进度条要有个确定的 total，不能是 null */
private val BIG_MANIFEST = MANIFEST.copy(sizeBytes = 24_115_200L)

private const val APK_PATH = "/data/user/0/com.jianyi.outfit/files/jianyi-2.2.0.apk"

/**
 * 假的是 UpdateChecker 接缝，不是 UpdateRepository —— 后者的 check 是 final，
 * 而且它会真的去发网络请求。
 *
 * `seenVersionCode` 只为了一个断言：VM 交给判定层的是**注入进来的**装机 versionCode。
 * VM 里不许有任何版本比较（那条规矩见 UpdateDecision.kt），所以"传对了吗"只能在这儿锁。
 */
private class FakeChecker(private val decision: UpdateDecision) : UpdateChecker {
    var calls = 0
    var seenVersionCode: Int? = null

    override suspend fun check(currentVersionCode: Int): UpdateDecision {
        calls++
        seenVersionCode = currentVersionCode
        return decision
    }
}

private class FakeInstaller(
    private val outcome: DownloadEvent = DownloadEvent.Success(APK_PATH)
) : ApkInstaller {
    var downloads = 0
    var seenManifest: UpdateManifest? = null

    override fun download(manifest: UpdateManifest): Flow<DownloadEvent> {
        downloads++
        seenManifest = manifest
        return flowOf(outcome)
    }
}

/**
 * 一直不发射的下载器，用来观察 **Downloading 这一帧**：
 * FakeInstaller 的 flowOf 瞬间就走完了，Downloading 状态永远看不到第二眼。
 *
 * 用完必须在用例结尾把 outcome 完成掉 —— 否则那个 collect 会挂着，
 * runTest 收尾时留下未完成的协程。
 */
private class PendingInstaller : ApkInstaller {
    val outcome = CompletableDeferred<DownloadEvent>()
    var downloads = 0

    override fun download(manifest: UpdateManifest): Flow<DownloadEvent> {
        downloads++
        return flow { emit(outcome.await()) }
    }
}

/**
 * 会发进度事件的下载器：`Progress` 是这条链上唯一能让进度条动起来的东西。
 *
 * 存在的理由是一段真实的接口缺陷：`ApkInstaller` 原本只有 `Success | Failure` 两个终态，
 * VM 之后再也没有任何东西给 `Downloading.done` 赋非 null 值 —— 于是 24MB 的下载里
 * 进度条从 0% 一路死到装完，而测 `progressOf`/`sizeText` 算术的 UpdateCopyTest 照样全绿。
 * 这三条用例（下面三条）就是把"没人发 Progress"和"发了没人显示"这两半钉住。
 */
private class ProgressInstaller(
    private val events: List<DownloadEvent>
) : ApkInstaller {
    var downloads = 0

    override fun download(manifest: UpdateManifest): Flow<DownloadEvent> {
        downloads++
        return flowOf(*events.toTypedArray())
    }
}

/**
 * 按脚本给结论的检查器：一条用例里要模拟"冷启动查到门禁，之后手动检查恰好断网"。
 * 脚本用完之后一律按 Unreachable 处理（等价于"再查也拿不到清单"）。
 */
private class ScriptedChecker(private val decisions: List<UpdateDecision>) : UpdateChecker {
    var calls = 0

    override suspend fun check(currentVersionCode: Int): UpdateDecision =
        decisions.getOrElse(calls) { UpdateDecision(UpdateVerdict.Unreachable, null) }.also { calls++ }
}

private class FakeGateway(
    override val supported: Boolean = true,
    private val installResult: InstallResult = InstallResult.Launched
) : AppUpdateGateway {
    var permissionRequests = 0
    var installedPath: String? = null

    override fun hasInstallPermission(): Boolean = installResult != InstallResult.PermissionMissing

    override fun requestInstallPermission() {
        permissionRequests++
    }

    override fun install(apkPath: String): InstallResult {
        installedPath = apkPath
        return installResult
    }
}

/** 一次冷启动：构造 VM、跑完 checkOnce()，返回停在最终状态的 VM */
private suspend fun checkedVm(
    scope: TestScope,
    verdict: UpdateVerdict,
    installer: ApkInstaller = FakeInstaller(),
    gateway: AppUpdateGateway = FakeGateway(),
    manifest: UpdateManifest? = if (verdict == UpdateVerdict.Unreachable) null else MANIFEST
): UpdateViewModel = UpdateViewModel(
    appVersion = AppVersion(8, "2.1.2"),
    checker = FakeChecker(UpdateDecision(verdict, manifest)),
    installer = installer,
    gateway = gateway,
    scope = scope
).also {
    it.checkOnce()
    scope.advanceUntilIdle()
}

class UpdateViewModelTest {

    /** 没查之前不许谎报结论：设置页的副标题分「还没查过」和「查过、已是最新」两句话 */
    @Test fun nothing_is_claimed_before_the_first_check() = runTest {
        // 判定层的结论刻意是 UpToDate：VM 一次都没问，UI 就不该已经知道它
        val checker = FakeChecker(UpdateDecision(UpdateVerdict.UpToDate, MANIFEST))
        val vm = UpdateViewModel(AppVersion(8, "2.1.2"), checker, FakeInstaller(), FakeGateway(), this)
        assertEquals(0, checker.calls)
        assertNull(vm.lastVerdict.value, "没查过就不能报结论：设置页那句副标题要分「还没查过」和「已是最新」")
        assertIs<UpdateUiState.Hidden>(vm.state.value)
    }

    @Test fun hidden_when_up_to_date() = runTest {
        val vm = checkedVm(this, UpdateVerdict.UpToDate)
        assertIs<UpdateUiState.Hidden>(vm.state.value)
        assertEquals(UpdateVerdict.UpToDate, vm.lastVerdict.value)
    }

    /**
     * 拉不到清单 ⇒ 什么都不显示，而且**连下载都不许发起**。
     * apkUrl() 这时候必须是 null：Task 6 的"复制下载链接"逃生口没地址就该整行不出现，
     * 而不是复制出一个空串让用户去浏览器里撞 404。
     */
    @Test fun unreachable_stays_hidden_and_starts_nothing() = runTest {
        val installer = FakeInstaller()
        val vm = checkedVm(this, UpdateVerdict.Unreachable, installer)
        assertIs<UpdateUiState.Hidden>(vm.state.value)
        vm.startDownload()
        assertEquals(0, installer.downloads, "没有清单就没有包，不该发任何下载请求")
        assertNull(vm.apkUrl())
    }

    @Test fun forced_becomes_gate() = runTest {
        val vm = checkedVm(this, UpdateVerdict.Forced)
        val s = assertIs<UpdateUiState.Gate>(vm.state.value)
        assertEquals(9, s.manifest.versionCode)
    }

    @Test fun optional_becomes_dismissible_card() = runTest {
        val vm = checkedVm(this, UpdateVerdict.Optional)
        assertIs<UpdateUiState.OptionalCard>(vm.state.value)
        vm.dismissOptional()
        assertIs<UpdateUiState.Hidden>(vm.state.value)
    }

    /**
     * 门禁下"跳过"必须是空操作。这条存在的理由：dismiss 逻辑一旦写错，
     * 用户就能绕过破坏性版本继续用旧版 —— 强更直接失效，而且不会有任何报错。
     */
    @Test fun gate_cannot_be_dismissed() = runTest {
        val vm = checkedVm(this, UpdateVerdict.Forced)
        vm.dismissOptional()
        assertIs<UpdateUiState.Gate>(vm.state.value)
    }

    /**
     * 平台不支持 ⇒ **一次网络都不打**（评审 Important「iOS 白打一次网络」）。
     *
     * 这一条替代了原来那句"仍然发布结论"：过去 `runCheck` 先 `checker.check`（GET +
     * 可能的 HEAD）**才**判 `!gateway.supported`，于是 iPhone 每次冷启动都真打一次 Gitee，
     * 而那一端永远不显示任何更新入口。把 supported 判断前移之后，唯一"损失"是
     * `lastVerdict` 在 iOS 上保持 null —— 而那才是诚实的取值（它确实没查过），
     * 而它唯一的读者（Task 9 设置页那一行）本来就该被 `supported` 挡在门外。
     * 这条断言比原来强：0 次调用是原来那条用例看不见的。
     */
    @Test fun an_unsupported_platform_never_touches_the_network() = runTest {
        val checker = FakeChecker(UpdateDecision(UpdateVerdict.Forced, MANIFEST))
        val installer = FakeInstaller()
        val vm = UpdateViewModel(
            appVersion = AppVersion(8, "2.1.2"),
            checker = checker,
            installer = installer,
            gateway = FakeGateway(supported = false),
            scope = this
        )
        vm.checkOnce()
        advanceUntilIdle()
        assertEquals(0, checker.calls, "iOS 上没有入口，这次清单请求是白打的网络流量")
        assertIs<UpdateUiState.Hidden>(vm.state.value)
        assertNull(vm.lastVerdict.value, "没查过就不许报结论")
        vm.startDownload()
        assertEquals(0, installer.downloads, "不支持的平台上连下载都不该有能力开始")
    }

    /**
     * 门禁不许被一次「检查更新」解除（评审 Important）。
     *
     * 症状原本很具体：门禁挂着时点设置页的「检查更新」，恰好断网 ⇒ Unreachable ⇒ Hidden ⇒
     * 用户随手一点就把这个功能存在的全部意义绕过去了。修法是把 `forcedThisLaunch` 做成
     * 真正的粘滞：只进不出，检查失败最多让卡片停在门禁那一套上。
     *
     * 这一条与"Failed 不吞返回键"（Task 8）**不矛盾**：这里禁的是"检查失败后卡片整张消失"，
     * 不是"下载失败之后不许退出"。放人走的那一半仍然成立，见
     * `failed_releases_back_so_the_user_can_leave` 与 `a_failed_download_stops_holding_the_page`。
     */
    @Test fun a_manual_check_that_cannot_reach_the_manifest_cannot_lift_the_gate() = runTest {
        val vm = UpdateViewModel(
            appVersion = AppVersion(8, "2.1.2"),
            checker = ScriptedChecker(
                listOf(
                    UpdateDecision(UpdateVerdict.Forced, MANIFEST),
                    UpdateDecision(UpdateVerdict.Unreachable, null)
                )
            ),
            installer = FakeInstaller(),
            gateway = FakeGateway(),
            scope = this
        )
        vm.checkOnce()
        advanceUntilIdle()
        assertIs<UpdateUiState.Gate>(vm.state.value)

        vm.forceCheck()
        advanceUntilIdle()

        val s = assertIs<UpdateUiState.Gate>(vm.state.value, "一次断网的检查不许解除门禁")
        assertEquals(MANIFEST, s.manifest, "重新挂上的门禁必须还是原来那份清单（apkUrl 不能丢）")
        assertEquals(UpdateVerdict.Unreachable, vm.lastVerdict.value, "结论照实发布，只是不解除门禁")
        // 粘滞的另一半：之后每个状态都还得记得自己是门禁（Task 8 的返回键读这个字段）
        vm.startDownload()
        advanceUntilIdle()
        assertIs<UpdateUiState.Hidden>(vm.state.value, "FakeInstaller 的成功终态会把状态推到 Hidden（装完了）")
    }

    /**
     * 同一条粘滞也要覆盖"下载失败之后再手动检查"：那一态的 `_state` 不是 Gate，
     * 但这次冷启动已经挂过门禁，所以 Hidden 同样不许成立。
     *
     * 少了这一半的话，绕过口只是换了个位置：先在门禁上点下载、让它失败，再去点「检查更新」。
     */
    @Test fun a_failed_download_still_cannot_be_reset_to_hidden_by_a_manual_check() = runTest {
        val vm = UpdateViewModel(
            appVersion = AppVersion(8, "2.1.2"),
            checker = ScriptedChecker(
                listOf(
                    UpdateDecision(UpdateVerdict.Forced, MANIFEST),
                    UpdateDecision(UpdateVerdict.Unreachable, null)
                )
            ),
            installer = FakeInstaller(DownloadEvent.Failure(DownloadFailure.Network)),
            gateway = FakeGateway(),
            scope = this
        )
        vm.checkOnce()
        advanceUntilIdle()
        vm.startDownload()
        advanceUntilIdle()
        val failed = assertIs<UpdateUiState.Failed>(vm.state.value)
        assertTrue(failed.forced, "门禁下的失败要记得自己是门禁")
        // 失败态放开页面（gateHoldsPage 在 Failed 为 false）是刻意的：下不下来必须能走。
        assertFalse(gateHoldsPage(failed))

        vm.forceCheck()
        advanceUntilIdle()

        assertIs<UpdateUiState.Gate>(vm.state.value, "失败之后一次断网的检查不许把门禁整张抹掉")
    }

    /**
     * 判定说要拦、却没给清单（第三方/未来 checker 违约）。
     * 选 fail-open（保持 Hidden）而不是 `!!`：冷启动路径上崩掉比不拦更糟，
     * 而整个功能的规矩本来就是"任何失败都不许把用户锁死"。
     */
    @Test fun an_incomplete_decision_fails_open_instead_of_crashing() = runTest {
        val vm = checkedVm(this, UpdateVerdict.Forced, manifest = null)
        assertIs<UpdateUiState.Hidden>(vm.state.value)
        assertEquals(UpdateVerdict.Forced, vm.lastVerdict.value)
    }

    @Test fun start_download_installs_on_success() = runTest {
        val installer = FakeInstaller()
        val gateway = FakeGateway()
        val vm = checkedVm(this, UpdateVerdict.Forced, installer, gateway)
        vm.startDownload()
        advanceUntilIdle()
        assertEquals(1, installer.downloads)
        assertEquals(MANIFEST, installer.seenManifest, "下的必须是这次判定给出的那份清单")
        assertEquals(APK_PATH, gateway.installedPath)
    }

    /** 装成功后状态必须回到 Hidden：门禁卡片继续挂着就是"装完了还拦着" */
    @Test fun launched_install_hides_the_gate() = runTest {
        val vm = checkedVm(this, UpdateVerdict.Forced)
        vm.startDownload()
        advanceUntilIdle()
        assertIs<UpdateUiState.Hidden>(vm.state.value)
    }

    /**
     * 权限没给：**门禁不许退成可跳过卡片**。
     *
     * 退成的话，症状就是"只要拒绝授权，强更自动变成可跳过"——
     * 门禁被它要防的那个动作（拒绝授权）本身绕过，而且绕过后看起来一切正常。
     * 留在 Gate 上，用户从系统设置里授权回来还能接着装。
     */
    @Test fun permission_missing_keeps_the_gate_and_opens_settings() = runTest {
        val gateway = FakeGateway(installResult = InstallResult.PermissionMissing)
        val vm = checkedVm(this, UpdateVerdict.Forced, gateway = gateway)
        vm.startDownload()
        advanceUntilIdle()
        val s = assertIs<UpdateUiState.Gate>(vm.state.value)
        assertEquals(MANIFEST, s.manifest)
        assertEquals(1, gateway.permissionRequests, "没授权就要把用户送到系统设置页，不然门禁上只有一个装不上的包")
    }

    /** 非强制那一档才可以退成可跳过卡片：它本来就可跳过，退回去让用户一键继续 */
    @Test fun permission_missing_downgrades_only_a_non_forced_card() = runTest {
        val gateway = FakeGateway(installResult = InstallResult.PermissionMissing)
        val vm = checkedVm(this, UpdateVerdict.Optional, gateway = gateway)
        vm.startDownload()
        advanceUntilIdle()
        assertIs<UpdateUiState.OptionalCard>(vm.state.value)
        assertEquals(1, gateway.permissionRequests)
    }

    /** 弹不出安装页（FileProvider 路径不对、URI 被拒）也是失败，不是"没事发生" */
    @Test fun install_failure_shows_a_reason_and_keeps_the_gate() = runTest {
        val gateway = FakeGateway(installResult = InstallResult.Failed)
        val vm = checkedVm(this, UpdateVerdict.Forced, gateway = gateway)
        vm.startDownload()
        advanceUntilIdle()
        val s = assertIs<UpdateUiState.Failed>(vm.state.value)
        assertEquals(DownloadFailure.Io, s.reason)
        assertTrue(s.forced, "门禁下弹不出安装页也得记得自己是门禁")
        assertEquals(0, gateway.permissionRequests, "弹失败不是没权限，不该把用户丢进系统设置页")
    }

    @Test fun download_failure_exposes_reason_and_keeps_gate_context() = runTest {
        val vm = checkedVm(
            this,
            UpdateVerdict.Forced,
            FakeInstaller(DownloadEvent.Failure(DownloadFailure.ChecksumMismatch))
        )
        vm.startDownload()
        advanceUntilIdle()
        val s = assertIs<UpdateUiState.Failed>(vm.state.value)
        assertEquals(DownloadFailure.ChecksumMismatch, s.reason)
        assertTrue(s.forced, "门禁下的失败必须记得自己是门禁")
        // 失败态是"复制下载链接"逃生口的唯一用处，地址必须还在
        assertEquals("https://gitee.com/x.apk", vm.apkUrl())
    }

    @Test fun retry_reissues_download() = runTest {
        val installer = FakeInstaller(DownloadEvent.Failure(DownloadFailure.Network))
        val vm = checkedVm(this, UpdateVerdict.Forced, installer)
        vm.startDownload()
        advanceUntilIdle()
        vm.retry()
        advanceUntilIdle()
        assertEquals(2, installer.downloads)
    }

    /**
     * Downloading 必须带上 forced —— Task 8 的返回键拦截读的就是这个字段。
     * 漏传的后果不是难看，是**门禁在下载中途被返回键绕过**。
     */
    @Test fun downloading_under_a_gate_is_marked_forced() = runTest {
        val installer = PendingInstaller()
        val vm = checkedVm(this, UpdateVerdict.Forced, installer, manifest = BIG_MANIFEST)
        vm.startDownload()
        val s = assertIs<UpdateUiState.Downloading>(vm.state.value)
        assertTrue(s.forced, "门禁下点下载之后，这一帧仍然必须是门禁那一套")
        assertNull(s.done, "还没有进度就该是不确定态，而不是 0 字节")
        assertEquals(24_115_200L, s.total)
        assertEquals(BIG_MANIFEST, s.manifest)
        // 收尾：让挂起的那次下载落地，顺带确认它真能走出 Downloading
        installer.outcome.complete(DownloadEvent.Success(APK_PATH))
        advanceUntilIdle()
        assertIs<UpdateUiState.Hidden>(vm.state.value)
    }

    /** 反面对照：可跳过那一档下载中不许被误标成门禁（否则返回键把用户困在一张可跳过的卡片上） */
    @Test fun downloading_from_an_optional_card_is_not_forced() = runTest {
        val installer = PendingInstaller()
        val vm = checkedVm(this, UpdateVerdict.Optional, installer)
        vm.startDownload()
        val s = assertIs<UpdateUiState.Downloading>(vm.state.value)
        assertFalse(s.forced, "非强制的下载中途不该拦返回键")
        installer.outcome.complete(DownloadEvent.Success(APK_PATH))
        advanceUntilIdle()
    }

    /**
     * 下载进行中再点一次（双击、或者重组前连点）不许并发起第二条下载：
     * 两个写入者落同一个 apk 路径 ⇒ 包被写坏（表现为"校验不通过"，查不到原因），
     * 或者系统安装页被连弹两次。失败结束之后 retry 仍要真的重下。
     */
    @Test fun a_download_in_flight_is_not_started_twice_but_retry_after_failure_is() = runTest {
        val installer = PendingInstaller()
        val vm = checkedVm(this, UpdateVerdict.Forced, installer)
        vm.startDownload()
        // 先把那次下载真的跑起来（测试调度器不会自己走），才谈"进行中"
        advanceUntilIdle()
        assertEquals(1, installer.downloads, "第一次点击必须已经下单")
        vm.startDownload()
        vm.startDownload()
        vm.retry()
        assertEquals(1, installer.downloads, "还在下的时候再点必须是空操作")
        installer.outcome.complete(DownloadEvent.Failure(DownloadFailure.Network))
        advanceUntilIdle()
        assertIs<UpdateUiState.Failed>(vm.state.value)
        vm.retry()
        advanceUntilIdle()
        assertEquals(2, installer.downloads, "失败结束后 retry 必须真的重新下单")
    }

    /** 冷启动只查一次（幂等，UI 重组不会打网络）；手动检查走 forceCheck() 明确复位 */
    @Test fun check_once_is_idempotent_until_force_check() = runTest {
        val checker = FakeChecker(UpdateDecision(UpdateVerdict.UpToDate, MANIFEST))
        val vm = UpdateViewModel(AppVersion(8, "2.1.2"), checker, FakeInstaller(), FakeGateway(), this)
        vm.checkOnce()
        vm.checkOnce()
        advanceUntilIdle()
        assertEquals(1, checker.calls, "checkOnce 必须幂等：门禁层挂在 RootScreen 上，每次重组都会再调一次")
        assertEquals(8, checker.seenVersionCode, "交给判定层的必须是注入的装机 versionCode")
        vm.forceCheck()
        advanceUntilIdle()
        assertEquals(2, checker.calls)
    }

    /**
     * 关掉可跳过卡片后，同一冷启动内重复 checkOnce 不该再弹。
     * （设置页的 LaunchedEffect 与任何重组都可能再调一次；点了"跳过"就该安安静静的。）
     */
    @Test fun dismissed_optional_stays_dismissed_within_the_same_launch() = runTest {
        val vm = checkedVm(this, UpdateVerdict.Optional)
        vm.dismissOptional()
        vm.checkOnce()
        advanceUntilIdle()
        assertIs<UpdateUiState.Hidden>(vm.state.value)
    }

    /**
     * 手动「检查更新」是用户的第二个意图，它必须能重新弹出刚被关掉的卡片。
     *
     * 不复位 optionalDismissed 才是真正的缺陷形状：用户点掉卡片之后，
     * 设置页里那个按钮按多少次都毫无反应，而且看起来"已经检查过了"。
     */
    @Test fun force_check_reshows_a_dismissed_optional() = runTest {
        val vm = checkedVm(this, UpdateVerdict.Optional)
        vm.dismissOptional()
        assertIs<UpdateUiState.Hidden>(vm.state.value)
        vm.forceCheck()
        advanceUntilIdle()
        assertIs<UpdateUiState.OptionalCard>(vm.state.value)
    }

    /** forceCheck 也不该把门禁"复位"成可跳过：它只是再查一次 */
    @Test fun force_check_keeps_a_gate_a_gate() = runTest {
        val vm = checkedVm(this, UpdateVerdict.Forced)
        vm.forceCheck()
        advanceUntilIdle()
        assertIs<UpdateUiState.Gate>(vm.state.value)
    }

    /**
     * Progress 事件必须真的落到 `Downloading.done` 上。
     *
     * 这条锁的是整条链上最容易"各自都没错、拼起来是死的"的一环：
     * `ApkInstaller` 原本只有 `Success | Failure` 两个终态，VM 之后再也没有任何东西给
     * `done` 赋非 null 值 —— 于是 24MB 的下载里进度条从 0% 一路死到装完，
     * 而 `UpdateCopyTest` 测的 `progressOf`/`sizeText` 算术全都对、VM 测的终态跳转也全都对。
     * 只有"发 Progress"和"显示 Progress"分开各钉一条，中间那段才不可能再断。
     */
    @Test fun progress_event_moves_the_bar() = runTest {
        val installer = ProgressInstaller(listOf(DownloadEvent.Progress(12_000_000L, 24_000_000L)))
        val vm = checkedVm(this, UpdateVerdict.Forced, installer)
        vm.startDownload()
        advanceUntilIdle()
        val s = vm.state.value
        assertIs<UpdateUiState.Downloading>(s)
        assertEquals(12_000_000L, s.done, "进度事件没落到状态上，UI 拿到的就是一根不动的条")
        assertEquals(24_000_000L, s.total)
        assertTrue(s.forced, "门禁下下载中仍然是门禁态 —— Task 8 的返回键拦截读的就是这个字段")
        assertEquals(1, installer.downloads)
    }

    /**
     * 清单没声明 `sizeBytes` 时不许编一个总数出来。
     *
     * 画一根"未知总量却从 0% 往上涨"的条，比走不确定态更让人以为卡住了 ——
     * 这条断言 `total` 保持 null，是给 Task 6 的进度条留的接口契约。
     */
    @Test fun progress_with_unknown_total_keeps_the_bar_indeterminate() = runTest {
        val noSize = UpdateManifest(9, "2.2.0", 9, "https://gitee.com/x.apk")
        val installer = ProgressInstaller(listOf(DownloadEvent.Progress(5_000_000L, null)))
        val vm = checkedVm(this, UpdateVerdict.Forced, installer, manifest = noSize)
        vm.startDownload()
        advanceUntilIdle()
        val s = vm.state.value
        assertIs<UpdateUiState.Downloading>(s)
        assertEquals(5_000_000L, s.done)
        assertNull(s.total, "清单没给 sizeBytes 就不能凭空造一个总数出来")
    }

    /**
     * 判定实现抛异常时：按"拿不到清单"处理 —— 不崩、不拦人、留一条日志。
     *
     * `UpdateChecker` 是个接口，今天的实现三条路径都自带 catch，但将来任何一个实现抛出，
     * 容器那层的 SupervisorJob 不吞它 ⇒ **冷启动路径上直接崩 App**。
     * 而"崩一次"和"这一次不提示更新"之间不需要犹豫。
     *
     * 这条用例同时是那个 catch 的红绿证明：把 try/catch 摘掉它就会红
     * （runTest 会把 scope 里未捕获的异常算成失败），所以它不是装饰。
     */
    @Test fun a_throwing_checker_fails_open_instead_of_crashing() = runTest {
        val logs = mutableListOf<String>()
        val vm = UpdateViewModel(
            appVersion = AppVersion(8, "2.1.2"),
            checker = object : UpdateChecker {
                override suspend fun check(currentVersionCode: Int): UpdateDecision =
                    throw IllegalStateException("fake checker blew up")
            },
            installer = FakeInstaller(),
            gateway = FakeGateway(),
            scope = this,
            logWarning = { logs.add(it) }
        )
        vm.checkOnce()
        advanceUntilIdle()
        assertIs<UpdateUiState.Hidden>(vm.state.value)
        assertEquals(UpdateVerdict.Unreachable, vm.lastVerdict.value)
        assertEquals(1, logs.size)
        assertTrue(logs.single().contains("fake checker blew up"), "日志要带上原因，否则等于没留痕：$logs")
    }
}
