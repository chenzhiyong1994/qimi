# 产品宣传素材

`qimi-banner.png` 用于双语 README 与主页分享预览，表现栖密的本地保存、主密码保护和日常查找价值，不使用 Logo / 应用图标设计展示板。画面中的手机为宣传插画，不是实际 APP 截图；没有真实账号或密码。2026-10-14 起主页正文使用下述当前版本真实界面。

Banner 先由 Dreamina `seedream_4.5` 生成，再通过内置 `imagegen` 定向修正指令残留、手机摄像头与多余控件。最终画面已实际查看；保留原生 PNG，不重新编码。核心生成与修图要求：

> 16:9 产品宣传画面，森林绿与暖白、自然光和植物投影、清楚的中英文产品文案、安卓手机中的合成账号列表。不得出现独立 Logo、App 图标、钥匙、盾牌、水印、色值或排版指令；密码使用圆点遮蔽，不暗示尚未实现的功能。

固定文案为“栖密 / Qimi”“密码留在本地，轻松留给日常。”“Passwords stay local. Everyday life feels lighter.”及“离线保存 · 主密码保护 · 快速查找”。

`qimi-list.png`、`qimi-edit.png`、`qimi-generator.png` 与 `qimi-detail.png` 于 2026-10-14 通过 [WebsiteScreenshotProbe](../../app/src/androidTest/java/com/localpasswordmanager/app/WebsiteScreenshotProbe.kt) 导出，均为 1080 × 2400 原生 PNG。使用与 `v1.0.0` 相同的 APP 生产源码，在独立 Android 16 合成模拟器中实际操作：密码库三条记录、五字段编辑表单、20 位且选择四类字符的高级生成器、账号详情。浅色主题、1.0 字体、320 dpi；界面仍为中文，网站 English 介绍不代表 APP 已提供英文界面。

探针绘制真实 Activity / Dialog View 树，不缩放、裁剪、替换 UI 或取消 `FLAG_SECURE`。不含系统状态栏和导航栏的系统绘制层；网页手机边框与背景由 CSS 装饰。原密码和生成候选均隐藏，账户和网址仅使用 `example.com` 示例域。设备配置、夹具校验与复现命令见[截图来源](screenshots-source.md)，本轮验证见[开源记录](../../docs/open-source.md)。

`website/assets/` 中保留相同图片供独立静态主页部署，`qimi-symbol.svg` 为品牌目录中同名原稿的副本。修改素材时同步来源与部署副本，并检查字节摘要一致。品牌母稿与 Logo 展示另见[品牌资产](../brand/README.md)。
