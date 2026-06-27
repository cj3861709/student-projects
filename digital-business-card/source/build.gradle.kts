// 顶层构建文件，配置项目全局插件（Android应用/Kotlin/Compose），子模块共享配置
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}