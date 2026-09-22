import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * 领域层模块：穿搭推荐引擎、生活指数引擎、全部数据模型。
 *
 * 这一层本来就零 Android 依赖（Models.kt 连 import 都没有，引擎只 import
 * 同包的模型和 kotlin.math），所以它是 KMP 改造里最安全的第一块 —— 搬过来
 * 不需要 expect/actual，也不需要引任何平台库。
 *
 * **包名保持 com.jianyi.outfit.*，与 app 里完全一致**：这样 app 侧一行 import
 * 都不用改，这一步对线上 Android 产物是真正零行为变化，出问题也只可能是构建配置，
 * 不可能是逻辑。
 *
 * 没有引 Compose Multiplatform —— 这一层没有 UI，先只上 Kotlin Multiplatform，
 * 少一个版本配套变量。UI 层的迁移是后面单独一步。
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    /**
     * jvmTarget 必须显式钉在 17，与 android.compileOptions 和 app 模块一致。
     * KMP 不像 kotlin-android 那样从 kotlinOptions 继承，默认会跟着运行构建的 JDK 走；
     * 本机 JDK 是 21，于是报 "Inconsistent JVM-target compatibility detected
     * for tasks compileDebugJavaWithJavac (17) and compileDebugKotlinAndroid (21)"。
     */
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // 真机 + Apple Silicon 模拟器。iosX64（Intel 模拟器）故意不加：
    // 我们没有 Intel 机器可验，加进来只是一个没人跑过的目标。
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // 空。commonMain 必须保持零依赖，这是这个模块存在的理由：
            // 一旦有人往这里加一个 Android-only 的库，iOS 就编不动了。
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.jianyi.outfit.shared"
    compileSdk = 35

    defaultConfig {
        // 与 app 一致；shared 本身不依赖任何高版本 API，这个下限只由 app 决定
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
