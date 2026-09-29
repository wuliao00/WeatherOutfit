import java.util.Properties
import org.gradle.api.tasks.testing.AbstractTestTask

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

/**
 * 仅 debug 生效的**清单地址覆盖**：`./gradlew :app:assembleDebug -PupdateManifestUrl=<url>`。
 *
 * ## 为什么需要这条路
 * `update.json` 的 `minSupportedVersionCode` 保持 1（用户确认没有存量用户），于是生产数据上
 * **硬门禁永远不会出现** —— 门禁那套 UI（全屏遮罩、返回键不放行、进度条、权限引导）
 * 到合并时等于从未运行过。而它恰恰是这批改动里唯一"错了就把用户锁死"的一层。
 *
 * ## 为什么不干脆把 min 抬到 99 来"验收"
 * 抬 min 会真的拦住每一个旧版设备，而此刻 v2.2.0 的附件还不存在（HEAD 探包失败 ⇒
 * 判定层降级成"可跳过提示"）：既看不到门禁，又把一次本机演练变成线上事故。
 * 所以正确形状是**把清单地址换成一份专门用来触发门禁的测试清单**（仓库根 `update.test.json`），
 * 而不是改动那份会被真设备读到的清单。
 *
 * ## 为什么 release 侧必须是恒空串
 * 覆盖只可能来自命令行属性，而发布包由 CI 从 tag 构建、不传这个属性；这里写死 `"\"\""`
 * 是让"发布包里读不到覆盖"成为**代码形状**而不是约定。AppContainer 那边空即回退常量，
 * 所以这条缝只有"有覆盖 / 没覆盖"两种状态，没有第三种"忘了清"。
 *
 * 先例就在本项目里：设置页允许用户自填天气接口地址，留空回退内置默认。
 */
val updateManifestUrlOverride = providers.gradleProperty("updateManifestUrl").orNull?.trim().orEmpty()

// BuildConfig 的 String 字段要的是**带引号的源码字面量**，所以先把 URL 里的反斜杠与引号转义掉。
// 不转义的话，含引号的属性值会生成一份语法错的 BuildConfig —— 报错点离原因很远。
val updateManifestUrlOverrideLiteral =
    "\"" + updateManifestUrlOverride.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.jianyi.outfit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jianyi.outfit"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "2.2.0"
        // 抬这两个值必须同时改仓库根 `update.json` 的 versionCode / versionName 与 apkUrl 的
        // tag 段（三处一起改的理由见 README 发布手册第一节）。只改本文件不会有任何编译错，
        // 表现是"所有人永远收不到更新"，唯一能把它变红的地方是 UpdateManifestFileTest。

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
        // 清单地址覆盖只给 debug。两个 buildType 都要声明这个字段：AppContainer 在 src/main，
        // 两端都要编得过，少一处就是 Unresolved reference。
        getByName("debug") {
            buildConfigField("String", "UPDATE_MANIFEST_URL_OVERRIDE", updateManifestUrlOverrideLiteral)
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
            // 恒空串：发布包永远读生产清单，即使有人在本机带属性跑过一次 assembleRelease，
            // 传进去的值也不会进包（覆盖这条路只能出现在 debug 的 APK 里）。
            buildConfigField("String", "UPDATE_MANIFEST_URL_OVERRIDE", "\"\"")
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

// update.json 是仓库根的一份手写文件，被 app/src/test 的 UpdateManifestFileTest 直接读，
// 但它**不是**这条测试任务的声明输入 —— 于是"只改清单、不改代码"的那次提交里，
// 本地任务判 UP-TO-DATE、CI 里更糟是直接 FROM-CACHE，那把"T11 抬版本却忘了改清单"的锁
// 会在最该响的时候安静下来。显式登记成输入后，文件内容变化会进任务的输入快照
// （Gradle 按内容哈希而非 mtime 判），既让本地重跑，也让 CI 的缓存 key 跟着变。
tasks.withType<AbstractTestTask>().configureEach {
    if (name == "testDebugUnitTest") {
        inputs.file(rootProject.file("update.json"))
        // 门禁演练用的那份同理：UpdateRehearsalManifestTest 读它，而它也是手写的。
        // 不登记的话"只改这份测试清单"的那次提交里任务会判 UP-TO-DATE，
        // 而"演练清单其实已经不会触发门禁"这件事只有这一条用例会红。
        inputs.file(rootProject.file("update.test.json"))
        // 同一个理由：UpdateInstallPathTest 读这份 xml 来比对 FileProvider 的 root 与
        // shared 的 UPDATE_DIR_NAME。它是源码目录下的资源文件，不是这条任务的声明输入，
        // 只改这一行 xml 时任务会判 UP-TO-DATE —— 而"配置与代码漂移"正是这条用例要抓的。
        inputs.file(project.file("src/main/res/xml/file_paths.xml"))
    }
}

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
