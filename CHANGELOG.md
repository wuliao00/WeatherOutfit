# 更新日志

本项目的所有显著变更都记录在本文件中。
格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [Unreleased]

### Added
- 每日穿搭推送迁移至 WorkManager：任务随系统持久化，设备重启后自动恢复；新增开机自启接收器（`BootReceiver`）按 DataStore 中的设定时刻对齐调度。
- 设置页支持自填 API 凭证（appid / appkey / apiurl），留空回退内置默认；凭证仅存本地 DataStore。
- 设置页新增每日推送时刻选择（6:00 ~ 9:00，默认 8:00）。
- 新增 GitHub Actions CI：push / PR 触发，跑 lint、单元测试与 Debug 构建。

### Changed
- 天气缓存拆分至独立数据库文件 `weather_cache.db`，云备份与设备迁移按文件排除缓存（Room schema 同步导出至 `app/schemas/`，业务库升级至 v2）。
- 缓存表新增过期清理：保留 7 天内的过期数据作弱网 / 限流回退，超期在每次写入缓存后自动删除。
- 城市切换的「查重 → 插入 → 清标记 → 设当前」多步写改为 Room 事务，保证原子性。
- 仓库层抽象出接口（Weather / City / Settings / Template），ViewModel 依赖抽象，便于 JVM 单测。
- 穿搭详情页改经导航参数（天气缓存 key）从仓库读取首页快照，移除全局会话可变单例，消除 null / 旧值竞态。
- 首页自动定位链增加去重标记，`currentCity` 连续发射 null 时不再重复触发定位与请求。
- `OkHttpClient` 全局单例化（连接池复用）；通知改用应用自有小图标 `ic_notification`。

### Removed
- 移除 AlarmManager 每日闹钟路径与 `DailyAlarmReceiver`（由 WorkManager 取代）。
- 移除零引用的 `NetworkStatus` 工具类。

## [2.2.0] - 2026-09-30

versionCode `8 → 9`。首个支持应用内更新的版本。

> 说明：上面 `[Unreleased]` 里那批 2.0.0 ~ 2.1.2 的变更从未按版本切成条目（本文件此前只到 `1.0.0`）。
> 本次不搬动它们，以免把两份历史混在一起 —— 补账是单独一件整备项。

### Added
- 安卓应用内强制更新链路：冷启动从 Gitee raw 域读仓库根 `update.json`（App 侧不需要任何 token），
  按 `minSupportedVersionCode` 分级 —— 普通新版本只弹可跳过提示；低于最低可用版本**且那个附件确实能下载**
  时进入全屏硬门禁（遮罩吃掉点击、返回键不放行、没有任何关闭入口）。
- App 内下载 APK：流式落盘、完整性与进度分母按响应 `Content-Length` 算、每 512KB 报一次进度，
  清单声明了 `sha256` 就强制比对；下载完经 FileProvider 拉起系统安装页，
  没给「安装未知应用」权限时先把用户送到系统设置页（门禁本身不退化成可跳过提示）。
- 失败一律不锁死用户：清单读不到 / 看不懂 / 探包失败都不拦人，下载失败放开返回键
  （门禁下次冷启动会重新出现），门禁下还提供「复制下载链接」逃生口。
- 设置页「检查更新」手动入口：反馈只走那一行的副标题，六档状态互相分得开 ——
  还没查过 / 正在检查 / 已是最新 / 发现新版本（可跳过）/ 需要更新 / 无法确认。
  检查失败**绝不**写成"已是最新"（那是把失败伪装成成功）；iOS 上整行不渲染。
- CI 发布：推 `v*` tag 触发签名构建并自动发布到 Gitee Release（`.github/workflows/release.yml`），
  签名 secrets 缺失时宁可红也不给一个"绿而没发出去"。README 新增「发布手册」含手动兜底全流程。
- 仅 debug 生效的清单地址覆盖（出包时 `-PupdateManifestUrl=`）与仓库根 `update.test.json`：
  在发版之前把硬门禁整条链路（真网络 + 真解析 + 真 HEAD + 真 UI + 真返回键）在真机上跑一遍的
  唯一路径 —— 因为 `minSupportedVersionCode` 保持 1，生产数据上门禁本来永远不会出现。

### Changed
- 设置页「关于」里那行写死的 `版本 2.0.0` 改为注入的 `versionName`：装机早已 2.1.2，
  而它一直显示 2.0.0；抬到 2.2.0 后它会紧挨着"当前版本 2.2.0（…）"，两个版本号互相否认。

## [1.0.0]

### Added
- 首个公开版本：实时天气查询（IP 自动定位 / GPS 精确定位 / 手动选城），按温度、湿度、紫外线、风力四维度生成通勤 / 户外 / 休闲三场景穿搭推荐。
- 历史城市管理、自定义穿搭模板（保存 / 左滑删除）、每日穿搭推送、极端天气预警通知。
- 穿搭推荐引擎纯 Kotlin 实现，配套 JVM 单元测试。
- 首次启动使用须知弹窗（可勾选不再提示）。
- minSdk 26 ~ targetSdk 35，手机 / 折叠屏 / 平板自适应布局。
