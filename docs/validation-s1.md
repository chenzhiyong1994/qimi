# S1 实现与验证记录

日期：2026-10-01。对象：内部合成资料开发增量；完整首版验收仍以[验收场景](acceptance.md)为准。这里区分工程、行为测试、设备证据与待验证项，不作为真实密码使用或发布结论。

## 构建与设备

- Debug `0.1.0-dev`，`com.localpasswordmanager.app.dev`；Release 尚未正式签名。
- JDK 17.0.20、Gradle 8.14.5、AGP 8.11.1、Kotlin 2.2.20、compile / target SDK 36；原生[重建入口](development.md)。
- 独立 AVD `local_password_manager_api36` / `emulator-5580`，Google APIs x86_64 Android 16 / API 36；fingerprint `google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys`，补丁 2025-07-05，emulator 37.1.11.0。
- 使用现有 SDK / JDK 新建专用 AVD，没有启动或清理旧 AVD；测试仅重建 debug APP 自身 `noBackupFilesDir/vault` 合成数据。设备飞行模式值为 `1`。

S1 基线安装包 SHA-256（下列 7 项 UI、进程重开及合成扫描的测试对象；快捷解锁增量另列）：

| 对象 | SHA-256 |
| --- | --- |
| Debug | `5B380A2733CB67A0307CF5F45F6433CAFBCE70A70280367B8183BF146134F0B6` |
| Release unsigned | `F27117168F3BDA33FA891050E0DC329EC1A32DB8F8EA846C239DD90F2F5D0D1A` |

## 已完成证据

| 验证 | 实际结果 | 入口与范围 |
| --- | --- | --- |
| 密码库 JVM 行为 | 12 tests，0 failures / errors / skipped，39.040 秒 | `:vault-core:test`；[核心模块](../vault-core/README.md) |
| 主密码等待策略 | 2 tests，0 failures / errors / skipped | `:app:testDebugUnitTest`；单调时间、部分秒、失败成本上限和成功重置 |
| 工程与 lint | Debug、Release、androidTest APK 构建及 lint 通过 | `scripts/android.ps1`；制品校验开启，版本锁定 |
| 最终 Manifest 静态边界 | Debug / Release 均通过 | `scripts/check-package.ps1`；无 INTERNET、禁备份、两代规则、无导出 provider |
| Android 设备 UI | 7 tests，0 failures / errors / skipped，273.310 秒 | `S1UiTest`；专用 Android 16 AVD |
| 独立进程重开 | 建立夹具 1 项通过；旧进程退出确认；新进程只读验证 1 项通过 | `scripts/check-process-restart.ps1`；原值可读、文件字节不变 |
| 合成资料落盘与日志扫描 | 3 个文件、8 个标记、3 种编码，0 命中 | `scripts/check-synthetic-data.ps1`；目标 APP 私有目录与其 UID 日志 |
| 文档结构与引用 | 通过 | `scripts/check-docs.ps1`；不替代 APP 验证 |

核心行为覆盖五字段 Unicode / emoji / 组合字符 / CRLF / 空格原值往返；密码错及裁剪变体拒绝；重复建库不覆盖；明文标记扫描；名称/账号/host 派生搜索；8 类损坏（含终止块 HMAC、尾随、截断、负长度与超大 KDF 请求）；4 个提交故障点；目录同步失败及回退同步持续失败保全加密旧副本；版本冲突；字段上限/未配对 surrogate；正在写入时锁定立即撤销后续访问。

Android 层目录同步使用真实 `Os.open` / `Os.fsync`，候选保存后才返回成功。错误密码或损坏不销毁文件。主密码冷却时间保留在进程内，后台不清除；正在解锁时不重复排队。进程重启后的本机等待不抵抗离线猜测。

设备 UI 覆盖确认不一致不建库、字段原值保存与搜索、错误主密码、Activity 重建、后台迟到解锁不能重新授权、旧授权代次不能复制、敏感剪贴板与原值、同文及整份 ClipData 再复制后保留新内容、自有内容到期清理、Home 后跨期限返回清理、窗口安全标志、SavedState Bundle 无合成秘密，以及未保存表单继续编辑/明确丢弃且库文件不变。

首轮 UI 为 6/7 通过，1 项因测试先查存在、再读节点时页面切换产生竞态；改为每次读取一次节点快照后，最终 7/7 通过。断言、超时及场景没有削弱，没有跳过。原失败报告保留在 `output/verification/first-ui-run/`，最终 XML 在 `output/verification/ui-final.xml`。

独立进程检查先建立合成库，再强制停止 APP 并用 `pidof` 确认旧进程不存在，最后由新进程解锁读取；读取检查不写库，库文件摘要保持一致。私有目录与目标 APP UID 日志扫描检查 UTF-8 / UTF-16LE / UTF-16BE 合成标记，没有命中。扫描仅覆盖本次标记与读取到的文件，不替代正式密码学审查或所有外流路径验证。

可再生原生报告在 `vault-core/build/reports/tests/test/`、`app/build/reports/tests/testDebugUnitTest/` 与 `app/build/reports/androidTests/connected/debug/`；独立证据在 `output/verification/process-restart.txt`、`output/verification/synthetic-scan.txt`。lint 没有错误，仍报告固定依赖有新版本、图标 v26 目录冗余及缺少 monochrome 图标等警告；未通过抑制规则隐藏。

## 模拟器快捷解锁增量

日期：2026-10-01。Debug / 标准 Android Emulator 在已有库的解锁页新增“一键解锁测试库”，使用现有合成口令正常认证；不自动登录，不修改主密码，不保存用户口令，不重建或覆盖库。Release 对应 source set 为空实现。

Debug / Release / androidTest 构建、APP 2 项单元测试及两种构建的 lint 通过。首次增加 `lintRelease` 时发现 release unit test 配置未锁定既有 JUnit / Hamcrest，已只补充这两个配置的锁定范围，版本不变；随后按严格锁定方式检查。

在当前专用模拟器保留现库执行 `QuickUnlockProbe`：1 test 通过，9.842 秒；不输入主密码即可解锁，主动锁定后再次解锁，文件字节与测试前完全一致。原生记录在 `output/verification/quick-unlock.txt`。本轮没有重跑会重建夹具的 S1 套件；上方 7 项 UI、进程重开及扫描属于基线证据。

最终 Debug / Release Manifest 检查通过。逐个 DEX 检查合成口令、快捷按钮文案和 test tag：Debug 三项均存在，Release 三项均不存在。

快捷解锁增量安装包 SHA-256（后续 UI 打磨安装包另列）：

| 对象 | SHA-256 |
| --- | --- |
| Debug | `7AA348A022C332F495FAA43312A2F4C59A6E5F5DE59A04923B0DD7F91F60800F` |
| Release unsigned | `9906D3706F71FEC478579EFF7449334F17ECC450251CDDFDB8E4DC2E891385D5` |

## 视觉与交互打磨增量

日期：2026-10-01。沿用现有 Compose / Material 3，五个页面统一森林绿、暖白与系统深色、卡片、字体层级和本地图标；接入搜索清空、同一授权代次的列表位置、表单展开尺寸反馈、IME Done、复制按钮短暂成功状态、密码隐藏倒计时及忙碌 / 错误反馈。生产认证、存储、锁定和剪贴板判断未改。完整设计与当前覆盖分别见[体验规格](experience-spec.md)。

最终生产源通过 APP 2 项单元测试、Debug / Release / androidTest 构建及两种构建 lint；新增探针和失败诊断随后通过 androidTest 构建与 debug lint。严格依赖锁定及制品校验保持开启。Debug / Release 最终 Manifest 均无 INTERNET、禁备份并保留两代排除规则；逐个 DEX 检查确认快捷解锁的合成口令、文案和 test tag 在 Debug 存在、Release 缺失。

UI 打磨增量安装包 SHA-256（后续品牌增量另列）：

| 对象 | SHA-256 |
| --- | --- |
| Debug | `82F057929755276BCEAAD4D1620FBBD4AA1F33D68D4516730516A51C8BAD7A69` |
| Release unsigned | `A5DD269495B2E1E91522BB80A136824986F11DF9BB97F7CAC48A46319699A9D1` |

`UiPolishProbe` 在已有合成库的 `emulator-5580` 完成 2 项只读检查，0 failures / skipped，41.771 秒。它先核验库仅有一条完整已知 S1 夹具，五字段全部匹配；不建库、不保存、不删库，前后 KDBX 字节完全相同。覆盖普通界面、`font_scale=1.5` / 系统深色五页面与主要控件可达性、搜索清空、列表上下文、未保存继续编辑 / 丢弃及后台锁定；系统字体和夜间模式原值由 `finally` 恢复。

10 张 PNG 通过本应用 View 树软件绘制并实际查看，始终启用 `FLAG_SECURE`，仅导出上述完整已知夹具。图像在 `output/verification/ui-polish-images/ui-polish/`，原生结果在 `output/verification/ui-polish-probe.txt`。首轮 1 / 2 失败来自清空搜索断言把浮动标签也当作正文；已改为精确核对 `EditableText` 为空，同时保留前后结果数量、原场景与超时。原失败在 `ui-polish-first-probe.txt`。

完整 S1 回归使用新建独立 AVD `local_password_manager_ui_regression` / `emulator-5582`，只重建该设备 debug APP 自身的合成库；用户现有 `5580` 保留数据。首轮 6 / 7 通过，451.329 秒；`backgroundDuringUnlockDoesNotRestoreExpiredAuthorization` 在第二次解锁后等待列表超时，旧认证完成与旧授权撤销的断言已经通过。原样聚焦重跑再次超时，152.926 秒；追加只输出页面、忙碌 / 等待 / 错误布尔和合成输入相等结果的失败诊断后，两次聚焦重跑通过（36.592、136.106 秒）。生产源、认证门禁、原断言及 120 秒超时未改；键盘动画下触摸时序是候选原因，尚未确认，不把重跑通过称作根因修复。

第二次完整回归仍为 6 / 7，514.745 秒，同一处等待超时。初版诊断错误地捕获 `Exception`，未覆盖 Compose 1.9 直接继承 `Throwable` 的 `ComposeTimeoutException`；查验本地库后已精确捕获该类型。修正捕获后的聚焦 1 项通过（31.666 秒），相邻剪贴板 / 后台解锁 2 项通过（123.052 秒），仍未取得原超时的认证状态现场。

测试现已在解锁真实触摸前增加同步：按钮 enabled、完整可见，按钮几何和原生窗口 / IME 快照连续真实时间 250 ms 稳定且没有待布局；未完整可见则重新滚动。保留系统键盘与动画、物理 `performClick`、原场景、安全断言和认证 120 秒超时，没有语义点击替代或跳过。此改动补强输入同步，不宣称已经确认原失败的根因；补强后聚焦后台解锁 1 项通过，33.351 秒，完整 `S1UiTest` 最终 7 / 7 通过，0 failures / skipped，301.011 秒。最终原生结果在 `output/verification/ui-polish-s1-final.txt`。

原始证据保留在 `output/verification/ui-polish-s1-regression.txt`、`ui-polish-s1-diagnostic.txt`、`ui-polish-unlock-focused.txt`、`ui-polish-unlock-diagnostic.txt`、`ui-polish-unlock-diagnostic-repeat.txt`、`ui-polish-unlock-state.txt`、`ui-polish-unlock-pair-state.txt`、`ui-polish-unlock-touch-sync.txt`。新增测试源的构建与 lint 原生输出在 `ui-polish-touch-sync-build.txt`；生产 APK 摘要未变。

最终 Debug APK 已更新并打开于用户预览 AVD `local_password_manager_api36` / `5580`；安装前后原库 SHA-256 相同，未清数据或卸载。记录在 `output/verification/ui-polish-preview-install.txt`。完成测试后关闭本轮新建的 headless `5582` 进程，AVD 与合成夹具保留用于后续回归。

核心模块本轮未改，12 项行为、独立进程重开与合成扫描沿用 S1 基线证据，未重复执行。只读 UI 与静态包检查不证明完整首版或所有外流路径安全。

## 品牌名称与文案增量

日期：2026-10-01。名称与 slogan 采用[产品方案](product-plan.md)中的“栖密”。应用标题和建库 / 解锁主文案由本地字符串资源提供，口号在逗号处分行；Debug 安装名为“栖密 · 开发版”，Release 为“栖密”。工程标识、加密库路径、认证、存储和测试源未改。

最终 Debug / Release / androidTest 构建与两种构建 lint 通过；最终 Manifest 检查和 APK 实际 label 检查通过。记录在 `output/verification/brand-name-final-build.txt`、`brand-name-package-checks.txt`、`brand-name-package-metadata.json`。

最终版本的 `UiPolishProbe` 首轮为 1 / 2，29.046 秒：新增页输入后，返回按钮的可见性断言失败。保留原代码、断言、场景和超时原样重跑，2 / 2 通过，40.671 秒；键盘 / 滚动布局时序是候选原因，未确认根因，不把重跑通过称作修复。失败与重跑分别在 `brand-name-final-ui-probe.txt`、`brand-name-final-ui-repeat.txt`。口号分行前的一轮 2 / 2，35.084 秒，在 `brand-name-ui-probe.txt`。

只读探针核验完整已知 S1 合成夹具并保持前后库字节一致，字体和夜间模式已恢复。最终 10 张软件绘制图像在 `output/verification/brand-name-images/ui-polish/`，品牌页普通与大字体 / 深色画面已实际查看，始终保留 `FLAG_SECURE`。最终 Debug APK 已保留数据更新并打开于 `local_password_manager_api36` / `5580`；安装和检查后原库 SHA-256 与本轮开始相同，见 `brand-name-preview-install.txt`。本轮没有重跑会重建夹具的 S1 套件或核心测试，相关行为沿用上方基线证据。

品牌名称与文案增量安装包 SHA-256（后续 Logo 增量另列）：

| 对象 | SHA-256 |
| --- | --- |
| Debug | `BA1832797387A37037B59282492C61FB33A84E335FBE68D6F5607148F8095BC0` |
| Release unsigned | `FB214643A24DAE74D39E9937E24525123ED7C5FB897FAA2B9058B382E8BF3523` |

## Logo 设计与接入增量

日期：2026-10-01。主标记采用开口栖巢、单齿短钥匙与 q 形轮廓；森林绿 / 暖白 SVG 母稿、透明 PNG、应用图标与组合预览在[品牌资产](../assets/brand/README.md)。AUTH / LIST 顶部品牌标记与 adaptive launcher 共用两条本地矢量路径；Android 13+ 提供同轮廓 monochrome。操作图标、认证、存储、包名和测试源未改，APP 不加载生成探索素材。

Debug / Release / androidTest 构建及两种构建 lint 通过，最终 Debug / Release Manifest 检查通过。证据在 `output/verification/logo-build.txt`、`logo-package-checks.txt` 与 `logo-package-metadata.json`。矢量曲线控制点均位于 adaptive 前景中心半径 33dp 内；当前未实测全部厂商遮罩或 Launcher 主题图标开关。

在保留现有合成库的 `local_password_manager_api36` / `emulator-5580` 执行两项聚焦只读检查：`QuickUnlockProbe.oneTapUnlockAndLockAgainPreservesExistingVault` 和 `UiPolishProbe.largeFontAndDarkLayoutKeepControlsReachable`，2 / 2 通过，0 failures / skipped，25.635 秒。前后库字节一致，`font_scale=1.0` 与夜间模式 `no` 已恢复，最新 Debug APK 已保留数据更新并打开。原生证据在 `output/verification/logo-focused-probes.txt` 与 `logo-preview-install.txt`。

本次导出 5 张大字体 / 深色 View 树软件绘制图像，位于 `output/verification/logo-ui-images/`；解锁与列表两处品牌标记画面已实际查看，始终保留 `FLAG_SECURE`。此次没有重跑完整 S1、普通模式 UI 探针或核心测试，相关行为仍沿用上述基线；不把本次两项聚焦检查写成整套验收通过。

Logo 增量安装包 SHA-256：

| 对象 | SHA-256 |
| --- | --- |
| Debug | `FDBBEF678571E6C05430AD18CD2FE306E653AC6BD9CF0D5AD541B0D7A3E380C7` |
| Release unsigned | `82DA1BB4F4F7B807EBD6B735E0E23DBB91833712B210031686590863C52D32AB` |

## GitHub 开源准备增量

日期：2026-10-01。用户明确授权公开源码。建立独立 Git 仓库，根 README 提供品牌、当前能力、路线和重建入口；自有源码及成品品牌资产采用 Apache-2.0，第三方许可和归属见[许可索引](../THIRD_PARTY_NOTICES.md)。密码库、签名文件、机器配置、构建 / 验证输出及原始生成素材由 Git 忽略，保留在本机。

为明确公开合成夹具与字段选择语义，将 `credential` 的布尔参数改名 `includePassword`，S1 测试字段改为 `RAW_ENTRY_FIXTURE`，核心编辑 / Unicode / 长度边界输入提取为具名合成字段。原密码值、业务判断、断言、场景和超时均保留；生产差异仅参数及引用命名，存储源码未改。

最终核心 12 项测试通过，0 failures / errors / skipped，32.225 秒；APP 2 项测试通过，0 failures / errors / skipped。Debug / Release / androidTest 构建及两种 lint 通过，103 秒；首次命令行任务数组传递错误在测试前停止，纠正为 PowerShell 数组调用后完成全部检查。两次原生日志分别保留在 `output/verification/opensource-build-first-call.txt` 与 `opensource-build.txt`。本轮没有操作模拟器或重跑设备测试，设备行为沿用前述增量证据。

官方 Gradle 8.14.5 Wrapper JAR 校验通过，SHA-256 为 `7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172`。公开前须对精确提交完整审计并核对远端 OID；实际发布状态由[项目状态](project-status.md)维护。本次仅公开源码与成品资产，不提供正式签名安装版，不将源码开源视为完整首版验收通过。

## 验收映射与限制

| 场景 | 当前覆盖 | 未完成的完整场景 |
| --- | --- | --- |
| AC-01 | 无联网权限与 S1 离线设备环境 | 生成、草稿、恢复及 APP 身份网络活动全量核验 |
| AC-02 | S1 核心加密、篡改、提交候选与回退行为 | 草稿、历史管理、快照和导出后全范围扫描；正式密码学审查 |
| AC-05 / AC-07 | S1 建库、后台锁定、原值取用与复制等设备子项通过 | 生物识别回退、全部登录方式、填充及全部外部可见性 |
| AC-12 | Android 16 自有剪贴板清理、后台返回核验及新复制内容保留子项通过 | 杀进程后的剪贴板清理不作保证；各版本与输入法副本边界仍须覆盖 |
| AC-19 | JVM 提交阶段故障注入、目录 flush 失败回退 | Android 真实满盘、每个提交点断进程、物理断电、迁移/恢复/换密 |
| AC-20 | S1 标签、隐藏语义、可滚动页面及普通 / 1.5 倍字体 + 深色五页面可达性子项通过 | 全部字体 / 显示缩放档位、完整 TalkBack、1000 条规模及用户任务性能验收 |
| AC-03 / AC-04 | 备份排除配置已检查 | 云备份与 D2D / 厂商迁移实测；本轮 `bmgr enabled` 返回 disabled，不能据此称排除通过 |
| 后续增量场景 | 尚未实现的对应功能没有可点击伪入口 | AC-06/08/09/10/11/13–18/21 待执行 |

闲置 5 分钟、密码 10 秒隐藏、IME 禁个性化学习与 `FLAG_SECURE` 已实现，但真实锁屏、定时隐藏/闲置、屏幕录制与任务预览、完整字体缩放 / 读屏和不同 IME 的实际表现尚未完整执行。最小 API 29 与厂商真机未测，不列已验证支持；当前设备也没有完成云备份或真实 D2D。

没有主动备份恢复、草稿、编辑界面、生物识别或自动填充；不把当前构建称作完整 MVP。KDBX 读取仅支持项目固定参数，不承诺任意外部库导入；崩溃现场恢复扫描、跨设备互操作、物理断电与规模性能尚未验证。下一增量优先 S2 离线备份、验证与恢复。

早期增量尚未建立 Git；本次依据用户明确开源请求初始化独立仓库，远端发布结果见[项目状态](project-status.md)。文档比较基线保留在本机 `output/document-baseline/`、`output/logo-baseline/` 与 `output/opensource-baseline/`，其余生成证据在 `output/verification/` 与各模块 `build/`；这些不随源码公开，也不作为重建依赖。
