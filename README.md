# 简衣 · WeatherOutfit

[简体中文](README.md) | [English](README.en.md) | [Русский](README.ru.md)

![平台](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20M3-4285F4)
![API](https://img.shields.io/badge/API-26%20~%2035-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

**根据实时天气智能推荐穿搭的 Android 原生应用。** 基于中国气象局数据接口（apihz.cn），按温度、湿度、紫外线、风力四个维度生成通勤 / 户外 / 休闲三场景穿搭方案，支持耐寒耐热、常用风格等个性化修正。严格遵循「清晰、顺从、深度、极简主义」四大设计理念：首屏只保留温度、天气状态与穿搭推荐，无社交、无广告、无资讯，全 App 主色不超过三种。

> 开箱即用：项目内置公共测试凭证，clone 后直接编译运行；正式使用请注册个人凭证（见下文）。本应用完全免费，唯一作者为「莫」，任何以本应用名义收费的行为均为诈骗。

##  下    载
> 蓝奏云:https://wwazj.lanzoum.com/b01eupxd2f   密码:5pfd
> 夸克网盘：https://pan.quark.cn/s/9a513e59fe90?pwd=J1xs  提取码：J1xs
     <img width="156" height="149" alt="image" src="https://github.com/user-attachments/assets/2a947a91-5300-45b7-bfc7-b8bbc2fe0b96" />


## 功能一览

| 模块 | 说明 |
|------|------|
| 首页 | 居中大号温度 + 天气状态 + 体感/湿度/风力/紫外线四指标 + 穿搭推荐卡片（长按可保存为模板） |
| 穿搭详情 | 通勤 / 户外 / 休闲三场景方案，单品清单可勾选，支持自定义穿搭模板（左滑删除） |
| 城市管理 | 省份 + 城市搜索、GPS 定位、历史城市快速切换；冷启动按「GPS 优先 → IP 兜底」自动定位 |
| 设置 | 耐寒耐热程度 / 常用风格 / 性别、温度与风力单位、每日穿搭推送（时刻可选）、极端天气预警、接口凭证自填、重新查看使用须知 |
| 使用须知 | 首次启动弹窗：作者「莫」、免费声明、防骗提醒、数据来源与隐私说明；勾选「不再提示」后持久化不再弹出 |

## 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose + Material Design 3
- **架构**：MVVM（ViewModel + Repository + Data Source）
- **网络**：Retrofit + OkHttp + Kotlin Coroutines
- **后台任务**：WorkManager（每日推送持久化调度，重启自动恢复）
- **图片加载**：Coil（天气图标）
- **本地存储**：Room（历史城市、穿搭模板、天气缓存）+ DataStore（轻量配置）
- **定位**：FusedLocationProviderClient（Google Play Services）
- **适配**：minSdk 26（Android 8.0）~ targetSdk 35（Android 15），手机/折叠屏/平板自适应布局

## 快速开始

1. 克隆仓库并用 Android Studio 打开，等待 Gradle 同步完成。
2. 确认 `local.properties` 中的天气接口凭证（项目已内置官方公共测试凭证，开箱即用）。
3. 连接设备或模拟器，点击 Run。

 命令行构建：`./gradlew assembleDebug`（需 JDK 17+）。
> 单元测试：`./gradlew test`（穿搭推荐引擎）。

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
app/src/main/java/com/jianyi/outfit/
├── data/
│   ├── remote/          # Retrofit 接口定义、API 响应模型、RetrofitClient
│   ├── local/           # Room 数据库、DAO、Entity（城市/模板/缓存）
│   ├── repository/      # 数据仓库层（天气/城市/设置/模板）
│   └── model/           # 领域模型（天气、偏好、推荐结果）
├── ui/
│   ├── home/            # 首页（天气 + 穿搭卡片）
│   ├── detail/          # 穿搭详情页（场景方案 + 模板管理）
│   ├── city/            # 城市管理（搜索/GPS/历史城市）
│   ├── settings/        # 设置页（偏好/单位/通知）
│   ├── theme/           # 主题、颜色、字体定义
│   ├── navigation/      # 导航图与转场动画
│   └── components/      # 可复用 UI 组件
├── engine/              # 穿搭推荐引擎（纯 Kotlin，可单测）
├── notification/        # 通知渠道、每日推送调度（WorkManager）与开机自启接收器
├── di/                  # 手动依赖容器 + ViewModel 工厂
└── util/                # 工具类（格式化、定位）
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

- **定位策略（按可靠性排序）**：手动选择的城市缓存 → GPS 经纬度 → IP 兜底。IP 定位依赖运营商 IP 库，存在跨城漂移（如在中山显示湛江），因此仅在未授权定位 / 无 Play Services / 定位服务关闭时降级使用；IP 模式下城市名旁显示 Wi-Fi 图标，并出现「点击使用 GPS 精确定位」提示条。
- **经纬度端点为全球数据源**（`tqybjw1.php`）：响应结构与 IP/地址端点不同——无省份、无昼夜温差、无预警，**城市名显示为拼音**（如 Zhongshan）；体感温度缺省回气温，风力按风速换算蒲福风级。
- **限流防护**：公共凭证高峰期限流时，仓库层按接口返回的建议等待秒数（`s` 字段）自动退避重试一次；屏上无数据时展示倒计时卡片（自动重试仅一次，之后转手动模式），有缓存数据时降级为提示并继续展示缓存；手动刷新与「使用精确定位」共享 10 秒节流。
- **紫外线为估算值**：接口未提供紫外线字段，引擎按「天气现象 + 时间段」估算（晴 7 / 多云 5 / 阴 2 / 雨雪 1，夜间归零），展示为「指数·等级」形式。
- **GPS 依赖 Google Play Services**：设备无 GMS 时定位失败，App 会提示改用搜索或 IP 定位，功能不受阻。
- **每日推送**基于 WorkManager 持久化周期任务（每 24 小时，推送时刻可在设置页选择，省电策略下可能有几分钟浮动），任务随系统持久化，**设备重启后会自动恢复，无需打开应用重新注册**（开机自启接收器会按设定时刻重新对齐）；Android 13+ 首次开启时申请通知权限。
- **暗黑模式**跟随系统（底色 `#121212`、卡片 `#1E1E1E`、文字 `#E0E0E0`，避免纯黑）。

## 开源协议

本项目基于 [MIT License](LICENSE) 开源，作者「莫」。

## 致谢

- 天气数据：[apihz.cn 接口盒子](https://cn.apihz.cn) 提供的中国气象局数据
- 图标与设计规范：[Material Design 3](https://m3.material.io/)
