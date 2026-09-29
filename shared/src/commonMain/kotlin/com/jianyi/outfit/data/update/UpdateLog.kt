package com.jianyi.outfit.data.update

/**
 * 清单故障的观测通道。
 *
 * 为什么非要有这条：`UpdateManifestParser.parse` 把"缺失/读不到/看不懂"统一收敛成 null，
 * 判定层再把它统一压成 Unreachable —— 于是**清单里一个拼错 = 永远没有人收到更新**，
 * 而测试全绿、UI 什么都不显示、logcat 安静。这条日志就是把"离线"和"清单坏了"分开。
 *
 * 只在"正文拿到了但解析失败"时用；离线是正常态，不该产日志，
 * 否则每次飞行模式启动都刷一条 warning，等于把信号埋进噪声里。
 */
internal expect fun logUpdateWarning(message: String)
