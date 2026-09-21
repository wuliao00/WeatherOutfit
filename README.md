# 简衣 · WeatherOutfit

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
| 设置 | 背景与动效（选景方式 / 视差 / 呼吸）、玻璃模糊三档、高帧率开关、耐寒耐热 / 风格 / 性别、温度与风力单位、每日推送与极端天气预警 |
| 使用须知 | 首次启动弹窗：作者「莫」、免费声明、防骗提醒、数据来源与隐私说明；勾选「不再提示」后持久化不再弹出 |

## 视觉体系（2.0 新增）

**风景背景。** 内置 8 张竖屏自然风景（晨山云海、高山静湖、林间晨光、海岸暮色、烟雨青峦、雪原松林、星野银河、秋谷层林），WebP 编码后合计约 0.8 MB。三种选景方式：

- **跟随天气**（默认）：`SceneryResolver` 按「夜间 → 降雪/极寒 → 降水/低能见度 → 高温 → 大风 → 晴的时段 → 季节」的优先级短路匹配，设置页会显示「自动选景：有降水」这样的理由，不让人猜。
- **固定一张**：设置页缩略图直接点选。
- **每日轮换**：按当年第几天取，同一天内稳定。

横滑背景即可换景，进度由手指逐帧驱动；天气自动换景走交叉淡入，两种切换共用同一个状态机。

**液态玻璃。** 基于 [Haze](https://github.com/chrisbanes/haze) 的真实背景模糊（Android 12+ 走 `RenderEffect`，低版本自动降级为静态磨砂）。全 App 只维护**一个**模糊源（风景层），所有玻璃共用同一张缓存好的模糊纹理——把滚动内容也设为源会形成自采样，每帧模糊面积成倍增长。顶栏、卡片、胶囊、抽屉统一由 `ui/glass/Glass.kt` 的 `glassMaterial` 修饰符出，改一处即全局生效。

**跟手。** 位移一律写进 `Animatable` 并只在 `graphicsLayer` 的 lambda 里读取，因此每帧只更新变换矩阵、不触发重组；弹簧取代固定时长 tween，使快速连点时动画从当前速度接续而非重播。

**高帧率。** `View.requestedFrameRate = REQUESTED_FRAME_RATE_CATEGORY_HIGH`（Android 12+）向系统请求高帧类别，配合 `android:preferMinimalPostProcessing` 压低合成延迟。注意这是**请求**而非保证：实际能到多少仍由设备可用刷新率决定（见「已知说明」）。

三档性能策略可在设置里切换，默认「均衡」：

| 档位 | 卡片 | 顶栏/浮层 |
|------|------|-----------|
| 全实时 | 实时模糊 | 实时模糊 |
| 均衡（默认） | 静态磨砂 | 实时模糊 |
| 流畅优先 | 静态磨砂 | 静态磨砂，且不挂模糊源 |

## 技术栈

- **语言**：Kotlin 2.1
- **UI**：Jetpack Compose + Material Design 3 + Haze（背景模糊）
- **架构**：MVVM（ViewModel + Repository + Data Source）
- **网络**：Retrofit + OkHttp + Kotlin Coroutines
- **图片加载**：Coil（天气图标）
- **本地存储**：Room（历史城市、穿搭模板、天气缓存）+ DataStore（轻量配置）
- **定位**：FusedLocationProviderClient（Google Play Services）
- **适配**：minSdk 26（Android 8.0）~ targetSdk 35（Android 15），手机/折叠屏/平板自适应布局

## 快速开始

1. 用 Android Studio 打开本目录（或 `git clone` 后导入）。
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

## 如何注册获取 API 凭证

1. 访问 [apihz.cn](https://cn.apihz.cn) 注册账号并登录后台。
2. 在「接口盒子」后台获取个人的 `id` 与 `key`（独享调用频次，可用于发布）。
3. 打开项目根目录的 `local.properties`，替换以下两项：

```properties
WEATHER_API_ID=你的ID
WEATHER_API_KEY=你的KEY
```

凭证经 `BuildConfig` 注入代码（见 `app/build.gradle.kts`），不会进入版本库。

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
app/src/main/java/com/jianyi/outfit/
├── data/
│   ├── remote/          # Retrofit 接口定义、API 响应模型、RetrofitClient
│   ├── local/           # Room 数据库、DAO、Entity（城市/模板/缓存）
│   ├── repository/      # 数据仓库层（天气/城市/设置/模板）
│   └── model/           # 领域模型（天气、偏好、推荐结果、选景与玻璃档位）
├── ui/
│   ├── root/            # 应用根节点：风景 + 玻璃宿主 + 主题
│   ├── glass/           # 液态玻璃材质与组件（唯一的模糊源在此定义）
│   ├── scenery/         # 风景主题、天气→选景规则、背景层、换景选择器
│   ├── motion/          # 跟手基础件：按压弹簧、滚动折叠控制器
│   ├── home/            # 首页（含七日曲线卡、生活指数卡）
│   ├── detail/          # 穿搭详情（场景横滑 + 模板管理）
│   ├── city/            # 城市管理（搜索/GPS/历史城市）
│   ├── settings/        # 设置页（视觉与性能 + 偏好/单位/通知）
│   ├── theme/           # 颜色、字体、动效弹簧、主题装配
│   ├── navigation/      # 导航图与转场
│   └── components/      # 可复用 UI 组件
├── engine/              # 穿搭推荐引擎、生活指数引擎（纯 Kotlin，可单测）
├── notification/        # 通知渠道、每日推送调度与接收器
├── di/                  # 手动依赖容器 + ViewModel 工厂
└── util/                # 工具类（格式化、定位、网络状态、帧率申请）
```

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

- **高帧率是「请求」，不是「保证」。** 系统会按设备可用刷新率与功耗策略决定最终档位。在 vivo V2156A（`supportedModes` 仅 `fps=60.0`）上实测静止与滚动均稳定跑满 60Hz，但这块屏本身没有更高档可给；换到 90/120Hz 机型并由系统放行时才能看到更高帧率。另外该机的 presentation deadline 仅 9.4ms，`dumpsys gfxinfo` 会把几乎每一帧记成 janky——判断是否真的掉帧要看「单位时间产帧数」，不能只看 janky 百分比。
- **风景素材由 AI 生成。** 8 张背景为本次改造生成，出处已在提交说明中记录；构图上裁掉了底部约 6% 以避开生成器加盖的「AI 生成」角标——这是重新取景，不是涂抹遮盖，来源属性在此如实说明。
- **实时模糊只有一份源。** 卡片与顶栏都只采样风景层，因此正文滚到顶栏底下时，压住它的是随滚动加深的磨砂而非真实模糊。这是刻意的取舍：多一个源就多一倍每帧模糊面积，中低端机直接掉帧。
- **七日预报只在「省 + 市」维度可得。** 经纬度端点（GPS 定位）不返回省份，此时卡片会直接写明「当前为经纬度定位，数据源不提供该地逐日预报」，而不是静默留空让人以为坏了。
- **上游限流文案已过滤。** 数据源在频次超限时会返回带「购买钻石会员」等推销话术的原文，界面会识别限流语义并换成中性表述；其余错误仍保留原文，避免掩盖真实故障。
- **定位策略（按可靠性排序）**：手动选择的城市缓存 → GPS 经纬度 → IP 兜底。IP 定位依赖运营商 IP 库，存在跨城漂移（如在中山显示湛江），因此仅在未授权定位 / 无 Play Services / 定位服务关闭时降级使用；IP 模式下城市名旁显示 Wi-Fi 图标，并出现「点此用 GPS 精确定位」提示条。
- **经纬度端点为全球数据源**（`tqybjw1.php`）：响应结构与 IP/地址端点不同——无省份、无昼夜温差、无预警，**城市名显示为拼音**（如 Zhongshan）；体感温度缺省回气温，风力按风速换算蒲福风级。气压、能见度、云量、日出日落仅该端点提供，缺数据时整块不显示。
- **限流防护**：公共凭证高峰期限流时，仓库层按接口返回的建议等待秒数（`s` 字段）自动退避重试一次；屏上无数据时展示倒计时卡片（自动重试仅一次，之后转手动模式），有缓存数据时降级为提示并继续展示缓存；手动刷新与「使用精确定位」共享 10 秒节流。
- **紫外线与生活指数均为估算值**：接口未提供这些字段，全部由本地规则从实况推导，界面标注「本地估算」，不冒充官方发布值。
- **GPS 依赖 Google Play Services**：设备无 GMS 时定位失败，App 会提示改用搜索或 IP 定位，功能不受阻。
- **每日推送**使用系统 AlarmManager 非精确重复闹钟（省电策略下可能有几分钟浮动）；Android 13+ 首次开启时申请通知权限；**设备重启后闹钟不会自动恢复，需打开一次应用重新注册**。
- **深浅色由风景决定**：暗调背景（星野、林间、烟雨、秋谷）会强制启用深色配色，保证玻璃上的文字在任何一张图上都有足够对比度，而不是只跟随系统设置。

## 持续集成

`.github/workflows/android.yml`：先跑单元测试，再构建 debug 与 release APK 并上传为 artifact。

- 天气凭证通过仓库 Secrets（`WEATHER_API_ID` / `WEATHER_API_KEY`）注入 `local.properties`；不配置时退回公共演示凭证，构建仍会成功，但线上必然被限流。
- release 签名走 `RELEASE_KEYSTORE_BASE64` / `RELEASE_KEYSTORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` 四个 Secrets；未配置时产出**未签名**包，用于验证 R8 混淆与资源收缩没有裁坏功能，而不是让 CI 变红。注意 PKCS12 密钥库要求 store 密码与 key 密码一致。

## 开源协议

本项目基于 [MIT License](LICENSE) 开源，作者「莫」。

## 致谢

- 天气数据：[apihz.cn 接口盒子](https://cn.apihz.cn) 提供的中国气象局数据
- 背景模糊：[Haze](https://github.com/chrisbanes/haze) by Chris Banes
- 图标与设计规范：[Material Design 3](https://m3.material.io/)
