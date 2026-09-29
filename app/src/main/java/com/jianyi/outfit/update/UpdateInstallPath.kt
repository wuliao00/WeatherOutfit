package com.jianyi.outfit.update

import com.jianyi.outfit.data.InstallResult
import com.jianyi.outfit.data.update.UPDATE_DIR_NAME
import java.io.File

/**
 * 层间契约守卫：`install()` 只接受**下载器落盘位置之下**的文件。
 *
 * ## 为什么这道守卫放在这里，而不是放在下载器里
 * `DownloadEvent.Success(path)` → `AppUpdateGateway.install(String)` 是这条链上唯一一个
 * "上一个环节的输出了变成下一个环节的输入"的跨层接缝，而评审复核的结论是：过去**没有任何一处**
 * 校验过那个 path 真的在 `cacheDir/update/` 下。下载器那侧的清洗（`apkFileNameFor` 夹掉分隔符
 * 与连续的点）只是**策略**，它保证的是"我自己会写到哪儿"；一旦将来多出任何一个生产者
 * （缓存复用、别的目录、将来某个"已经下过了就别重下"的优化），守卫就只剩口头承诺。
 * 只有 gateway 知道 FileProvider 的 root 是哪一层，所以契约由它来定 ——
 * 这也正好是 FileProvider 自己拒发 URI 时唯一能给成因的地方。
 *
 * ## 不做的后果长什么样
 * 越界路径不会崩，而是走 `FileProvider.getUriForFile` 抛 `IllegalArgumentException`，
 * 被 `install()` 折成 `InstallResult.Failed` 之后，UI 文案是"写入失败，请清理手机存储后重试"。
 * 症状、日志、文案三者在说三件不同的事，而归因只能靠读代码。现在它直接被挡在最前面，
 * 并且**不抛**。
 */
internal fun precheckInstallPath(apkPath: String, cacheDir: File): InstallResult? {
    val root = File(cacheDir, UPDATE_DIR_NAME)
    val file = File(apkPath)
    // 越界 ⇒ Failed。这里不许退化成"交给 FileProvider 去拒"：那条路的异常会被折成
    // 一句与成因无关的文案，而这一层是唯一能把它说清楚的地方。
    if (!isUnderRoot(file, root)) return InstallResult.Failed
    // 用 isFile 而不是 exists()：目录也能 exists()==true，那种路径进 FileProvider 一样是异常。
    if (!file.isFile) return InstallResult.Failed
    return null
}

/**
 * `file` 解析（canonical）之后是不是落在 `root` 这一层之下。
 *
 * 必须比 canonical 路径：直接比字符串的话，`update/../secret.apk` 这种带着 `..` 的路径
 * 看着在 root 里，实际解析出来在 root 外面 —— 那正是这道守卫要挡的那一类。
 * 拼完 root 再补一个分隔符，是为了不放行 `update-evil/` 那种"前缀撞上了"的兄弟目录。
 */
internal fun isUnderRoot(file: File, root: File): Boolean = try {
    val rootPath = root.canonicalFile.path
    val prefix = if (rootPath.endsWith(File.separator)) rootPath else rootPath + File.separator
    file.canonicalFile.path.startsWith(prefix)
} catch (e: Exception) {
    // canonicalFile 会抛 IOException（路径里含非法字符时）。守卫的失败姿态是"拒"，不是"放"：
    // 一次解析失败就当越界处理，宁可装不上也不能把任意路径交给系统安装页。
    false
}
