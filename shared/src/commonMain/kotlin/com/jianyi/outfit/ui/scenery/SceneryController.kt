package com.jianyi.outfit.ui.scenery

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.jianyi.outfit.data.model.SceneryMode
import com.jianyi.outfit.data.model.UserPreferences
import com.jianyi.outfit.platform.currentDayOfYear
import com.jianyi.outfit.platform.currentMonth

/** 各页取用风景控制器（背景在根节点，页面只负责上报滚动量与读当前风景） */
val LocalSceneryController = staticCompositionLocalOf<SceneryController?> { null }

/**
 * 风景换景的中央状态机，挂在依赖容器上（全应用单例）。
 *
 * 为什么放容器而不是各页 ViewModel：背景要跨页面连续。
 * 如果每页自己算一张，从首页进详情页就会看到背景「跳」一下，
 * 而用户横滑换景后回到首页，背景又会退回旧的那张。
 *
 * 日期与月份走 platform 的 expect/actual：Java 的 `Calendar.MONTH` 是 0 基、
 * iOS 的 NSCalendar 是 1 基，直接写 Calendar 会让 iOS 悄悄选错季节风景。
 */
@Stable
class SceneryController {

    /** 当前生效的风景 */
    var scenery: Scenery by mutableStateOf(Scenery.DEFAULT)
        private set

    /** 为什么是这一张（给设置页/长按提示用，让自动换景可被解释） */
    var reason: String by mutableStateOf("正在定位")
        private set

    var mode: SceneryMode by mutableStateOf(SceneryMode.AUTO_WEATHER)
        private set

    /**
     * 各页滚动量，供背景视差读取。
     *
     * 用 MutableFloatState 而不是普通字段：只有 graphicsLayer 的 lambda 读它，
     * 每帧只是重算一次变换矩阵，不会引起任何重组。
     * （FloatState 是只读接口，写入必须是 MutableFloatState）
     */
    val scrollPx: MutableFloatState = mutableFloatStateOf(0f)

    private var lastWeather: WeatherSnapshot? = null
    private var prefs: UserPreferences? = null

    /** 天气到手：AUTO_WEATHER 模式下据此重算风景 */
    fun onWeather(condition: String, tempC: Double, hour: Int, windScale: Int) {
        val snapshot = WeatherSnapshot(condition, tempC, hour, windScale)
        if (snapshot == lastWeather) return
        lastWeather = snapshot
        resolve()
    }

    fun onPreferences(prefs: UserPreferences) {
        this.prefs = prefs
        // 偏好是唯一事实来源：设置页改了什么，这里就按什么重算
        mode = prefs.sceneryMode
        resolve()
    }

    /**
     * 用户手动挑了一张：先乐观切过去（手指立刻见效），
     * 随后 DataStore 回读会把 mode=FIXED + sceneryKey 送到 onPreferences 完成确认。
     */
    fun onUserSelect(target: Scenery) {
        mode = SceneryMode.FIXED
        resolveWith(target, "你手动选择了「${target.label}」")
    }

    private fun resolve() {
        when (mode) {
            SceneryMode.FIXED -> {
                val chosen = Scenery.fromKey(prefs?.sceneryKey)
                if (chosen != null) resolveWith(chosen, "固定使用「${chosen.label}」")
                else resolveAuto()   // 选了固定但还没挑过：先按天气给一张，别空着
            }

            SceneryMode.DAILY_ROTATE -> {
                val todays = Scenery.forDay(currentDayOfYear())
                resolveWith(todays, "今天轮到「${todays.label}」")
            }

            SceneryMode.AUTO_WEATHER -> resolveAuto()
        }
    }

    private fun resolveAuto() {
        val snapshot = lastWeather ?: return
        val pick = SceneryResolver.pick(
            condition = snapshot.condition,
            tempC = snapshot.tempC,
            hour = snapshot.hour,
            windScale = snapshot.windScale,
            month = currentMonth()
        )
        resolveWith(pick.scenery, "自动选景：${pick.reason}")
    }

    private fun resolveWith(target: Scenery, why: String) {
        reason = why
        if (target != scenery) scenery = target
    }

    private data class WeatherSnapshot(
        val condition: String,
        val tempC: Double,
        val hour: Int,
        val windScale: Int
    )
}
