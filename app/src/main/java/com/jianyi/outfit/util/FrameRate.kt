package com.jianyi.outfit.util

import android.os.Build
import android.view.Display
import android.view.View
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 高帧率申请。
 *
 * Android 从 12 起把「选哪个刷新率」收归系统统一调度：即使手机是 120Hz 屏，
 * 普通 App 默认也只会被分到 60/90Hz，想跑满必须显式表态。
 *
 * ## 两条通道，各有各的闸门
 * 1. **锁档位** `WindowManager.LayoutParams#preferredDisplayModeId`（**API 31**）。
 *    把窗口钉在面板某个模式上，不依赖任何动态刷新率机制，是游戏/视频类最常用的那条。
 * 2. **发精确值** `View#setRequestedFrameRate(float)`（**API 35**）。
 *    正数是精确 Hz，负数哨兵（HIGH = -4f）是「类别」，二者互斥，只能二选一 ——
 *    类别值到了 framework 里要经 `config_defaultHighFrameRateCategoryRate` 这类**厂商 overlay**
 *    翻译成具体 Hz，"高"到底等于多少是 OEM 说了算（PLB110 上就被翻译成 90）。
 *
 * ## 为什么必须两条一起发（2026-09-22 真机定位）
 * 只发通道 2 时，OPPO PLB110（Android 15 / 60·90·120 三档）上跑了整整一轮：设置页写着
 * "已申请 120Hz"，面板却始终停在 `modeId 2, renderFrameRate 90.0`，滚动期间采样 30 次无一例外。
 * 翻 `dumpsys SurfaceFlinger` 才看到根因 —— 微信的图层带着
 * `requestedFrameRate: {120.00 Hz FrameRateCompatibility::Exact}`，
 * **而我们的图层一条票都没有**。
 *
 * 对着 AOSP android15-release 的 `View.java` / `ViewRootImpl.java` 追下来：
 * `setRequestedFrameRate()` 只是把数字存进 `View.mPreferredFrameRate`，真正投递发生在绘制时的
 * `votePreferredFrameRate()`，而那条路被 `ViewRootImpl.shouldEnableDvrr()` 挡着：
 *
 * ```
 * shouldEnableDvrr() = sEnableVrr
 *     && sToolkitFrameRateViewEnablingReadOnlyFlagValue
 *     && sToolkitSetFrameRateReadOnlyFlagValue
 *     && isFrameRatePowerSavingsBalanced()      // ← 读的是窗口属性
 * ```
 *
 * 最后这项来自 `WindowManager.LayoutParams#isFrameRatePowerSavingsBalanced()`，**默认不打开**；
 * 要么主题里写 `android:windowIsFrameRatePowerSavingsBalanced`，要么运行时调
 * `Window#setFrameRatePowerSavingsBalanced(true)`。没打开时那张票根本发不出去，
 * 而且**不报错、不返回失败** —— 这正是"开关显示已申请、实际没生效"的成因。
 * 所以这里在发精确值之前先把闸门打开，同时保留锁档位这条不依赖闸门的通道兜底。
 *
 * ## 仍然只是「请求」
 * 系统完全可以不理会（省电模式、机身温度、厂商白名单都会否决），所以状态一律**读回来**再上报，
 * 而不是把"我调了 setter"当成"平台收了"。`View#getRequestedFrameRate()` 在平台侧开关关着时
 * 恒返回 0，正好用来判断这条通道到底通不通。关掉开关时发 NO_PREFERENCE 并把档位锁回 0。
 */
object FrameRate {

    /** `preferredDisplayModeId` 可用的最低版本 */
    private const val MIN_SDK_MODE = Build.VERSION_CODES.S  // API 31

    /** `View#setRequestedFrameRate` 可用的最低版本 */
    private const val MIN_SDK_EXACT = Build.VERSION_CODES.VANILLA_ICE_CREAM  // API 35

    /** 超过这个数就不像是能申请到的档位，宁可退回类别值 */
    private const val PLAUSIBLE_MAX_HZ = 240f

    /**
     * 面板的一个档位。
     *
     * 单独抽出来（而不是直接传 `Display.Mode`）是为了让挑选逻辑能在 JVM 单测里跑：
     * `Display.Mode` 没有公开构造器，而真机上出问题的恰恰是"挑出来的那个数字对不对"。
     */
    data class ModeSpec(val id: Int, val width: Int, val height: Int, val hz: Float)

    /**
     * 纯函数：从候选档位里挑最高的可信值；挑不出可信档位返回 0。
     *
     * 抽出来是为了能在 JVM 单测里覆盖 NaN / 无穷 / 0 / 负数 / 离谱值 ——
     * 这些正是 `Display.Mode.refreshRate` 在虚拟屏或异常驱动下真会给出来的东西，
     * 而把 0f 或 NaN 发出去等于没申请，且不会有任何报错。
     */
    fun highestOf(rates: List<Float>): Float {
        var best = 0f
        for (rate in rates) {
            if (rate.isFinite() && rate > best && rate <= PLAUSIBLE_MAX_HZ) best = rate
        }
        return best
    }

    /**
     * 纯函数：在**与当前分辨率一致**的档位里挑最高 Hz 的那一档，连 id 一起给出。
     *
     * 只跟同分辨率的档位比：有些机型 144Hz 只在降分辨率时开放，拿那一档去锁，
     * 等于让系统在"分辨率"和"帧率"之间替我们做选择。
     * （`Display.Mode` 并没有 `isSynthetic()`，别照着 SurfaceFlinger 的字段名去猜 ——
     * 公开方法只有 getModeId / getPhysicalWidth / getPhysicalHeight / getRefreshRate /
     * getAlternativeRefreshRates / getSupportedHdrTypes。）
     * 同 Hz 时取 id 大的那个：驱动一般把较新的模式排在后面。
     */
    fun peakMode(modes: List<ModeSpec>, current: ModeSpec?): ModeSpec? {
        if (current == null) return null
        return modes
            .filter { it.width == current.width && it.height == current.height }
            .filter { it.hz.isFinite() && it.hz > 0f && it.hz <= PLAUSIBLE_MAX_HZ }
            .maxWithOrNull(compareBy<ModeSpec>({ it.hz }, { it.id }))
    }

    private fun modeSpec(mode: Display.Mode?): ModeSpec? = mode?.let {
        ModeSpec(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate)
    }

    /** 本机面板在当前分辨率下能跑的最高档；读不到返回 null（虚拟屏、display 尚未 attach） */
    fun peakOf(view: View?): ModeSpec? {
        val display = view?.display ?: return null
        val current = modeSpec(display.mode) ?: return null
        return peakMode(display.supportedModes.mapNotNull { modeSpec(it) }, current)
    }

    /**
     * 本次真正发出去的精确请求值（Hz），**从 View 读回来的**；0 表示这条通道这次没成立。
     *
     * 做成 Compose 可观察状态而不是普通 `var`：设置页的副标题要显示"实际申请到了多少"，
     * 而 `request()` 是在 `MainActivity` 的 `SideEffect` 里调的 —— 普通字段改了不会触发重组，
     * 那行说明会永远停在第一帧的值上。之前副标题是自己再调一次 `peak()` 现算，
     * 于是界面上写的是"我以为会发的"，不是"实际发出去的"，两处一旦分叉就没人知道。
     */
    var requestedHz: Float by mutableFloatStateOf(0f)
        private set

    /** 本次是否走的是「类别」通道而不是精确值 */
    var usedCategoryFallback: Boolean by mutableStateOf(false)
        private set

    /** 锁住的显示模式 id；0 = 没锁（交回系统） */
    var lockedModeId: Int by mutableIntStateOf(0)
        private set

    /** 锁住的档位是多少 Hz（设置页要显示这个数字） */
    var lockedHz: Float by mutableFloatStateOf(0f)
        private set

    /**
     * 精确值这条通道这次是否真的被平台收下。
     *
     * 判据是 `View#getRequestedFrameRate()` 能不能读回刚写进去的数 ——
     * 平台侧 toolkit 开关关着时 setter 是空实现、getter 恒返回 0，
     * 所以"读得回来"才等于"票真的发出去了"。
     */
    var exactChannelAccepted: Boolean by mutableStateOf(false)
        private set

    /** 窗口的 dVRR 闸门（`isFrameRatePowerSavingsBalanced`）这次是否处于打开状态 */
    var powerSavingsBalanced: Boolean by mutableStateOf(false)
        private set

    /** 本机是否具备申请高帧率的能力（设置页据此解释开关为何无效） */
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= MIN_SDK_MODE

    /** 精确值通道要 Android 15 以上才有 */
    val hasExactChannel: Boolean get() = Build.VERSION.SDK_INT >= MIN_SDK_EXACT

    /**
     * @param window 活动的窗口。锁档位与 dVRR 闸门都挂在它身上；传 null 时只剩类别通道。
     */
    fun request(window: Window?, view: View, high: Boolean) {
        val peak = peakOf(view)

        // ---- 通道 1：锁档位（API 31+）----
        val wantModeId = if (high) peak?.id ?: 0 else 0
        if (Build.VERSION.SDK_INT >= MIN_SDK_MODE && window != null) {
            val attrs = window.attributes
            if (attrs.preferredDisplayModeId != wantModeId) {
                attrs.preferredDisplayModeId = wantModeId
                window.attributes = attrs  // 触发 dispatchWindowAttributesChanged
            }
            lockedModeId = wantModeId
        } else {
            lockedModeId = 0
        }
        lockedHz = if (high && lockedModeId != 0) peak?.hz ?: 0f else 0f

        // 版本判断要写全，lint 才认这个守卫（它推不出 hasExactChannel 等价于 SDK_INT>=35）
        if (Build.VERSION.SDK_INT < MIN_SDK_EXACT) {
            requestedHz = 0f
            usedCategoryFallback = false
            exactChannelAccepted = false
            powerSavingsBalanced = false
            return
        }

        // ---- 通道 2：精确值（API 35+）----
        // 先开闸门：不开的话下面那行只是往一个字段里写数字，票永远到不了 SurfaceFlinger。
        if (window != null && !window.isFrameRatePowerSavingsBalanced) {
            window.isFrameRatePowerSavingsBalanced = true
        }
        powerSavingsBalanced = window?.isFrameRatePowerSavingsBalanced ?: false

        val wanted = when {
            !high -> View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE
            peak != null -> peak.hz
            // 读不到面板档位（虚拟屏、display 尚未 attach）：退回类别值，
            // 让厂商去翻译"高"，总比什么都不发好
            else -> View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        }
        view.requestedFrameRate = wanted
        usedCategoryFallback = high && peak == null

        // 读回来才算数：getter 返回 0 说明平台的 toolkit 开关没开，这次申请是空操作
        val echoed = view.requestedFrameRate
        exactChannelAccepted = kotlin.math.abs(echoed - wanted) < 0.001f
        requestedHz = if (high && echoed > 0f) echoed else 0f
    }
}
