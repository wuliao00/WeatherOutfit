// 项目级构建脚本：统一声明插件版本，不在子模块中重复
//
// 新增插件必须写在这里（哪怕只有子模块用）。原因：Kotlin Gradle Plugin 是一个
// 整体制品，app 模块引了 kotlin.android 之后它就已经在构建脚本的 classpath 上；
// 这时 shared 模块再带版本号地引 kotlin.multiplatform，Gradle 会直接报
// "plugin is already on the classpath with an unknown version, compatibility
// cannot be checked" —— 版本只能有一个出处，就是这里。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
}
