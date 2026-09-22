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
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.compose.multiplatform)
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
            /**
             * 这里一律用 api() 而不是 implementation()：DrawableResource 这类类型
             * 出现在 shared 的公开签名里（Scenery.resId），app 要拿它去调
             * painterResource(...)。用 implementation 会把依赖藏起来，
             * app 那边就看不到 CMP 的 painterResource 重载。
             */
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            api(compose.material3)
            api(compose.components.resources)

            // 网络层。这里一律 implementation()：DTO 是 shared 的公开类型，
            // 但 Ktor 的 HttpClient 不出现在任何公开签名里，不该泄漏给 app。
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
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

/**
 * CMP 资源：图片放 src/commonMain/composeResources/drawable/，
 * 生成的 Res 类默认在 `<namespace>.generated.resources`。
 * 这里显式钉住包名，避免以后改 namespace 时 app 侧的 import 集体失效。
 */
compose.resources {
    publicResClass = true
    packageOfResClass = "com.jianyi.outfit.shared.res"
}
