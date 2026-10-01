# 栖密 Logo 资产

主标记由开口圆弧“栖巢”、单齿短钥匙与流动的 q 形尾部组成，表达密码有个安心的归处。品牌名称和 slogan 维护在[产品方案](../../docs/product-plan.md)。

- `qimi-symbol.svg` / `.png`：森林绿 `#244D3C`，透明底主标记。
- `qimi-symbol-inverse.svg` / `.png`：暖白 `#F6F7F0`，供深色底使用。
- `qimi-app-icon.svg` / `.png`：森林绿底的应用图标预览；实际圆角和遮罩由 Launcher 决定。
- `qimi-brand-preview.png`：标记、中文名称、slogan 与应用图标的组合预览。
- 本机 `source/`：生成探索的原始素材，仅供追溯，由 Git 忽略，不随开源源码发布，也不由 APP 加载。

SVG 与 Android [brand_mark.xml](../../app/src/main/res/drawable/brand_mark.xml) / [icon_foreground.xml](../../app/src/main/res/drawable/icon_foreground.xml)共享相同路径。矢量是实际交付轮廓；原始生成图中的纹理和背景不用于生产。保持完整比例与透明负形，图标内部不放名称或 slogan。

Header 以主题色呈现标记。Adaptive 前景使用 108dp 画布，全部曲线控制点处于中心半径 33dp 内；Android 13+ 提供同轮廓 `monochrome`，其他系统沿用原 adaptive 图标。

PNG 由 SVG 的路径直接渲染。Windows / PowerShell 7 下运行 `pwsh -NoProfile -File scripts/export-brand.ps1` 可重新导出；使用本机 `System.Drawing` 和 Microsoft YaHei，不新增 APP 依赖。此脚本只重建上述 PNG，不处理或覆盖 `source/` 生成图片。
