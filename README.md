# 简衣 · WeatherOutfit

[简体中文](README.md) | [English](README.en.md) | [Русский](README.ru.md)

![平台](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20M3-4285F4)
![API](https://img.shields.io/badge/API-26%20~%2035-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

**根据实时天气智能推荐穿搭的 Android 原生应用。** 基于中国气象局数据接口（apihz.cn），按温度、湿度、紫外线、风力四个维度生成通勤 / 户外 / 休闲三场景穿搭方案，支持耐寒耐热、常用风格等个性化修正。2.0 起整套界面重写为「风景 + 液态玻璃」视觉体系，动效全部改为弹簧驱动的跟手实现。

仍然遵守「清晰、顺从、深度、极简主义」四条：首屏只给温度、天气与穿搭，无社交、无广告、无资讯，主色不超过三种。

> 开箱即用：项目内置公共测试凭证，clone 后直接编译运行；正式使用请注册个人凭证（见下文）。本应用完全免费，唯一作者为「莫」，任何以本应用名义收费的行为均为诈骗。

##  下    载
> 蓝奏云:https://wwazj.lanzoum.com/b01eupxd2f   密码:5pfd
> 夸克网盘：https://pan.quark.cn/s/9a513e59fe90?pwd=J1xs  提取码：J1xs
     <img width="156" height="149" alt="image" src="https://github.com/user-attachments/assets/2a947a91-5300-45b7-bfc7-b8bbc2fe0b96" />


## 功能一览

| 模块 | 说明 |
|------|------|
| 首页 | 全屏风景背景 + 玻璃悬浮顶栏（随滚动连续折叠）、大号温度与今日温差条、体感/湿度/风力/紫外线四指标、穿搭推荐卡（长按存为模板）、五项生活指数、未来 7 天温度曲线、气压/能见度/日出日落补充实况 |
| 穿搭详情 | 通勤 / 户外 / 休闲三场景改为**横滑翻页**，分段滑块跟随手指连续位移；单品清单可勾选；自定义模板管理（左滑删除） |
| 城市管理 | 省份 + 城市搜索、GPS 定位、历史城市快速切换；冷启动按「GPS 优先 → IP 兜底」自动定位 |
| 设置 | 背景与动效（选景方式 / 视差 / 呼吸）、玻璃模糊三档、高帧率开关、耐寒耐热 / 常用风格 / 性别、温度与风力单位、每日穿搭推送（时刻可选）、极端天气预警、接口凭证自填、重新查看使用须知 |
| 使用须知 | 首次启动弹窗：作者「莫」、免费声明、防骗提醒、数据来源与隐私说明；勾选「不再提示」后持久化不再弹出 |

## 视觉体系（2.0 新增）

**风景背景。** 内置 8 张竖屏自然风景（晨山云海、高山静湖、林间晨光、海岸暮色、烟雨青峦、雪原松林、星野银河、秋谷层林）。按 2K/120Hz 面板的 1256x2760 出图（Lanczos 放大 + unsharp 补偿，WebP q88，合计约 2.1 MB），绘制时 1:1 不再被拉伸——生成器只出 1024x1792，直接放进 1256x2760 的屏上要放大 1.64 倍，夜景的平滑渐变会糊成一片。三种选景方式：

- **跟随天气**（默认）：`SceneryResolver` 按「夜间 → 降雪/极寒 → 降水/低能见度 → 高温 → 大风 → 晴的时段 → 季节」的优先级短路匹配，设置页会显示「自动选景：有降水」这样的理由，不让人猜。
- **固定一张**：设置页缩略图直接点选。
- **每日轮换**：按当年第几天取，同一天内稳定。

横滑背景即可换景，进度由手指逐帧驱动；天气自动换景走交叉淡入，两种切换共用同一个状态机。

**液态玻璃。** 基于 [Haze](https://github.com/chrisbanes/haze) 的真实背景模糊（Android 12+ 走 `RenderEffect`，低版本自动降级为静态着色）。顶栏、卡片、胶囊、抽屉统一由 `ui/glass/Glass.kt` 的 `glassMaterial` 修饰符出，改一处即全局生效。

材质只有四条规则，每一条都对应上一版被否掉的具体原因：

1. **一层着色，不再叠白。** 着色进 `HazeStyle` 的 tint，实时模糊路径上绝不再 `background()` 补一层白。上一版「静态磨砂 + 上缘高光 + 1px 白描边」三层白叠下来实际不透明度接近 0.9，糊成什么也看不见，屏幕上就是一排灰色圆角板。
2. **本体色从当前风景取**，不用中性灰：上半偏风景中段色、下半偏风景强调色。中性灰 `#1B2129` 压在蓝调夜景上只会得到一块泥。
3. **受光只贴在上下两条边上**，中间完全透明，不再有横贯卡片的光带。
4. **描边是方向性的且很弱**（alpha 0.10~0.23）：左上最亮、迅速消失、右下回一点。等宽亮环是「贴纸感」的唯一来源。

模糊半径本身也分浓度（18 / 26 / 38 / 52 dp）——越厚的玻璃把背景化得越开，这层级差就是纵深感的来源。

**只有一个模糊源。** 风景层是唯一的 `hazeSource`，所有玻璃共用同一张缓存好的模糊纹理，模糊成本与玻璃数量无关。试过给「滚动内容」再挂一个源、让顶栏糊正在滚过去的卡片，真机上不成立：`hazeEffect` 只是在自己这块区域**另画一份**糊过的源纹理，底下那层原始内容照画不误，浮层本身是半透的，糊过的那份盖不住清楚的那份——标题该和顶栏文字叠还是叠，只是白多花一份全屏离屏缓冲。浮层要压住内容靠的是**本体色**（`glassMaterial(bodyAlpha = …)`：一层从风景取色、下缘更实的渐变，夹在模糊与描边之间），效果是「同一块玻璃变厚了」而不是「玻璃上又贴了张灰膜」。

**跟手。** 位移一律写进 `Animatable` 并只在 `graphicsLayer` 的 lambda 里读取，因此每帧只更新变换矩阵、不触发重组；弹簧取代固定时长 tween，使快速连点时动画从当前速度接续而非重播。刷新按钮的无限旋转动画只在真正加载时才挂进组合，平时一帧都不空转。

**高帧率。** `View.setRequestedFrameRate(float)` 要 **Android 15（API 35）** 才有（SDK 的 `api-versions.xml` 记的就是 `since=35`，早先按 31 守卫会在 Android 12~14 抛 `NoSuchMethodError`），低版本直接跳过。关键一点：`REQUESTED_FRAME_RATE_CATEGORY_HIGH` 并不是独立的「类别 API」，它就是**同一个 float 参数上的负数哨兵**（-4.0f），所以「发类别」和「发精确值」互斥、不能两个都发；类别值进了 framework 还要经厂商 overlay（`config_defaultHighFrameRateCategoryRate` 之类）翻译成具体 Hz，「高」等于多少是 OEM 说了算。现在发的是**当前分辨率下面板支持的最高 Hz**（`FrameRate.peak`），读不到时才退回类别通道，关闭时发 `NO_PREFERENCE`。这仍然只是**请求**，所以设置页把实际申请到的数字直接写出来，而不是只写「已开启」。配合 `android:preferMinimalPostProcessing` 压低合成延迟。**但要注意：发精确值在 PLB110 上并没有把档位顶到 120**——见「已知说明」里的实测表，天花板另有其因。

三档性能策略可在设置里切换，默认「全实时」（Android 12 以下会自动逐级降级，设置页会如实说明）：

| 档位 | 卡片 | 顶栏/浮层 |
|------|------|-----------|
| 全实时（默认） | 实时模糊 | 实时模糊 |
| 均衡 | 静态着色 | 实时模糊 |
| 流畅优先 | 静态着色 | 静态着色，且不挂模糊源 |

## 技术栈

- **语言**：Kotlin 2.1
- **UI**：Jetpack Compose + Material Design 3 + Haze（背景模糊）
- **架构**：MVVM（ViewModel + Repository + Data Source）
- **网络**：Ktor + kotlinx.serialization（在 `shared` 模块，Android/iOS 共用；Coroutines）
- **后台任务**：WorkManager（每日推送持久化调度，重启自动恢复）
- **图片加载**：Coil3（天气图标；需显式挂 OkHttp fetcher，见 Application）
- **本地存储**：Room（历史城市、穿搭模板、天气缓存）+ DataStore（轻量配置）
- **定位**：FusedLocationProviderClient（Google Play Services）
- **适配**：minSdk 26（Android 8.0）~ targetSdk 35（Android 15），手机/折叠屏/平板自适应布局

## iOS / Kotlin Multiplatform 现状

**目前只有 Android 版**，但 iOS 化的可行性已经用 CI 实测过，不是纸面推演。

`ios-probe/` 是一个**独立的 Gradle 构建**（根构建不 include 它，对线上 App 零影响），
把 `ui/glass/Glass.kt` 去 Android 化后交给 macOS runner 编 iOS，验证两件事：玻璃层能不能
住进 `commonMain`，以及 Haze 的 iOS 产物有没有暴露我们在用的每个 API
（`HazeStyle` / `HazeTint` / `hazeSource` / `hazeEffect` 的 scope lambda /
`HazeInputScale` / `HazeEffectScope.mask`）。

版本组合矩阵（`.github/workflows/ios-probe.yml`，三档全绿）：

| Kotlin | Compose Multiplatform | 编 iOS |
|---|---|---|
| **2.1.0**（App 现在的版本） | **1.8.2** | ✅ |
| 2.2.20 | 1.8.2 | ✅ |
| 2.4.20 | 1.12.0 | ✅ |

**最关键的一行是第一行**：App 现在的 Kotlin 2.1.0 配 CMP 1.8.2 就能编 iOS，
所以做 KMP 迁移**不需要升 Android 的工具链**（Kotlin / AGP / compileSdk / Compose 代际都不用动）。

依赖可用性核查（查的是各仓库自己的元数据，不是凭印象）：

- ✅ Haze **1.6.10 就有 iosarm64**（core 与 materials 都有），玻璃层不用换库、不用升版本
- ✅ Room 有 iOS 产物，自 2.7.0-alpha01 起（现 2.8.5）—— **只需升版本，不用换 SQLDelight**
  （已做：钉 **2.7.2**。挑版本靠读制品而不是查文档 —— `room-runtime-iosarm64/2.7.2` 的
  klib manifest 是 `abi_version=1.201.0`，与我们的 Kotlin 2.1.0 同一条 ABI 线；
  而 2.8.5 是 `2.2.0`，换它就得先升 Kotlin。`sqlite-bundled` 同理取 2.5.1）
- ✅ DataStore / Coil3 / JB 的 lifecycle-viewmodel 与 navigation / Ktor / kotlinx-serialization 都有 iosarm64
- ❌ **WorkManager 没有 iOS 对应物**，这是唯一真正要重新设计的功能

每日推送在 iOS 上的替代方案（尚未实现）：iOS 的**预定本地通知本身就跨重启存活**，
不需要任何 App 代码 —— Android 上靠 WorkManager + 开机广播去"恢复"的那件事，
系统直接替你做了。做法是把未来 N 天排成 N 条 `UNCalendarNotificationTrigger`
（每 App 上限 64 条，够用），`BGAppRefreshTask` 只负责后台刷新天气数据。
一个诚实的降级：**极端天气预警**在 iOS 上做不到 Android 那种随时推送，那需要 APNs 与后端。

迁移进度（进行中）：~~领域层抽出 shared（引擎 + 模型）~~ ✅ → ~~CI 编译 shared 的 iOS target~~ ✅
→ ~~天气网络层换 Ktor + kotlinx.serialization（DTO 与缓存编解码进 commonMain，Gson↔kotlinx 等价性由对拍单测钉住）~~ ✅
→ ~~穿搭模板清单迁离 Gson（至此主代码零 Gson；Gson 仅留作对拍测试参照）~~ ✅
→ ~~液态玻璃 + 跟手动效整层进 commonMain（supportsRealtimeBlur / 系统动画缩放 expect/actual 化，材质策略测试随迁 commonTest）~~ ✅
→ ~~仓库接口下沉 + 依赖接缝 AppDependencies + 三个 ViewModel（首页/设置/详情）进 commonMain~~ ✅
→ ~~Coil→Coil3 + 无权限依赖的 UI（卡片/组件/风景背景与抽屉/详情页/主题）进 commonMain~~ ✅
→ ~~权限检查抽象成能力接口（LocationProvider / NotificationGate / HighFrameRateApi）
+ HomeScreen 与 SettingsScreen 进 commonMain~~ ✅
→ ~~**Room 升 2.7.2 KMP**：Entity / DAO / 两个数据库定义进 commonMain，
开库动作留平台侧（Android 用 Context、iOS 用 Documents 路径 + 显式 bundled 驱动）；
导出的 schema 与 2.6.1 那份逐字节相同，所以老用户的数据文件仍被认作同一版本~~ ✅
→ ~~城市/模板/天气三个仓库**实现**下沉 commonMain（BuildConfig 默认凭证改构造参数）~~ ✅
→ ~~CityViewModel + CityScreen 进 commonMain（至此三个页面本体重叠完成）~~ ✅
→ 剩余：**SettingsRepository 的 DataStore 实现**（要抽一层偏好存储，iOS 走 NSUserDefaults）、
**iOS 入口**（`ComposeUIViewController` + Xcode 工程骨架 + iOS 侧的 AppDependencies 实现）、
**通知抽象与 iOS 本地通知**（预定通知天然跨重启，见上）。

跨端坑记录（都是本地 Android 编译看不见、只有 iOS 编译才报的）：
`LocalConfiguration` 是 androidx 但 Compose Multiplatform 没有；`Math.PI` 不需要 import
所以「没有 java. 前缀」不代表不是 JVM 的；`collectAsStateWithLifecycle` 当前版本无 iOS 产物；
Coil3 不自带网络栈，不显式挂 fetcher 就是静默空白；Room 的 native
`RoomDatabase.Builder.build()` 会 `requireNotNull(driver)`，忘了 `setDriver` 是启动即崩；
调 `NSFileManager.URLForDirectory` 要 `@OptIn(ExperimentalForeignApi::class)`；
`@Database` 在 KMP 下必须配 `@ConstructedBy` + 一个 `expect object` 构造入口
（actual 由各 target 的 KSP 生成，但 commonMain 的元数据编译看不到，得压掉那条告警）。

> 想在 macOS 上直接跑：`cd ios-probe && ../gradlew compileKotlinIosArm64`。
> 不带参数即用最保守的 2.1.0 + 1.8.2；`-PkotlinVersion=` / `-PcomposeVersion=` 可覆盖。


## 快速开始

1. 克隆仓库并用 Android Studio 打开，等待 Gradle 同步完成。
2. 确认 `local.properties` 中的天气接口凭证（项目已内置官方公共测试凭证，开箱即用）。
3. 连接设备或模拟器，点击 Run。

命令行构建（需 JDK 17+）：

```bash
./gradlew assembleDebug          # 产出 app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest # 穿搭引擎 / 选景规则 / 生活指数 / 格式化 的单元测试
```

辅助脚本（`tools/`，不参与构建）：

| 脚本 | 用途 |
|------|------|
| `scenery_webp.py` | 把风景原图裁切并压成 `drawable-nodpi` 下的 WebP |
| `scenery_colors.py` | 从图片实测采样 sky/ground/accent，供 `Scenery.kt` 使用 |
| `kotlin_lint.py` | 括号配平与可疑写法自查，几秒内发现本该等 6 分钟编译才暴露的低级错 |
| `install-device.sh` | 装包并自动通过 vivo 的「未知来源」确认框 |

📖 完整使用说明见 [docs/天气穿搭助手使用说明文档.docx](docs/天气穿搭助手使用说明文档.docx)（保留中文文件名，避免破坏历史链接）。

## 如何注册获取 API 凭证

1. 访问 [apihz.cn](https://cn.apihz.cn) 注册账号并登录后台。
2. 在「接口盒子」后台获取个人的 `id` 与 `key`（独享调用频次，可用于发布）。
3. 打开项目根目录的 `local.properties`，替换以下两项：

```properties
WEATHER_API_ID=你的ID
WEATHER_API_KEY=你的KEY
```

凭证经 `BuildConfig` 注入代码（见 `app/build.gradle.kts`），不会进入版本库。

> 也可以不重新编译：直接在应用内「设置 → 接口凭证（可选）」填入个人 `appid` / `appkey` / 接口地址，用户配置优先生效；留空则回退内置默认凭证（仅存本地 DataStore）。

> 项目默认携带官方公共测试凭证 `88888888 / 88888888`，与所有开发者**共享频次限制**（高峰期可能返回「调用频次过快」），仅用于开发调试，不可用于发布。

## 如何替换主题色

主题色统一定义在 `app/src/main/java/com/jianyi/outfit/ui/theme/Color.kt`：

```kotlin
val MorandiBlue = Color(0xFF6B82A6)   // 修改这一行即可全局换色（浅色模式主色）
val DarkPrimary = Color(0xFFA7BCDA)   // 暗黑模式主色（保持足够对比度）
```

同时同步修改资源文件 `app/src/main/res/values/colors.xml`（启动图标/窗口底色引用）与 `drawable/ic_launcher_background.xml`（图标底色）。

按设计规范，全 App 不超过 3 种主色：

- 主题色 1 种：莫兰迪蓝 `#6B82A6`
- 状态色 2 种：高温浅橙 `#F5A67A`、低温浅蓝 `#7EB3D5`（仅用于提示）

## 如何修改推荐规则

推荐逻辑全部集中在 `app/src/main/java/com/jianyi/outfit/engine/OutfitRecommendationEngine.kt`，为纯 Kotlin 实现，配套单元测试 `app/src/test/.../OutfitRecommendationEngineTest.kt`：

- **温度分档**：改 `temperatureBucketItems()` 与 `temperatureBucketRange()`
- **紫外线 / 风力 / 湿度叠加规则**：改 `recommend()` 第 3 步
- **场景化改造**（通勤挺括、户外速干、休闲宽松）：改 `commuteItems()` / `outdoorItems()` / `casualItems()`
- **耐寒耐热偏移量**：改 `toleranceOffset`（当前 ±2℃）

修改后运行 `./gradlew test` 验证规则未被破坏。

## 项目结构

```
app/src/main/java/com/jianyi/outfit/   # 只剩 Android 专属部分（13 个文件）
├── data/repository/SettingsRepository.kt  # DataStore 实现（接口与其余三个仓库都在 shared）
├── ui/navigation/AppNavHost.kt        # 导航图与转场（navigation-compose），负责注入各页 VM
├── notification/                      # 通知渠道、每日推送（WorkManager）与开机自启接收器
├── di/                                # AppContainer（实现 AppDependencies）+ ViewModel 工厂
├── util/                              # 帧率申请、FusedLocation、权限申请（activityResultRegistry）
└── MainActivity / WeatherOutfitApp    # 入口；App 同时是 Coil3 的 ImageLoader 工厂
```

跨平台的 `shared/` 模块（Android 与 iOS 同源，包名与 app 一致所以 app 侧零 import 改动）：

```
shared/src/
├── commonMain/kotlin/com/jianyi/outfit/
│   ├── engine/          # 穿搭推荐引擎、生活指数引擎（纯 Kotlin，可单测）
│   ├── data/model/      # 领域模型
│   ├── data/local/      # Room Entity / DAO / 两个 @Database（Room 2.7 起是 KMP 库）
│   ├── data/remote/     # Ktor 客户端、@Serializable DTO、缓存编解码（格式兼容旧 Gson 行）
│   ├── data/repository/ # 三个仓库接口 + 城市/模板/天气三份实现（DTO→领域模型也在这）
│   ├── data/AppDependencies.kt  # ViewModel 的依赖接缝 + 平台能力接口
│   ├── ui/glass/        # 液态玻璃材质（唯一的模糊源与四条红线）
│   ├── ui/motion/       # 跟手基础件：按压弹簧、折叠控制器
│   ├── ui/scenery/      # 风景主题、选景规则、背景层与抽屉（图片用 CMP 资源）
│   ├── ui/theme/        # 颜色 token、字体形状、主题装配、动效规范
│   ├── ui/root/         # 应用根节点：风景 + 玻璃宿主 + 主题 + LocalAppDependencies
│   ├── ui/home|city|detail|settings/  # 四个页面的 ViewModel 与界面本体
│   ├── ui/components/   # 通用组件与使用须知弹窗
│   ├── util/            # Formatters（日期用纯儒略日算术）
│   └── platform/        # expect/actual：时间/月份/时区、坐标 key、HTTP 引擎
├── androidMain/         # actual：OkHttp 引擎、RenderEffect 门槛、系统动画开关、Room 开库（Context）
├── iosMain/             # actual：Darwin 引擎、坐标 key 定宽格式化、Room 开库（Documents + bundled 驱动）
└── commonTest/          # 缓存格式与玻璃材质的"钉子"测试
```

仓库根的其它目录：`tools/` 是出图、取色、括号自查等辅助脚本；`ios-probe/` 是**独立的
Gradle 构建**（根构建不 include 它），只为在 macOS 上验证玻璃层能否为 iOS 编出来，
详见上面「iOS / Kotlin Multiplatform 现状」。

## 天气数据接口

数据源：apihz.cn 提供的中国气象局数据（免费注册）。已封装三个实况端点与 7 天预报：

| 端点 | 用途 |
|------|------|
| `GET /api/tianqi/tqybip.php` | IP 自动定位（首次打开） |
| `GET /api/tianqi/tqyb.php?sheng=&place=` | 地址查询（城市搜索） |
| `GET /api/tianqi/tqybjw1.php?lat=&lon=` | 经纬度查询（GPS 定位） |
| `GET /api/tianqi/tqyb.php?...&day=7` | 7 天预报 |

- 响应 `code=200` 成功、`code=400` 失败，失败原因见 `msg` 并以 Snackbar/错误面板提示用户。
- 天气数据本地缓存 30 分钟，过期自动刷新；断网时回退过期缓存保证可用。

## 已知说明

- **本机跑不到 120Hz，而且是两重原因叠在一起。** 测试机 OPPO PLB110（Android 15 / 面板 120Hz，`peak_refresh_rate=120.0`，未开省电、温度正常），系统桌面与设置页的刷新率浮层显示 **120**，本 App 无论怎么申请都只有 **90**。release 包（R8 + 非 debug Compose）滚动期间实测：

  | 玻璃档位 | 每帧 p50 | p90 | p99 | 系统给的 Hz |
  |---|---|---|---|---|
  | 全实时（默认） | 13ms | 15ms | 16ms | 90 |
  | 均衡 | 9ms | 12ms | 14ms | 90 |
  | 流畅优先 | 7ms | 8ms | 10ms | **仍然 90** |

  120Hz 的每帧预算是 **8.33ms**，90Hz 是 11.1ms（deadline 14.1ms）。所以「全实时」那 13ms 本来就到不了 120 —— 系统定在 90 是算出来的正确结果，不是被谁拦了。而卡片实时模糊单项就值约 **4ms/帧**（13→9ms）。**但即使降到 7ms、已经在 8.33ms 预算内，系统依旧只给 90Hz**，说明还有一层与本 App 绑定的策略上限（桌面能拿 120，第三方被定在 90），这不是应用侧能改的。三档 `Janky frames` 都是 **0.00%**、`Missed Vsync` 0 —— 90Hz 下是跑满的，没有任何丢帧。
- **debug 与 release 的帧耗时基本一致**（都是 p50 13ms），所以"上 release 就能高刷"这条路在这台机器上不成立，测帧率不必先等 release 构建。
- **`dumpsys display` 的 `mActiveSfDisplayMode` / `renderFrameRate` 不能当实时档位用**：本机上它长期停在 90 的条目、`DisplayDeviceInfo` 里那段又是 120，读出来会自相矛盾。可信的两个探针是**开发者选项 → 显示刷新频率**的浮层数字，以及 `dumpsys SurfaceFlinger | grep renderRate`。
- **`gfxinfo` 的 `Total frames rendered` 除以"我 sleep 了几秒"是错的**：adb 往返本身有几百毫秒开销，早期版本据此写出的"约 104fps"超过了面板实际能给的 90Hz，是个不可能成立的数字。要算窗口请用报告里的时间戳，或者直接看百分位与产帧密度。旧测试机 vivo V2156A 只有 60Hz 一档，当时「本机无法演示高刷」的结论已随换机失效。
- **呼吸动画会让屏幕持续重绘。** 静止时仍以 90~120fps 出帧，是「背景不死板」这个观感需求的直接代价。设置页的「呼吸漂移」关掉即可回到真正的静止帧。
- **风景素材由 AI 生成。** 8 张背景为本次改造生成，出处已在提交说明中记录；构图上裁掉了底部约 6% 以避开生成器加盖的「AI 生成」角标——这是重新取景，不是涂抹遮盖，来源属性在此如实说明。
- **实时模糊只有一份源。** 卡片与顶栏都只采样风景层，因此正文滚到顶栏底下时，压住它的是随滚动加深的磨砂而非真实模糊。这是刻意的取舍：多一个源就多一倍每帧模糊面积，中低端机直接掉帧。
- **七日预报只在「省 + 市」维度可得。** 经纬度端点（GPS 定位）不返回省份，此时卡片会直接写明「当前为经纬度定位，数据源不提供该地逐日预报」，而不是静默留空让人以为坏了。
- **上游限流文案已过滤。** 数据源在频次超限时会返回带「购买钻石会员」等推销话术的原文，界面会识别限流语义并换成中性表述；其余错误仍保留原文，避免掩盖真实故障。
- **定位策略（按可靠性排序）**：手动选择的城市缓存 → GPS 经纬度 → IP 兜底。IP 定位依赖运营商 IP 库，存在跨城漂移（如在中山显示湛江），因此仅在未授权定位 / 无 Play Services / 定位服务关闭时降级使用；IP 模式下城市名旁显示 Wi-Fi 图标，并出现「点此用 GPS 精确定位」提示条。
- **经纬度端点为全球数据源**（`tqybjw1.php`）：响应结构与 IP/地址端点不同——无省份、无昼夜温差、无预警，**城市名显示为拼音**（如 Zhongshan）；体感温度缺省回气温，风力按风速换算蒲福风级。气压、能见度、云量、日出日落仅该端点提供，缺数据时整块不显示。
- **限流防护**：公共凭证高峰期限流时，仓库层按接口返回的建议等待秒数（`s` 字段）自动退避重试一次；屏上无数据时展示倒计时卡片（自动重试仅一次，之后转手动模式），有缓存数据时降级为提示并继续展示缓存；手动刷新与「使用精确定位」共享 10 秒节流。
- **紫外线与生活指数均为估算值**：接口未提供这些字段，全部由本地规则从实况推导，界面标注「本地估算」，不冒充官方发布值。
- **GPS 依赖 Google Play Services**：设备无 GMS 时定位失败，App 会提示改用搜索或 IP 定位，功能不受阻。
- **每日推送**基于 WorkManager 持久化周期任务（每 24 小时，推送时刻可在设置页选择，省电策略下可能有几分钟浮动），任务随系统持久化，**设备重启后会自动恢复，无需打开应用重新注册**（开机自启接收器会按设定时刻重新对齐）；Android 13+ 首次开启时申请通知权限。
- **接口凭证可自填**：设置页「接口凭证（可选）」可填自己的 appid / appkey / apiurl，留空即回退内置公共凭证。取值优先级为「用户自填 > BuildConfig 默认」。

## 开源协议

本项目基于 [MIT License](LICENSE) 开源，作者「莫」。

## 致谢

- 天气数据：[apihz.cn 接口盒子](https://cn.apihz.cn) 提供的中国气象局数据
- 背景模糊：[Haze](https://github.com/chrisbanes/haze) by Chris Banes
- 图标与设计规范：[Material Design 3](https://m3.material.io/)
