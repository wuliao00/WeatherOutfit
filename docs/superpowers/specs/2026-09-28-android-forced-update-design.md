# 简衣 · 安卓强制更新（更新源 Gitee）设计

- 日期：2026-09-28
- 状态：已与用户逐段确认，待评审本文件后进入实现计划
- 范围：**只做 Android 侧的强制更新**。黄历 / 星座运势 / 历史上的今天 三块内容是独立的一批，本文件末尾只记录调研结论与待决项，不在实现范围内。

---

## 1. 目标与非目标

**目标**

1. App 冷启动时检查是否有新版本；破坏性版本可以硬门禁（不更新不能用），普通新版本只弹可跳过提示。
2. 更新源必须是 Gitee（国内可达），且 App 端**不需要任何 token**。
3. 更新失败、源不可达、清单写错，任何一种情况都**不能把用户锁死在 App 外面**。

**非目标**

- 不做 iOS 侧更新（App Store 规则不允许自建更新通道；iOS 的 `UpdateGateway` 是空实现，入口不显示）。
- 不做断点续传（见 §7，附件域名不支持 Range）。
- 不做"静默自动安装"（Android 不允许）。
- 不做灰度/分渠道发布。

---

## 2. 已确认的决策（用户选定）

| 决策点 | 选定 |
|---|---|
| 交付顺序 | 先强制更新，内容卡片第二批 |
| 强制程度 | **分级**：只有低于 `minSupportedVersionCode` 才硬拦；介于最低与最新之间只弹可跳过提示 |
| 发布链路 | App 侧 + CI 自动发布到 Gitee |
| APK 到手方式 | App 内下载 + 进度条 + 拉起系统安装页 |
| 检查时机 | 冷启动一次 + 设置页「检查更新」手动入口 |
| 存量用户 | 基本没有外部用户，不为"旧包签名不一致"设计专门分支，只给一句提示 |

---

## 3. 事实基础（本轮实测，不是推断）

这些是设计的约束来源，实现时不要重新假设：

1. **Gitee v5 API 匿名读也是 403**。对照组用超大公共仓库 `opencv/opencv` 同样 403 ⇒ 不是权限配置问题。App 端不能走 API。
2. **`https://gitee.com/<ns>/<repo>/raw/<ref>/<path>` 免鉴权可用**：302 → `raw.giteeusercontent.com/…?metadata=<base64>&signature=…` → 200，实测 README.md 29,459 字节完整。**raw 域支持 Range（实测 206 + `Content-Range`）**。
3. **Gitee Release 附件免鉴权可下载，`.apk` 不被特殊拦截**：实测真实仓库的 79,027,726 字节 APK，三跳 `releases/download/<tag>/<file>` → 302 → `attach_files/<id>/download/<file>` → 302 → `foruda.gitee.com/attach_file/<oid>/<小写名>?token=&ts=&attname=` → 200，`Content-Type: application/zip`，头 8 字节 `50 4b 03 04 00 00 00 00`（`PK\x03\x04`）。无 cookie、无 Referer，六种 UA（含 `okhttp/4.12.0`、`Dalvik/2.1.0 (…Android 14…)`）全部 200。
4. **附件域名 `foruda.gitee.com` 忽略 Range**：请求 `bytes=0-1023` 返回 200 + 全量，无 `Accept-Ranges`、无 `Content-Range` ⇒ **断点续传不可用**。2026-09-29 再测复现（`bytes=0-99` 直接开始整文件传输，30 秒推了 26MB / 58MB 才掐断）。
4b. **附件地址对 `HEAD` 答 200（2026-09-29 新增）**：`curl -I` 一个第三方真实存在附件的地址
   `gitee.com/beijing-jicang/yichenbao/releases/download/v1.0.34/24-12-22.apk`
   → 跟完三跳后 `HTTP=200`，FINAL 落在 `foruda.gitee.com/attach_file/<id>/<file>?token=&ts=&attname=`。
   这条是 `headOk` 探针的前提：它若不成立（403/405），"包下不到就不拦人"那条降级就会**永远**成立，
   门禁静默失效而所有测试全绿。机制现已证明，我们自己第一个 release 出来后只需顺手复核一次
   （确认附件真传上去了、tag 与 versionName 拼对了），不再是"机制未知"。
5. **签名链接 15 分钟过期，失败码是 401 不是 403**（实测 +14.3min 仍 200，+15.3min 起 401，body `{"error":"unauthorized"…}`）。⇒ 不能缓存第三跳 URL，每次下载都要从 `releases/download/…` 重新走。
6. **`/releases/latest` 的响应形态由 UA 决定**：`okhttp/4.12.0` 给 JSON，`Dalvik/2.1.0 (…)` 给 HTML，`curl` UA 直接给源码 zip。App 默认 UA 正是 Dalvik。⇒ **不用它当清单**，避免依赖 UA 协商。
7. **配额**：单附件 100MB（GVP 200MB）、单仓库附件总量 1GB、Git 单文件 50MB、仓库 500MB。当前 debug APK 23,999,408 字节，release 未签名 4,630,357 字节 ⇒ 走附件余量充足。
8. **Gitee Pages 已下线**（`*.gitee.io` 全 404，官方 sitemap 无条目）；**Git LFS 仅付费企业**。备选承载只剩"提交进仓库走 raw"（受 50MB 单文件限制）。

---

## 4. 架构与文件清单

沿用仓库既有分层：领域/状态进 `shared/commonMain`，平台能力走 `AppDependencies` 里的接口 + 各端实现（与 `PushScheduler` / `LocationProvider` / `NotificationGate` / `ExtremeAlerter` 同构）。

**新增 `shared/src/commonMain/kotlin/com/jianyi/outfit/data/update/`**

- `UpdateManifest.kt` — `@Serializable`，字段见 §5。
- `UpdateDecision.kt` — `enum class UpdateVerdict { UpToDate, Optional, Forced, Unreachable }` + **纯函数** `decide(manifest: UpdateManifest?, currentVersionCode: Int, apkReachable: Boolean): UpdateDecision`。
- `UpdateRepository.kt` — `suspend fun fetch(): UpdateManifest?`（Ktor GET raw，超时、解析失败一律返回 null）、`suspend fun probeApk(url: String): Boolean`（HEAD，5 秒超时，跟完重定向后 2xx 才算 true）。

**`shared/src/commonMain/.../data/AppDependencies.kt`** 内新增接口：

```kotlin
interface AppUpdateGateway {
    val supported: Boolean                       // iOS = false，UI 据此不显示任何更新入口
    fun hasInstallPermission(): Boolean
    fun requestInstallPermission()
    fun install(apkPath: String): InstallResult  // 拉起系统安装页
}

enum class InstallResult { Launched, PermissionMissing, Failed }
```

**一次 `check()` 的完整流程**（冷启动与设置页手动入口走同一条）：
`fetch()` → 无效则直接 `Unreachable`（**不发 HEAD，省一次请求**）→ 有效且 `current < versionCode` 才 `probeApk()` → `decide(manifest, current, reachable)`。

**新增 `shared/src/commonMain/.../ui/update/`**

- `UpdateViewModel.kt` — 状态机（§6），持有 `UpdateRepository` + `AppUpdateGateway` + 当前 versionCode。
- `UpdateGateLayer.kt` — 门禁/提示卡片，挂在 `RootScreen` 的 layer 上（与 `DisclaimerLayer` 同套路），**不进导航栈**。

**新增 `app/src/main/java/com/jianyi/outfit/`**

- `update/ApkDownloader.kt` — 流式写盘 + 进度 Flow + 边下边算 sha256。
- `update/AndroidUpdateGateway.kt` — FileProvider + `ACTION_VIEW` 安装；`canRequestPackageInstalls()` 判断权限。
- `platform/IosUpdateGateway.kt`（`shared/src/iosMain`）— `supported = false` 的空实现。

**改动**

- `app/src/main/AndroidManifest.xml` — 加 `REQUEST_INSTALL_PACKAGES` 与 FileProvider（authority `${applicationId}.fileprovider`）。
- `di/AppContainer.kt` — 注入 `AppUpdateGateway` 与当前版本号（沿用既有 `BuildConfig` 注入路；`kotlin_lint.py` 已禁 commonMain 出现 `BuildConfig`）。
- `ui/settings/SettingsScreen.kt`（在 shared）— 加「检查更新」行 + 当前版本号 + 免责声明。
- `.github/workflows/release.yml` — 新建（§8）。
- `update.json` — 仓库根目录新建（§5）。

---

## 5. 数据契约：`update.json`

放在仓库根，走 `https://gitee.com/wuliao11541/WeatherOutfit/raw/main/update.json`。由**版本号提升的那次提交一起改**（可 review、与代码同版本），不由 CI 写。

```json
{
  "versionCode": 9,
  "versionName": "2.2.0",
  "minSupportedVersionCode": 9,
  "apkUrl": "https://gitee.com/wuliao11541/WeatherOutfit/releases/download/v2.2.0/jianyi-2.2.0.apk",
  "sha256": "<64 位小写十六进制>",
  "sizeBytes": 24115200,
  "notes": "本次更新内容…"
}
```

必填：`versionCode`、`versionName`、`minSupportedVersionCode`、`apkUrl`。
可选：`sha256`（缺失则跳过校验，见下）、`sizeBytes`（缺失则进度条走不确定态）、`notes`（缺失则卡片不显示说明段）。

**`sha256` 为什么改成可选（写计划时发现的时序死结）**：清单由人在提版本号时手写，而那一刻 APK 还不存在、
哈希无从得知；若把 `sha256` 定为必填，就只能先填一个假哈希（校验必失败）或者让 CI 回写清单
（CI 就没有 Gitee 写权限）。所以：清单里**声明了** `sha256` 就强制校验、不一致即判失败；
**没声明**则跳过校验并照常安装。CI 首次成功产出包之后，由我把真哈希补进这份清单
（此后每次发布都要更新它，§8 第 2 步会比对声明值与实际包）。

**解析口径（写死，不留歧义）**：任一必填字段缺失、类型不符、`sha256` 声明了但不是 64 位十六进制 ⇒ 整份清单判**无效** ⇒ `Unreachable`。未知字段忽略（`ignoreUnknownKeys`）。理由：宁可不提示，也不能拿半份清单去拦人。


---

## 6. 判定与状态机

`decide()` 规则（纯函数，commonTest 表驱动覆盖）：

| 条件 | 结果 |
|---|---|
| 清单 null / 无效 / 拉取失败 / 超时 | `Unreachable` |
| `currentVersionCode >= versionCode` | `UpToDate`（含"装了更新的测试包"） |
| `currentVersionCode < minSupportedVersionCode` **且** `apkReachable` | `Forced` |
| `currentVersionCode < minSupportedVersionCode` **但** `apkReachable == false` | `Optional` |
| 其余（`minSupported <= current < versionCode`） | `Optional` |

**HEAD 探包是硬门禁的前提**：清单已提交但 CI 还没把 APK 传上去的那个窗口期，HEAD 拿不到 2xx ⇒ 降级成 `Optional`，绝不出现"拦住了但下不到包"。这是对"清单拉不到绝不拦"的同一条延伸。

**`minSupportedVersionCode > versionCode`（写错）**：按 `min(minSupportedVersionCode, versionCode)` 处理，即仍然 `Forced`。不静默忽略，靠 §9 的仓库自校验用例兜住。

UI 状态：`Idle → Checking → (Hidden | OptionalCard | Gate) → Downloading(progress) → Installing → Failed(reason)`。
`Gate` 无关闭按钮、返回键不放行；`OptionalCard` 可跳过，每次冷启动最多弹一次 —— 这个"已弹过"只存在 ViewModel 内存里，**不落盘**（落盘会让用户永远看不到第二次提示，而强更的提示本来就该每次开 App 都提醒一次）。

---

## 7. 下载与安装

- 落盘 `context.cacheDir/update/jianyi-<versionName>.apk`，先删同名旧文件。
- **每次下载都从 `apkUrl` 重新走三跳**，不缓存签名 URL（15 分钟过期）。
- 流式写盘 + 边写边算 sha256；下完比对，不一致 ⇒ 删文件、判失败。
- **不支持断点续传**（foruda 忽略 Range）⇒ 失败即整文件重下，最多自动重试 1 次，之后进 `Failed` 给逃生口。
- 安装：`FileProvider.getUriForFile` + `ACTION_VIEW` + `application/vnd.android.package-archive` + `FLAG_GRANT_READ_URI_PERMISSION`。API 26+ 需 `REQUEST_INSTALL_PACKAGES`，未授予时用 `ACTION_MANAGE_UNKNOWN_APP_SOURCES` 引导（各 ROM 表现不同，见 §10 真机清单）。
- 逃生口（`Failed` 与权限被拒时都给出）：「重试」+「复制下载链接」。复制链接是最后一道保险 —— 用户可以拿去浏览器或另一台设备下。

---

## 8. CI 发布

新建 `.github/workflows/release.yml`，触发条件 `on: push: tags: ['v*']`：

1. 签名构建 `assembleRelease`（沿用现有 `RELEASE_KEYSTORE_*` 环境变量机制）。
2. 计算 APK 的 sha256 与字节数，与 `update.json` 里的值比对；**不一致就让 job 失败**（防止清单和包漂移）。
3. `POST https://gitee.com/api/v5/repos/{owner}/{repo}/releases`（formData：`access_token`、`tag_name`、`name`、`body`、`target_commitish`、`prerelease`）→ 取响应 `id`。
4. `POST …/releases/{id}/attach_files`（`multipart/form-data`，字段 `file`）。

需要的 secrets：`RELEASE_KEYSTORE_BASE64`、`RELEASE_KEYSTORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`、`GITEE_API_TOKEN`。
**当前这五个全部未配置**（今天已查实 `Decode release keystore` 步骤是 skipped）。

**手动兜底（必须写进 README）**：Gitee 网页 → 发行版 → 新建（tag 与 `update.json` 里 `apkUrl` 的 tag 段一致）→ 上传 APK 附件。CI 那两步坏掉时，功能不中断。

---

## 9. 测试

**自动（commonTest，两端各跑；iOS 模拟器那套现成）**

- `UpdateDecisionTest`：表驱动 ≥ 10 例，覆盖 §6 全部分支，含"当前版本高于清单""`min > latest` 错配""HEAD 探不到包 ⇒ 降级 Optional"。
- `UpdateManifestTest`：合法 / 缺必填 / 类型错 / `sha256` 非 64 位十六进制 / 含未知字段。
- **仓库自校验用例**：读真实 `update.json` 文件，断言必填字段齐全、`sha256` 若声明则格式正确、`apkUrl` 的 tag 段与 `versionName` 一致。这条专门拦"改了版本号忘了改清单"。
  实现位置是 **app 的 JVM 测试**而不是 commonTest —— commonTest 没有读文件的 API，且 iOS 模拟器沙箱里也没有仓库工作树；从测试工作目录向上找到含 `settings.gradle.kts` 的根目录再读。


**真机（本机 Windows 测不了的部分，vivo V2156A 与 OPPO PLB110 各一遍）**

1. 清单 404 / 域名不可达 ⇒ 正常进首页，不拦。
2. `minSupported` 高于当前 ⇒ 全屏门禁，返回键不放行。
3. 清单说有新版本但 `apkUrl` 指向不存在的 tag ⇒ 只弹可跳过提示，**不拦**。
4. 下载进度条、sha256 校验、拉起安装页、装成功。
5. 权限被拒 → 引导页 → 回来能继续。
6. 下载中途断网 → 重试 → 逃生口「复制链接」可用。
7. 各 ROM 安装页差异（ColorOS / OriginOS 的"未知来源"引导文案不同）。

---

## 10. 风险与未验证项（明确标出，不假装已验）

| 项 | 状态 | 影响与对策 |
|---|---|---|
| CI 那两步 Gitee API | **只有 OpenAPI 文档，无真实 token 实测** | 首次打 tag 就是第一次真跑；失败走 §8 手动兜底 |
| token 放 formData 还是 header | 未实测（规范只写 formData） | 实现时先按 formData，失败再试 query |
| 五个 secrets | 未配置 | 用户操作；未配前 CI 出的是未签名包，装不上 |
| foruda 忽略 Range | 已实测 | 不做续传，整文件重下 |
| 附件名被小写化 | 已实测（`attname` 保留原名） | `apkUrl` 里的小写文件名按实际填，别靠大小写区分 |
| 内容审核字段 `censor_failed` | 存在，行为未知 | 首次发布后检查附件是否可下载 |
| 门禁 + 源不可达 | 设计已消解 | HEAD 探包 + 清单无效即 Unreachable |

---

## 11. 第二批（内容卡片）调研结论存档

不在本批实现范围，但结论已带证据，供下一批决策：

- **黄历：可做，且能进 commonMain 两端共用。** `cn.6tail:tyme4kt` 1.5.0（MIT，实测 `.module` 含 `iosArm64`/`iosSimulatorArm64` 变体，klib 466KB，运行期仅依赖 kotlin-stdlib）。宜忌表确实在包里 —— 实跑 2026-09-28 得 `农历丙午年八月十八`、宜 `[嫁娶,纳采,订盟,…]`、忌 `[安葬,纳畜,出行,…]`、神煞、节气、干支、生肖、星座。两个坑：调休表只编到 2026；宜忌由干支+建除+神煞推导，**与具体印刷通书可能对不上**，文案不得宣称权威。
- **`NateScarlet/holiday-cn`：真实可用（MIT、2180★、2026-09-27 仍在推），但解决的是长期维护，不是当前断层** —— 实测 `2027.json` 是 236 字节空壳（`papers: []`），`2025/2026.json` 各 1 条通知。国务院尚未发布 2027 安排。
- **星座运势：无任何可用免 key 源**（`api.oick.me`、`meow.la`、`v.api.hehesw.cn`、`api.fangliangapp.com`、`api.vvhan.com`、`api.60s.cc` 等全部 NXDOMAIN；`api.k780.com` 证书不可信；`api.dujin.org` 404；tianapi 要 key）。可选路径：一次性预生成静态数据集内置（无 key、无 cron）／每日 LLM 生成（要 key + 一条 cron）／砍掉。**待用户决策。**
- **历史上的今天：不建议做。** 百度百科月度 JSON 免鉴权可读（`.../cms/home/eventsOnHistory/09.json` → 200、340,486 字节、全年约 4.2MB），但**找不到允许再分发的授权**；Wikimedia 在本网络不可达；GitHub 上 8 个同类数据集**许可证全部为空**（含 97★ 的 `PrintNow/TodayInHistory`，其数据源是维基百科 = CC BY-SA，share-alike 传染）。无许可证 = 保留所有权利，与直接爬百度百科是同一档风险。被引用的 `timqian/history` 实测 **404 不存在**。
- **合规**：黄历/星座若上架应用商店会碰"封建迷信"红线，需免责声明 + 可能要从首页降到二级页。**当前是 Gitee 直发 APK、未上架**，故本批不为该规则牺牲首页体验；上架时再处理。
