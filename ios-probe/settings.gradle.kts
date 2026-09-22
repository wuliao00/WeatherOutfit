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
 * 版本从 gradle.properties 读，是为了让 CI 能用矩阵一次问出多个组合行不行
 * （见 .github/workflows/ios-probe.yml）。最关键的一个问题是：
 * **Compose Multiplatform 1.8.2 配 App 现在的 Kotlin 2.1.0 能不能编 iOS** ——
 * 如果能，KMP 迁移就不需要动 Android 的任何工具链；如果不能，才要评估升 Kotlin。
 * 这个问题在 Windows 本机问不了（Kotlin/Native 的 iOS target 要 macOS 宿主），
 * 所以交给 CI 回答。
 */
rootProject.name = "ios-probe"
