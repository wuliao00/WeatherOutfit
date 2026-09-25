package com.jianyi.outfit

import com.jianyi.outfit.util.LOCATION_BUDGET_MS
import com.jianyi.outfit.util.acquireWithinBudget
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 看门狗：外层再套一个远大于预算的硬超时。
 *
 * 没有它，「超时被删掉」这种回归的表现是**测试永远挂着不返回**（被测的就是
 * "永不回调"，去掉上限后自然真的永不结束），CI 会卡到 job 超时而不是给一个
 * 红色失败 —— 既慢又难归因。套上之后那种回归在 3 秒内以
 * TimeoutCancellationException 失败，信息直接指向"预算没生效"。
 */
private const val WATCHDOG_MS = 3_000L

/**
 * 取位总超时（LOCATION_BUDGET_MS）的行为验证。
 *
 * 起因是真机缺陷：vivo V2156A 上点「GPS 定位」后「定位中…」挂了约 25 秒才回弹，
 * 因为 FusedLocation 的两条 await 都没有上限。这类"超时到底生没生效"在真机上
 * 只能靠等，而且复现条件（弱信号 + 无缓存）不可重复 —— 所以把它做成纯 Kotlin 的
 * 泛型函数后在这里钉住：
 * - 来源永不返回时，预算到点给 null（不是异常、也不是继续等）；
 * - 到点时那个还不回来的等待确实被取消了（不是留一个永远挂着的协程在那儿）；
 * - 缓存命中不再发第二次定位请求，因此超时不会变成"重复定位"；
 * - 来源抛异常收敛成 null，界面不会因此报错。
 *
 * 验不了的：FusedLocation 在真实弱信号下到底多久回调 —— 那只有真机有答案。
 * 本项目的 app 测试是普通 JUnit4，没有 Robolectric，所以 `android.location.Location`
 * 和 GMS 的 `Task` 都不在测试里出现（这正是上面把等待逻辑抽成泛型函数的原因）。
 */
class LocationBudgetTest {

    /** 永不完成的挂起来源，等价于 getCurrentLocation 的 Task 一直不回调 */
    private suspend fun hangsForever(onCancel: () -> Unit): String =
        suspendCancellableCoroutine<String> { cont -> cont.invokeOnCancellation { onCancel() } }

    @Test
    fun `a source that never returns is cut off by the budget and yields null`() = runBlocking {
        var cancelled = false
        val elapsed = measureTimeMillis {
            val result = withTimeout(WATCHDOG_MS) {
                acquireWithinBudget(
                    budgetMs = 60,
                    cached = { hangsForever { cancelled = true } },
                    fresh = { hangsForever { cancelled = true } }
                )
            }
            assertNull(result)
        }
        assertTrue("等待应被超时切断，而不是留一个永远挂着的协程", cancelled)
        assertTrue("总耗时应贴着预算（60ms），实测 $elapsed ms", elapsed in 40..3_000)
    }

    /** 超时只放弃等待、不重试，所以不能因为第一个来源慢就多发一次定位请求 */
    @Test
    fun `a cache hit never starts a fresh request`() = runBlocking {
        var freshCalled = false
        val result = acquireWithinBudget(
            budgetMs = LOCATION_BUDGET_MS,
            cached = { "108.9,34.2" },
            fresh = { freshCalled = true; "116.4,39.9" }
        )
        assertEquals("108.9,34.2", result)
        assertTrue("有缓存位置时不该再发一次 getCurrentLocation", !freshCalled)
    }

    @Test
    fun `no cache falls through to the fresh fix`() = runBlocking {
        val result = acquireWithinBudget(
            budgetMs = LOCATION_BUDGET_MS,
            cached = { null },
            fresh = { "113.3,23.1" }
        )
        assertEquals("113.3,23.1", result)
    }

    /** 无权限机型同步抛 SecurityException、无 Play Services 时 Task 失败：都不能变成界面报错 */
    @Test
    fun `a throwing source degrades to the next one and then to null`() = runBlocking {
        val recovered = acquireWithinBudget(
            budgetMs = LOCATION_BUDGET_MS,
            cached = { throw SecurityException("missing permission") },
            fresh = { "121.5,31.2" }
        )
        assertEquals("121.5,31.2", recovered)

        val bothBroken = acquireWithinBudget<String>(
            budgetMs = LOCATION_BUDGET_MS,
            cached = { throw IllegalStateException("no play services") },
            fresh = { throw IllegalStateException("location switch off") }
        )
        assertNull(bothBroken)
    }

    /**
     * 预算数值本身也钉一下：这个缺陷的成因就是"没有上限"，
     * 将来有人为了成功率把它调回 30 秒，界面上就又变成 25 秒转圈 ——
     * 而代码评审最容易漏的就是一个孤零零的数字。
     */
    @Test
    fun `production budget is short enough not to feel frozen and long enough to get a fix`() {
        assertTrue(
            "预算应落在 3~10 秒（真机实测无上限时挂到约 25 秒）：$LOCATION_BUDGET_MS ms",
            LOCATION_BUDGET_MS in 3_000..10_000
        )
    }
}
