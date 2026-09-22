pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WeatherOutfit"
include(":app")
// 纯 Kotlin 的领域层：引擎 + 数据模型。零 Android 依赖，所以能同时给 iOS 用。
// 包名与 app 里完全一致，迁移期间 app 侧一行 import 都不用改。
include(":shared")
