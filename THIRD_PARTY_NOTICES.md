# 第三方许可与归属

栖密自有源码与仓库中成品品牌资产采用 [Apache-2.0](LICENSE)。下列组件保留原作者版权与各自许可；本文件不改变第三方授权。依赖的实际版本由 Gradle 锁文件维护。

## 密码库与内嵌组件

| 组件 | 当前版本 / 来源 | 许可与归属 |
| --- | --- | --- |
| Kotpass | 0.13.0 | MIT，Copyright (c) 2021 Denis T.；[完整许可](licenses/Kotpass-MIT.txt)、[固定版本来源](https://github.com/keemobile/kotpass/tree/0.13.0) |
| Bouncy Castle 衍生加密实现 | Kotpass 内嵌，包括 Argon2、Blake2b、ChaCha / Salsa、Twofish 等 | Copyright (c) 2000-2021 The Legion Of The Bouncy Castle Inc.；[完整许可](licenses/Bouncy-Castle.txt)、[固定版本许可头](https://github.com/keemobile/kotpass/blob/0.13.0/kotpass/src/main/kotlin/app/keemobile/kotpass/cryptography/engines/Argon2Engine.kt) |
| Kotlin Xml Builder | Kotpass 内嵌修改版 1.9.3 | Apache-2.0；[完整许可](licenses/Kotlin-Xml-Builder-Apache-2.0.txt)、[来源](https://github.com/keemobile/kotpass/tree/0.13.0/kotpass/src/main/kotlin/org/redundent/kotlin/xml) |
| Apache Commons Lang | Kotpass 内嵌修改版 3.17.0 | Apache-2.0，Copyright 2001-2024 The Apache Software Foundation；[原始 NOTICE](licenses/Commons-Lang-NOTICE.txt)、[来源](https://github.com/keemobile/kotpass/tree/0.13.0/kotpass/src/main/java/org/apache/commons/lang3) |
| Base64 实现 | Kotpass `io/Base64.kt` | Apache-2.0，作者 Alexander Y. Kleymenov；[来源与许可头](https://github.com/keemobile/kotpass/blob/0.13.0/kotpass/src/main/kotlin/app/keemobile/kotpass/io/Base64.kt) |

Kotpass 的内嵌组件说明见其[固定版本 README](https://github.com/keemobile/kotpass/blob/0.13.0/README.md)。Apache-2.0 全文位于根 [LICENSE](LICENSE)，Commons Lang 的归属同时保留在根 [NOTICE](NOTICE)。

## 其他直接依赖与构建工具

| 组件 | 当前版本 | 许可 / 官方来源 |
| --- | --- | --- |
| AndroidX Compose BOM / UI / Material3 | 2025.08.01 / 1.9.0 / 1.3.2 | Apache-2.0；[AndroidX](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt) |
| AndroidX Activity / Lifecycle | 1.10.1 / 2.9.3 | Apache-2.0；[AndroidX](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt) |
| Kotlin 标准库与测试库 | 2.2.20 | Apache-2.0；[固定版本许可](https://github.com/JetBrains/kotlin/blob/v2.2.20/license/LICENSE.txt) |
| Kotlin Coroutines | 1.10.2 | Apache-2.0；[固定版本许可](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/LICENSE.txt) |
| Okio JVM | 3.15.0 | Apache-2.0；[固定版本许可](https://github.com/square/okio/blob/parent-3.15.0/LICENSE.txt) |
| AndroidX Test ext JUnit / runner | 1.2.1 / 1.6.2，仅测试 | Apache-2.0；[来源](https://github.com/android/android-test) |
| JUnit | 4.13.2，仅测试，不在 Release runtime | EPL-1.0；[完整许可](licenses/JUnit-EPL-1.0.txt)、[固定版本来源](https://github.com/junit-team/junit4/tree/r4.13.2) |
| Gradle Wrapper | 8.14.5，仓库包含官方 wrapper JAR | Apache-2.0；[来源](https://github.com/gradle/gradle/tree/v8.14.5)、[官方校验值](https://services.gradle.org/distributions/gradle-8.14.5-wrapper.jar.sha256) |

本次开源发布提供源码与成品品牌资产，不提供正式安装版或 instrumentation APK。后续分发二进制时，须将适用的完整第三方许可与 NOTICE 一并打包并核验；当前源码归属文件不能替代 APK 内的许可交付。
