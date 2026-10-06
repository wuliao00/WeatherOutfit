# 今日三卡设计：黄历 · 历史上的今天 · 星座运势

日期：2026-10-05
状态：待评审
范围：`shared` 模块新增三个离线内容引擎 + 首页三张可关卡片 + 设置页开关

---

## 1. 背景与目标

现版本首页只回答两个问题：「现在什么天气」和「该穿什么」。本设计在其下方增加三张**可独立关闭**的信息卡，把「今天」这个维度补全：农历黄历、历史上的今天、星座。

关键结论先行：**这三块全部离线实现，整套功能零网络请求、零新增 API 凭证、零限流面。** 这不是妥协，而是调研后的最优解——理由见第 3 节。

同时这构成一次产品宣言修订：README 现有「无社交、无广告、无资讯」，加卡片即自相矛盾，须改措辞（第 9 节）。

---

## 2. 已验证事实（含负面结果，请勿重试）

以下均在本机实测取得，不是推测。记录在此是为了避免后人重复踩。

### 2.1 逆向系统天气：封死，放弃

设备 OPPO PLB110 / ColorOS / Android 15（API 35），未 root，adb shell 为 uid 2000。

- `com.coloros.weather.service` 暴露三个数据 provider，**全部 `exported=true` 且不声明 permission**：
  `com.coloros.weather.service.provider.data`、`com.oplus.weather.service.provider.data`、`com.oplusos.weather.service.provider.data`
  （authority 由 `adb pull` + `aapt2 dump xmltree AndroidManifest.xml` 取得，类名 ≠ authority）
- 表结构极具吸引力：`oppo_weather_info` 含 `life_index_1..9`（附 level/icon）、`sunrise/sunset/moonrise/moonset`、`moon_phase`、`current_uv_index`、AQI 分项、`visibility`、`pressure`；另有 `hourly_forecast_weather`、`minutely_rain`、`weather_warn`、`attent_city`。
- **但任何查询都返回空游标。** logcat 给出根因：

  ```
  WeatherS_ApkSignedCheck: checkCertificateWhiteList: not in inWhiteList android.uid.shell:2000
  WeatherS_WeatherProvider: checkPermission:uid = 2000, result = false
  ```

  dex 中引用 `android.app.OppoWhiteListManager`——按**调用方 APK 签名证书白名单**放行，拒绝时**静默返回空而非抛异常**。
- **方法学教训**：未授权时任意路径（含瞎写的 `definitely_not_real_xyz`）一律返回 `No result found.`，所以 `content query` 的输出**无法**用来判断 URI 路径是否正确，必须配合 logcat。
- 绕过白名单需要 hook 或伪造签名，那已属破坏访问控制，**本项目不做**。合法路径只有向 OPPO 开放平台申请证书加白（需企业资质，个人开源项目实际拿不到）。

### 2.2 抄它的上游：同样封死

APK strings 提取到上游为 `weather.oppomobile.com`（私有网关）、`weather.com`（The Weather Company，需企业合同）、`m.weathercn.com`（中国天气网）。

实测中国天气网公开 JSON：`https://d1.weather.com.cn/sk_2d/101280201.html` → **HTTP 403，Forbid_code 020200**，加 `Referer: https://m.weathercn.com/` 仍 403。2026 年其反爬已生效，此路不通。

### 2.3 系统日历：可用，但本设计不用

`content://com.android.calendar/*` 在 adb 下完全可读（实测拿到真实日程与 `com.heytap` 的生日/纪念日/倒数日日历）。第三方 App 用标准 `READ_CALENDAR` 运行时权限即可，**无需逆向**。

本期不纳入：读取用户日程会影响穿搭推荐，那是另一条独立的产品线（引擎输入信号），与本设计的「可看的内容」目标不同。列为后续候选（第 11 节）。

### 2.4 星座运势：不存在免费可靠源

- apihz **不提供**运势接口。注意其文档站对不存在的路径也返回 HTTP 200，必须比字节数判别（真实页 48177B vs 伪造页 72B）。
- `api.vvhan.com`、`inapi.cn`：DNS 解析失败，域名已死。
- `api.tianapi.com/xingzuo`：返回 `{"code":230,"msg":"key错误或为空"}`，必须 key，且响应自带域名迁移提示。
- **决定性发现**：连业内常用的 `vikiboss/60s`（5.7k★，MIT）自己也是假的——其源码 `getDailyFortune()` 从写死的文案数组按日期 hash 取一条（career/money/love 同构，`/v2/luck` 亦然）。

即：全行业没有可信源，只有"看起来像"的源。据此选择本地文案池（3.3）。

### 2.5 「历史上的今天」候选源逐条实测

| 源 | 实测结果 | 判定 |
|---|---|---|
| `zh/en.wikipedia.org` REST `feed/onthisday/events/{MM}/{DD}` | HTTP 000 超时；对照组 baidu/apihz 均 200 | **否**，大陆不可达，目标用户拿不到 |
| apihz `/api/zici/today.php` | 200，但**一次只返回 1 条随机事件**；连调两次得 1986 亚运会 / 1793 法国大革命 | **否**，做卡片需 N 次请求，公共凭证实测打 3 次即 `code:400, s:7` |
| `60s.viki.moe/v2/today-in-history` | 200，14 条，字段 `title/year/description/event_type/link` | 内容合格，但作者自陈公共域名额度有限仅供调试，且上游为百度百科非公开 CMS |
| `PrintNow/TodayInHistory`（97★，数据源标注维基百科） | `history_in_today.json` **6.0MB**，结构为扁平数组 `{year,month,day,type,data}`，data 为 `\uXXXX` 转义中文 | **只作事实种子，描述自己写**，见 2.7 与 3.2 |

### 2.7 数据集许可与时效核实（已关闭的阻塞项）

针对 2.5 采用的数据集做了逐字核实，结论是它**不能直接入包**。

- **LICENSE**：确为 MIT（(c) 2020 Chuwen，经 GitHub API blob base64 解码确认，1063 字节）。但其授权对象是 "software and associated documentation files"。
- **数据源为作者自认**：`api.php` 第 82 行的响应里写着 `数据源于"维基百科"，经过加工后为您呈现本数据，数据采集于 2020-04-12 12:00…本站不承担任何因数据改变而造成的任何责任`。中文维基百科正文适用 **CC BY-SA 4.0**，因此**作者无权把衍生数据改授为 MIT**——那份 MIT 对 JSON 不成立。
- **暴露面量化**（19,464 条样本，取自约 60% 的截断 blob）：描述长度 median **20 字**、mean 24.3、p90 43；75.5% ≤30 字，96.5% ≤60 字。绝大多数是裸事实（事实不受版权保护，受保护的是表述）。但 **1.6%（约 300 条）残留 `[n]` 维基引用标记**，属逐字搬运的表达。
- **时效是更致命的问题**：`year` 上限为 **2020**，`year >= 2021` 命中 **0 条**。数据断档至今 6 年半，一个 2026 年的 App 里「历史上的今天」永远查不到近六年任何事件。
- **质量**：0.64% 描述为空；58% 不以句号结尾，格式不统一。
- **type 语义（已确认，非推测）**：`1`=事件（7009 条）、`2`=出生（9537）、`3`=逝世（2918，其中 92.8% 命中「逝世/去世/卒」特征词）。
- **日期覆盖**：截断样本内 1 月 1 日至 8 月 27 日每一天均有记录（239/239），缺失的 126 天全在截断点之后，故**极可能是截断而非真实缺口**；全量 366 天覆盖需在实现阶段用完整文件复验。

**决策**：只从中提取 `(year, month, day, type)` —— 这些是不受版权保护的事实 —— 作为骨架；描述文案全部由本项目自行撰写，并补齐 2020-04 至今的事件。这样法律上干净（表述是原创的）、断档问题解决、格式统一可控。代价是约 1100 句撰写工作量（365 天 × 3 条），由 agent 起草、作者审定。

### 2.6 黄历离线库：核实到源码级

`cn.6tail:tyme4kt:1.5.0`（MIT）。已读 Gradle Module Metadata 与 `LunarDay.kt` 源码确认：

- 发布变体：`androidJvm`(aar) / `iosArm64` / `iosSimulatorArm64` / `jvm` / `wasmJs` / common —— 与 `:shared` 现有 target（`androidTarget` + `iosArm64` + `iosSimulatorArm64`）**完全吻合**，且 `lunar-java`（`cn.6tail:lunar`，JVM-only jar）不可用于 commonMain。
- **传递依赖为零**（各变体 `dependencies` 数组为空，metadata 仅 `kotlin-stdlib`）——不会污染已钉死的 Kotlin 2.1.0 / Compose Multiplatform 1.8.2 / Compose BOM 2025.06.01 配套。
- `LunarDay` 暴露：`getSixtyCycle()`、`getDuty()`（建除十二值）、`getTwelveStar()`（黄道黑道）、`getTwentyEightStar()`（二十八宿）、`getPhase()`（月相）、`getPhaseDay()`、`getSixtyCycleDay()`、`getGods()`（吉神宜趋/凶神宜忌）、`getFestival()`（`LunarFestival?`）。
- **宜忌可纯离线算出**：`getRecommends()` / `getAvoids()` 内部实现为 `getSixtyCycleDay().getRecommends()`，即由干支日推得，返回 `List<Taboo>`，无网络。
- 节气算法引自 `sxwnl/sxwnl`。
- **统一入口是 `SolarDay`**（`com/tyme/solar/SolarDay.kt`）：`getLunarDay()` 通往黄历，`getConstellation()` 通往星座。`Constellation`（`com/tyme/culture/Constellation.kt`）**不是 enum**，是基于 name 的类，带 `fromIndex(Int)` / `fromName(String)` 伴生构造，取名字用 `.name`。引擎层不得把它当枚举 switch，否则星座卡静默匹配不到文案池。

`android.icu.util.ChineseCalendar` 不适用于本设计：它是 Android 平台类，进不了 commonMain，且只覆盖农历纪年/月/日/闰月，不含宜忌。

---

## 3. 架构决策

### 3.1 总原则：三块都是「引擎」，不是「数据源」

现有代码里已有这个形状的范例——`engine/LifeIndexEngine.kt`：纯 Kotlin、零 Android 依赖、可直接 JVM 单测、输出领域模型，UI 只负责展示并标注性质。

三个新模块沿用同一形状，因此**不引入 Repository、不引入 Room 表、不引入 Ktor 接口**：

```
没有网络  →  没有失败态  →  没有缓存需求  →  没有退避/限流代码
```

这与天气那条链路（`WeatherApiClient` → `WeatherRepository` → `weather_cache.db`，含 30 分钟 TTL、限流退避、过期回退）形成有意对照：**能离线确定的东西不应该走网络**。收益是首页冷启动少两个必然失败的路径，以及三张卡的逻辑 100% 进 CI。

### 3.2 「历史上的今天」：内置自撰数据集（外部源仅作事实种子）

依 2.7 的核实结论，**不搬运任何外部文本**，而是提取不受版权保护的事实骨架，描述全部自撰。

流水线（三步，前两步是 `tools/` 下的一次性脚本，产物进仓库）：

1. **抽骨架**：`tools/extract_today_facts.py` 读全量 `history_in_today.json`，只输出 `(year, month, day, type)`，丢弃 `data` 字段。产物是事实清单，非派生文本。同时复验 366 天覆盖（2.7 遗留项）。
2. **补近年**：追加 2020-04 至今的事件，按 `(year, month, day, type)` 同构补进清单——这一步是 2.7 里断档问题的正解。
3. **撰描述 + 选题**：每天从候选事实中挑 ≤5 条，各写一句原创中文描述。产出 `shared/src/commonMain/composeResources/files/today_in_history.json`，格式：

   ```json
   [{"m":10,"d":5,"y":1950,"t":1,"s":"一句原创描述"}]
   ```

   字段名压到单字母，因为这是打进 APK 的资产。

- **体积**：1830 条 × ~24 字 ≈ 130KB 原始，APK 压缩后约 40~60KB（早前估的 300~400KB 作废，那是按搬运原文算的）。
- 每日选题数与排序：`type` 优先（事件 > 出生 > 逝世），同级按年份距今远近——近 30 年的事件优先入选，因为对用户更有感知。
- 读取：CMP 资源 `Res.readText("files/today_in_history.json")`；`compose.resources { packageOfResClass = "com.jianyi.outfit.shared.res" }` 已配置，无需改构建脚本。解析用 `kotlinx.serialization`（已在用，1.7.3）。
- **署名**：README 致谢只需说明「事实清单参考了公开的历史日期编目，描述由本项目独立撰写」；因为最终入库的是事实 + 原创表述，**不需要 CC BY-SA 署名**。若实现阶段发现有句子在事实上无法避开原表述（例如极长的专有名词），逐条单独标注。
- **撰写责任**：约 1100 句由 agent 起草、作者审定。审定不是走过场——这是本卡片唯一的内容质量闸门（见第 10 节）。

### 3.3 星座运势：本地文案池

既然不存在可信源，就不伪装存在。采用 60s 同构做法但**明确标注**：

- 星座名由 `tyme4kt` 的 `Constellation` 本地算出（太阳星座按公历日期），不需网络也不需第二个库。
- 运势文案为本仓库手写的固定池，按 `(星座, 日期)` 派生确定性索引，保证**同一天同一星座刷新结果不变**（这条必须有，否则用户一刷新看到不同内容立刻识破是随机的）。
- 池规模：每星座 n 条通用文案 × 分项（整体/爱情/事业/财运）。起步取小值，宁缺勿滥。
- 展示措辞固定为娱乐向（第 6 节）。

---

## 4. 组件设计

新增文件（全部在 `shared/src/commonMain/kotlin/com/jianyi/outfit/`）：

| 文件 | 职责 | 依赖 |
|---|---|---|
| `engine/AlmanacEngine.kt` | 公历日期 → 农历/干支/生肖/节气/宜/忌/冲煞/月相/节日 | `tyme4kt` |
| `engine/HistoryTodayEngine.kt` | `MM-DD` → 当日事件列表；数据集缺失时返回空 | 序列化后的内置 JSON |
| `engine/HoroscopeEngine.kt` | `(日期, 星座) → 运势分项`；确定性取池 | 内置文案池 |
| `data/model/TodayModels.kt` | `AlmanacDay`、`HistoricalEvent`、`Horoscope` | 无 Android 依赖 |

修改：

- `shared/build.gradle.kts`：commonMain 增 `implementation(libs.tyme4kt)`；`gradle/libs.versions.toml` 加坐标。
- `data/model/Models.kt`：不动（新模型另开文件，避免已 229 行的它继续膨胀）。
- `data/repository/SettingsRepository.kt` + `SettingsRepositoryImpl`：加三个开关的 `Flow<Boolean>` 与 setter，沿用既有 `sceneryKey` / `disclaimerAccepted` 的写法。
- `ui/home/`：新增 `AlmanacCard.kt`、`HistoryTodayCard.kt`、`HoroscopeCard.kt`（各自 ≤150 行，对齐 `LifeIndexCard.kt` 的 128 行体量）。
- `ui/home/HomeScreen.kt`：**只加三行卡片挂载调用**，内容全在新文件。该文件已 983 行，本设计明确禁止往里写内容。
- `ui/settings/SettingsScreen.kt`：加「今日信息」分组与三个开关。

### 4.1 引擎接口形状

```kotlin
object AlmanacEngine {
    fun of(date: LocalDate): AlmanacDay        // 纯函数，异常不外抛
}
object HistoryTodayEngine {
    fun eventsFor(month: Int, day: Int): List<HistoricalEvent>   // 缺数据 → emptyList()
}
object HoroscopeEngine {
    fun forDate(date: LocalDate, sign: Constellation): Horoscope
}
```

三者的来源标注以**各自的 `SOURCE_LABEL` 常量**暴露，UI 一律读常量。模型上不存在任何可代入的标注参数，所以「UI 不得自行编造来源」是编译期保证，而不是纪律要求。这是把 `LifeIndexEngine` 那句注释——「把估算包装成官方数据是这个 App 最不该做的事」——变成结构约束。

### 4.2 领域模型

```kotlin
data class AlmanacDay(
    val lunarDateText: String,    // 农历丙午年八月廿五（实测形态，含月与日）
    val ganzhiYear: String,       // 年柱干支，按春节换年
    val ganzhiDay: String,        // 干支纪日
    val zodiac: String,           // 生肖，取年柱地支
    val jieqi: String?,           // 当日节气，无则 null
    val recommends: List<String>, // 宜，已截断到展示上限
    val avoids: List<String>,     // 忌，已截断到展示上限
    val duty: String,             // 建除十二值
    val chongSha: String,         // 冲煞，实测形态如「冲马(丙午)煞南」
    val moonPhase: String,        // 月相
    val festival: String?,        // 农历节日
)
```

模型不存公历日期：日期由调用方经 `CivilDate` 传入，模型只承载黄历内容。

来源标注不是构造参数，而是各模型的伴随常量 `SOURCE_LABEL`（见 4.1）。

`recommends` / `avoids` 在 UI 上**截断到前 3 项**并给出展开态；黄历原文动辄十余项，全量铺开与极简调性冲突。

---

## 5. 数据流

首页渲染时：`HomeViewModel` 暴露三个 `StateFlow`（`almanac`、`historyToday`、`horoscope`），值由引擎直接算出，无 IO、无协程调度、无加载态。三张卡各自受设置开关控制；开关为关时不计算也不组合。

日期基准统一走既有 `platform/PlatformClock` / `PlatformTime`（`expect/actual` 已存在，Android 与 iOS 各有实现），**不在引擎里直接调 `Clock.System`**，否则 iOS 侧与单测的时间无法固定。

---

## 6. 文案与标注规则

| 卡片 | 必须显示的来源标注 | 理由 |
|---|---|---|
| 黄历 | 「按传统历法推算」 | 宜忌各家规则不一致，非权威答案，不能表现得像事实 |
| 历史上的今天 | 「史料整理，未逐条核实」 | 描述为本项目自撰（见 3.2），事实骨架来自公开编目，仍可能有过时或失准之处，不能装作权威 |
| 星座 | 「娱乐内容，非预测」 | 内容为本仓库手写池按日轮换，不存在任何依据 |

措辞实现为字符串资源，中文优先；现有 README 已有 en/ru 版，`strings.xml` 与 composeResources 的 i18n 同步问题在实现计划中处理。

---

## 7. 边界与异常态

离线方案没有网络故障态，但有以下情况，**每条都要求 UI 有对应表现**：

1. **数据集缺当日** → 卡片整体不出现（不显示空壳、不显示「暂无数据」占位）。注意第 8 节有一条测试会断言全年 366 天全覆盖，所以正常运行时不该走到这条；它是**给未来数据集回退用的运行时防线**，不是预期路径。
2. **宜/忌为空**（个别干支日确实可能算出空列表）→ 该行不显示，其余照常。
3. **节气/节日为 null** → 不显示该字段，不做「无节气」这类填充文案。
4. **文案池未覆盖某星座** → 星座卡不出现，并在构建期由单测保证 12 星座全覆盖（配置完整性用测试兜，而非运行时判断）。
5. **tyme4kt 对极端日期抛异常**（公元前、极大年份）→ 引擎入口 try/catch 收敛为 `null`，首页不显示该卡；App 不因日期崩溃。
6. **系统日期被改到未来/过去** → 黄历与星座照常可算（纯算法）；「历史上的今天」按 `MM-DD` 取，仍然可算。三者都不依赖服务器时间，所以没有"时间作弊"问题。
7. **跨午夜**：卡片停留在旧日期直到刷新。本期不做监听时间自动刷新（YAGNI，与现有天气卡行为一致）。

---

## 8. 测试

新增，全部纯 JVM，`./gradlew test` 可跑，纳入现有 CI（已跑 lint + 单测 + Debug 构建）：

- `AlmanacEngineTest`
  - 钉死已知日期：给定公历日 → 农历文本、干支纪日、生肖、月相与权威值一致
  - 节气边界：冬至/夏至当日命中，相邻日不命中
  - 宜忌非空且为已知词表子项（防止上游 API 变更后静默产出垃圾）
  - 冲煞格式
  - 极端/非法日期入口不抛
- `HistoryTodayEngineTest`
  - 全年 366 个 `MM-DD` 全部命中（**同时即数据集完整性测试**）
  - 每日条数 ≤ 上限
  - 描述非空、无残留 HTML 标签、无 `\u` 未解转义
- `HoroscopeEngineTest`
  - 同 `(日期, 星座)` 两次调用结果相等（确定性）
  - 12 星座全覆盖
  - 相邻日结果不同（防文案池退化成一条）
- 三张卡：本期不做 UI 自动测试，与仓库现状一致（现有 UI 无任何 Compose 测试）。

数据集本身另有一个构建期校验脚本（`tools/validate_today_dataset.py`）：条数、日期覆盖、字段完整、体积阈值。

---

## 9. README 变更（需产品确认）

1. 「无社交、无广告、**无资讯**」→ 需改写。建议：「无社交、无广告、无推送轰炸；仅有的三张今日卡默认关闭，且全部离线可关」。
2. 「功能一览」表加三行。
3. 「已知说明」新增：黄历宜忌为传统历法推算而非权威、历史事件描述为本项目自撰且未逐条核实、星座运势为娱乐内容且无依据。
4. 「致谢」加 `6tail/tyme4kt`（MIT）。历史数据集**不需要** CC BY-SA 署名（理由见 3.2），但需说明事实骨架的参考来源。
5. 顶部设计理念那句「首屏只保留温度、天气状态与穿搭推荐」同样需要相应修订。

---

## 10. 未决项（实现前必须关闭）

1. ~~数据集许可~~ **已关闭**，见 2.7 与 3.2：外部源只作事实种子，文本全部自撰，无需 CC BY-SA 署名。
2. **全量 366 天覆盖复验**（遗留自 2.7，因 blob 截断未能确认）。在 `tools/extract_today_facts.py` 里作为断言，不通过就停下。
3. **每日选题数**。设计按 ≤5 定，但作者的内容工作量为 1095~1826 句，二者差近一倍。需定：每日 3 条（约 1100 句）够不够。
4. **星座文案池基调**。是否允许带幽默/口语色彩，还是保持现有莫兰迪极简文风的中性陈述。这是内容风格问题，需作者定调。
5. **iOS 侧是否同期展示**。代码进 commonMain 后 iOS 自动可用，但现有 `ios/` 工程的 UI 完备程度未审计，需确认 iOS 是否同期发布这三张卡，还是 Android 先上。
6. **内容审校责任**。约 1100 句自撰描述是本卡片唯一的质量闸门，需要明确由谁逐条过（作者一人，还是抽样）。无人审校等于把「未逐条核实」变成「大概率有误」。

---

## 11. 后续候选（本期不做）

- **系统日历作为穿搭输入**：`READ_CALENDAR` + `CalendarContract` 已实测可读。把「今天有军训/体测/面试」接进 `OutfitRecommendationEngine`，是比这三张卡更贴合「天气推荐穿搭」定位的能力，且完全合规。建议单独立项。
- OPPO 开放平台证书加白申请（若能拿到企业资质，可解锁官方生活指数，替换掉现在的「本地估算」紫外线）。
- 「24 节气」专题视图（`tyme4kt` 数据已具备，无需新依赖）。
