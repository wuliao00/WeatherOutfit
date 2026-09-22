pluginManagement {
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
 * 原因：Compose Multiplatform 1.12 对应的是 Jetpack Compose 1.12，而线上 App 还在
 * Compose 1.8.3 / compileSdk 35。真做 KMP 迁移时整套工具链（Kotlin / AGP / compileSdk）
 * 都要一起升，那是有风险的改动；探针不能把这个风险带进 App 的构建里，
 * 所以这里用一套自己的版本，独立解析、独立编译。
 */
rootProject.name = "ios-probe"
