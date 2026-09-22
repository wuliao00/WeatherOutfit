package com.jianyi.outfit.util

import android.os.Build
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 高帧率申请。
 *
 * Android 从 12 起把「选哪个刷新率」收归系统统一调度：即使手机是 120Hz 屏，
 * 普通 App 默认也只会被分到 60Hz，因为系统要省电。想跑满必须显式表态。
 *
 * ## 通道只有一个，而且是 float
 * `View#setRequestedFrameRate(float)`（**API 35 才有**，SDK 的 api-versions.xml 里
 * 记的就是 since=35，早先按 31 守卫会在 Android 12~14 抛 NoSuchMethodError）。
 * 关键的一点：`REQUESTED_FRAME_RATE_CATEGORY_HIGH` 并不是一个独立的「类别 API」，
 * 它就是同一个 float 参数上的**负数哨兵**（HIGH = -4.0f）。
 * 所以「给类别」和「给精确值」是互斥的，只能二选一 —— 不存在「两个都设更强」。
 *
 * ## 为什么这里选精确值而不是 CATEGORY_HIGH
 * 类别值到了 framework 里要经 `config_defaultHighFrameRateCategoryRate` 这类**厂商 overlay**
 * 翻译成一个具体 Hz。也就是说"高"到底等于多少，是 OEM 说了算，很多机型把高档定在 90，
 * 120 留给更高的档或游戏场景。真机实测印证了这点：只发 CATEGORY_HIGH 时，
 * OPPO PLB110（面板 60/90/120 三档）滚动期间平均约 104fps、静止约 96fps，
 * 且 `dumpsys display` 一度直接报 `renderFrameRate 90.0` —— 明显没拿到顶档。
 * 于是改成把面板**实际支持的最高 Hz** 作为精确值发过去。
 *
 * ## 仍然只是「请求」
 * 系统完全可以不理会（省电模式、机身温度、厂商策略都会否决），所以设置页把
 * 实际申请到的数字如实显示出来，而不是只写一句"已开启高帧率"。
 * 关掉开关时发 NO_PREFERENCE，把选择权交回系统。
 */
object FrameRate {

    /** 该方法可用的最低系统版本 */
    private const val MIN_SDK = Build.VERSION_CODES.VANILLA_ICE_CREAM  // API 35

    /** 超过这个数就不像是能申请到的档位，宁可退回类别值 */
    private const val PLAUSIBLE_MAX_HZ = 240f

    /**
     * 纯函数：从候选档位里挑最高的可信值。
     *
     * 抽出来是为了能在 JVM 单测里覆盖 NaN / 无穷 / 0 / 负数 / 离谱值 ——
     * 这些正是 `Display.Mode.refreshRate` 在虚拟屏或异常驱动下真会给出来的东西，
     * 而把 0f 或 NaN 发给 setRequestedFrameRate 等于没申请。
     */
    fun highestOf(rates: List<Float>): Float {
        var best = 0f
        for (rate in rates) {
            if (rate.isFinite() && rate > best && rate <= PLAUSIBLE_MAX_HZ) best = rate
        }
        return best
    }

    /**
     * 本机面板在当前分辨率下能跑的最高档（Hz）；取不到返回 0。
     *
     * 只跟**当前分辨率一致**的档位比：有些机型 144Hz 只在降分辨率时开放，
     * 拿那个数字去申请，反而会被系统整个否决掉。
     * （`Display.Mode` 并没有 `isSynthetic()`，别照着 SurfaceFlinger 的字段名去猜——
     * 公开方法只有 getModeId / getPhysicalWidth / getPhysicalHeight / getRefreshRate /
     * getAlternativeRefreshRates / getSupportedHdrTypes。）
     */
    fun peak(view: View?): Float {
        val display = view?.display ?: return 0f
        val current = display.mode ?: return 0f
        return highestOf(
            display.supportedModes
                .filter {
                    it.physicalWidth == current.physicalWidth &&
                        it.physicalHeight == current.physicalHeight
                }
                .map { it.refreshRate }
        )
    }

    /**
     * 本次**真正发出去**的精确请求值（Hz）；0 表示这轮没发精确值。
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

    fun request(view: View, high: Boolean) {
        if (Build.VERSION.SDK_INT < MIN_SDK) return
        if (!high) {
            requestedHz = 0f
            usedCategoryFallback = false
            view.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE
            return
        }
        val peak = peak(view)
        if (peak > 0f) {
            requestedHz = peak
            usedCategoryFallback = false
            view.requestedFrameRate = peak
        } else {
            // 读不到面板档位（虚拟屏、display 尚未 attach）：退回类别值，
            // 让厂商去翻译"高"，总比什么都不发好
            requestedHz = 0f
            usedCategoryFallback = true
            view.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        }
    }

    /** 当前设备是否具备申请高帧率的能力（设置页据此解释开关为何无效） */
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= MIN_SDK
}
