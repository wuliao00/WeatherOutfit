import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
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
        versionCode = 5
        versionName = "2.0.0"

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

dependencies {
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

    // Room 本地数据库
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore 轻量配置
    implementation(libs.androidx.datastore.preferences)

    // 网络
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)

    // 图片加载
    implementation(libs.coil.compose)

    // 定位
    implementation(libs.play.services.location)

    // 测试
    testImplementation(libs.junit)

    debugImplementation(libs.androidx.ui.tooling)
}
