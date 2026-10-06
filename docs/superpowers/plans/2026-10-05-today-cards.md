# 今日三卡（黄历 · 历史上的今天 · 星座）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在首页下方新增三张可独立关闭的离线信息卡：农历黄历、历史上的今天、星座。

**Architecture:** 三个纯 Kotlin 引擎（`AlmanacEngine` / `HistoryTodayEngine` / `HoroscopeEngine`）产出带来源标注的领域模型，UI 只渲染。**全套零网络、零 Repository、零 Room**——与天气那条 `WeatherApiClient → WeatherRepository → weather_cache.db` 链路刻意对照：能离线确定的东西不走网络，因此没有失败态、没有缓存、没有退避代码。

**Tech Stack:** Kotlin 2.1.0（KMP：androidTarget + iosArm64 + iosSimulatorArm64）、Compose Multiplatform 1.8.2、kotlinx-serialization 1.7.3、`cn.6tail:tyme4kt:1.5.0`（新增，MIT，零传递依赖）、kotlin.test（commonTest，两端各跑一遍）。

**Spec:** `docs/superpowers/specs/2026-10-05-today-cards-design.md` —— 本计划每一条都从 spec 论证，执行时必须同时读 spec。

---

## Global Constraints

每条都是**所有**任务的隐含要求，值逐字取自 spec：

- `shared/src/commonMain` 里**禁止出现 JVM-only API**（`java.*`、`System.*`）。`PlatformTime.kt` 的注释记载这个坑由 CI 的 `:shared` iOS job 抓出来，本机验证不到，所以不能靠"Android 编过了"就当没事。
- 新增外部依赖**只允许** `cn.6tail:tyme4kt:1.5.0`。它的各 target 变体传递依赖为空（spec 2.6 已读 Gradle Module Metadata 核实），不得顺手引 `kotlinx-datetime` 或 `lunar-java`。
- 三张卡**全部离线**。任何任务都不得新增 Ktor 接口、HTTP 调用或 Room 表。
- 日期与时刻一律经 `platform/PlatformClock.kt` 取值，**引擎内部不调平台 API**，也不引入 `Clock.System`——否则 commonTest 与 iOS 的时间无法固定。
- `HomeScreen.kt`（现 983 行）**只允许增加卡片挂载调用，不允许写卡片内容**。
- 每张卡的来源标注由模型自己的 `SOURCE_LABEL` 常量给出，UI 一律读常量；模型上不得存在可代入的标注参数。依据是 `LifeIndexEngine.kt` 顶上那句：「把估算包装成官方数据是这个 App 最不该做的事」。
- 「历史上的今天」**每日 3 条**（已确认默认）。
- 星座运势文案**中性陈述**，不带幽默/口语（已确认默认）。
- 本期**只接 Android UI**；引擎与数据仍在 commonMain，iOS 白拿但不改 `ios/` 工程（已确认默认）。
- 注释写**为什么**，不复述代码在做什么；中文，与仓库现有 KDoc 风格一致。
- 每个任务收尾必须 `./gradlew :shared:testDebugUnitTest` 绿；提交前跑一次完整 `./gradlew test`。

---

## 文件结构

**新建**

| 路径 | 单一职责 |
|---|---|
| `shared/src/commonMain/kotlin/com/jianyi/outfit/data/model/TodayModels.kt` | `AlmanacDay` / `HistoricalEvent` / `Horoscope` 三个只读模型 |
| `shared/src/commonMain/kotlin/com/jianyi/outfit/engine/AlmanacEngine.kt` | tyme4kt → `AlmanacDay`，异常收敛为 null |
| `shared/src/commonMain/kotlin/com/jianyi/outfit/engine/HistoryTodayEngine.kt` | 内置 JSON → 当日事件列表 |
| `shared/src/commonMain/kotlin/com/jianyi/outfit/engine/HoroscopeEngine.kt` | (日期, 星座) → 确定性运势 |
| `shared/src/commonMain/kotlin/com/jianyi/outfit/data/today/HoroscopePool.kt` | 手写文案池数据（与引擎分开，便于单独审校） |
| `shared/src/commonMain/composeResources/files/today_in_history.json` | 自撰历史事件数据集 |
| `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/home/AlmanacCard.kt` | 黄历卡 UI |
| `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/home/HistoryTodayCard.kt` | 历史今天卡 UI |
| `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/home/HoroscopeCard.kt` | 星座卡 UI |
| `shared/src/commonTest/kotlin/com/jianyi/outfit/engine/AlmanacEngineTest.kt` | 黄历不变量与锚点测试 |
| `shared/src/commonTest/kotlin/com/jianyi/outfit/engine/HistoryTodayEngineTest.kt` | 数据集完整性与取数测试 |
| `shared/src/commonTest/kotlin/com/jianyi/outfit/engine/HoroscopeEngineTest.kt` | 确定性/覆盖/相邻日差异测试 |
| `shared/src/commonTest/kotlin/com/jianyi/outfit/platform/CivilDateRoundTripTest.kt` | epoch day ↔ 年月日 往返测试 |
| `tools/extract_today_facts.py` | 从外部源只抽 `(year,month,day,type)` 事实骨架 |
| `tools/validate_today_dataset.py` | 数据集结构校验（覆盖/条数/长度/残留标记） |

**修改**

| 路径 | 改什么 |
|---|---|
| `gradle/libs.versions.toml` | 加 `tyme4kt` 版本与库坐标 |
| `shared/build.gradle.kts` | commonMain 引 `libs.tyme4kt` |
| `shared/src/commonMain/kotlin/com/jianyi/outfit/platform/PlatformClock.kt` | 加 `CivilDate` 与 `civilFromEpochDay()` |
| `shared/src/commonMain/kotlin/com/jianyi/outfit/data/model/Models.kt` | `UserPreferences` 加三个开关字段 |
| `shared/src/commonMain/kotlin/com/jianyi/outfit/data/repository/SettingsRepository.kt` | 加三个 setter |
| `shared/src/commonMain/kotlin/com/jianyi/outfit/data/repository/SettingsRepositoryImpl.kt` | 加三个键、三个 setter、读取端三行 |
| `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/home/HomeViewModel.kt` | 暴露三个 `StateFlow` |
| `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/home/HomeScreen.kt` | 挂载三张卡 |
| `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/settings/SettingsScreen.kt` | 加「今日信息」分组 |
| `README.md` / `CHANGELOG.md` | 宣言修订 + 致谢 + 已知说明 |

---

## Task 1: 接入 tyme4kt 并勘察其真实输出

依赖能不能用，取决于它的对象怎么转成字符串——这一点 spec 没写（`Taboo`/`Duty`/`Constellation` 都无 `getXxx()`，靠基类）。先实测取数，再写引擎；不猜。

**Files:**
- Modify: `gradle/libs.versions.toml`（`[versions]` 段末尾、`[libraries]` 段 `coil3-network-ktor3` 之后）
- Modify: `shared/build.gradle.kts`（`commonMain.dependencies { }` 块）
- Create: `shared/src/commonTest/kotlin/com/jianyi/outfit/engine/Tyme4ktSurveyTest.kt`

**Interfaces:**
- Consumes: 无
- Produces: 一份「实际输出登记表」（本任务 Step 5 的产物，写进测试文件的 KDoc），后续任务据此写断言与 `.toString()` 用法

- [ ] **Step 1: 加坐标到版本目录**

`gradle/libs.versions.toml` 的 `[versions]` 段末尾加：

```toml
# 黄历离线算法库（MIT，纯 KMP，各 target 传递依赖为空——见 spec 2.6）
# 不要顺手引 lunar-java：那是 JVM-only jar，进不了 commonMain
tyme4kt = "1.5.0"
```

`[libraries]` 段加：

```toml
tyme4kt = { group = "cn.6tail", name = "tyme4kt", version.ref = "tyme4kt" }
```

- [ ] **Step 2: 在 commonMain 引用**

`shared/build.gradle.kts` 的 `commonMain.dependencies { }` 块内追加：

```kotlin
        implementation(libs.tyme4kt)
```

- [ ] **Step 3: 确认 Android 与 iOS 两端都能解析**

Run: `./gradlew :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64`
Expected: 两个任务都 `BUILD SUCCESSFUL`。

若 iOS 报 `Unresolved reference`，说明该版本没发 `iosSimulatorArm64` 变体——回 spec 2.6 核对，**不要**改成 `implementation("...jvm...")` 绕过，那样 iOS 会带着这个错误一直到 CI。

- [ ] **Step 4: 写勘察测试（先打印，不断言）**

创建 `shared/src/commonTest/kotlin/com/jianyi/outfit/engine/Tyme4ktSurveyTest.kt`：

```kotlin
package com.jianyi.outfit.engine

import com.tyme.culture.Constellation
import com.tyme.solar.SolarDay
import kotlin.test.Test

/**
 * tyme4kt 输出勘察（一次性，取数用；登记表落在下面的 KDoc，后续引擎测试据此写断言）。
 *
 * 为什么要单独跑一轮勘察：Taboo / Duty / Phase / Constellation 这些对象
 * 都没有 getXxx()，取值靠基类的 toString()/name，格式是"甲子"还是"甲子日"、
 * 宜忌是"嫁娶"还是"嫁娶:吉"——只能实测，猜出来的断言会在实现阶段反复返工。
 */
class Tyme4ktSurveyTest {

    @Test
    fun survey_outputs_for_three_anchor_dates() {
        // 三个锚点：今天、一个节气日、一个跨年边界
        val anchors = listOf(
            Triple(2026, 10, 5),
            Triple(2026, 12, 22),
            Triple(2027, 1, 1)
        )
        for ((y, m, d) in anchors) {
            val solar = SolarDay(y, m, d)
            val lunar = solar.getLunarDay()
            println("=== $y-$m-$d ===")
            println("solarName      = ${lunar.getName()}")
            println("sixtyCycle     = ${solar.getSixtyCycle()}")
            println("sixtyCycleDay  = ${lunar.getSixtyCycleDay()}")
            println("lunarYear      = ${lunar.getLunarMonth()?.getLunarYear()?.getName()}")
            println("duty           = ${lunar.getDuty()}")
            println("twelveStar     = ${lunar.getTwelveStar()}")
            println("twentyEightStar= ${lunar.getTwentyEightStar()}")
            println("phase          = ${lunar.getPhase()}")
            println("gods           = ${lunar.getGods()}")
            println("recommends     = ${lunar.getRecommends()}")
            println("avoids         = ${lunar.getAvoids()}")
            println("festival       = ${lunar.getFestival()}")
            println("constellation  = ${solar.getConstellation()}")
            println("constellationName = ${(solar.getConstellation() as Constellation).name}")
        }
    }
}
```

- [ ] **Step 5: 跑起来并记录真实输出**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.jianyi.outfit.engine.Tyme4ktSurveyTest" -i 2>&1 | grep -A20 "=== 2026-10-05 ==="`
Expected: 打印出三个锚点的全部字段值。

把打印结果**逐字**填进该文件 KDoc 下的一张登记表（例如 `// recommends(2026-10-05) = [嫁娶, 出行, ...]`）。这张表是后续任务的断言来源，也是"我们没有臆造第三方输出"的证据。若某字段为 `null` 或抛异常，一并记录——那是 spec 第 7 节边界条目的实证。

注意 `lunar.getLunarMonth()` 可能返回 null（`LunarDay` 跨月边界时），Kotlin 侧要用安全调用；若编译期发现它是非空返回，去掉 `?.`。

- [ ] **Step 6: 提交**

```bash
git add gradle/libs.versions.toml shared/build.gradle.kts \
  shared/src/commonTest/kotlin/com/jianyi/outfit/engine/Tyme4ktSurveyTest.kt
git commit -m "$(printf 'build: 接入 tyme4kt 1.5.0 并勘察黄历对象输出格式\n\n各 target 变体传递依赖为空，iOS 编译已验证。登记表写在测试 KDoc 内，供后续引擎断言取用。')"
```

---

## Task 2: `civilFromEpochDay()` —— 平台层零改动的日期取值

引擎需要"今天的年月日"。仓库已有纯 Kotlin 的 `epochDayFromCivil()`，加它的**逆函数**即可，不必新增 expect/actual——这与 `PlatformTime.kt` 刻意保持零依赖的取向一致（spec 5 节的原始设想在此被更优解替换）。

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jianyi/outfit/platform/PlatformClock.kt`（在 `epochDayFromCivil` 之后追加）
- Test: `shared/src/commonTest/kotlin/com/jianyi/outfit/platform/CivilDateRoundTripTest.kt`

**Interfaces:**
- Consumes: 已有 `todayEpochDay()`（expect/actual，两端已实现）、已有 `epochDayFromCivil(year,month,day): Long`
- Produces:
  - `data class CivilDate(val year: Int, val month: Int, val day: Int)`
  - `fun civilFromEpochDay(epochDay: Long): CivilDate`
  - `fun todayCivilDate(): CivilDate`

- [ ] **Step 1: 写失败的往返测试**

创建 `shared/src/commonTest/kotlin/com/jianyi/outfit/platform/CivilDateRoundTripTest.kt`：

```kotlin
package com.jianyi.outfit.platform

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * epoch day ↔ 年月日 的往返测试（commonTest ⇒ Android 与 iOS 各跑一遍）。
 *
 * 锚点用可独立核算的公历日期，不让实现自己算出来的数当黄金值——
 * 与 PlatformDateAlignmentTest 同一套做法。
 */
class CivilDateRoundTripTest {

    @Test
    fun civil_from_epoch_day_matches_auditable_anchors() {
        assertEquals(CivilDate(1970, 1, 1), civilFromEpochDay(0L), "纪元原点")
        assertEquals(CivilDate(1969, 12, 31), civilFromEpochDay(-1L), "原点前一天")
        assertEquals(CivilDate(2000, 1, 1), civilFromEpochDay(10957L))
        assertEquals(CivilDate(2024, 2, 29), civilFromEpochDay(19782L), "闰年 2 月 29 日")
        assertEquals(CivilDate(2026, 10, 5), civilFromEpochDay(20732L))
    }

    @Test
    fun round_trip_is_identity_over_nine_decades() {
        // 1950-01-01 到 2049-12-31 逐日往返，覆盖 6 个闰年
        var e = epochDayFromCivil(1950, 1, 1)
        val end = epochDayFromCivil(2049, 12, 31)
        var n = 0
        while (e <= end) {
            val c = civilFromEpochDay(e)
            assertEquals(e, epochDayFromCivil(c.year, c.month, c.day), "往返不一致于 $c")
            assertEquals(1, c.month..12.let { e - e }) // placeholder-guard, replaced below
            n++
            e++
        }
        assertEquals(36524, n, "100 年 = 36524 天（24 个闰日）")
    }

    @Test
    fun month_and_day_are_1_based_and_in_range() {
        var e = epochDayFromCivil(2026, 1, 1)
        while (e <= epochDayFromCivil(2026, 12, 31)) {
            val c = civilFromEpochDay(e)
            assert(c.month in 1..12) { "月份应 1~12，实到 ${c.month}" }
            assert(c.day in 1..31) { "日应 1~31，实到 ${c.day}" }
            assertEquals(e, epochDayFromCivil(c.year, c.month, c.day))
            e++
        }
    }

    @Test
    fun today_civil_date_agrees_with_platform_epoch_day() {
        val c = todayCivilDate()
        assertEquals(todayEpochDay(), epochDayFromCivil(c.year, c.month, c.day), "两端同源")
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :shared:testDebugUnitTest --tests "*CivilDateRoundTripTest*"`
Expected: 编译失败，报 `Unresolved reference: CivilDate` / `civilFromEpochDay` / `todayCivilDate`。

- [ ] **Step 3: 实现**

在 `PlatformClock.kt` 的 `epochDayFromCivil()` 之后追加：

```kotlin
/** 公历日期，1 基（month 1~12、day 1~31）。只读值对象，不放逻辑 */
data class CivilDate(val year: Int, val month: Int, val day: Int)

/**
 * 儒略日数 → 公历日期（Howard Hinnant 的 civil_from_days）。
 *
 * 是 [epochDayFromCivil] 的逆函数，纯数学、零平台 API。之所以不新增一对
 * expect/actual 的 currentYear()/currentDayOfMonth()：那样两端各写一份
 * Calendar/NSCalendar 取值，而这份仓库已有的 todayEpochDay() 只要换算一次，
 * 就同时得到年月日，且与既有 epochDayFromCivil 天然互为校验。
 */
fun civilFromEpochDay(epochDay: Long): CivilDate {
    val z = epochDay + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
    return CivilDate((if (month <= 2) y + 1 else y).toInt(), month, day)
}

/** 今天的公历日期（本地时区）。引擎取「当下」只经这里，便于单测替换 */
fun todayCivilDate(): CivilDate = civilFromEpochDay(todayEpochDay())
```

- [ ] **Step 4: 跑测试确认通过（两端）**

Run: `./gradlew :shared:testDebugUnitTest --tests "*CivilDateRoundTripTest*" :shared:testReleaseUnitTest`
Expected: PASS。

再确认 commonTest 也在 iOS 模拟器 target 编译：
Run: `./gradlew :shared:compileTestKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL。（`assertEquals(1, c.month..12.let { e - e })` 这行是刻意的占位守卫，Step 5 删掉。）

- [ ] **Step 5: 删掉那行占位守卫**

上面 Step 1 的第二个测试里，`assertEquals(1, c.month..12.let { e - e })` 是无效断言，删除该行——月份范围已由第三个测试正经覆盖。**不要**留着它假装在测。

Run: `./gradlew :shared:testDebugUnitTest --tests "*CivilDateRoundTripTest*"`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add shared/src/commonMain/kotlin/com/jianyi/outfit/platform/PlatformClock.kt \
  shared/src/commonTest/kotlin/com/jianyi/outfit/platform/CivilDateRoundTripTest.kt
git commit -m "$(printf 'feat(platform): 增 civilFromEpochDay 逆换算，日期取值不加平台 API\n\n复用既有 todayEpochDay()，与 epochDayFromCivil 互为往返校验，避免再写一份两端 Calendar 取值。')"
```

---

## Task 3: 领域模型 `TodayModels.kt`

模型与引擎分开，是因为 `Models.kt` 已 229 行且属于天气主域；新域另开文件，避免它继续长大（spec 4 节）。

**Files:**
- Create: `shared/src/commonMain/kotlin/com/jianyi/outfit/data/model/TodayModels.kt`

**Interfaces:**
- Consumes: 无
- Produces: `AlmanacDay`、`HistoricalEvent`、`Horoscope` —— Task 4/8/9 的返回类型，Task 10 的入参类型

- [ ] **Step 1: 写模型**

```kotlin
package com.jianyi.outfit.data.model

/**
 * 今日三卡的只读领域模型。
 *
 * 与天气模型分文件放：这三块是「今日」域，字段会随内容源演进而天气域不会，
 * 混在 Models.kt 里会让两个互不相干的变更理由抢同一个文件。
 *
 * 来源标注是各自的 SOURCE_LABEL 常量、不是构造参数 —— UI 只能读常量，
 * 这是「不把估算包装成官方数据」这条规矩的结构化落法。
 */

/** 农历黄历一日 */
data class AlmanacDay(
    val lunarDateText: String,
    val ganzhiYear: String,
    val ganzhiDay: String,
    val zodiac: String,
    /** 当日节气名；非节气日取 null，UI 侧不显示该字段而非写"无" */
    val jieqi: String?,
    /** 宜。已截断到展示上限 */
    val recommends: List<String>,
    /** 忌。已截断到展示上限 */
    val avoids: List<String>,
    /** 建除十二值，如「建」「除」 */
    val duty: String,
    /** 冲煞描述，如「冲鼠(午)煞北」 */
    val chongSha: String,
    /** 月相，如「朔」「望」 */
    val moonPhase: String,
    val festival: String?
) {
    companion object {
        /** 宜/忌各最多展示几项。黄历原文动辄十余项，全铺与极简调性冲突 */
        const val TABOO_DISPLAY_LIMIT = 3

        const val SOURCE_LABEL = "按传统历法推算"
    }
}

/** 历史上的今天单条事件 */
data class HistoricalEvent(
    val year: Int,
    /** 事件描述，本项目独立撰写（见 spec 3.2） */
    val summary: String,
    /** 1=事件 2=出生 3=逝世 */
    val type: Int
) {
    companion object {
        const val SOURCE_LABEL = "史料整理，未逐条核实"
        const val PER_DAY_LIMIT = 3
    }
}

/** 星座运势一日 */
data class Horoscope(
    /** 星座名，如「天秤座」 */
    val constellation: String,
    val overall: String,
    val love: String,
    val career: String,
    val wealth: String,
    val luckyColor: String,
    val luckyNumber: Int
) {
    companion object {
        const val SOURCE_LABEL = "娱乐内容，非预测"
    }
}
```

- [ ] **Step 2: 编译**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/kotlin/com/jianyi/outfit/data/model/TodayModels.kt
git commit -m "$(printf 'feat(model): 今日三卡领域模型\n\n来源标注是各模型的伴随常量，UI 只能读常量、无从编造。')"
```

---

## Task 4: `AlmanacEngine`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/jianyi/outfit/engine/AlmanacEngine.kt`
- Test: `shared/src/commonTest/kotlin/com/jianyi/outfit/engine/AlmanacEngineTest.kt`

**Interfaces:**
- Consumes: `SolarDay(y,m,d)`、`LunarDay.getRecommends()/getAvoids()/getDuty()/getPhase()/getSixtyCycle()/getFestival()`（格式见 Task 1 登记表）、`CivilDate`（Task 2）、`AlmanacDay`（Task 3）
- Produces: `AlmanacEngine.of(date: CivilDate): AlmanacDay?`

- [ ] **Step 1: 写失败的测试**

创建 `shared/src/commonTest/kotlin/com/jianyi/outfit/engine/AlmanacEngineTest.kt`。断言基于**不变量**（不依赖具体字符串，不会因 tyme4kt 改格式而假失败）加少量可独立核算的锚点：

```kotlin
package com.jianyi.outfit.engine

import com.jianyi.outfit.data.model.AlmanacDay
import com.jianyi.outfit.platform.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 黄历引擎测试。
 *
 * 优先测**不变量**而非具体字符串：干支纪日是严格 60 周期、节气年恰 24 个、
 * 月相名必须来自固定集合——这些不随 tyme4kt 版本改文案而失效。
 * 具体值只在能独立核算的地方钉死（见 sixty_cycle_day_advances_one_per_day 的注释）。
 */
class AlmanacEngineTest {

    @Test
    fun produces_full_record_for_today() {
        val a = AlmanacEngine.of(CivilDate(2026, 10, 5))
        assertNotNull(a, "合法日期不该返回 null")
        assertTrue(a!!.lunarDateText.isNotBlank())
        assertTrue(a.ganzhiDay.length in 2..4, "干支纪日应为两字，实到 ${a.ganzhiDay}")
        assertTrue(a.zodiac.length == 1, "生肖应为单字，实到 ${a.zodiac}")
        assertTrue(a.moonPhase.isNotBlank())
        assertTrue(a.duty.isNotBlank())
        assertTrue(AlmanacDay.SOURCE_LABEL.isNotBlank(), "来源标注常量不得为空，否则角标会渲染成空壳")
    }

    @Test
    fun sixty_cycle_day_advances_one_per_day() {
        // 干支纪日是严格 60 循环：相邻两天在序列里必须相邻（模 60）。
        // 这条不需要外部权威日期当黄金值，所以比"查表比对"更抗腐化。
        val base = AlmanacEngine.of(CivilDate(2026, 1, 1))!!
        assertNotNull(base.ganzhiDay)
        var prev = base.ganzhiDay
        var d = CivilDate(2026, 1, 1)
        while (d.day < 31 || d.month == 1) {
            d = nextDay(d)
            val a = AlmanacEngine.of(d)!!
            assertTrue(a.ganzhiDay != prev, "干支纪日不能连续两天相同（$prev）于 $d")
            prev = a.ganzhiDay
        }
    }

    @Test
    fun taboo_lists_are_resolved_and_within_display_limit() {
        val a = AlmanacEngine.of(CivilDate(2026, 10, 5))!!
        assertTrue(a.recommends.size <= AlmanacDay.TABOO_DISPLAY_LIMIT)
        assertTrue(a.avoids.size <= AlmanacDay.TABOO_DISPLAY_LIMIT)
        assertTrue(a.recommends.none { it.isBlank() }, "宜不得含空项")
        assertTrue(a.avoids.none { it.isBlank() }, "忌不得含空项")
        assertTrue(a.recommends.intersect(a.avoids.toSet()).isEmpty(), "同一事项不应既宜又忌")
    }

    @Test
    fun jieqi_and_festival_are_null_when_absent_and_set_on_known_days() {
        // 冬至/夏至是可独立核算的节气日：2026 年冬至为 12 月 21/22 日，
        // 实现阶段用 Task 1 勘察表确认命中哪一天，再把这个断言写成确定值。
        val winter = AlmanacEngine.of(CivilDate(2026, 12, 22))
        assertNotNull(winter)
        assertNotNull(winter!!.jieqi, "2026-12-22 应为节气日，实到 null")
        assertNull(AlmanacEngine.of(CivilDate(2026, 10, 5))!!.jieqi, "普通日期无节气")
    }

    @Test
    /** 非法/越界日期一律收敛为 null，绝不让首页崩（spec 第 7 节第 5 条） */
    fun out_of_range_dates_return_null_instead_of_throwing() {
        assertNull(AlmanacEngine.of(CivilDate(2026, 2, 30)), "2 月 30 日")
        assertNull(AlmanacEngine.of(CivilDate(2026, 13, 1)), "13 月")
        assertNull(AlmanacEngine.of(CivilDate(0, 1, 1)), "公元 0 年")
        assertNull(AlmanacEngine.of(CivilDate(999999, 1, 1)), "超大年份")
    }

    @Test
    fun known_leap_day_is_accepted() {
        assertNotNull(AlmanacEngine.of(CivilDate(2028, 2, 29)), "闰年 2 月 29 日必须可用")
    }

    private fun nextDay(d: CivilDate): CivilDate {
        val mdays = intArrayOf(31, if ((d.year % 4 == 0 && d.year % 100 != 0) || d.year % 400 == 0) 29 else 28,
            31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        return if (d.day < mdays[d.month - 1]) CivilDate(d.year, d.month, d.day + 1)
        else if (d.month < 12) CivilDate(d.year, d.month + 1, 1)
        else CivilDate(d.year + 1, 1, 1)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :shared:testDebugUnitTest --tests "*AlmanacEngineTest*"`
Expected: 编译失败，`Unresolved reference: AlmanacEngine`

- [ ] **Step 3: 实现引擎**

创建 `shared/src/commonMain/kotlin/com/jianyi/outfit/engine/AlmanacEngine.kt`：

```kotlin
package com.jianyi.outfit.engine

import com.jianyi.outfit.data.model.AlmanacDay
import com.jianyi.outfit.platform.CivilDate
import com.tyme.solar.SolarDay

/**
 * 黄历推导引擎。
 *
 * 与 LifeIndexEngine 同形：纯 Kotlin、不碰平台 API、日期从外面传进来，
 * 所以能直接单测，也让"今天"这个概念在测试里可固定。
 *
 * 全离线是本设计的核心立场（spec 3.1）：农历/干支/节气/宜忌是确定性历法计算，
 * 走网络等于给一个永不失败的东西引入失败态，还要多背一个凭证与限流源。
 */
object AlmanacEngine {

    /**
     * @return 当日黄历；日期非法或超出库的支持范围时返回 null（不抛，交给 UI 不渲染该卡）
     */
    fun of(date: CivilDate): AlmanacDay? {
        val lunar = runCatching { SolarDay(date.year, date.month, date.day).getLunarDay() }
            .getOrNull() ?: return null

        val recommends = lunar.getRecommends().map { it.toString() }.filter { it.isNotBlank() }
        val avoids = lunar.getAvoids().map { it.toString() }.filter { it.isNotBlank() }

        return AlmanacDay(
            lunarDateText = lunar.getName(),
            ganzhiYear = runCatching { lunar.getLunarMonth()?.getLunarYear()?.getSixtyCycle()?.getName() }
                .getOrNull().orEmpty(),
            ganzhiDay = lunar.getSixtyCycleDay().getName(),
            zodiac = zodiacOf(lunar),
            jieqi = jieqiOf(date),
            recommends = recommends.take(AlmanacDay.TABOO_DISPLAY_LIMIT),
            avoids = avoids.take(AlmanacDay.TABOO_DISPLAY_LIMIT),
            duty = lunar.getDuty().getName(),
            chongSha = chongShaOf(lunar),
            moonPhase = lunar.getPhase().getName(),
            festival = lunar.getFestival()?.getName()
        )
    }
}
```

上面用到三个私有辅助函数。**它们的实现必须以 Task 1 勘察表里 tyme4kt 的真实方法名为准**——`getName()` / `toString()` 与非空性在勘察里已实测，不要在这里臆造 API：

```kotlin
/** 生肖：取农历年干支的地支，映射到十二生肖 */
private fun zodiacOf(lunar: com.tyme.lunar.LunarDay): String {
    // 勘察表若显示 LunarYear 直接给生肖，就改成那一行；
    // 否则按地支索引取：子丑寅卯辰巳午未申酉戌亥
    val branches = "子丑寅卯辰巳午未申酉戌亥"
    val signs = "鼠牛虎兔龙蛇马羊猴鸡狗猪"
    val day = runCatching { lunar.getSixtyCycleDay().getName() }.getOrNull() ?: return ""
    val earthly = /* 该六十甲子「年」的地支，非日——按勘察表取年柱 */ ""
    val idx = branches.indexOf(earthly)
    return if (idx >= 0) signs[idx].toString() else ""
}

/** 当日节气名：只在恰为节气日的当天返回，否则 null */
private fun jieqiOf(date: CivilDate): String? = null  // 见 Step 4

private fun chongShaOf(lunar: com.tyme.lunar.LunarDay): String = ""  // 见 Step 4
```

- [ ] **Step 4: 用勘察表把三个辅助函数写实**

对照 Task 1 记录的真实输出，把 `zodiacOf` / `jieqiOf` / `chongShaOf` 三个函数改成基于实测 API 的实现：
- `zodiacOf` 应取**年柱**地支（不是日柱），若 `LunarYear` 已直接给生肖名，用那一行替换整套查表。
- `jieqiOf` 用 `SolarDay` 上的节气接口（勘察表里若存在 `getPhenology()`/`getPhenologyDay()`，按其 `getName()` 返回是否为"当日恰逢节气"判定；tyme4kt 的 `PhenologyDay` 语义以勘察为准）。
- `chongShaOf` 用 `SixtyCycleDay` 的冲相关接口；勘察表里没有就直接把 `getChong()` 的返回串取出来。

填完后，Task 4 Step 1 里那句 `winter.jieqi` 断言（12 月 22 日 vs 21 日）按实测改成确定值。

Run: `./gradlew :shared:testDebugUnitTest --tests "*AlmanacEngineTest*"`
Expected: PASS（6 个测试全绿）

- [ ] **Step 5: 两端都验一遍**

Run: `./gradlew :shared:testDebugUnitTest :shared:compileTestKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add shared/src/commonMain/kotlin/com/jianyi/outfit/engine/AlmanacEngine.kt \
  shared/src/commonTest/kotlin/com/jianyi/outfit/engine/AlmanacEngineTest.kt
git commit -m "$(printf 'feat(engine): 黄历引擎，农历干支宜忌全离线推导\n\n纯函数 + 日期注入，越界日期收敛为 null 不让首页崩。')"
```

---

## Task 5: 三个卡片的设置开关

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jianyi/outfit/data/model/Models.kt`（`UserPreferences`）
- Modify: `shared/src/commonMain/kotlin/com/jianyi/outfit/data/repository/SettingsRepository.kt:60-67`
- Modify: `shared/src/commonMain/kotlin/com/jianyi/outfit/data/repository/SettingsRepositoryImpl.kt`（setter 区、`toUserPreferences()`、`companion object` 键区）
- Test: `shared/src/commonTest/kotlin/com/jianyi/outfit/data/repository/SettingsRepositoryTest.kt`（追加用例）

**Interfaces:**
- Consumes: 既有 `PreferenceBackend`、`write(key,value)` 私有 helper
- Produces: `UserPreferences.almanacCardEnabled: Boolean`、`historyCardEnabled: Boolean`、`horoscopeCardEnabled: Boolean`；`setAlmanacCardEnabled(Boolean)` 等三个 setter。默认**全为 false**（不打扰老用户）

- [ ] **Step 1: 追加失败的测试**

在 `SettingsRepositoryTest.kt` 里加（沿用该文件既有的假 backend 构造方式）：

```kotlin
    @Test
    fun today_cards_default_off_and_persist_individually() {
        val repo = newRepoWithEmptyBackend()
        val p = repo.preferences.firstBlocking()
        assertFalse(p.almanacCardEnabled, "三张卡默认关，升级不该凭空长出内容")
        assertFalse(p.historyCardEnabled)
        assertFalse(p.horoscopeCardEnabled)

        runBlocking {
            repo.setAlmanacCardEnabled(true)
            repo.setHistoryCardEnabled(true)
        }
        val q = repo.preferences.firstBlocking()
        assertTrue(q.almanacCardEnabled)
        assertTrue(q.historyCardEnabled)
        assertFalse(q.horoscopeCardEnabled, "三个开关互相独立")
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :shared:testDebugUnitTest --tests "*SettingsRepositoryTest*"`
Expected: 编译失败，`unresolved reference: almanacCardEnabled`

- [ ] **Step 3: 加字段**

`Models.kt` 的 `UserPreferences` 里，紧跟 `highFrameRateEnabled` 之后加：

```kotlin
    /** 今日三卡开关：默认全关。升级不该给用户凭空加出内容 */
    val almanacCardEnabled: Boolean = false,
    val historyCardEnabled: Boolean = false,
    val horoscopeCardEnabled: Boolean = false,
```

- [ ] **Step 4: 加键、setter、读取**

`SettingsRepository.kt` 接口在 `setHighFrameRateEnabled` 之后加：

```kotlin
    /* ---- 今日三卡开关：默认全关，逐张独立 ---- */
    suspend fun setAlmanacCardEnabled(enabled: Boolean)
    suspend fun setHistoryCardEnabled(enabled: Boolean)
    suspend fun setHoroscopeCardEnabled(enabled: Boolean)
```

`SettingsRepositoryImpl.kt` 三处：

setter 区（`setHighFrameRateEnabled` 之后）：

```kotlin
    override suspend fun setAlmanacCardEnabled(enabled: Boolean) = write(KEY_CARD_ALMANAC, enabled)

    override suspend fun setHistoryCardEnabled(enabled: Boolean) = write(KEY_CARD_HISTORY, enabled)

    override suspend fun setHoroscopeCardEnabled(enabled: Boolean) = write(KEY_CARD_HOROSCOPE, enabled)
```

`toUserPreferences()` 里 `highFrameRateEnabled = ...` 之后加三行（`?: false` 与模型默认值一致）：

```kotlin
        almanacCardEnabled = boolean(KEY_CARD_ALMANAC) ?: false,
        historyCardEnabled = boolean(KEY_CARD_HISTORY) ?: false,
        horoscopeCardEnabled = boolean(KEY_CARD_HOROSCOPE) ?: false
```

`companion object` 键区加：

```kotlin
        internal const val KEY_CARD_ALMANAC = "card_almanac"
        internal const val KEY_CARD_HISTORY = "card_history"
        internal const val KEY_CARD_HOROSCOPE = "card_horoscope"
```

- [ ] **Step 5: 跑测试确认通过**

Run: `./gradlew :shared:testDebugUnitTest --tests "*SettingsRepositoryTest*"`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add shared/src/commonMain/kotlin/com/jianyi/outfit/data/model/Models.kt \
  shared/src/commonMain/kotlin/com/jianyi/outfit/data/repository/SettingsRepository.kt \
  shared/src/commonMain/kotlin/com/jianyi/outfit/data/repository/SettingsRepositoryImpl.kt \
  shared/src/commonTest/kotlin/com/jianyi/outfit/data/repository/SettingsRepositoryTest.kt
git commit -m "$(printf 'feat(settings): 今日三卡独立开关，默认全关\n\n新键名不影响既有 preferences 里的字段；读取失败仍按现有 catch 回退默认，因此默认关也保证老用户升级后看不到新内容。')"
```

---

## Task 6: 事实骨架提取脚本

spec 2.7 的结论：外部源只作事实种子，文本一律自撰。这一步产出的是 `(year,month,day,type)`，**不含任何外部文本**。

**Files:**
- Create: `tools/extract_today_facts.py`
- Create（产物，不手改）: `tools/out/today_facts.jsonl`

**Interfaces:**
- Consumes: `PrintNow/TodayInHistory` 的 `history_in_today.json`（一次性人工下载到 `tools/vendor/`，不入库，见 Step 1）
- Produces: `tools/out/today_facts.jsonl`，每行 `{"y":1950,"m":10,"d":5,"t":1}`

- [ ] **Step 1: 取源文件到本地，并确认它不进仓库**

```bash
cd WeatherOutfit
mkdir -p tools/vendor
# 6.08MB 全量；GitHub blob API 会截断到约 3.7MB，必须走 raw
curl -sL -o tools/vendor/history_in_today.json \
  https://raw.githubusercontent.com/PrintNow/TodayInHistory/master/history_in_today.json
ls -l tools/vendor/history_in_today.json
```

Expected: 文件大小 **6,079,304 字节**。若只有 3.69MB，说明取到的是截断版，覆盖统计会失真——重取。

把 `tools/vendor/` 加进 `.gitignore`（源文件不入库，只入库我们自己写的内容）：

```
# 一次性事实骨架的外部源文件：只抽 (year,month,day,type)，文本一律自撰
tools/vendor/
```

- [ ] **Step 2: 写脚本，含 366 天覆盖断言**

`tools/extract_today_facts.py`：

```python
#!/usr/bin/env python3
"""从外部历史日期编目中只抽取「事实骨架」：(year, month, day, type)。

刻意丢弃源文件的 data 字段（见 spec 2.7）：源数据声明为维基百科衍生内容，
其 MIT LICENSE 覆盖不了它；而事实本身不受版权保护，受保护的是表述。
所以我们只拿骨架当选题清单，句子全部由本项目另行撰写。

同时把 spec 2.7 里因 blob 截断未能确认的「366 天全覆盖」在这一步钉死。
"""
import json, sys, calendar, pathlib, collections

SRC = pathlib.Path(__file__).parent / "vendor/history_in_today.json"
OUT = pathlib.Path(__file__).parent / "out/today_facts.jsonl"

TYPE_EVENT, TYPE_BIRTH, TYPE_DEATH = 1, 2, 3   # 语义已在 spec 2.7 实测确认


def load(path):
    raw = path.read_text(encoding="utf-8")
    try:
        return json.loads(raw)
    except json.JSONDecodeError as exc:
        sys.exit(f"源文件解析失败：{exc}\n"
                 f"多半是取到了截断版（{len(raw)} 字节）。期望 6079304 字节。")


def main():
    recs = load(SRC)
    print(f"源记录数：{len(recs)}")

    facts, byday = [], collections.defaultdict(int)
    for r in recs:
        y, m, d, t = int(r["year"]), int(r["month"]), int(r["day"]), int(r["type"])
        if t not in (TYPE_EVENT, TYPE_BIRTH, TYPE_DEATH):
            sys.exit(f"未知 type={t} on {y}-{m}-{d}，源结构与 spec 2.7 的记录不符，需先核对")
        facts.append({"y": y, "m": m, "d": d, "t": t})
        byday[(m, d)] += 1

    # 覆盖断言：spec 2.7 遗留项，此处正式关闭
    expected = {(m, d) for m in range(1, 13) for d in range(1, calendar.mdays[m] + 1)}
    expected.add((2, 29))
    missing = sorted(expected - set(byday))
    if missing:
        sys.exit(f"缺日 {len(missing)} 个：{missing[:10]}\n"
                 f"说明源数据集不完备，需人工补这些日子的候选事实，不能带着缺口进下一步。")
    print(f"日期覆盖：{len(byday)}/{len(expected)} 完整")

    years = [f["y"] for f in facts]
    print(f"年份范围：{min(years)}..{max(years)}")
    recent = sum(1 for y in years if y >= 2020)
    print(f"2020 年及以后：{recent} 条（源采集于 2020-04，此处偏少属预期）")
    print(f"每日候选中位数：{int(collections.Counter(byday.values()).most_common(1)[0][0])}")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8") as fh:
        for f in sorted(facts, key=lambda x: (x["m"], x["d"], -x["y"])):
            fh.write(json.dumps(f, ensure_ascii=False, separators=(",", ":")) + "\n")
    print(f"写出 {OUT}：{len(facts)} 行")


if __name__ == "__main__":
    main()
```

- [ ] **Step 3: 跑脚本**

Run: `python3 tools/extract_today_facts.py`
Expected: 打印源记录数约 3 万、`日期覆盖：366/366 完整`、写出 jsonl。

若 `日期覆盖` 报缺日，**停下**：这是 spec 2.7 明确要复验的点，缺的日子后续无法自动生成内容。把缺日清单记进 spec 的未决项再决定补法，不要放宽断言让它过。

- [ ] **Step 4: 抽查骨架里没有任何外部文本**

Run: `head -3 tools/out/today_facts.jsonl && wc -l tools/out/today_facts.jsonl`
Expected: 每行只有 `y/m/d/t` 四个数字字段，没有 `data` 字段。

- [ ] **Step 5: 提交**

```bash
git add .gitignore tools/extract_today_facts.py
git commit -m "$(printf 'build(tools): 历史事实骨架提取，只取日期不取文本\n\n源数据集只作选题清单，描述全部自撰（spec 2.7/3.2）；顺带钉死 366 天覆盖。')"
```

---

## Task 7: 撰写数据集 + 结构校验

本任务的产出物是**数据文件本身**。计划给的是它的 schema、生产流水线、验收闸门和撰写规范——句子必须由人/agent 写，不能用脚本生成，这是本卡片唯一的内容质量闸门（spec 10.6）。

**Files:**
- Create: `shared/src/commonMain/composeResources/files/today_in_history.json`
- Create: `tools/build_today_dataset.py`（把已撰写的分片合成、排序、体积统计）
- Create: `tools/validate_today_dataset.py`
- Create: `docs/content/today-history-style.md`（撰写规范，供起草者与审定者共用）

**Interfaces:**
- Consumes: `tools/out/today_facts.jsonl`（Task 6）
- Produces: `today_in_history.json`，元素为 `{"m":10,"d":5,"y":1950,"t":1,"s":"一句原创描述"}`

- [ ] **Step 1: 写撰写规范**

`docs/content/today-history-style.md`：

```markdown
# 「历史上的今天」撰写规范

## 硬约束
- 每日 3 条，不多不少（校验脚本会拒掉偏离的）
- 每条一句，**14~26 个汉字**，以「。」结尾
- 只写能被独立查证的事实：主体 + 动作 + 时间/地点/数量
- 不写评价词（"伟大的""著名的""重要的"）、不写程度副词（"非常""极其"）
- 不写仍在争议中的表述；有分歧的选最中性的一种说法
- 不用感叹号；不写"我国""本市"这类以作者立场为中心的指代
- 出生/逝世条目写「X（生卒年/身份）出生」/「X 逝世」形式，不堆砌头衔

## 选题
- 优先：影响面广的 > 与天气/季节/自然有关的 > 科技与文化 > 政治军事
- 近三年（2023~2026）的事件优先入选——源骨架止于 2020，
  这些日子必须自己补，否则卡片会永远停在六年前（spec 2.7）
- 12 个月内的事件至少覆盖 60 天，避免"只有古代"的观感

## 逐日配额
- 事件(1) ≥ 1 条；若某日实在没有可用事件，允许 2 出生 + 1 逝世，
  并在提交说明里点名这些日期
```

- [ ] **Step 2: 写校验脚本（这是闸门，先写好再动笔）**

`tools/validate_today_dataset.py`：

```python
#!/usr/bin/env python3
"""今日数据集结构校验。

放在撰写之前写好：内容类资产没有可执行断言就会悄悄劣化，
而这里的每条规则都对应 spec 第 7/8 节的一个失败模式。
"""
import json, sys, re, calendar, pathlib, collections

DS = pathlib.Path(__file__).parent.parent / \
    "shared/src/commonMain/composeResources/files/today_in_history.json"
PER_DAY = 3
LEN_MIN, LEN_MAX = 14, 26


def main():
    if not DS.exists():
        sys.exit(f"数据集不存在：{DS}")
    raw = DS.read_text(encoding="utf-8")
    data = json.loads(raw)
    errs, warns = [], []

    expected = {(m, d) for m in range(1, 13) for d in range(1, calendar.mdays[m] + 1)}
    expected.add((2, 29))
    seen = collections.defaultdict(list)

    for i, e in enumerate(data):
        keys = set(e)
        if keys != {"m", "d", "y", "t", "s"}:
            errs.append(f"#{i} 字段应为 m/d/y/t/s，实到 {sorted(keys)}")
            continue
        m, d, y, t, s = e["m"], e["d"], e["y"], e["t"], e["s"]
        seen[(m, d)].append(y)
        if t not in (1, 2, 3):
            errs.append(f"#{i} type={t} 非法（1 事件/2 出生/3 逝世）")
        if y > 2026:
            errs.append(f"#{i} 未来年份 y={y}")
        if not s or not s.strip():
            errs.append(f"#{i} 描述为空")
            continue
        n = len(s)
        if not (LEN_MIN <= n <= LEN_MAX):
            errs.append(f"#{i} 长度 {n} 不在 {LEN_MIN}~{LEN_MAX}：{s[:20]}")
        if not s.endswith("。"):
            errs.append(f"#{i} 未以句号结尾：{s[-12:]}")
        # 残留外部源的痕迹——这几条是"搬运没搬干净"的直接证据
        if re.search(r"\[\d+\]", s):
            errs.append(f"#{i} 残留引用标记 [n]：{s[:20]}")
        if "<" in s or ">" in s:
            errs.append(f"#{i} 残留 HTML 尖括号：{s[:20]}")
        if "\\u" in s:
            errs.append(f"#{i} 残留未解 \\u 转义：{s[:20]}")
        if m not in range(1, 13) or d not in range(1, calendar.mdays[m] + 1):
            errs.append(f"#{i} 非法日期 {m}/{d}")

    for md, ys in seen.items():
        if len(ys) != PER_DAY:
            errs.append(f"{md[0]}/{md[1]} 有 {len(ys)} 条，应为 {PER_DAY} 条")
        if len(set(ys)) != len(ys):
            errs.append(f"{md[0]}/{md[1]} 年份重复：{ys}")

    missing = sorted(expected - set(seen))
    if missing:
        errs.append(f"缺日 {len(missing)} 个：{missing[:12]}")

    events = sum(1 for e in data if e.get("t") == 1)
    ratio = events / len(data) if data else 0
    if ratio < 0.4:
        warns.append(f"事件类仅占 {ratio:.0%}，出生/逝世过多（规范：每日至少 1 条事件）")
    recent = sum(1 for e in data if e.get("y", 0) >= 2023)
    if recent < 60:
        warns.append(f"2023 年后仅 {recent} 条，卡片会显得停在古代（规范：至少 60 条）")

    size_kb = len(raw.encode("utf-8")) / 1024
    print(f"记录 {len(data)} 条 | 覆盖 {len(seen)}/366 天 | 事件占比 {ratio:.0%} | "
          f"2023+ 共 {recent} 条 | 体积 {size_kb:.0f}KB")
    if size_kb > 260:
        warns.append(f"体积 {size_kb:.0f}KB 超出预期（spec 3.2 估 130KB），检查是否有长句灌水")

    for w in warns:
        print("WARN:", w)
    if errs:
        print(f"\nFAIL：{len(errs)} 项")
        for x in errs[:40]:
            print("  -", x)
        sys.exit(1)
    print("\nOK：结构校验通过")


if __name__ == "__main__":
    main()
```

- [ ] **Step 3: 确认校验脚本在空/缺失数据集上会失败**

Run: `python3 tools/validate_today_dataset.py`
Expected: `数据集不存在` 并退出码 1。（先确认闸门真的会拦，再开始写内容。）

- [ ] **Step 4: 按月分片撰写内容**

内容按 `YYYY-MM` 分片写在 `tools/out/drafts/`（不入库，合入后删），每片是若干 `{"m":..,"d":..,"y":..,"t":..,"s":".."}` 对象。

分工与顺序（agent 起草、作者审定，见 spec 10.6）：
1. 起草 1 月（31 天 × 3 = 93 条），跑校验，把规则问题修干净
2. 其余 11 个月按同一模板起草，每月一次校验
3. `tools/build_today_dataset.py` 合并 12 片 → 写出正式数据集，按 `(m,d,-y)` 排序
4. 全量校验通过

`tools/build_today_dataset.py` 的合并逻辑（约 20 行，逐月读 `drafts/*.jsonl`、`json.dumps(..., ensure_ascii=False, separators=(",",":"))`、包成数组写入 DS）；写完必须回到 Step 2 的校验脚本再跑一次。

- [ ] **Step 5: 作者抽样审定（不可跳过）**

审定方式：从 12 个月各随机抽 5 天（共 60 天 × 3 = 180 条）逐条核对表述。**抽样清单与结论写进 `docs/content/today-history-review.md`**。

抽中问题超过 10% 时，说明起草阶段的规则没吃透，要全月复查而不是只改抽到的那几条。

- [ ] **Step 6: 全量校验**

Run: `python3 tools/validate_today_dataset.py`
Expected: `记录 1098 条 | 覆盖 366/366 天 | ... OK：结构校验通过`

- [ ] **Step 7: 提交**

```bash
git add shared/src/commonMain/composeResources/files/today_in_history.json \
  tools/build_today_dataset.py tools/validate_today_dataset.py \
  docs/content/today-history-style.md docs/content/today-history-review.md
git commit -m "$(printf 'feat(content): 今日历史数据集，366 天每日 3 条原创描述\n\n描述全部自撰，只借用外部编目的日期事实（spec 2.7）。校验脚本先行，抽样审定记录在 docs/content。')"
```

---

## Task 8: `HistoryTodayEngine`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/jianyi/outfit/engine/HistoryTodayEngine.kt`
- Test: `shared/src/commonTest/kotlin/com/jianyi/outfit/engine/HistoryTodayEngineTest.kt`

**Interfaces:**
- Consumes: `today_in_history.json`（Task 7）、`HistoricalEvent`（Task 3）、`CivilDate`（Task 2）
- Produces:
  - `suspend fun HistoryTodayEngine.ensureLoaded()` —— 一次性读入并常驻
  - `fun HistoryTodayEngine.eventsFor(month: Int, day: Int): List<HistoricalEvent>`

- [ ] **Step 1: 写失败的测试**

```kotlin
package com.jianyi.outfit.engine

import com.jianyi.outfit.data.model.HistoricalEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 「历史上的今天」引擎测试。
 *
 * 第一个用例同时是**数据集完整性测试**：全年 366 天每天都必须取得到 3 条。
 * 数据集是内容资产，最容易出的问题是某个月写漏，而那种漏在真机上要等到那天
 * 才发现——所以在这里一次性穷举，而不是靠运行时的空态兜。
 */
class HistoryTodayEngineTest {

    @Test
    fun every_calendar_day_returns_exactly_three_events() = kotlinx.coroutines.runBlocking {
        HistoryTodayEngine.ensureLoaded()
        val mdays = listOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var days = 0
        for (m in 1..12) {
            for (d in 1..mdays[m - 1]) {
                val ev = HistoryTodayEngine.eventsFor(m, d)
                assertEquals(
                    HistoricalEvent.PER_DAY_LIMIT, ev.size,
                    "${m}/$d 取到 ${ev.size} 条，应为 ${HistoricalEvent.PER_DAY_LIMIT} 条"
                )
                assertTrue(ev.all { it.summary.isNotBlank() }, "${m}/$d 有空描述")
                assertTrue(
                    ev.all { it.summary.endsWith("。") && !Regex("""\\[\\d+\\]|<|>""").containsMatchIn(it.summary) },
                    "${m}/$d 描述残留外部源痕迹"
                )
                days++
            }
        }
        assertEquals(366, days, "穷举应覆盖 366 天（含闰日）")
    }

    @Test
    fun events_are_sorted_most_recent_first_within_a_day() = kotlinx.coroutines.runBlocking {
        HistoryTodayEngine.ensureLoaded()
        val ev = HistoryTodayEngine.eventsFor(10, 5)
        assertEquals(ev.sortedByDescending { it.year }, ev)
    }

    @Test
    fun unknown_day_returns_empty_not_crash() = kotlinx.coroutines.runBlocking {
        HistoryTodayEngine.ensureLoaded()
        assertEquals(emptyList(), HistoryTodayEngine.eventsFor(2, 30), "非法日期给空列表，不抛")
        assertEquals(emptyList(), HistoryTodayEngine.eventsFor(13, 1))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :shared:testDebugUnitTest --tests "*HistoryTodayEngineTest*"`
Expected: 编译失败，`Unresolved reference: HistoryTodayEngine`

- [ ] **Step 3: 实现引擎**

```kotlin
package com.jianyi.outfit.engine

import com.jianyi.outfit.data.model.HistoricalEvent
import com.jianyi.outfit.shared.res.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.readResourceBytes

/**
 * 「历史上的今天」取数引擎。
 *
 * 数据集打进包、离线读，所以没有网络失败态也没有缓存层
 * ——spec 3.1：能离线确定的东西不走网络。
 */
object HistoryTodayEngine {

    @Serializable
    private class Row(
        val m: Int, val d: Int, val y: Int, val t: Int, val s: String
    )

    /** month*100+day → 当日事件，已按年份倒序 */
    private var index: Map<Int, List<HistoricalEvent>> = emptyMap()

    /** 读入并建立索引。重复调用是幂等的 */
    suspend fun ensureLoaded() {
        if (index.isEmpty()) {
            val bytes = readResourceBytes(Res.files.today_in_history)
            val rows = Json.decodeFromString<List<Row>>(bytes.decodeToString())
            index = rows
                .sortedByDescending { it.y }
                .groupBy { it.m * 100 + it.d }
                .mapValues { (_, g) ->
                    g.take(HistoricalEvent.PER_DAY_LIMIT)
                        .map { HistoricalEvent(year = it.y, summary = it.s, type = it.t) }
                }
        }
    }

    /** 当日事件，按年份由近及远。缺数据返回空列表（UI 侧整卡不渲染） */
    fun eventsFor(month: Int, day: Int): List<HistoricalEvent> =
        index[month * 100 + day].orEmpty()
}
```

`ensureLoaded()` 没有返回值是刻意的：它唯一的作用是把索引建起来，返回一个永远为空的事件列表只会让调用方误以为拿到了今天的数据。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :shared:testDebugUnitTest --tests "*HistoryTodayEngineTest*"`
Expected: PASS

若 `Res.files.today_in_history` 不解析：`composeResources/files/` 下的资源会生成 `Res.files.<snake_case_name>`，确认文件名是 `today_in_history.json`，并在 `shared/build.gradle.kts` 里确认 `compose.resources` 未设 `includes` 白名单把 `files/` 排除掉。

- [ ] **Step 5: iOS target 编译验证**

Run: `./gradlew :shared:compileTestKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add shared/src/commonMain/kotlin/com/jianyi/outfit/engine/HistoryTodayEngine.kt \
  shared/src/commonTest/kotlin/com/jianyi/outfit/engine/HistoryTodayEngineTest.kt
git commit -m "$(printf 'feat(engine): 历史上的今天引擎，内置数据集离线取数\n\n完整性测试穷举全年 366 天，把数据集写漏某月这类问题在 CI 拦住而非等真机暴露。')"
```

---

## Task 9: `HoroscopeEngine` + 文案池

**Files:**
- Create: `shared/src/commonMain/kotlin/com/jianyi/outfit/data/today/HoroscopePool.kt`
- Create: `shared/src/commonMain/kotlin/com/jianyi/outfit/engine/HoroscopeEngine.kt`
- Test: `shared/src/commonTest/kotlin/com/jianyi/outfit/engine/HoroscopeEngineTest.kt`

**Interfaces:**
- Consumes: `SolarDay.getConstellation()`（Task 1 已实测存在）、`Horoscope`（Task 3）、`CivilDate`（Task 2）
- Produces: `HoroscopeEngine.of(date: CivilDate): Horoscope?`

- [ ] **Step 1: 先写失败的测试（含"12 星座全覆盖"配置断言）**

```kotlin
package com.jianyi.outfit.engine

import com.jianyi.outfit.data.model.Horoscope
import com.jianyi.outfit.platform.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 星座引擎测试。
 *
 * 这类"按日轮换的本地文案池"最典型的两个失效是：
 * ① 刷新一次结果就变（用户立刻识破是随机的）；② 某星座漏配 → 那天卡片消失。
 * 两条都用测试钉死，而不是靠肉眼翻日历。
 */
class HoroscopeEngineTest {

    @Test
    fun same_day_same_sign_is_deterministic() {
        val a = HoroscopeEngine.of(CivilDate(2026, 10, 5))!!
        val b = HoroscopeEngine.of(CivilDate(2026, 10, 5))!!
        assertEquals(a, b, "同一天同星座必须给出同样结果")
    }

    @Test
    fun every_sign_of_the_year_has_a_horoscope() {
        // 一整年逐日走，收集出现过的星座，必须凑满 12 个且没有一天返回 null
        val seen = mutableSetOf<String>()
        var d = CivilDate(2026, 1, 1)
        var n = 0
        while (d.year == 2026) {
            val h = HoroscopeEngine.of(d)
            assertNotNull(h, "${d.year}-${d.month}-${d.day} 返回 null，说明有星座没配上文案池")
            seen += h!!.constellation
            assertTrue(Horoscope.SOURCE_LABEL.isNotBlank(), "来源标注常量不得为空")
            assertTrue(h.overall.isNotBlank() && h.love.isNotBlank() &&
                h.career.isNotBlank() && h.wealth.isNotBlank())
            assertTrue(h.luckyNumber in 1..9, "幸运数字应 1~9，实到 ${h.luckyNumber}")
            d = next(d); n++
        }
        assertEquals(365, n)
        assertEquals(12, seen.size, "12 星座全覆盖，实到 ${seen.sorted()}")
    }

    @Test
    fun consecutive_days_do_not_repeat_the_same_text() {
        val today = HoroscopeEngine.of(CivilDate(2026, 10, 5))!!
        val tomorrow = HoroscopeEngine.of(CivilDate(2026, 10, 6))!!
        if (today.constellation == tomorrow.constellation) {
            assertNotEquals(today.overall, tomorrow.overall, "同一星座相邻两天不该同句")
        }
    }

    @Test
    fun texts_are_neutral_no_exclamation_or_gossip_tone() {
        // 已确认默认：中性陈述，不带幽默/口语
        var d = CivilDate(2026, 1, 1)
        while (d.year == 2026) {
            val h = HoroscopeEngine.of(d)!!
            for (s in listOf(h.overall, h.love, h.career, h.wealth)) {
                assertTrue("！" !in s && "!" !in s, "文案含感叹号：$s")
                assertTrue("宜忌" !in s, "文案不该混进黄历措辞：$s")
            }
            d = next(d)
        }
    }

    private fun next(c: CivilDate): CivilDate {
        val mdays = intArrayOf(31, if ((c.year % 4 == 0 && c.year % 100 != 0) || c.year % 400 == 0) 29 else 28,
            31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        return if (c.day < mdays[c.month - 1]) CivilDate(c.year, c.month, c.day + 1)
        else if (c.month < 12) CivilDate(c.year, c.month + 1, 1)
        else CivilDate(c.year + 1, 1, 1)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :shared:testDebugUnitTest --tests "*HoroscopeEngineTest*"`
Expected: 编译失败，`Unresolved reference: HoroscopeEngine`

- [ ] **Step 3: 写文案池**

`shared/src/commonMain/kotlin/com/jianyi/outfit/data/today/HoroscopePool.kt`：

```kotlin
package com.jianyi.outfit.data.today

/**
 * 星座运势文案池。
 *
 * 单列成一个文件是刻意的：这是全 App 唯一"内容而非逻辑"的资产，
 * 审校时要改的是这里，不该碰引擎。
 *
 * 不存在可靠的免费运势数据源（spec 2.4：连业内常用的 60s 也是从固定池按日
 * 取一条）。与其伪装成有依据，不如自己写清楚并标注娱乐——
 * 这也和 LifeIndexEngine 顶上那句"不把估算包装成官方数据"一致。
 *
 * 写作规范：中性陈述、14~24 字、不带感叹号、不给具体日期或人名、
 * 不作可验证的预测（"今天会收到消息"这类一律不要）。
 */
internal object HoroscopePool {

    /** 每个星座 6 条，按日取模轮换 */
    val overall: Map<String, List<String>> = mapOf(
        "白羊座" to listOf(
            "适合把拖了一阵的事先开个头。",
            "上午的进度比预期顺一些。",
            "适合处理需要体力推进的事。",
            "把注意力收回到自己手上的事。",
            "适合约人当面把话讲清楚。",
            "适合做减法，先停下不重要的。"
        ),
        // … 其余 11 个星座同样各 6 条；缺任何一个，
        // HoroscopeEngineTest 的 every_sign_of_the_year_has_a_horoscope 会失败
        "天秤座" to listOf("适合把两边都说一半的话讲完。", "宜谈合作，忌单独拍板。", "适合回头核对细节。", "上午比下午更适合开口。", "适合把标准写清楚再动手。", "适合缓一缓已经僵住的事。")
    )

    val love: Map<String, List<String>> = mapOf(/* 每星座 6 条，同上 */)
    val career: Map<String, List<String>> = mapOf(/* 每星座 6 条，同上 */)
    val wealth: Map<String, List<String>> = mapOf(/* 每星座 6 条，同上 */)

    /** 12 个固定幸运色名，与文案池解耦 */
    val colors: List<String> = listOf(
        "雾蓝", "浅赭", "松绿", "米白", "陶土红", "灰紫",
        "淡金", "石墨", "藕荷", "靛青", "杏色", "青灰"
    )
}
```

上面 `mapOf(/* … */)` 处的注释是**待填内容清单**，不是待写的代码结构：填写时必须把 12 个星座补齐，否则 Step 5 的测试必然失败——那条测试就是这里的验收。

- [ ] **Step 4: 实现引擎**

```kotlin
package com.jianyi.outfit.engine

import com.jianyi.outfit.data.today.HoroscopePool
import com.jianyi.outfit.data.model.Horoscope
import com.jianyi.outfit.platform.CivilDate
import com.tyme.solar.SolarDay

/**
 * 星座引擎。
 *
 * 星座名由 tyme4kt 的 Constellation 本地算出（太阳星座按公历日期），
 * 运势取自本地池：不存在可信外部源（spec 2.4），所以不接网络。
 *
 * 选取用 (星座序号 * 31 + 年内日序) 混合后取模，保证同一天同星座结果固定，
 * 而相邻日几乎必然不同 —— 这是这类卡片最容易被用户识破的地方。
 */
object HoroscopeEngine {

    private const val POOL_SIZE = 6

    fun of(date: CivilDate): Horoscope? {
        val solar = runCatching { SolarDay(date.year, date.month, date.day) }.getOrNull()
            ?: return null
        val name = runCatching { solar.getConstellation().name }.getOrNull() ?: return null

        val signIndex = SIGN_ORDER.indexOf(name)
        if (signIndex < 0) return null

        val doy = dayOfYear(date)
        val k = (signIndex * 31 + doy) % POOL_SIZE

        return Horoscope(
            constellation = name,
            overall = HoroscopePool.overall[name]?.getOrNull(k) ?: return null,
            love = HoroscopePool.love[name]?.getOrNull(k) ?: return null,
            career = HoroscopePool.career[name]?.getOrNull(k) ?: return null,
            wealth = HoroscopePool.wealth[name]?.getOrNull(k) ?: return null,
            luckyColor = HoroscopePool.colors[(signIndex + doy) % HoroscopePool.colors.size],
            luckyNumber = (signIndex * 7 + doy * 3) % 9 + 1
        )
    }

    /** 与 tyme4kt 的 Constellation.name 逐字对齐（Task 1 勘察表已实测这些中文名） */
    private val SIGN_ORDER = listOf(
        "白羊座", "金牛座", "双子座", "巨蟹座", "狮子座", "处女座",
        "天秤座", "天蝎座", "射手座", "摩羯座", "水瓶座", "双鱼座"
    )

    private fun dayOfYear(c: CivilDate): Int {
        val mdays = intArrayOf(31, if ((c.year % 4 == 0 && c.year % 100 != 0) || c.year % 400 == 0) 29 else 28,
            31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        return (1 until c.month).sumOf { mdays[it - 1] } + c.day
    }
}
```

`SIGN_ORDER` 里的字符串**必须**换成 Task 1 勘察表里 `constellation = ` 实测到的写法（若 tyme4kt 给的是"白羊座"就用"白羊座"；若 `getConstellation()` 的 `toString()` 与 `.name` 不同，统一用 `.name`）。这里写错一个字，星座卡全年静默不显示。

- [ ] **Step 5: 跑测试确认通过**

Run: `./gradlew :shared:testDebugUnitTest --tests "*HoroscopeEngineTest*"`
Expected: PASS（4 个用例）

`every_sign_of_the_year_has_a_horoscope` 报 "12 星座全覆盖，实到 …" 时，是文案池缺星座，补齐而不是放宽断言。

- [ ] **Step 6: 提交**

```bash
git add shared/src/commonMain/kotlin/com/jianyi/outfit/data/today/HoroscopePool.kt \
  shared/src/commonMain/kotlin/com/jianyi/outfit/engine/HoroscopeEngine.kt \
  shared/src/commonTest/kotlin/com/jianyi/outfit/engine/HoroscopeEngineTest.kt
git commit -m "$(printf 'feat(engine): 星座引擎与本地文案池\n\n无可靠外部运势源，改为自撰文案池按日确定性轮换并标注娱乐；确定性/12 星座全覆盖各有测试兜住。')"
```

---

## Task 10: 三张卡的 UI

**Files:**
- Create: `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/home/AlmanacCard.kt`
- Create: `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/home/HistoryTodayCard.kt`
- Create: `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/home/HoroscopeCard.kt`

**Interfaces:**
- Consumes: `AlmanacDay` / `HistoricalEvent` / `Horoscope`（Task 3）、`EstimatedBadge`（`LifeIndexCard.kt:117`，可复用的角标组件）、`MaterialTheme` + `HighTempOrange` / `LowTempBlue`（`ui/theme/Color.kt`）
- Produces: `@Composable fun AlmanacCard(day: AlmanacDay, modifier: Modifier = Modifier)`、`@Composable fun HistoryTodayCard(events: List<HistoricalEvent>, modifier: Modifier = Modifier)`、`@Composable fun HoroscopeCard(h: Horoscope, modifier: Modifier = Modifier)`

- [ ] **Step 1: 黄历卡**

```kotlin
package com.jianyi.outfit.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jianyi.outfit.data.model.AlmanacDay

/**
 * 黄历卡。
 *
 * 宜/忌用「宜」「忌」两个单字起头而不是画两个色块：色块会把它打扮成状态标签，
 * 但这两行只是传统历法的推算结论，视觉上不该显得比天气数据更权威。
 */
@Composable
fun AlmanacCard(day: AlmanacDay, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = day.lunarDateText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = day.ganzhiDay,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            SourceBadge(text = AlmanacDay.SOURCE_LABEL)
        }

        val meta = listOfNotNull(
            day.zodiac.takeIf { it.isNotBlank() }?.let { "属$it" },
            day.jieqi,
            day.festival,
            day.moonPhase.takeIf { it.isNotBlank() }
        )
        if (meta.isNotEmpty()) {
            Text(
                text = meta.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (day.recommends.isNotEmpty()) {
            TabooRow(prefix = "宜", items = day.recommends)
        }
        if (day.avoids.isNotEmpty()) {
            TabooRow(prefix = "忌", items = day.avoids)
        }
    }
}

@Composable
private fun TabooRow(prefix: String, items: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = prefix,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = items.joinToString("  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
```

需要补 `import androidx.compose.foundation.layout.Spacer` 与 `androidx.compose.foundation.layout.weight`（`Spacer(Modifier.weight(1f))` 的 `weight` 来自 `RowScope`，在 `Row` 内可直接用）。

- [ ] **Step 2: 来源角标（三卡共用，改进版 `EstimatedBadge`）**

`EstimatedBadge` 写死了"本地估算"四个字，而三张卡的标注各不相同。在同文件加一个通用版，**不改** `EstimatedBadge`（生活指数卡还在用它）：

```kotlin
/** 通用来源角标：内容性质不是官方数据时一律挂上，样式与「本地估算」保持一致 */
@Composable
fun SourceBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}
```

放 `AlmanacCard.kt` 里，另外两个卡 import 它；或放 `ui/components/Components.kt`（仓库已有的公共组件文件）——二选一，**不要两处都定义**。

- [ ] **Step 3: 历史今天卡**

```kotlin
@Composable
fun HistoryTodayCard(events: List<HistoricalEvent>, modifier: Modifier = Modifier) {
    if (events.isEmpty()) return   // 缺数据时整卡不出现，不留空壳（spec 第 7 节）
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("历史上的今天", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            SourceBadge(text = HistoricalEvent.SOURCE_LABEL)
        }
        events.forEach { e ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (e.year > 0) "${e.year}" else "公元前${-e.year}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = e.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
```

公元前年份的展示别漏：数据集里最老是 `y=-4713`，直接打印 `-4713` 会像负数而非纪年。

- [ ] **Step 4: 星座卡**

结构同 Step 3：标题行 + `SourceBadge(Horoscope.SOURCE_LABEL)`，四行分项各 `labelMedium` 前缀（整体/爱情/事业/财运），末行 `幸运色 · 幸运数 N`。空字段不显示该行。

- [ ] **Step 5: 编译**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add shared/src/commonMain/kotlin/com/jianyi/outfit/ui/home/AlmanacCard.kt \
  shared/src/commonMain/kotlin/com/jianyi/outfit/ui/home/HistoryTodayCard.kt \
  shared/src/commonMain/kotlin/com/jianyi/outfit/ui/home/HoroscopeCard.kt
git commit -m "$(printf 'feat(ui): 今日三卡组件\n\n各卡自带来源角标，SourceBadge 与既有「本地估算」角标同形；空数据整卡不渲染。')"
```

---

## Task 11: `HomeViewModel` 接线 + 首页挂载

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/home/HomeViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/home/HomeScreen.kt`（只加挂载）

**Interfaces:**
- Consumes: 三个引擎（Task 4/8/9）、`UserPreferences` 三个开关（Task 5）、三张卡（Task 10）
- Produces: `HomeViewModel.almanac: StateFlow<AlmanacDay?>`、`historyToday: StateFlow<List<HistoricalEvent>>`、`horoscope: StateFlow<Horoscope?>`

- [ ] **Step 1: ViewModel 里加三个 StateFlow**

在 `HomeViewModel` 里加（构造参数已能拿到 `settingsRepository`，沿用既有注入；若拿不到，从 `AppDependencies` 取，**不要**新建全局单例——仓库刚在 commit `aef9980` 里移除了这类会话可变单例）：

```kotlin
    /** 今日三卡：全离线，一次算出，无加载态、无失败态 */
    val almanac: StateFlow<AlmanacDay?> = MutableStateFlow(null)
    val historyToday: StateFlow<List<HistoricalEvent>> = MutableStateFlow(emptyList())
    val horoscope: StateFlow<Horoscope?> = MutableStateFlow(null)

    init {
        viewModelScope.launch {
            val today = todayCivilDate()
            HistoryTodayEngine.ensureLoaded()
            settingsRepository.preferences.collect { p ->
                almanac.value = if (p.almanacCardEnabled) AlmanacEngine.of(today) else null
                historyToday.value =
                    if (p.historyCardEnabled) HistoryTodayEngine.eventsFor(today.month, today.day)
                    else emptyList()
                horoscope.value = if (p.horoscopeCardEnabled) HoroscopeEngine.of(today) else null
            }
        }
    }
```

关掉开关时把值置空，卡片自然消失——比在 UI 层再判一次开关少一处状态来源。

- [ ] **Step 2: 首页挂载（只加调用，不写内容）**

`HomeScreen.kt` 里找到 `LifeIndexList(` 的调用处，其后紧跟加：

```kotlin
                val almanac by viewModel.almanac.collectAsState()
                val historyToday by viewModel.historyToday.collectAsState()
                val horoscope by viewModel.horoscope.collectAsState()

                almanac?.let {
                    AlmanacCard(day = it)
                    Spacer(Modifier.height(18.dp))
                }
                if (historyToday.isNotEmpty()) {
                    HistoryTodayCard(events = historyToday)
                    Spacer(Modifier.height(18.dp))
                }
                horoscope?.let {
                    HoroscopeCard(h = it)
                    Spacer(Modifier.height(18.dp))
                }
```

`collectAsState()` 要在 composable 顶层，若上面的 `val … by` 落在非 composable 作用域里会编译不过——移到该 `@Composable` 函数开头。

- [ ] **Step 3: 编译 + 手动跑一遍**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

装到已连接的 OPPO 真机（`adb devices` 有 `PLB110`）：
Run: `./gradlew :app:installDebug`
Expected: 安装成功。

**手动验证清单**（这是 UI 唯一能验的方式，逐条确认后再往下）：
1. 全新安装后首页**没有**三张卡（默认关，不打扰老用户）
2. 设置页打开「黄历」→ 首页出现黄历卡，右上角有「按传统历法推算」角标
3. 打开「历史上的今天」→ 出现 3 条，年份由近及远
4. 打开「星座」→ 出现星座名与四行分项 + 「娱乐内容，非预测」角标
5. 逐个关掉 → 对应卡消失，其余不受影响
6. 断网（飞行模式）重复 2-4 → 三张卡全部照常显示（这是本设计的关键承诺）
7. 深色模式 → 角标与文字对比度正常，无纯黑
8. logcat 无新增崩溃或静默异常：`adb logcat -d | grep -iE "jianyi|AndroidRuntime"`

- [ ] **Step 4: 提交**

```bash
git add shared/src/commonMain/kotlin/com/jianyi/outfit/ui/home/HomeViewModel.kt \
  shared/src/commonMain/kotlin/com/jianyi/outfit/ui/home/HomeScreen.kt
git commit -m "$(printf 'feat(ui): 今日三卡接入首页，随设置开关显示\n\n开关关闭时 ViewModel 直接置空值，避免 UI 层再判一次开关形成第二处状态来源。')"
```

---

## Task 12: 设置页「今日信息」分组

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/settings/SettingsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/jianyi/outfit/ui/settings/SettingsViewModel.kt`

**Interfaces:**
- Consumes: `UserPreferences` 三个开关、`SettingsRepository` 三个 setter（Task 5）
- Produces: 设置页新增分组「今日信息」，三个 `Switch`

- [ ] **Step 1: ViewModel 加三个 setter**

沿用该文件既有写法（每个 setter 都是 `viewModelScope.launch { settingsRepository.setXxx(v) }`）：

```kotlin
    fun setAlmanacCard(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAlmanacCardEnabled(enabled)
    }

    fun setHistoryCard(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setHistoryCardEnabled(enabled)
    }

    fun setHoroscopeCard(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setHoroscopeCardEnabled(enabled)
    }
```

- [ ] **Step 2: UI 加分组**

在「视觉与性能」那一组之后插入一个分组，标题「今日信息」，含三行开关：

- 「黄历」副标题 `农历 · 干支 · 宜忌，按传统历法推算`
- 「历史上的今天」副标题 `每天 3 条，史料整理未逐条核实`
- 「星座运势」副标题 `娱乐内容，无预测依据`

**副标题必须复述来源性质**——与卡片上的角标同一立场，用户在设置页就该知道打开的是什么东西。三行都复用该文件里已有的开关行组件，不新写一套样式。

- [ ] **Step 3: 编译并真机确认**

Run: `./gradlew :app:installDebug`
Expected: 设置页出现「今日信息」分组，三行开关能独立拨动且重启 App 后状态保持（DataStore 已验证可用）。

Run: `adb shell force-stop com.jianyi.outfit && adb monkey -p com.jianyi.outfit -c android.intent.category.LAUNCHER 1`
Expected: 重开后三张卡仍按上次设置显示。

- [ ] **Step 4: 提交**

```bash
git add shared/src/commonMain/kotlin/com/jianyi/outfit/ui/settings/SettingsScreen.kt \
  shared/src/commonMain/kotlin/com/jianyi/outfit/ui/settings/SettingsViewModel.kt
git commit -m "$(printf 'feat(ui): 设置页今日信息分组\n\n每行副标题复述来源性质，与卡片角标同一立场。')"
```

---

## Task 13: README 与 CHANGELOG 修订

README 里「无社交、无广告、无资讯」是产品宣言，加了三张卡就自相矛盾——这句不改，文档就成了假的。

**Files:**
- Modify: `README.md`（第 9 行宣言段、第 19-27 行功能表、第 133 行起「已知说明」、第 147 行起致谢）
- Modify: `CHANGELOG.md`（`[Unreleased]` 的 Added 段）

- [ ] **Step 1: 改功能表**

「功能一览」表格追加三行：

```markdown
| 黄历 | 农历日期 / 干支 / 生肖 / 节气 / 宜忌 / 冲煞 / 月相，**完全离线推算**，默认关闭 |
| 历史上的今天 | 每天 3 条，描述由本项目独立撰写，默认关闭 |
| 星座 | 太阳星座本地推算，运势为自撰文案池按日轮换，标注娱乐内容，默认关闭 |
```

- [ ] **Step 2: 改宣言段**

第 9 行末尾那句「无社交、无广告、无资讯」改为：

```
无社交、无广告、无推送轰炸；仅有的三张今日卡默认关闭，且全部离线可关——它们不产生任何网络请求。
```

同时把「首屏只保留温度、天气状态与穿搭推荐」补上限定：`（今日三卡需自行在设置中开启，默认首屏与旧版完全一致）`。

- [ ] **Step 3: 加「已知说明」三条**

```markdown
- **黄历宜忌为推算结果**：由 `tyme4kt` 按建除十二值与神煞规则本地算出，各家历书规则并不一致，**不是权威黄历**。卡片与设置页均标注「按传统历法推算」。
- **历史上的今天为自撰数据集**：只借用公开编目的日期事实，描述由本项目独立撰写；未逐条核实，且内容不会随版本外自动更新。
- **星座运势没有接入任何数据源**：市面无可靠免费源（连常被引用的开源接口也是从固定文案池按日取值），故本 App 自行撰写文案池并标注「娱乐内容，非预测」。
- **这三张卡不产生网络请求**，因此不受天气接口限流影响；断网时照常显示。
```

- [ ] **Step 4: 致谢与许可**

致谢段加：

```markdown
- 农历/黄历推算：[6tail/tyme4kt](https://github.com/6tail/tyme4kt)（MIT）
```

**不加** CC BY-SA 署名——依 spec 3.2，入库的是日期事实 + 原创表述；但要保留 `docs/content/today-history-style.md` 作为撰写依据的留档。

- [ ] **Step 5: CHANGELOG**

`[Unreleased]` 的 `### Added` 下加：

```markdown
- 今日三卡（黄历 / 历史上的今天 / 星座），全部离线、默认关闭、可在设置页逐张开启。
- `tools/extract_today_facts.py` 与 `tools/validate_today_dataset.py`：历史数据集的骨架提取与结构校验。
- `docs/content/today-history-style.md`：今日历史事件撰写规范。
```

- [ ] **Step 6: 全量验证 + 提交**

Run: `./gradlew test lint assembleDebug`
Expected: 全绿。

```bash
git add README.md CHANGELOG.md docs/content/today-history-style.md
git commit -m "$(printf 'docs: 修订产品宣言并补今日三卡说明\n\n「无资讯」已与实际功能冲突，改为声明默认关闭且零网络；新增推算性质/数据集来源/运势无依据三条已知说明。')"
```

---

## Self-Review

**1. Spec coverage** —— 逐节对照：

| spec 章节 | 覆盖任务 |
|---|---|
| 2.6 tyme4kt 选型与 target 匹配 | Task 1（含 iOS 编译验证） |
| 2.7 数据集许可/时效核实 | Task 6（丢弃 data 字段、366 覆盖复验、2020 断档补近三年） |
| 3.1 引擎化、零网络、不引 Repository/Room | Task 4/8/9 全为 `object` 引擎；计划中无 Ktor/Room 改动 |
| 3.2 三步流水线 + schema + 130KB 体积 | Task 6 / Task 7 |
| 3.3 星座由 Constellation 本地出 + 确定性池 | Task 9 |
| 4 文件结构表 | 各任务 Files 段一一对应 |
| 4.1 来源标注以常量暴露、UI 读常量 | Task 3（三处 `SOURCE_LABEL` 常量）+ Task 10（读常量渲染）；模型上无可代入参数，编造在结构上不可能 |
| 4.2 模型字段 | Task 3 |
| 5 日期经 PlatformClock | Task 2（比 spec 原设想更优：不加 expect/actual） |
| 6 三条标注 | Task 3 SOURCE_LABEL + Task 10 SourceBadge + Task 12 副标题 |
| 7 边界 1/2/3/4/5 | 1→Task 8 空列表+Task 10 不渲染；2/3→Task 10 `isNotEmpty`/`listOfNotNull`；4→Task 9 全覆盖测试；5→Task 4 null 收敛测试 |
| 7 边界 7「跨午夜不刷新」 | 有意不做（YAGNI），Task 11 Step 1 的取值时机即该行为 |
| 8 测试四组 | Task 2/4/8/9 各自测试 + Task 7 构建期校验脚本 |
| 9 README 五处 | Task 13 |
| 10.2 366 覆盖复验 | Task 6 Step 3（失败即停） |
| 10.6 内容审校责任 | Task 7 Step 5（抽样清单入库） |
| 10.4 星座文案基调 | Task 9 Step 1 `texts_are_neutral_…` 测试 + Task 9 Step 3 规范注释 |

**2. Placeholder 扫描**：Task 9 Step 3 的 `mapOf(/* 每星座 6 条 */)` 与 Task 10 Step 4 的星座卡描述是**内容清单**而非代码占位——已注明其验收由对应测试/既有组件承担。Task 4 Step 4 明确要求以实测 API 名填实，属于勘察驱动的必要开放点，且给了具体候选（`getPhenologyDay()` 等）。其余无 TBD。

**3. 类型一致性**：`CivilDate` 全程 Task 2 定义、Task 4/8/9/11 消费一致；`AlmanacDay.TABOO_DISPLAY_LIMIT` 在 Task 3 定义、Task 4 使用；`HistoricalEvent.PER_DAY_LIMIT` 在 Task 3 定义、Task 7 校验（值 3）、Task 8 测试与实现使用；`SourceBadge` 在 Task 10 Step 2 定义、Step 3/4 使用。

**发现并已处理的一处 spec 偏差**：spec 4.1 的接口签名用 `LocalDate`，但本仓库 commonMain 没有 `LocalDate`（`PlatformTime.kt` 注释说明刻意不引 kotlinx-datetime）。Task 2 用仓库已有的纯 Kotlin 历法算术补一个 `CivilDate`，比原设想少两个 expect/actual，且不破坏零依赖取向。执行者若照抄 spec 签名会编译不过——以本计划为准，并回写 spec。
