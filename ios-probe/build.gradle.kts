/**
 * 探针：只回答一个问题 —— 简衣那套液态玻璃代码，搬到 commonMain 之后
 * 能不能为 iOS 编出来，以及 Haze 的 iOS 产物有没有暴露我们在用的那几个 API
 * （HazeStyle / HazeTint / hazeSource / hazeEffect / HazeInputScale / HazeEffectScope.mask）。
 *
 * 只有 iOS target，没有 Android target —— 不需要 AGP、不需要 compileSdk，
 * 也就不会把「升工具链」的风险引进来。
 *
 * 插件版本一律由 settings.gradle.kts 的 pluginManagement 提供，这里不写版本号：
 * plugins {} 块看不见脚本顶部的 val，写了会直接编译失败。
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
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
