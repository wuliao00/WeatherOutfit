import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.serialization)
    // KSP 不再在 app 启用：唯一的注解处理器是 Room，它随持久层一起搬进了 shared
}

// 读取 local.properties 中的天气 API 凭证，避免硬编码进版本库
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.jianyi.outfit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jianyi.outfit"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "2.1.2"

        // 通过 BuildConfig 注入接口凭证，代码中统一使用 BuildConfig.WEATHER_API_ID / KEY
        buildConfigField(
            "String", "WEATHER_API_ID",
            "\"${localProps.getProperty("WEATHER_API_ID", "88888888")}\""
        )
        buildConfigField(
            "String", "WEATHER_API_KEY",
            "\"${localProps.getProperty("WEATHER_API_KEY", "88888888")}\""
        )

        vectorDrawables { useSupportLibrary = true }
    }

    // release 签名走环境变量：密钥只存在于 CI secrets 里，仓库不落任何凭据文件。
    // 没有配齐时保持未签名，让 assembleRelease 依然可用来验证 R8 与资源收缩。
    val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH").orEmpty()
    if (releaseKeystorePath.isNotBlank() && file(releaseKeystorePath).exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                // keystore 密码与 key 密码必须一致，否则 PKCS12 会报 BadPadding
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: storePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// （KSP / Room schema 已随持久层一起搬到 shared，app 不再跑注解处理器）

dependencies {
    // 领域层（引擎 + 数据模型）。包名与 app 内一致，所以这一步不改任何 import。
    implementation(project(":shared"))

    // AndroidX 基础
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose：通过 BOM 统一版本
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)

    // 液态玻璃：真实背景模糊 + 官方材质预设
    implementation(libs.haze.core)
    implementation(libs.haze.materials)

    // 启动屏
    implementation(libs.androidx.core.splashscreen)

    // 导航
    implementation(libs.androidx.navigation.compose)

    // Room 本地数据库：库定义与开库函数已整体搬到 shared（Room 2.7 起是 KMP 库），
    // app 只通过 buildAppDatabase(context) 调用，room 依赖随代码一起从这页删掉了。

    // DataStore 已随设置仓库实现搬到 shared 的 androidMain：
    // app 只 new 一个 DataStorePreferenceBackend(context)，它的签名里没有 datastore 类型，
    // 编译期不需要、运行期由 shared 的 AAR 带进来。

    // 后台任务：每日穿搭推送（WorkManager 持久化调度）
    implementation(libs.androidx.work.runtime.ktx)

    // JSON：模板清单序列化（天气网络层在 shared 模块用同一套 kotlinx.serialization）
    implementation(libs.kotlinx.serialization.json)

    // 测试
    testImplementation(libs.junit)
    // 对拍测试的参照实现：证明 kotlinx.serialization 与旧版 Gson 行为一致（主代码已无 Gson）
    testImplementation(libs.gson)

    // 图片加载
    // 图片加载：Coil3 多平台版 + Android 的 OkHttp 引擎
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)

    // 定位
    implementation(libs.play.services.location)

    debugImplementation(libs.androidx.ui.tooling)
}
