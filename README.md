# GPS Speed

一个面向 Android 12+ 的 GPS 实时测速 App，适合把手机固定在车把支架上，像数字车速表一样查看当前速度。

## 项目简介

本项目聚焦“骑行时一眼看清速度”，提供大数字测速主界面、全屏测速、悬浮窗测速、骑行记录入库，以及历史折线统计。

当前项目为纯 Android 本地应用，不依赖后端服务。

## 当前功能

- GPS 实时测速
- 稳定 / 实时 两种测速模式
- 主界面大数字速度展示
- 全屏测速模式
- 悬浮窗测速
- 测速中与悬浮窗显示时常亮屏幕
- 本地数据库保存骑行记录
- 历史记录列表
- 每次骑行的历史速度折线图
- 当前骑行中的实时趋势折线图

## 当前实现说明

- 当前骑行折线图显示在主页统计区下方
- 当前骑行折线图按 `10s` 粒度刷新一次
- 历史记录与速度采样存储在本地 `Room` 数据库
- 历史记录中的每条骑行都会展示一张速度趋势折线图

## 技术栈

- Kotlin
- Jetpack Compose
- Android ViewModel
- Kotlin Coroutines / Flow
- Google Play Services Location
- Room
- KSP

## 目录结构

```text
app/src/main/java/com/mk/gpsspeed/
├── data/                 数据模型、Repository、Room 数据库
├── location/             GPS 定位封装
├── service/              前台测速服务、悬浮窗服务
├── ui/                   ViewModel、Compose 页面与主题
├── FullscreenSpeedActivity.kt
└── MainActivity.kt
```

## 环境要求

- macOS / Linux / Windows
- Android Studio 最新稳定版
- JDK 17
- Android SDK 36
- Android 12 及以上设备或模拟器

## 本地运行

### 1. 克隆项目

```bash
git clone git@github.com:tmax-top/gps-speed-android.git
cd gps-speed-android
```

### 2. 编译 Debug 包

```bash
./gradlew assembleDebug
```

### 3. 安装到设备

先确保设备已连接：

```bash
adb devices
```

然后执行安装：

```bash
./gradlew installDebug
```

## 权限说明

App 运行依赖以下权限：

- 精确定位权限
- 通知权限
- 悬浮窗权限
- 前台服务相关能力

如果要正常体验测速、悬浮窗和持续记录，请在设备上完成授权。

## 主要页面

- 主测速页：展示当前速度、最高速度、时长、里程和当前骑行趋势
- 全屏测速页：适合骑行时远距离查看
- 历史记录面板：查看每次骑行记录与速度折线图
- 设置面板：切换测速模式、管理悬浮窗

## 数据存储

当前使用本地数据库保存以下数据：

- 骑行开始时间
- 骑行时长
- 总里程
- 最高速度
- 平均速度
- 速度采样点

其中速度采样用于绘制：

- 当前骑行趋势图
- 历史记录趋势图

## 开发说明

- 业务核心在 `TrackingRepository`
- 实时定位由 `RideTrackingService` 持续采集
- 悬浮窗由 `SpeedOverlayService` 管理
- 主页面 UI 位于 `HomeRoute`

## 相关文档

- [PRD.md](./PRD.md)
- [UI_Design.md](./UI_Design.md)

## 后续计划

- 历史详情页增强
- 更完整的轨迹展示
- 导出与分享能力
- 更多悬浮窗交互能力
