# 密码库核心模块

这是不依赖 Android UI 的 Kotlin/JVM 核心。Android 调用方必须把创建、解锁、查询与保存放到后台线程，正式文件及整个所在目录放进 `noBackupFilesDir`。

## 固定依赖与格式

- `app.keemobile:kotpass:0.13.0`；MIT；官方[仓库](https://github.com/keemobile/kotpass/tree/0.13.0)与 [Maven Central](https://central.sonatype.com/artifact/app.keemobile/kotpass/0.13.0)。库含带原许可说明的 Bouncy Castle 派生实现，发布时须保留对应第三方许可。
- `com.squareup.okio:okio-jvm:3.15.0`：Kotpass 的公开模型含 `ByteString`，它的发布元数据把 Okio 列为 runtime，故本模块显式声明编译依赖。
- 创建 KDBX 4.1、AES-256-CBC、Argon2id v1.3：32 MiB、8 次遍历、并行度 2、32 字节随机盐。沿用[库默认成本](https://github.com/keemobile/kotpass/blob/0.13.0/kotpass/src/main/kotlin/app/keemobile/kotpass/database/header/KdfParameters.kt)，把 variant 选为库支持的 Argon2id；尚待 Android 设备性能收敛。
- 外层完整加密覆盖标题、账号、密码、URL、备注、ID、版本与历史；五个正文值都使用库的 protected field，避免 XML 文本处理改写换行。没有明文磁盘索引。

当前读取策略只接受上述固定格式与 KDF 配置，最大加密文件 32 MiB，拒绝超大头字段、重复头字段、非法块长度、截断及尾随字节。这是本项目自建库的边界，不承诺任意外部 KeePass 文件导入或跨工具往返兼容。

Kotpass 0.13.0 的 [`ContentBlocks`](https://github.com/keemobile/kotpass/blob/0.13.0/kotpass/src/main/kotlin/app/keemobile/kotpass/database/ContentBlocks.kt) 验证数据块 HMAC，但忽略最终空块的 HMAC。本模块补充[官方 KDBX 规范](https://keepass.info/help/kb/kdbx.html)的最终空块 HMAC 校验：使用库生成的变换密钥，按规范计算块密钥，以 JCA `HmacSHA256` 验证，不更改密码学格式。`LibraryKdfBridge.java` 复用库 JVM-public/Kotlin-internal 的 `BaseKdfProvider`；它是精确版本绑定，升级必须重新编译并通过末尾篡改及往返回归。

## 调用契约

`VaultRepository(File)` 提供 `exists()`、`create(CharArray)` 与 `unlock(CharArray)`；数组仍由调用方持有，调用返回后应尽快覆盖。主密码长度 8–128，五个输入上限依次为 100/256/1024/2048/10000，均按 Unicode code point 计数；空标题与空密码拒绝。未配对 UTF-16 surrogate 明确拒绝，其他原值不裁剪、不规范化、不截断。

`VaultSession` 提供 `listEntries(query)`、`getEntry(id)`、`saveEntry(EntryInput, id?, expectedVersion?)`、`close()`。搜索仅在会话内，按 NFC 和忽略大小写的派生值检索标题、账号与 URL host，不检索密码、备注或 URL 路径/查询；派生值不写回。重复名称/账号创建独立 ID；更新须提供基准版本，密码更新的原内容与最近 5 份历史在同一提交中保存。历史没有公开读取或恢复接口，本阶段 UI 不提供编辑。

`close()` 立即撤销之后的调用并释放库引用，不等待已有写入。已开始的写入可完成安全提交或回退；其迟到结果必须由 UI 授权代次丢弃。String 与库内部数据无法保证全部物理清零，锁定不等于运行时内存取证防护。

异常仅暴露 `VaultException.reason` 固定分类，不携带输入、搜索词、文件路径或底层异常消息。错误密码与一般损坏共用 `UNLOCK_FAILED`；不声称能可靠区分两者。数据对象 `toString()` 固定脱敏，调用方也不得记录字段。

## 保存、回退与限制

每次保存使用同目录 `.<正式文件名>-<UUID>.candidate` 与 `.previous`；两者都只含 KDBX 字节。候选写入并 `FileDescriptor.sync()`，完整解密和业务字段/版本/历史对照后，通过 `ATOMIC_MOVE` 替换，再读取实际正式文件复验后返回成功。旧库摘要与会话基准不一致则报 `CONFLICT`，不覆盖。生产调用方通过 `directorySync: (File) -> Unit` 提供平台目录持久化；候选与回退副本准备后、正式重命名后、回退重命名后均调用，任何真实同步失败都不能报成功。JVM 默认空实现不承诺目录重命名的断电持久性。

替换前失败保留旧正式文件；替换后失败使用本次加密回退副本写入 `.restore` 并原子还原，回退文件在目录同步成功前保留。若回退也失败，保留本次现场，不删候选或回退副本，不虚报成功。成功或明确回退才清理本操作临时文件。仅本进程按正式路径串行化，外部进程修改不受本模块同步锁保护。

本模块尚未包含崩溃恢复扫描、磁盘满的真实设备注入、跨进程文件锁、库迁移/主密码更换/主动备份。Android 目录 `fsync` 由 APP 适配器提供；候选同步与原子重命名不等于已证明断电持久性，仍需在 Android 上分别验证提交边界。系统备份、D2D、截图和剪贴板属于 Android 层验证。

## 验证入口

```powershell
./gradlew.bat :vault-core:test
```

测试通过公开 Repository/Session 接缝和无内容的 `CommitFault` 注入提交失败，全部输入均为合成资料；测试报告位于 `vault-core/build/reports/tests/test/`。正式安全审查、外部格式互操作与设备性能证据不能用 JVM 行为测试替代。
