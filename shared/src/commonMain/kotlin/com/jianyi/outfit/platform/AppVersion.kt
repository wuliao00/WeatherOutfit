package com.jianyi.outfit.platform

import androidx.compose.runtime.Composable

/**
 * 平台点：当前安装包的版本名（Android 的 versionName / iOS 的 CFBundleShortVersionString）。
 *
 * 「关于」里原来写的是硬编码字面量，于是每次发版都必须记得改它，漏改就得到
 * 「包是 2.2.0、界面写着 2.0.0」这种只有用户能发现的矛盾。版本名本来就随包走，
 * 直接向系统要，从此不需要第二处维护。
 *
 * 取不到时返回 null 而不是编一个占位版本号：界面宁可显示「版本未知」，
 * 也不该显示一个看起来可信但可能错误的数字。
 */
@Composable
expect fun installedVersionName(): String?
