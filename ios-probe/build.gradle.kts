/**
 * 探针：只回答一个问题 —— 简衣那套液态玻璃代码，搬到 commonMain 之后
 * 能不能为 iOS 编出来，以及 Haze 的 iOS 产物有没有暴露我们在用的那几个 API
 * （HazeStyle / HazeTint / hazeSource / hazeEffect / HazeInputScale / HazeEffectScope.mask）。
 *
 * 只有 iOS target，没有 Android target —— 这样不需要 AGP、不需要 compileSdk，
 * 也就不会把「升工具链」的风险引进来。
 *
 * kotlin / compose 两个版本可由 CI 用 -P 覆盖，用来跑版本组合矩阵；
 * 本地不带参数时用最保守的那一档。
 */
val kotlinVersion: String = (properties["kotlinVersion"] as String?) ?: "2.1.0"
val composeVersion: String = (properties["composeVersion"] as String?) ?: "1.8.2"

plugins {
    kotlin("multiplatform") version kotlinVersion
    id("org.jetbrains.compose") version composeVersion
    id("org.jetbrains.kotlin.plugin.compose") version kotlinVersion
}

kotlin {
    // 真机 + Apple Silicon 模拟器，两个都编，避免只在模拟器上能过
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            // 与 App 当前锁定的版本一致：探针要验的是「这个版本在 iOS 上行不行」，
            // 不是「新版本能不能用」，换版本就失去意义了
            implementation("dev.chrisbanes.haze:haze:1.6.10")
            implementation("dev.chrisbanes.haze:haze-materials:1.6.10")
        }
    }
}
