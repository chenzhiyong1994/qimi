# Android 开发与重建

当前工程交付路线中的 S1 建库与手动取用增量，品牌使用[产品方案](product-plan.md)中的“栖密”。只使用合成资料；备份恢复与完整安全验收完成前，不用于保管真实密码。实际证据以[验证记录](validation-s1.md)为准。

## 工程与版本

| 对象 | 固定配置 | 用途 |
| --- | --- | --- |
| Gradle Wrapper | 8.14.5，官方分发 SHA-256 | 独立重建入口，不依赖另一项目的 wrapper |
| JDK | 17 | Gradle、Kotlin/JVM 与 Android 编译 |
| Android Gradle Plugin | 8.11.1 | 支持编译 API 36 |
| Kotlin / Compose compiler plugin | 2.2.20 | 同版本插件 |
| Compose BOM | 2025.08.01 | 原生 Compose / Material 3 界面 |
| Android | minSdk 29、compileSdk / targetSdk 36 | Android 10+ 候选；实测支持范围另记 |
| 密码库核心 | Kotpass 0.13.0、Okio JVM 3.15.0 | KDBX；[模块契约与兼容边界](../vault-core/README.md) |

[AGP 官方兼容表](https://developer.android.com/build/releases/agp-8-11-0-release-notes)要求 Gradle 至少 8.13 / JDK 17；[Kotlin 官方兼容表](https://kotlinlang.org/docs/gradle-configure-project.html)列出 Kotlin 2.2.20 与 Gradle 8.14、AGP 8.11.1 的兼容范围。这里记录实际固定组合，不追随自动升级。

依赖版本由 Gradle 脚本及两个模块的 `gradle.lockfile` 固定，下载制品由 [verification-metadata.xml](../gradle/verification-metadata.xml)校验；Wrapper 自带官方分发校验值。Gradle 原生下载缓存可以共享，项目源码、生成目录与依赖声明独立；不复制或借用兄弟项目的可写构建环境。首次重建需要下载声明的工具与依赖，之后可以尝试离线构建；APP 本身没有联网功能。

## 构建与检查

先配置 JDK 17 的 `JAVA_HOME`，以及含 Android 36 platform / Build Tools 35.0.0 的 `ANDROID_HOME`。也可给脚本显式传 `-JavaHome`、`-AndroidSdk`；本机路径不写入共享源码。SDK 的 `local.properties` 属于本机忽略文件。

```powershell
pwsh -NoProfile -File scripts/android.ps1
pwsh -NoProfile -File scripts/check-package.ps1
pwsh -NoProfile -File scripts/check-docs.ps1
```

默认运行核心 JVM 测试、APP 单元测试、Android lint、debug / release 构建和 instrumentation 测试包编译。只运行一个任务时可传 `-Task :vault-core:test`；缓存齐备后加 `-Offline`。不要以更新 lockfile / 校验元数据来掩盖未知制品变化；有意更新依赖时核对官方来源、版本、兼容与回归证据。

- 开发 APK：`app/build/outputs/apk/debug/app-debug.apk`，安装标识 `com.localpasswordmanager.app.dev`。
- Release 构建输出 `app/build/outputs/apk/release/app-release-unsigned.apk`，尚无正式签名或分发配置。
- 设备测试报告在 `app/build/reports/androidTests/connected/debug/`；核心报告在 `vault-core/build/reports/tests/test/`；这些都是可再生输出。

## 模拟器快捷解锁

Debug 在标准 Android Emulator 的已有密码库解锁页提供“一键解锁测试库”，无需键盘输入。它只提交现有合成夹具口令，继续经过正常密码验证、失败等待与会话撤销；主动锁定或返回前台后可再次点击。自定义主密码仍须手动输入，按钮不会重建库、修改主密码或覆盖文件。

完整控件与合成口令只位于 `app/src/debug/`，`app/src/release/` 对应入口为空；Release 无按钮或固定测试口令。此入口不保存用户主密码，也不自动解锁。

`QuickUnlockProbe` 是可单独运行的只读设备探针，验证不输入密码即可解锁、锁定后再次解锁且现库字节不变。需已有使用合成夹具口令的专用 debug 库，缺少库会失败，不创建或删除数据。

先构建并用 `adb install -r` 安装 debug 与 androidTest 包，再单独执行；不要为此运行会重建夹具的进程重开脚本：

```powershell
adb -s emulator-5580 shell am instrument -w -r -e dedicatedSyntheticDevice true -e class com.localpasswordmanager.app.QuickUnlockProbe com.localpasswordmanager.app.dev.test/androidx.test.runner.AndroidJUnitRunner
```

## 专用设备测试

测试会重建合成库，仅允许专用模拟器、debug applicationId 和显式授权参数。不得把已有用户资料的模拟器或手机当作测试环境。选择设备后运行：

```powershell
pwsh -NoProfile -File scripts/android.ps1 -Task :app:assembleDebugAndroidTest
adb -s emulator-5582 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5582 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5582 shell am instrument -w -r -e dedicatedSyntheticDevice true -e class com.localpasswordmanager.app.S1UiTest com.localpasswordmanager.app.dev.test/androidx.test.runner.AndroidJUnitRunner
pwsh -NoProfile -File scripts/check-process-restart.ps1 -DeviceSerial emulator-5582 -DedicatedSyntheticDevice
pwsh -NoProfile -File scripts/check-synthetic-data.ps1 -DeviceSerial emulator-5582 -DedicatedSyntheticDevice
```

序列号仅是本轮示例，先用 `adb devices -l` 核对。本轮 UI 回归使用独立 AVD `local_password_manager_ui_regression`（`emulator-5582`）；用户现有 `emulator-5580` 保留数据，不运行重建夹具的套件。测试清理范围限目标 debug APP 的 `noBackupFilesDir/vault`，没有卸载 APP 或清理其他应用；新的专用 AVD 和旧 AVD 各自保留。

普通设备套件选择 `S1UiTest`。独立进程检查先建立合成夹具，再 `force-stop` 且确认旧进程退出，最后单独运行不写库的 `ProcessRestartProbe`；缺少夹具或读取失败会失败，不跳过。

多台设备连接时优先使用上述显式 `adb -s` 定向安装和运行，避免未经核验的 Gradle 设备筛选触及保留数据的模拟器。S1 解锁测试在物理触摸前核验按钮完整可见和键盘 / 窗口布局稳定，不关闭动画或以语义回调替代触摸；原认证 120 秒超时与安全断言保持。

合成扫描脚本将当前 debug APP 私有目录与其 UID 日志读取到本机 `output/verification/`，检查固定合成标记；要求 PowerShell 7.4+。不用于真实库或其他 APP，不证明密码学安全。

## UI 组件与只读检查

品牌矢量、透明 PNG 和导出方式见[品牌资产](../assets/brand/README.md)。Header 的 `brand_mark.xml` 与 Launcher 的 `icon_foreground.xml` 共享图形路径；API 33+ 的 adaptive icon 额外引用同一前景作为 `monochrome`。APP 仅加载本地矢量资源，不读取生成探索素材。

[VaultScreen.kt](../app/src/main/java/com/localpasswordmanager/app/VaultScreen.kt)承载建库 / 解锁、列表、新增、详情和帮助页面；[VaultTheme.kt](../app/src/main/java/com/localpasswordmanager/app/ui/VaultTheme.kt)统一森林绿、暖白、深色配色、系统字体和圆角；[VaultIcons.kt](../app/src/main/java/com/localpasswordmanager/app/ui/VaultIcons.kt)在本机绘制线框图标。沿用现有 Compose / Material 3，无新增依赖或网络素材。

搜索一键清空、授权代次内的列表滚动记忆、新增更多信息的尺寸动效、主密码 IME Done 提交、错误滚动顶部与复制成功约 2.5 秒的按钮反馈已接入。密码约 10 秒隐藏、复制原值、读写核验和后台锁定继续使用原有判断；动效不保留锁定前页面或秘密。完整体验预期与当前覆盖分别见[体验规格](experience-spec.md)。

[UiPolishProbe.kt](../app/src/androidTest/java/com/localpasswordmanager/app/UiPolishProbe.kt)使用 `resetVault=false`，不建库、不保存、不删库。它要求现库仅有一条既定 S1 记录，名称、账号、密码、网址、备注五字段全部匹配，且检查前后 KDBX 字节相同；缺少夹具或出现未知资料即失败。先安装 debug 与 androidTest 包，在已有完整合成夹具的独立模拟器上定向执行：

```powershell
adb -s emulator-5582 shell am instrument -w -r -e dedicatedSyntheticDevice true -e class com.localpasswordmanager.app.UiPolishProbe com.localpasswordmanager.app.dev.test/androidx.test.runner.AndroidJUnitRunner
```

本轮 2 / 2 通过，分别检查普通界面与 `font_scale=1.5` / 系统深色；字体与夜间模式原值在 `finally` 恢复。10 张 PNG 由本应用 View 树软件绘制，仅允许已知合成资料，始终保留 `FLAG_SECURE`，不使用系统屏幕截图接口。设备输出为 APP 的 `cache/ui-polish/`，本轮本机证据在 `output/verification/ui-polish-images/ui-polish/`。此探针验证 UI 可达性与只读流程，不代表完整 S1 回归或安全验收全部通过。

## S1 的实现边界

已接入创建主密码确认、新增账号密码记录、搜索、详情显示与复制。此增量没有编辑、非密码登录方式、草稿、标签、待整理、恢复、生物识别或自动填充入口。表单未保存时返回须明确丢弃；后台或锁定丢弃未保存输入，界面不承诺恢复。后续仍按[迭代路线](iteration-roadmap.md)推进。

主密码与字段按 Unicode code point 计数：主密码 16–128；名称 100、账号 256、密码 1024、网址 2048、备注 10000。账号、密码与主密码原值保留；未配对 UTF-16 surrogate 拒绝，不替换。搜索仅用名称、账号与网址 host 的 NFC / 大小写派生值，不覆盖密码、备注或 URL path / query，不写磁盘索引。

Android 层把正式库和候选/回退文件放在 `noBackupFilesDir/vault`，配置 `allowBackup=false`、旧版与 cloud / device-transfer 排除规则；目录 `fsync` 适配由 [AndroidDurability.kt](../app/src/main/java/com/localpasswordmanager/app/AndroidDurability.kt)实现。系统云备份、D2D 和厂商迁移必须另行实测，当前不能宣称全部外流路径验证通过。

会话后台、锁屏、主动锁定和前台约 5 分钟闲置撤销。UI 授权代次先失效，正在执行的写入完成提交或回退；迟到结果不能重新显示内容。全部窗口 `FLAG_SECURE`；Activity 不保存页面 Bundle，输入禁用自动纠错并向 IME 请求禁止个性化学习，排除系统自动填充接收。无法保证受控系统、恶意输入法或所有运行时内存副本不泄露。

密码显示约 10 秒后隐藏；复制标记敏感，约 30 秒或锁定时核对本次 UUID 与系统剪贴板时间戳后清理。无法读取时保留核验信息，返回前台且获得焦点后重试；进程结束、后台限制及接收方/输入法副本不能保证清除。反馈不包含原值。
