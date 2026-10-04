# 开源展示与发布规范

## 依据与范围

2026-10-01 用户指出首次公开发布使用了 Logo / 应用图标设计展示板作为 README Banner，且缺少项目主页和中英双语 README。本规范将这次明确要求集中到栖密项目，适用于公开仓库的展示、主页维护和后续公开更新。

此前 HushWake、如约、Novelist Workflow 等项目已有双语 README 与 GitHub Pages 主页实践；部分项目采用独立的产品场景封面。这些是历史实施模式，不能冒称原有全局规则已统一规定上述交付物，其他项目的封面也并非一律禁止 Logo。本项目以下要求自本轮起为现役规范；不扩大为其他项目的默认规则，不改变密码数据仅保存在本机的产品边界。

## 必须维护的展示内容

- **完整双语 README**：根 [README.md](../README.md) 默认简体中文，[README.en.md](../README.en.md) 提供完整英文版本，两份文件在顶部互链。产品介绍、当前能力、未完成范围、构建验证、参与开发、许可和主页入口保持一致；英文 README 不代表 APP 已提供英文界面。
- **独立产品宣传 Banner**：封面表现栖密的使用价值和产品体验，采用本项目的森林绿与暖白视觉。不得以 Logo、透明 / 反白版本、应用图标和小尺寸对照组成的品牌设计展示板替代。`assets/brand/qimi-brand-preview.png` 仍用于品牌资产说明；README 使用独立的 `assets/promo/qimi-banner.png`。宣传文案与可见界面不得暗示尚未实现的功能。
- **双语项目主页**：主页提供中文和 English 两套完整介绍、当前版本边界与源码入口，默认中文，可切换语言。源码独立维护于 `website/`，由 GitHub Pages 发布；主页是公开产品介绍，不处理密码、提供云账户或模拟已上线的密码管理服务。
- **主页入口一致**：仓库 About 的 Website、中文 README 和英文 README 均指向 `https://chenzhiyong1994.github.io/qimi/`。仓库地址为 `https://github.com/chenzhiyong1994/qimi`。仓库名称或所有者变化时，同步主页 canonical / Open Graph 地址、公开链接与发布配置。
- **仅用合成内容**：产品截图来自本项目真实渲染的合成验收场景，注明演示内容。检查公开像素及文件元数据；不使用真实密码、真实账号、私人聊天、签名材料、客户资料或带认证信息的链接。外部推广素材不由 APP 在运行时联网加载。

两份 README 与主页始终区分已实现、已验证、待完成和已分发。1.0.0 的功能范围与实际发布状态见[项目状态](project-status.md)，正式版本号、开源仓库、宣传页面或构建成功均不能代替完整产品验收。

## 本地验证

1. 对照两份 README 和双语主页，确认功能、版本、许可、运行条件、未完成事项与下载 / 源码入口一致。不存在正式 Release 时，不提供虚构的 APK 下载按钮。
2. 查看 Banner 与截图的实际像素，确认封面是产品宣传、合成内容清楚、文字可读且没有凭证。保留生成或导出方式与素材来源；资产构建成功不能替代目视检查。
3. 在本地浏览主页，检查桌面和窄屏排版、图片加载、中文 / English 切换、键盘焦点、链接以及控制台错误。GitHub Pages 的项目子路径为 `/qimi/`，资源不能依赖仓库根路径。
4. 检查 Markdown 本地链接与格式、主页脚本语法和发布文件范围。Pages 只上传 `website/` 的公开静态内容，不能上传仓库根目录、密码库、缓存或本机 `output/`。
5. 检查 `git status --short`、本次差异及 `git diff --check`；稳定并通过适用验证后创建本地提交。仅展示内容变化时复用仍覆盖当前 APP 源码的已有构建证据，不把文档检查当作 APP 验证。

文档检查入口：

```powershell
pwsh -NoProfile -File scripts/check-docs.ps1
```

主页入口为 [website/index.html](../website/index.html)，可直接在浏览器中打开本地文件预览，无需安装网站依赖。脚本语法检查使用 `node --check website/main.js`；发布工作流见 [Pages 配置](../.github/workflows/pages.yml)，仅上传 `website/`，Actions 引用固定到已核对的官方提交。

## 公开更新与线上验证

每次向公开远端更新前使用 `safe-open-source-release`，确认公开目标、远端 tip 和待发布提交，审阅全部新增文本与媒体，解决审计 blocker 和 review。审计报告留在仓库外；通过 `verify` 后由守门器发布精确的 `refs/heads/main`，不直接推送宽泛 ref、跳过检查或改写公开历史。回读远端 main，确认与已审提交一致。

用户明确授权版本发布时，GitHub Release 绑定本轮已审计并已公开的精确提交，禁止覆盖已有版本标签或上传未验证附件。正式 APK 使用独立、仓库外保管的签名身份，附件仅包含签名 APK、SHA-256 校验清单和公开证书摘要；私钥、口令及其本地保护文件不进入 Git 或 Release。源码审计、APK 签名验证、设备安装验证和 GitHub 附件回读分别记录，不把任一项替代其他检查。

源码发布和主页部署分别验证。GitHub Pages 工作流应只部署已审的公开静态目录，使用最小必要权限；启用 Pages 或调整工作流后核对实际部署结果。完成部署后检查：

- 部署运行成功，线上中文与英文内容、Banner 和其他资源返回成功，实际文件与已审本地版本一致。
- 在真实浏览器中检查线上桌面 / 窄屏、语言切换、图片和链接；本地预览通过不代表线上部署完成。
- 仓库 About 与两份 README 的主页入口一致并可访问。

完成证据窄幅记录到本文件与[项目状态](project-status.md)，注明已修改、已本地验证、已推送、已部署和已线上验证的实际阶段。公开源码、主页上线和正式签名 APP 分发分别记录。

## 本轮状态

2026-10-01：产品宣传 Banner、完整双语 README 与静态双语主页已接入。Banner 与合成界面原图已实际查看，宣传素材说明见[素材来源](../assets/promo/README.md)。本地文件浏览通过中文 / English 的 1440、768、390、320 像素八组无横向溢出与图片加载检查，语言文案 / 标题 / README 链接切换、偏好保留、减少动效检查通过，未记录控制台或页面错误；桌面中文与手机英文完整页面已目视检查。

本地文件页面重载曾出现根 `lang` 回到 `zh-CN`、英文文案不变的情况；HTTPS 线上重新加载及移除查询参数后，英文文案与 `lang=en` 均保持，语言偏好保留检查通过。后台 HTTP 预览服务的启动被自动审批拒绝（`blocked by policy`），本地检查使用静态文件页面，线上以真实 HTTPS 页面核实。

宣传增量提交 `68669b31cdde17346c678aa4a64f16e2cd657bfe` 已通过增量审计：1 个新增提交、0 blocker，3 张新图经实际像素检查解决全部语义 review；守门器精确发布 `refs/heads/main`，远端 OID 回读一致。GitHub 纵深凭据扫描告警查询为 0 条。

[首次 Pages 部署](https://github.com/chenzhiyong1994/qimi/actions/runs/36830508349)成功，主页为 [栖密 Qimi](https://chenzhiyong1994.github.io/qimi/)，强制 HTTPS；仓库 About 的 Website 与两份 README 一致。线上两种语言的 1440、768、390、320 像素八组检查通过，无横向溢出、全部图片加载，未记录页面或控制台错误；中文桌面与 320 像素英文页面已查看。HTML、CSS、JS 和三张图片均返回 HTTP 200，CSS / JS / 图片与已审本地文件字节一致。

键盘检查发现跳转正文后焦点未转移，已给 `main` 增加 `tabindex=-1`；本地跳过导航、正文焦点、Enter 切换语言及 `lang` 检查通过。收尾提交 `bc82636ecca8f404ac0aa4b8f52f3e2fc955300a` 通过增量审计与精确发布，[部署运行](https://github.com/chenzhiyong1994/qimi/actions/runs/36831005190)成功。线上回读 `tabindex=-1` 后重跑键盘跳转 / 语言切换 / 重载语言属性检查全部通过。GitHub 实际渲染的 README 已确认加载独立产品 Banner，English 与主页链接正确。首次源码公开与 APP 行为验收仍见[项目状态](project-status.md)及[S1 验证记录](validation-s1.md)，没有新增正式 APK 分发。

### 2026-10-04：1.0.0 正式发布

本轮审计覆盖 4 个待公开提交，0 blocker、18 项 review 已逐项语义审阅并解决。分支守门器精确发布 main，远端回读到 `23a297b00c83aa9bc57afc503f7b98fcc4a72931`；轻量标签 `v1.0.0` 指向同一已公开提交，未覆盖已有标签。APK 来源与此标签固定，后续发布证据文档的提交独立于制品来源。

审计工具的 Kotlin 误报已修复，同时保留需要语义判定的 review；新增标签守门流程只允许标记已公开的 main，禁止覆盖标签。两项工具改动合并后 79 项测试通过，223.854 秒。这些是发布工具回归，不计入 APP 测试或安全验收。

[v1.0.0 Release](https://github.com/chenzhiyong1994/qimi/releases/tag/v1.0.0) 于 2026-10-04 19:28:51（Asia/Shanghai）发布，ID `402985598`，非草稿、非预发布，标为 Latest。附件为 `qimi-1.0.0.apk`、`SHA256SUMS.txt`、`certificate.txt`；GitHub 服务端 digest / size 与本机一致，最终均已匿名下载回读，SHA-256 全部一致。发布后再次核对标签、Latest 状态、附件名称与 digest，结果稳定；open secret-scanning alerts 查询返回 0 条。签名、最终 APK 设备检查、下载重试经过和制品校验值分别见[验证记录](validation-s1.md)与[发行说明](releases/1.0.0.md)。

[本轮 Pages 部署](https://github.com/chenzhiyong1994/qimi/actions/runs/37198134916)成功，来源为 `23a297b00c83aa9bc57afc503f7b98fcc4a72931`。线上 HTML、JS、CSS 与已审本地文件逐字节 SHA-256 一致；真实浏览器已核对中文无障碍树、英文 DOM 中的 1.0.0 内容以及 APK 下载 / 发行页两个入口，观察到选择英文并重开后仍保持英文。

本轮视口与截图接口多次超时，未完成新的桌面 / 窄屏视觉全矩阵；临时视口设置已 reset。此前八组布局证据仍作为历史基线，不能视为本轮已经重跑。主页旧截图继续注明此前版本；本轮发布不代表备份恢复、完整安全评审、设备迁移排除或完整 MVP 验收已完成。
