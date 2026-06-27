# 基于 Android Studio 个人"数字名片"生成器

本科课程大作业：移动软件开发

## 项目简介

基于 Android Studio 开发平台，使用 Kotlin 语言和 Jetpack Compose 声明式 UI 框架，采用 MVVM 架构设计的 Android 数字名片应用。支持名片信息编辑、多模板切换、头像选取、vCard 格式二维码生成、名片图片保存与分享。

## 技术栈

- **开发语言**：Kotlin
- **UI 框架**：Jetpack Compose
- **架构模式**：MVVM
- **数据持久化**：Jetpack DataStore
- **二维码生成**：ZXing
- **图片加载**：Coil

## App 架构

```
View 层 (Compose UI)
├── EditScreen.kt              编辑页面
├── PreviewScreen.kt           预览页面
├── CardTemplates.kt           三套模板
└── CardTemplateRenderer.kt    模板渲染器（图片导出）

ViewModel 层
└── CardViewModel.kt           状态管理 + 业务逻辑

Model 层
├── CardInfo.kt                数据模型 + vCard 生成
└── CardRepository.kt          DataStore 持久化

Utils
└── QrCodeGenerator.kt         ZXing 二维码
```

## 核心功能

- 名片信息编辑（姓名、职位、电话、邮箱等）
- 三套主题模板（简约白、科技蓝、学院灰）
- 相机拍摄/相册选取头像
- vCard 格式二维码（QUOTED-PRINTABLE 中文编码）
- 名片图片保存到相册+一键分享

## 项目文件

```
source/app/src/main/java/com/example/digitalcard/
├── MainActivity.kt
├── model/CardInfo.kt
├── data/CardRepository.kt
├── viewmodel/CardViewModel.kt
├── ui/screen/EditScreen.kt
├── ui/screen/PreviewScreen.kt
├── ui/template/CardTemplates.kt
├── ui/template/CardTemplateRenderer.kt
└── util/QrCodeGenerator.kt
```
