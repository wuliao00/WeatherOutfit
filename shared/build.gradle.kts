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
    // Room 2.7 的 KMP 支持靠 KSP 为每个 target 生成 `XxxDatabase_Impl` 与
    // `expect object XxxDatabaseConstructor` 的 actual，所以处理器在本模块启用。
    alias(libs.plugins.ksp)
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

    /**
     * 两个 iOS target 各配一份 framework（baseName = Swift 侧的 `import Shared`）。
     *
     * 不写成 `ios { }` 简写：那个 API 在 Kotlin 2.1 里已经是
     * "The ios() target shortcut is deprecated and no longer supported"，
     * 而且是**配置阶段**就抛错，本机连 Android 构建都会被一起拖死（实测过一次）。
     *
     * 选静态库（isStatic = true）是 KMP 官方向导的默认：链接期把 Kotlin 运行时并进去，
     * Xcode 侧少一个要 embed & sign 的动态库。怎么接工程见 README「跑 iOS」一节。
     *
     * iosX64（Intel 模拟器）故意不加：我们没有 Intel 机器可验，加进来只是没人跑过的目标。
     */
    iosArm64 {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    /**
     * Room 的 KMP 要求数据库构造入口写成 `expect object`（见 AppDatabase 里的注释），
     * 而 Kotlin 2.1 里 expect/actual 类整体还在 beta —— 不禁掉的话每次构建
     * 都为这 4 处（2 个 expect + 2 个 KSP 生成的 actual）各打一行警告，
     * 把真正需要看的警告埋掉。这里只静音这一条，不改任何代码语义。
     */
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

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
            api(compose.animation)
            api(compose.material3)
            api(compose.components.resources)
            // 扩展图标集（MoneyOff / WbSunny / BusinessCenter 等），组件与页面在用
            implementation(compose.materialIconsExtended)

            // 液态玻璃。HazeState 出现在 GlassHost 的公开构造参数里，必须 api()
            api(libs.haze.core)

            // Coil3：页面里的 AsyncImage（天气图标）。Android 引擎在 app 侧声明，
            // 这里只要 compose 组件本身（它有 iosarm64 产物）
            api(libs.coil3.compose)

            // 仓库接口的签名里有 Flow / suspend，coroutines 必须对 app 可见
            api(libs.kotlinx.coroutines.core)

            // 多平台 ViewModel：androidx 2.8 起 lifecycle-viewmodel 本身带 iOS 产物（包名不变）。
            // ViewModel 出现在公开构造签名里，必须 api()。
            // 导航参数由 app 侧工厂取出后以 String 传入，commonMain 不引入 SavedStateHandle
            api(libs.androidx.lifecycle.viewmodel)

            /**
             * Room 2.7 起本身是 KMP 库，所以实体/DAO/库定义都能留在 commonMain。
             * 用 api() 而不是 implementation()：app 的 AppContainer 里
             * `val database: AppDatabase` 的直接父类型就是这里的 RoomDatabase，
             * 藏起来 app 那边就编译不过。
             */
            api(libs.androidx.room.runtime)

            // 网络层。这里一律 implementation()：DTO 是 shared 的公开类型，
            // 但 Ktor 的 HttpClient 不出现在任何公开签名里，不该泄漏给 app。
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // 偏好落盘：Android 侧用 Preferences DataStore（iOS 侧是 NSUserDefaults，
            // 两边都只认 commonMain 的 PreferenceBackend 那一层）
            implementation(libs.androidx.datastore.preferences)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            // iOS 没有 Android 的 SQLite 框架，Room 在这边必须被显式喂一个驱动，
            // 而 native 的 Builder.build() 第一行就是 requireNotNull(driver)。
            // sqlite-bundled 带编译好的 sqlite3 二进制，版本跟 room-runtime 的
            // 传递依赖（androidx.sqlite 2.5.1）对齐，避免两份 sqlite。
            implementation(libs.androidx.sqlite.bundled)
            // 天气图标的网络加载。Coil3 不自带网络栈，不挂引擎就是静默空白
            // （Android 侧在 WeatherOutfitApp 挂的 OkHttp，这边对位挂 Ktor）
            implementation(libs.coil3.network.ktor3)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

/**
 * Room 注解处理器要挂到每个 target 的 ksp* 配置上。
 *
 * 名字不写死：KSP 给 commonMain 元数据编译建的那个配置在不同版本里改过名
 * （kspCommonMainMetadata / kspCommonMainKotlinMetadata），拼错的表现是
 * 「Android 编得过、iOS 编不出 Impl」这种只在 CI 才暴露的错。而且本机是 Windows，
 * iOS target 直接被禁用、那些配置压根不存在，只有 CI 的 mac 上才有 ——
 * 所以按前缀匹配现存的 ksp* 配置，两端各自挂自己那份。
 *
 * 排掉三类：裸 `ksp`（KMP 下已废弃，会打 deprecation 警告）、
 * `*ProcessorClasspath`（KSP 内部解析用，它已经 extends 了外面的配置，
 * 再塞一份等于把同一个处理器摆两遍）、`kspNdkLocation`（NDK 路径，不是处理器）。
 */
configurations.matching {
    val n = it.name
    n.startsWith("ksp") && n != "ksp" &&
        !n.endsWith("ProcessorClasspath") && !n.contains("NdkLocation")
}.all {
    project.dependencies.add(name, libs.androidx.room.compiler)
}

// schema 导出：从 app 搬到这里，目录结构不变（<库全限定名>/<版本>.json）。
// 三个 target 会各自导出一次，内容同源所以是同一份 JSON，覆盖写无害。
// 这份文件是「Room 升级没有改动表结构」的唯一凭据：identityHash 由 schema 算出，
// schema 逐字不变就等于老用户机器上的数据库仍然被认作同一版本。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
