pluginManagement {
    /**
     * 版本必须在这里给，不能写在 build.gradle.kts 的 plugins {} 里。
     *
     * 踩过一次：plugins {} 块在一个独立的脚本接收器里求值，看不见脚本顶部声明的
     * `val kotlinVersion`，结果三档矩阵全部以
     * "Unresolved reference: kotlinVersion" 编译失败 —— 连依赖都还没开始解析。
     * 更坑的是连"已知能过"的那一档也一起红了，看起来像是版本问题，其实探针本身坏了。
     */
    val kotlinVersion = providers.gradleProperty("kotlinVersion").orNull ?: "2.1.0"
    val composeVersion = providers.gradleProperty("composeVersion").orNull ?: "1.8.2"

    plugins {
        id("org.jetbrains.kotlin.multiplatform") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.compose") version kotlinVersion
        id("org.jetbrains.compose") version composeVersion
    }

    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

/**
 * 独立的 Gradle 构建，故意不挂到仓库根 settings.gradle.kts 上。
 *
 * 版本从 -P 读，是为了让 CI 用矩阵一次问出多个组合行不行
 * （见 .github/workflows/ios-probe.yml）。最关键的一档是 App 现在的组合
 * Kotlin 2.1.0 + CMP 1.8.2：它过了，KMP 迁移就不需要动 Android 的任何工具链。
 * 这个问题在 Windows 本机问不了（Kotlin/Native 的 iOS target 要 macOS 宿主）。
 */
rootProject.name = "ios-probe"
