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

## [1.0.0]

### Added
- 首个公开版本：实时天气查询（IP 自动定位 / GPS 精确定位 / 手动选城），按温度、湿度、紫外线、风力四维度生成通勤 / 户外 / 休闲三场景穿搭推荐。
- 历史城市管理、自定义穿搭模板（保存 / 左滑删除）、每日穿搭推送、极端天气预警通知。
- 穿搭推荐引擎纯 Kotlin 实现，配套 JVM 单元测试。
- 首次启动使用须知弹窗（可勾选不再提示）。
- minSdk 26 ~ targetSdk 35，手机 / 折叠屏 / 平板自适应布局。
