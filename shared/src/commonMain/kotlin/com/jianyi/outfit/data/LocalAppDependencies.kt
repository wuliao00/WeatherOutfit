package com.jianyi.outfit.data

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 把依赖容器交给页面用。
 *
 * 为什么用 CompositionLocal 而不是给每个 Screen 加参数：本项目的页面已经在用
 * `LocalSceneryController` / `LocalGlassHost` / `LocalScenery` 这套，
 * 再加一个显式参数会让每个页面的签名和每个调用点都变一遍，改动面大但没有收益。
 *
 * 页面拿它主要为了权限与高帧率这类「平台能力」——这些能力在 shared 侧是接口，
 * 所以页面本体可以进 commonMain，而实现仍然由 app 在根节点注入。
 */
val LocalAppDependencies = staticCompositionLocalOf<AppDependencies> {
    error("根节点未提供 LocalAppDependencies：请在 MainActivity 里 provides container")
}
