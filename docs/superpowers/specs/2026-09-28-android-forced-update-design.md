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
| 存量用户 | 基本没有外部用户，不为"旧包签名不一致"设计专门分支。**代码里也没有那句提示**（终审修复轮把文档对齐代码，不改代码）：`UpdateCopy.failureText` 只有四种成因 —— `Network` / `ChecksumMismatch` / `Io` / `NoSpace`，没有"签名不符"这一种；签名不匹配是**系统安装页**自己报的，App 侧看到的仍然是 `Launched`（安装页弹出去了），而 §7 那条 C1 保证这一刻门禁不松，所以它不会被误读成"已经装上了" |

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
9. **附件响应的 `Content-Length` 比清单里手写的 `sizeBytes` 更有资格当完整性依据**（2026-09-30 修复轮引入的判据，理由不是偏好而是两条实测）：
   - §3.3 那次实测（第三方 79,027,726 字节的 APK）里第三跳那条 200 是**定长响应**，长度是服务器给的；
   - §3.4 那次"掐半截"的失败形态是**通道正常结束、不抛异常** —— 于是"收到多少字节"只有对着服务器自己说的那个数才判得出来。
   清单里的 `sizeBytes` 是人在 APK 还不存在时手写的（与 §5 那条"sha256 改可选"是同一条理由，而那条 Ruling 只救了 sha256），
   CI 重跑一次、zip 时间戳变一字节就会与它差 1 ⇒ 用精确等式比它，每次下载都以"网络中断"告终；写大十倍 ⇒ 空间前置闸按错的数算 ⇒ **永久 NoSpace，硬门禁下没有任何出路**。
   所以现在的次序是：`Content-Length` 优先（完整性判定 + 进度分母 + 空间前置闸），手写值只在它缺席时兜底、
   并且"与实测不符"必须打一条 warning（走 §5 那条既有注入 sink），因为这个漂移否则永远没人知道。
10. **生产 `update.json` 今天既没有 `sha256` 也没有 `sizeBytes`**（2026-09-30 复核仓库根那份文件：只有 versionCode/versionName/minSupportedVersionCode/apkUrl/notes 五项）。
    ⇒ 清单侧的完整性输入是空的，全靠 §3.9 的 `Content-Length` 兜住；万一某个 CDN 形态不给这个头，就只剩"非空即完整"这一条。
    **这个状态由"CI 出包之后那一次提交"消除**（不是抬版本号那一次 —— 那一刻签名包还不存在，哈希无从得知）：
    从 CI 日志/摘要读实际值，补进 `update.json` 并推 main（两份远端），README 发布手册第七节列成必做步骤。
    否则 §9 那条仓库自校验用例仍然绿，而两道闸有一道是开着的。

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

- `update/AndroidUpdateGateway.kt` — FileProvider + `ACTION_VIEW` 安装；`canRequestPackageInstalls()` 判断权限。
  （实现落地时又加了一个文件：`update/UpdateInstallPath.kt` —— `install()` 的路径守卫，见 §7。）
- `platform/IosUpdateGateway.kt`（`shared/src/iosMain`）— `supported = false` 的空实现。

> **一处位置修订**：`ApkDownloader.kt` 最终不在 app 模块，而在 **`shared/src/androidMain/kotlin/com/jianyi/outfit/data/update/`**。
> 理由是 shared 的 Ktor 是 `implementation()`，app 的编译类路径上没有 `io.ktor.*`，而下载器要用的引擎工厂
> `httpClientEngine()` 本来就是 shared 模块内的 internal 声明（Ruling 见 SDD 台账）。
> 代价：将来若要把下载器挪回 app，得先给 app 显式声明 ktor 依赖。
> 测试放在同模块新建的 `shared/src/androidUnitTest/`（`sha256Of`、`downloadEvents` 都是 internal，跨模块看不见）。

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
可选：`sha256`（缺失则跳过校验，见下）、`sizeBytes`（缺失则进度条走不确定态；**且它本来就不参与完整性判定**，
判据是响应头的 `Content-Length`，见 §3.9 —— 这条在 2026-09-30 修复轮才写清，之前实现按手写值判）、
`notes`（缺失则卡片不显示说明段）。

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

**两条相反方向的力，必须同时成立（Task 6+7 评审轮定死，详见 §7.1）**：

1. `Failed` 放开遮罩与返回键，连 `forced=true` 也放开 —— 下不下来时必须能走。
2. `forcedThisLaunch` **只进不出**：这次冷启动挂过门禁之后，一次失败的检查（手动「检查更新」恰好断网）
   不许把状态打回 `Hidden` —— 门禁不许被用户随手一点解除。

还有第三力，方向与第 1 条相反、容易被第 1 条的措辞顺手"统一"掉（终审修复轮 C1）：
**安装页弹出去（`InstallResult.Launched`）不算失败也不算装完**，强制那一档必须仍然留在 `Gate`。
详见 §7 那条与 §7.1 里"失败逃生 ≠ 成功绕过"。

粘滞的实现**不能**读"当前状态是不是 `Gate`"：下载一开始状态就变成 `Downloading`，那一刻恒 false，
`Downloading/Failed` 的 `forced` 全丢 ⇒ 下载中途按一次返回就绕过门禁（评审给过这条修法，被否）。

`Downloading` 的 `done/total` 三种取值各对应一句不同的话（null/有值 × null/有值），
且 `total == 0` 与 `total == null` 是同一件"不知道"（进度条与文案必须一起走不确定态，
否则出现"条不涨、文字却写 `11.4 MB / 0.0 MB`"那种自相矛盾）。

---

## 7. 下载与安装

- 落盘 `context.cacheDir/update/jianyi-<versionName>.apk`，先删同名旧文件。目录名由 shared 的 `UPDATE_DIR_NAME` 一处定义：它是**下载落盘位置 / `file_paths.xml` 的 FileProvider root / `install()` 的路径守卫**三处共同的契约，各写一份字面量就会漂（漂了的表现是真机装不上而单测全绿）。
- **每次下载都从 `apkUrl` 重新走三跳**，不缓存签名 URL（15 分钟过期）。
- 流式写盘 + 边写边算 sha256；下完比对，不一致 ⇒ 删文件、判失败。
- **完整性与进度分母只看 `Content-Length`**（§3.9）：`isBodyComplete(收到的字节数, Content-Length ?: sizeBytes)`。
  清单手写的 `sizeBytes` 不参与判定，只在实测长度缺席时兜底，并且与实测不符时打一条 warning。
  空间前置闸（`shouldRefuseDownload`）同样按实测长度判，且对 `sizeBytes` 按配额 100MB 加顶 ——
  否则一个离谱的手写值会让 `total * 5` 溢出成负数，把这道"为了省流量而设"的闸静默关掉。
- **不支持断点续传**（foruda 忽略 Range）⇒ 失败即整文件重下。
- **自动重试只对 `Network` 这一种成因，最多 1 次**（2026-09-30 修复轮补的实现；这句过去只写在 spec 里，代码没人实现也没登记偏离）：
  `ChecksumMismatch` 重一遍只是再下一遍同一个坏包，`NoSpace` 重一遍必然还是不够，`Io` 是本地盘的问题 ——
  把它们也重一遍等于把一次故障变成两次流量。重试期间**不 emit 中间那条失败**，否则门禁卡片会在下载路上闪成失败态又跳回来。
- 安装：`FileProvider.getUriForFile` + `ACTION_VIEW` + `application/vnd.android.package-archive` + `FLAG_GRANT_READ_URI_PERMISSION`。API 26+ 需 `REQUEST_INSTALL_PACKAGES`，未授予时用 `ACTION_MANAGE_UNKNOWN_APP_SOURCES` 引导（各 ROM 表现不同，见 §10 真机清单）。
- **`install(path)` 只接受 `cacheDir/update/` 之下的文件**（层间契约守卫，2026-09-30 修复轮）：
  `Success(path)` → `install(String)` 是这条链上唯一的跨层输入，过去没有任何一处验过它真的是下载器落盘的那个文件。
  守卫放在 gateway 而不是下载器，因为只有 gateway 知道 FileProvider 的 root；下载器那侧的文件名清洗只是**策略**。
  越界 ⇒ 直接 `InstallResult.Failed` 并留一条带路径的日志，**不许**让 `getUriForFile` 抛 `IllegalArgumentException`
  再被折成"写入失败，请清理手机存储"那句误报文案。
- 逃生口（`Failed` 与权限被拒时都给出）：「重试」+「复制下载链接」。复制链接是最后一道保险 —— 用户可以拿去浏览器或另一台设备下。
  没有 `apkUrl` 可复制时，那颗按钮就不摆（这条过滤现在在 `gateActions(state, hasUrl)` 里，不在 UI 侧 `.filterNot`：
  两边各写一份的结果是"遮罩按住整页而按钮一颗不剩"，见 §6）。
- **`InstallResult.Launched` ≠ 装完了**（终审修复轮 C1，2026-09-30）：这个返回值的语义只有一个 ——
  `AndroidUpdateGateway` 成功 `startActivity`、安装页弹到了前台。安卓没有"安装完成"的回调，而那一刻包还没装上。
  所以强制那一档在 `Launched` 时**必须留在 `Gate`**（与 `PermissionMissing` 同一处理），非强制档才 `Hidden`。
  曾经的写法是 `Launched -> Hidden`，症状是"门禁 → 下完 55MB → 安装页弹 → 遮罩与返回键同帧松开 →
  用户在安装页按取消 → 回到一个完全没有门禁的旧版，直到杀进程"，而全程零红：
  `runCheck` 里"禁止 Gate→Hidden"只管检查路径、`checkedThisLaunch` 与 `LaunchedEffect(Unit)` 都不再触发第二次检查，
  这次启动内没有任何东西会把它挂回来。那条用例（`launched_install_hides_the_gate`）还把行为钉成了期望。
  **留在门禁里不会把"真装成功了"的用户锁在外面**：装成功必然由安装器杀掉本进程，下次冷启动是新 VM、
  判定层读到 versionCode 已达标 ⇒ `UpToDate` ⇒ 门禁自然消失（落盘没有任何"已跳过/已装"状态需要复位）。

### 7.1 失败态不锁死用户（**刻意的语义，不是遗漏**）

计划第 5 行的 Goal 写着"任何失败都能退出而不锁死用户"，这一句在代码里是三处一致的实现，改任何一处都要同时核对另外两处：

| 状态 | 遮罩 + 吃点击 `UpdateUiState.blocksUser(hasUrl)` | 返回键 `shouldBlockBack(state, hasUrl)` | 文案分量 `gateIsForced` | 关闭入口 |
|---|---|---|---|---|
| `Gate`（含"安装页已弹出去"那一刻） | 按住 | 吞 | 门禁那一套 | 无 |
| `Downloading(forced=true)` | 按住（`hasUrl` 为假时放开，否则零按钮） | 同上 | 门禁那一套 | 无 |
| `Failed(forced=true)` | **放开** | **放开** | **门禁那一套** | 无 |

- **`Failed` 放开是刻意的**：硬门禁 + 下载失败时把用户锁在屏幕上，正是本功能最要避免的"变砖"形态
  （下不下来、又走不掉，只能杀进程）。放开之后卡片仍然给「重试」与「复制下载链接」，逃生口在那里，不在返回键上。
- **但这一条不适用于 `Launched`（终审修复轮 C1，别把它当挡箭牌）**：§7.1 放开的是"**失败逃生**"，
  而"安装页弹出去之后状态退回 `Hidden`"是"**成功之后绕过**"——包就在用户手边、只差那一步他没按，
  按取消回来就是一个没有门禁的旧版。两类不同，结论相反：`Failed` 放开、`Launched` 留在 `Gate`。
  **也不许顺手把 `Failed` 改回锁死** —— 那正是本节存在的原因。
- **`blocksUser=false` 而 `gateIsForced=true` 这个差一个分支是接受的**（Task 8 实现时发现的这条不一致；那函数原名 `gateHoldsPage`）：
  文案分量决定"标题说什么、有没有关闭入口"，放开点击/返回决定"退得出去吗"，是两件事。
  合并前两条会让下载失败之后仍然按住用户；合并后两条会给 `Failed` 摆一颗按下去毫无反应的「以后再说」
  （VM 的 `dismissOptional()` 只认 `OptionalCard` 那一态）。两种合并都有具体症状，所以分开写、分开测。
- **放开不等于解除**：`Failed` 仍然没有关闭入口，标题仍然说"需要更新"，而**门禁下次冷启动会重新出现**
  （判定层每次冷启动重算，落盘没有任何"已跳过"状态）。
- 与之相对的**另一半**是"门禁不许被一次手动检查解除"（同轮修复）：`forcedThisLaunch` 是**只进不出**的粘滞，
  这次冷启动挂过门禁之后，`checkOnce`/`forceCheck` 拿到 `Unreachable`/`UpToDate` 也不许把状态打回 `Hidden`。
  这两条不矛盾：**被禁掉的是"检查失败后卡片整张消失"，被放开的只是"下不下来时的退出路"**。
  注意不要用"读当前状态是不是 `Gate`"来实现粘滞 —— 下载一开始状态就变成 `Downloading`，
  那一刻 `is Gate` 恒 false，`Downloading/Failed` 的 `forced` 全丢，正好复现"下载中途按一次返回就绕过门禁"。
- **遮罩盖不住 `Dialog`/`Popup`**：Compose 里它们是**独立 window**，不在本层的 composition 树内。
  所以"这层盖得住所有弹层"那句描述只在 Activity 的内容视图内成立（本轮之前写的是"永远盖在所有页面与弹层之上"，
  现已改口）。这不是绕过口：返回键与点击仍然被 Activity 这一层拦着，Dialog 挡不住的是"看得见"而不是"绕得过去"。

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
- **`ApkDownloadFlowTest`（`shared/src/androidUnitTest`，Task 6+7 评审轮新建）**：`downloadEvents` 那一层的判定与组装。
  ① 假响应走过**真实写盘**之后 flow 必须真的发过 `Progress`，且 `Success.path` 落在给定目录里；
  ② 跨层：同一个 `Progress(12_000_000, 24_000_000)` 喂进真 `UpdateViewModel`（门禁态）再读 `gateDownloadView` 必须是 `0.5f`；
  ③ 完整性 / 进度分母 / 空间前置闸都按 `Content-Length` 判（含"手写值差一字节仍算下完并报警"、"手写值大十倍不许把下载拒在门外"）；
  ④ 只有 `Network` 自动重试一次，且重试期间不 emit 中间那条失败；⑤ 目录建不出来时报得出成因、且不碰网络。
  **这个文件存在的理由本身要写在这里**：判据与平台接线揉在同一个 `flow {}` 里的时候，全仓没有一条测试构造过下载器，
  把 `onProgress = { emit(it) }` 改成空 lambda ⇒ 185 条全绿（评审 C3）。
- **`UpdateInstallPathTest`（app 侧，同轮新建）**：`install()` 的路径守卫 —— 越界 / 带 `..`（解析后越界）/
  前缀撞上的兄弟目录 / 目录自身 / 不存在的文件都判 `InstallResult.Failed` 且不抛；
  外加一条读真实 `file_paths.xml` 断言它覆盖 `UPDATE_DIR_NAME` 那一层（读真实文件的理由同下条）。
- **仓库自校验用例**：读真实 `update.json` 文件，断言必填字段齐全、`sha256` 若声明则格式正确、`apkUrl` 的 tag 段与 `versionName` 一致。这条专门拦"改了版本号忘了改清单"。
  实现位置是 **app 的 JVM 测试**而不是 commonTest —— commonTest 没有读文件的 API，且 iOS 模拟器沙箱里也没有仓库工作树；从测试工作目录向上找到含 `settings.gradle.kts` 的根目录再读。
  **两条被测的真实文件都要登记成测试任务的输入**（`app/build.gradle.kts` 的 `inputs.file(...)`）：
  `update.json` 与 `file_paths.xml` 都不是编译产物，不登记的话"只改这两个文件"的那次提交会让任务判 UP-TO-DATE、CI 里 FROM-CACHE，锁在最该响的时候安静。


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
| **生产 `update.json` 当前无 `sha256` 也无 `sizeBytes`**（§3.10） | **今天就是这样** | 完整性只能靠响应的 `Content-Length`；若某个 CDN 形态连这个头也不给，就只剩"非空即完整"这一条 ⇒ 回填发生在 **CI 出包之后那一次提交**（那之前哈希无从得知，"抬版本时一起回填"是做不到的一句话），README 发布手册第七节把它列成必做步骤 |
| `ApkDownloader.openBody`（Ktor 三跳接线那几行） | 无自动化覆盖 | 判定层全部挪进 `downloadEvents` 之后，剩下没锁的只有"GET/重定向/引擎"这几行；`ApkDownloadFlowTest` 用假 body 覆盖了它之上的每一条判据。真机回归第 4/6 项是唯一证据 |
| 容器接线（`AppContainer` 里 `installer = ApkDownloader(...)`） | 无自动化覆盖 | 写错成 `UnsupportedInstaller` 时 195 条全绿而真机永远下不下来 —— 台账里已记为"只能靠读 diff 与真机确认"的那一处 |
| 遮罩盖不住 `Dialog`/`Popup`（独立 window，§7.1 末条） | 已知并接受 | 拦人靠遮罩吃点击 + Activity 侧返回键，二者都不依赖"盖住 Dialog"；以后给更新流程加 Dialog 时要重新评估 |

---

## 11. 第二批（内容卡片）调研结论存档

不在本批实现范围，但结论已带证据，供下一批决策：

- **黄历：可做，且能进 commonMain 两端共用。** `cn.6tail:tyme4kt` 1.5.0（MIT，实测 `.module` 含 `iosArm64`/`iosSimulatorArm64` 变体，klib 466KB，运行期仅依赖 kotlin-stdlib）。宜忌表确实在包里 —— 实跑 2026-09-28 得 `农历丙午年八月十八`、宜 `[嫁娶,纳采,订盟,…]`、忌 `[安葬,纳畜,出行,…]`、神煞、节气、干支、生肖、星座。两个坑：调休表只编到 2026；宜忌由干支+建除+神煞推导，**与具体印刷通书可能对不上**，文案不得宣称权威。
- **`NateScarlet/holiday-cn`：真实可用（MIT、2180★、2026-09-27 仍在推），但解决的是长期维护，不是当前断层** —— 实测 `2027.json` 是 236 字节空壳（`papers: []`），`2025/2026.json` 各 1 条通知。国务院尚未发布 2027 安排。
- **星座运势：无任何可用免 key 源**（`api.oick.me`、`meow.la`、`v.api.hehesw.cn`、`api.fangliangapp.com`、`api.vvhan.com`、`api.60s.cc` 等全部 NXDOMAIN；`api.k780.com` 证书不可信；`api.dujin.org` 404；tianapi 要 key）。可选路径：一次性预生成静态数据集内置（无 key、无 cron）／每日 LLM 生成（要 key + 一条 cron）／砍掉。**待用户决策。**
- **历史上的今天：不建议做。** 百度百科月度 JSON 免鉴权可读（`.../cms/home/eventsOnHistory/09.json` → 200、340,486 字节、全年约 4.2MB），但**找不到允许再分发的授权**；Wikimedia 在本网络不可达；GitHub 上 8 个同类数据集**许可证全部为空**（含 97★ 的 `PrintNow/TodayInHistory`，其数据源是维基百科 = CC BY-SA，share-alike 传染）。无许可证 = 保留所有权利，与直接爬百度百科是同一档风险。被引用的 `timqian/history` 实测 **404 不存在**。
- **合规**：黄历/星座若上架应用商店会碰"封建迷信"红线，需免责声明 + 可能要从首页降到二级页。**当前是 Gitee 直发 APK、未上架**，故本批不为该规则牺牲首页体验；上架时再处理。
