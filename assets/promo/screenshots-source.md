# 1.0.0 真实界面截图来源

本批素材由 [WebsiteScreenshotProbe.kt](../../app/src/androidTest/java/com/localpasswordmanager/app/WebsiteScreenshotProbe.kt) 在专用 Android 模拟器上打开真实 App 页面并导出，日期为 2026-10-14。拍摄构建使用 `debug` 安装标识；`app/src/main/`、`app/src/debug/`、`app/src/release/` 与 `v1.0.0` 没有源码差异。截图中的列表、详情、编辑表单和密码生成器均为 1.0.0 已实现界面。

导出只使用本应用 `View.draw(Canvas)` 软件绘制。生成器按现场位置绘制 Activity 与 Dialog 的窗口栈；没有后期裁剪、缩放、拼造控件或替换文字。Activity 与 Dialog 的 `FLAG_SECURE` 始终保留，不使用系统截图接口。截图不展示任何密码原文，生成候选也保持隐藏。

## 合成资料与数据边界

三个账号完全虚构，邮箱与网址使用保留的 `example.com` 示例域名，没有对应的真实服务账户：

| 名称 | 示例账号 | 示例网址 | 备注 |
| --- | --- | --- | --- |
| 示例邮箱 | `hello@example.com` | `https://mail.example.com` | 日常通信，轻松查找。 |
| 灵感笔记 | `ideas@example.com` | `https://notes.example.com` | 留住每一个好想法。 |
| 工作空间 | `work@example.com` | `https://work.example.com` | 专注工作，从容开始。 |

主密码和条目密码为测试源码内的公开合成夹具，只用于这台专用模拟器。探针使用 `resetVault=false`：仅当密码库不存在且其目录为空时，经过正常建库和新增页面初始化；已有库必须恰好包含这三个完整五字段夹具，否则拒绝导出。不会删除、清空或替换已有密码库。初始化后的截图操作不保存编辑或候选，结束前核对 KDBX 字节完全相同。

## 复现入口

先按 [开发文档](../../docs/development.md) 配置已有 JDK 17、Android SDK 36。先核实目标 AVD 身份，只在专用合成模拟器运行；不要调用默认会重建夹具的设备套件。示例端口 `5584` 必须与实际设备一致。

```powershell
& ./scripts/android.ps1 -JavaHome $env:JAVA_HOME -AndroidSdk $env:ANDROID_HOME `
  -Task @(':app:assembleDebug', ':app:assembleDebugAndroidTest') -Offline
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
& $adb -s emulator-5584 emu avd name
& $adb -s emulator-5584 install -r app/build/outputs/apk/debug/app-debug.apk
& $adb -s emulator-5584 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb -s emulator-5584 shell am instrument -w -r `
  -e dedicatedSyntheticDevice true -e websiteScreenshotFixture 20261014 `
  -e class com.localpasswordmanager.app.WebsiteScreenshotProbe `
  com.localpasswordmanager.app.dev.test/androidx.test.runner.AndroidJUnitRunner
```

设备输出位于 debug App 的 `cache/website-refresh-20261014/`，可用 `adb exec-out run-as com.localpasswordmanager.app.dev cat <缓存文件>` 二进制导出。PowerShell 需 7.4+ 才能通过原生命令的 `>` 保持 PNG 字节。不要用文字编码接口保存二进制文件。

四个输出名分别为 `qimi-list.png`、`qimi-detail.png`、`qimi-edit.png`、`qimi-generator.png`。生成器画面启用高级自定义、密码长度 20 位，四种字符类型均选择；上方预设复杂度禁用，候选隐藏。

本批使用新建专用 AVD `qimi_website_refresh_20261014`（Android 36，`emulator-5584`），1080×2400 原始像素、320 dpi（540×1200 dp），字体 1.0、系统浅色。显示密度属于模拟器屏幕配置，没有对导出的 PNG 重采样。复制相同画面前应记录设备原密度，并在专用模拟器通过 `adb -s emulator-5584 shell wm density 320` 选择相同布局；结束后恢复原密度。本次原物理密度 420 dpi，之前没有覆盖值，结束使用 `wm density reset` 恢复。

探针在 `finally` 恢复此前字体与夜间模式，并等待 Activity 配置恢复。首次运行在首个表单控件等待超时，尚未创建库；随后发现新设备的系统级 “System UI isn't responding” 弹窗。关闭该系统弹窗、确认真实建库页面恢复后，第二次运行通过：1 / 1，95.12 秒。四张 PNG 已逐张查看，高级面板的完整规则、四种字符选项和底部操作均可见，所有密码遮罩保留。

构建与设备日志、截图哈希及设置恢复结果保存在本机 `output/verification/website-refresh-20261014/`。仅截图不能代表完整安全验收；发行范围仍以 [1.0.0 说明](../../docs/releases/1.0.0.md) 为准。
