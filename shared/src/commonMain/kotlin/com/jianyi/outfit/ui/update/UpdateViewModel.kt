package com.jianyi.outfit.ui.update

import com.jianyi.outfit.data.AppUpdateGateway
import com.jianyi.outfit.data.AppVersion
import com.jianyi.outfit.data.InstallResult
import com.jianyi.outfit.data.update.UpdateChecker
import com.jianyi.outfit.data.update.UpdateDecision
import com.jianyi.outfit.data.update.UpdateManifest
import com.jianyi.outfit.data.update.UpdateVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 下载失败的成因。分开列是因为 UI 要说的话完全不同，
 * 而且 ChecksumMismatch 是唯一一条**不该指望重试能好**的：哈希对不上说明那个包
 * 被改过或者传坏了，再来一遍只是再下一遍。
 */
enum class DownloadFailure {
    /** 网络中断 / 超时 / HTTP 非 2xx */
    Network,

    /** 下完的包哈希与清单声明不符 */
    ChecksumMismatch,

    /** 写盘失败，或者包下好了却弹不出安装页 */
    Io,

    /** 剩余空间不够装这个包 */
    NoSpace
}

/** 一次落盘下载的结果。Success 带本地 apk 的路径，直接喂给平台侧的 install */
sealed interface DownloadOutcome {
    data class Success(val path: String) : DownloadOutcome
    data class Failure(val reason: DownloadFailure) : DownloadOutcome
}

/**
 * 落盘下载的接缝：声明在这里，由 androidMain 的 ApkDownloader 实现（Task 7）。
 *
 * 抽开是为了让状态机的每一条跳转都能在普通 JVM 里用假实现跑完 —— 真机上一装就装上了，
 * "失败→重试""拒绝授权"那几条分支反而永远走不到，而它们恰恰最承重。
 *
 * 刻意**不含** launchInstall：拉起安装是平台能力，已经在
 * [com.jianyi.outfit.data.AppUpdateGateway.install] 里了，再开一个入口
 * 就会出现"两处都能装、只有一处管权限"。
 */
interface ApkInstaller {
    fun download(manifest: UpdateManifest): Flow<DownloadOutcome>
}

/**
 * 门禁层唯一可见的状态。
 *
 * Downloading 与 Failed 都带 forced：门禁下点了下载，卡片会变成 Downloading/Failed，
 * 这时候"能不能跳过、返回键要不要吞"仍然得按门禁那一套算。少这个字段，
 * Task 8 的返回键拦截就会在下载中途放行 —— 门禁被一次返回键绕过。
 */
sealed interface UpdateUiState {
    /** 什么都没有：还没查、没有更新、查失败，或者安装页已经弹出去了 */
    data object Hidden : UpdateUiState

    /** 有新版本，可以跳过 */
    data class OptionalCard(val manifest: UpdateManifest) : UpdateUiState

    /** 硬门禁：除了更新没有任何出路 */
    data class Gate(val manifest: UpdateManifest) : UpdateUiState

    /**
     * done 为 null 表示还没收到任何进度（UI 走不确定态进度条）；
     * total 为 null 表示清单没声明 sizeBytes —— 两者不是同一件事，所以是两个可空字段。
     */
    data class Downloading(
        val manifest: UpdateManifest,
        val done: Long?,
        val total: Long?,
        val forced: Boolean
    ) : UpdateUiState

    data class Failed(
        val manifest: UpdateManifest,
        val reason: DownloadFailure,
        val forced: Boolean
    ) : UpdateUiState
}

/**
 * 更新流程的状态机。
 *
 * 构造参数全部注入（不是 deps），这样每一条状态跳转都能在普通 JVM 里用假实现跑完。
 * 生命周期是**容器级单例**（挂在 AppContainer 上）而不是各页面 new 一个：
 * 门禁必须跨页面存在，而"这次冷启动已经查过 / 已经关过 / 已经是门禁"只该有一份真相。
 *
 * 本类不做任何版本号比较（那条规矩见 UpdateDecision.kt：比较只许存在于判定层一处）。
 * VM 只消费 verdict，currentVersionCode 原样交给 checker。
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
     * Hidden 既可能是"查过、没更新"，也可能是"没查过 / 查失败 / 平台不支持"，
     * 而这几种在那儿是几句完全不同的话（见 Task 9）。
     *
     * 用 StateFlow 而不是普通 var：副标题要在检查完成后**自己变**，
     * 普通 var 不触发重组，表现就是"检查完了那行字还是旧的"。
     */
    private val _lastVerdict = MutableStateFlow<UpdateVerdict?>(null)
    val lastVerdict: StateFlow<UpdateVerdict?> = _lastVerdict.asStateFlow()

    private var checkedThisLaunch = false
    private var optionalDismissed = false
    private var lastManifest: UpdateManifest? = null

    /**
     * 这次冷启动**屏幕上真的挂着门禁**吗。
     *
     * 跟着显示出来的状态走，而不是跟着 verdict 走：判定说要拦却没给清单时 UI 是 Hidden，
     * 那时装成 true 会让后面每个状态都冒充门禁（连返回键都跟着吞，用户彻底出不去）。
     */
    private var forcedThisLaunch = false

    /** 正在跑的那一次下载。存在的唯一理由：下载中再点一次不许并发起第二条 */
    private var downloadJob: Job? = null

    /** 冷启动入口：重复调用是安全的（门禁层挂在 RootScreen 上，每次重组都会再调一次） */
    fun checkOnce() {
        if (checkedThisLaunch) return
        checkedThisLaunch = true
        runCheck()
    }

    /**
     * 设置页「检查更新」用：复位"这次已查过 / 已关过"再走同一条路。
     *
     * optionalDismissed 也必须复位 —— 不复位才是真正的缺陷形状：用户点掉卡片之后，
     * 那个按钮按多少次都毫无反应，而屏幕上看起来"已经检查过了"。
     */
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
            _state.value = newStateFor(decision)
            forcedThisLaunch = _state.value is UpdateUiState.Gate
        }
    }

    private fun newStateFor(decision: UpdateDecision): UpdateUiState {
        val manifest = decision.manifest
        return when (decision.verdict) {
            UpdateVerdict.Unreachable, UpdateVerdict.UpToDate -> UpdateUiState.Hidden

            // 判定说要拦却没给清单：UI 连 apkUrl 都没有，无从拦起。
            // 这里 fail-open 而不用 !! —— 冷启动路径上崩掉比不拦更糟，
            // 而整个功能的规矩本来就是"任何失败都能退出，不许把用户锁死"。
            UpdateVerdict.Forced ->
                if (manifest == null) UpdateUiState.Hidden else UpdateUiState.Gate(manifest)

            UpdateVerdict.Optional -> when {
                optionalDismissed -> UpdateUiState.Hidden
                manifest == null -> UpdateUiState.Hidden
                else -> UpdateUiState.OptionalCard(manifest)
            }
        }
    }

    /** 只有 OptionalCard 能被关掉；Gate 调这个是空操作（见 gate_cannot_be_dismissed） */
    fun dismissOptional() {
        if (_state.value is UpdateUiState.OptionalCard) {
            optionalDismissed = true
            _state.value = UpdateUiState.Hidden
        }
    }

    fun startDownload() {
        if (downloadJob?.isActive == true) return
        val manifest = _state.value.manifestOrNull() ?: lastManifest ?: return
        // 先落状态再开下载：进度条要立刻出现，而 collect 的第一帧可能永远不来（网络卡死）
        _state.value = UpdateUiState.Downloading(
            manifest = manifest,
            done = null,
            total = manifest.sizeBytes,
            forced = forcedThisLaunch
        )
        downloadJob = scope.launch {
            installer.download(manifest).collect { outcome ->
                when (outcome) {
                    is DownloadOutcome.Success -> onDownloaded(manifest, outcome.path)
                    is DownloadOutcome.Failure ->
                        _state.value = UpdateUiState.Failed(manifest, outcome.reason, forcedThisLaunch)
                }
            }
        }
    }

    private fun onDownloaded(manifest: UpdateManifest, path: String) {
        when (gateway.install(path)) {
            // 装完了必须回到 Hidden：门禁卡片继续挂着就是"装完了还拦着"
            InstallResult.Launched -> _state.value = UpdateUiState.Hidden

            // 用户没给"安装未知应用"权限：跳系统设置页。
            // **门禁不许退成可跳过卡片** —— 那等于"只要拒绝授权，强更就自动变成可跳过"，
            // 门禁被它要防的动作本身绕过，而且绕过后看起来一切正常。
            // 非强制那一档才退回去：它本来就可跳过，退回去用户回来还能一键继续。
            InstallResult.PermissionMissing -> {
                _state.value = if (forcedThisLaunch) {
                    UpdateUiState.Gate(manifest)
                } else {
                    UpdateUiState.OptionalCard(manifest)
                }
                gateway.requestInstallPermission()
            }

            // 弹不出安装页（FileProvider 路径不对、URI 被拒）也要说清楚，
            // 而不是让卡片停在"下载中"假装还在忙
            InstallResult.Failed ->
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
