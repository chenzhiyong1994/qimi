# 本地密码管理项目指南

## 目的与当前阶段

- 面向个人用户的 Android 本地密码管理 APP，产品名“栖密”，品牌定义见[产品方案](docs/product-plan.md)。
- 当前已建立 Android 工程并推进 S1；实际实现状态以代码和 [项目状态](docs/project-status.md) 为准。
- 默认用简体中文沟通，命令、代码和 API 名保持英文。

## 读取入口

新任务先读 [README](README.md) 和 [项目状态](docs/project-status.md)，按改动范围继续：

- 目标、范围、优先级：[产品方案](docs/product-plan.md)。
- 页面、表单、文案与用户路径：[体验规格](docs/experience-spec.md)。
- 本地边界、密钥、数据、自动填充和恢复：[安全与数据规格](docs/security-and-data.md)。
- 验收步骤与覆盖面：[验收场景](docs/acceptance.md)。
- 增量依赖：[迭代路线](docs/iteration-roadmap.md)；技术与范围选择：[决策记录](docs/decisions.md)。
- 工程版本与重建：[开发文档](docs/development.md)；实际验证：[S1 记录](docs/validation-s1.md)；存储接口：[核心模块](vault-core/README.md)。
- 开源展示、双语 README、项目主页与公开发布：[开源规范](docs/open-source.md)。

## 核心约束

- 用户已确认 Android 首发、密码数据仅本地不上云；不得引入云账户、云同步、联网埋点、远程图标或云端 AI 处理密码。
- 最终安装包无 `INTERNET` 权限；系统云备份与厂商迁移分别排除并实测。无联网权限不等于已验证所有外流路径。
- 敏感数据包括账号、网址、备注、待整理原文、历史、草稿和元数据；全部纳入加密、备份与日志边界。
- 密码与账号原值保真；未知自动填充目标不释放凭据；不能以名称或模糊 URL 自动信任目标。
- 保存、恢复、迁移和主密码修改须有可验证提交与回退，不虚报成功，不静默覆盖。
- 主密码遗失、旧备份、设备内快照和永久删除边界必须真实；不增加解密后门或绝对安全承诺。
- 设计建议、待决选择、已实现和已验证须分开标注；本次方案不等于全部技术参数已冻结。
- 所有开发、原型与测试使用合成资料；不得把真实密码、客户资料、密钥或内部链接放入源码与文档。

## 执行与维护

- 行动请求按已授权范围持续完成；普通修订和实现不重新开审批。真正缺失的信息先核实，不编造业务事实。
- 按迭代路线完成可独立验证的增量；工程探索与交付分开记录，不先堆出全部 UI 或全部存储框架。
- 规则只在其负责文档维护；修改范围、数据、安全、交互或验收时，同步受影响引用。
- 新任务核对工作区现状。已有用户改动与数据保留；不擅自删库、清模拟器数据、卸载 APP 或覆盖备份。
- 若本机存在 `D:/AndroidDev/C盘迁移说明.md` 或 `D:/Tools/README.md`，Android 开发和工具定位前先读取并复用既有工具；其他环境按[开发文档](docs/development.md)配置 JDK / SDK，项目依赖保持独立。
- 不依赖兄弟项目的文档、依赖或缓存运行。新增技术决策记录实际版本、理由、兼容边界及重建入口。
- 用户仅要求方案或审查时不擅自实现 APP、创建聊天、启动 Goal、推送或发布。
- Git 已建立时按全局要求完成本次本地提交；未建立时不为满足提交要求初始化仓库。公开发布前执行适用安全审查。

## 最小验证

```powershell
pwsh -NoProfile -File scripts/android.ps1
pwsh -NoProfile -File scripts/check-package.ps1
pwsh -NoProfile -File scripts/check-docs.ps1
```

Android 脚本要求 JDK 17 / Android 36 SDK；设备测试另按开发文档指定专用合成模拟器。文档和包静态检查不替代设备验收。按照 [验收场景](docs/acceptance.md)覆盖改动面，只在完成适用验证后更新 [项目状态](docs/project-status.md)，如实标记未验证项。
