param([string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$logoRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
$logoDirectory = Join-Path $logoRoot 'assets/brand'

# 仅渲染项目的矢量母稿，不读取、处理或覆盖生成图片。
function Read-LogoVector([string]$SourcePath) {
    [xml]$document = Get-Content -LiteralPath $SourcePath -Raw
    $viewBox = @($document.DocumentElement.GetAttribute('viewBox') -split '[ ,]+' | ForEach-Object {
        [single]::Parse($_, [Globalization.CultureInfo]::InvariantCulture)
    })
    if ($viewBox.Count -ne 4 -or $viewBox[2] -le 0 -or $viewBox[3] -le 0) { throw 'Invalid logo viewBox.' }
    $paths = foreach ($element in $document.SelectNodes('//*[local-name()="path"]')) {
        $data = $element.GetAttribute('d')
        if ([regex]::Matches($data, '[a-zA-Z]') | Where-Object { $_.Value -notin @('M','L','C','Q','Z','e','E') }) {
            throw 'Brand renderer supports absolute M/L/C/Q/Z paths only.'
        }
        $tokens = @([regex]::Matches($data, '[MLCQZ]|[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?') | ForEach-Object Value)
        $path = [Drawing.Drawing2D.GraphicsPath]::new()
        $path.FillMode = if ($element.GetAttribute('fill-rule') -eq 'evenodd') {
            [Drawing.Drawing2D.FillMode]::Alternate
        } else { [Drawing.Drawing2D.FillMode]::Winding }
        $cursor = 0; $command = ''; $x = 0.0; $y = 0.0; $startX = 0.0; $startY = 0.0
        while ($cursor -lt $tokens.Count) {
            if ($tokens[$cursor] -cmatch '^[MLCQZ]$') { $command = $tokens[$cursor]; $cursor++ }
            $count = switch ($command) { 'M' { 2 } 'L' { 2 } 'C' { 6 } 'Q' { 4 } 'Z' { 0 } default { throw 'Missing path command.' } }
            if ($cursor + $count -gt $tokens.Count) { throw 'Incomplete logo path.' }
            $values = for ($index = 0; $index -lt $count; $index++) {
                [single]::Parse($tokens[$cursor + $index], [Globalization.CultureInfo]::InvariantCulture)
            }
            $cursor += $count
            switch ($command) {
                'M' { $path.StartFigure(); $x = $values[0]; $y = $values[1]; $startX = $x; $startY = $y; $command = 'L' }
                'L' { $path.AddLine([single]$x, [single]$y, $values[0], $values[1]); $x = $values[0]; $y = $values[1] }
                'C' { $path.AddBezier([single]$x, [single]$y, $values[0], $values[1], $values[2], $values[3], $values[4], $values[5]); $x = $values[4]; $y = $values[5] }
                'Q' {
                    $path.AddBezier([single]$x, [single]$y,
                        [single]($x + 2.0 / 3 * ($values[0] - $x)), [single]($y + 2.0 / 3 * ($values[1] - $y)),
                        [single]($values[2] + 2.0 / 3 * ($values[0] - $values[2])), [single]($values[3] + 2.0 / 3 * ($values[1] - $values[3])),
                        $values[2], $values[3])
                    $x = $values[2]; $y = $values[3]
                }
                'Z' { $path.CloseFigure(); $x = $startX; $y = $startY; $command = '' }
            }
        }
        $fill = $element.GetAttribute('fill')
        if (-not $fill) { $fill = $document.DocumentElement.GetAttribute('fill') }
        [pscustomobject]@{ Path = $path; Color = [Drawing.ColorTranslator]::FromHtml($fill) }
    }
    [pscustomobject]@{ ViewBox = $viewBox; Paths = @($paths) }
}

function New-LogoCanvas([int]$Width, [int]$Height, [Drawing.Color]$Background) {
    $bitmap = [Drawing.Bitmap]::new($Width, $Height, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear($Background)
    $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.TextRenderingHint = [Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    [pscustomobject]@{ Bitmap = $bitmap; Graphics = $graphics }
}

function Draw-Logo($Canvas, $Vector, [single]$X, [single]$Y, [single]$Size, [Drawing.Color]$Tint = [Drawing.Color]::Empty) {
    $viewBox = $Vector.ViewBox
    $scale = $Size / [Math]::Max($viewBox[2], $viewBox[3])
    $matrix = [Drawing.Drawing2D.Matrix]::new($scale, 0, 0, $scale,
        [single]($X + ($Size - $viewBox[2] * $scale) / 2 - $viewBox[0] * $scale),
        [single]($Y + ($Size - $viewBox[3] * $scale) / 2 - $viewBox[1] * $scale))
    try {
        foreach ($item in $Vector.Paths) {
            $path = $item.Path.Clone()
            $brush = [Drawing.SolidBrush]::new($(if ($Tint.IsEmpty) { $item.Color } else { $Tint }))
            try { $path.Transform($matrix); $Canvas.Graphics.FillPath($brush, $path) }
            finally { $path.Dispose(); $brush.Dispose() }
        }
    } finally { $matrix.Dispose() }
}

function Draw-LogoTile($Canvas, $Vector, [single]$X, [single]$Y, [single]$Size, [Drawing.Color]$Green, [Drawing.Color]$Ivory) {
    $radius = $Size * 0.22
    $diameter = $radius * 2
    $path = [Drawing.Drawing2D.GraphicsPath]::new()
    $brush = [Drawing.SolidBrush]::new($Green)
    try {
        $path.AddArc($X, $Y, $diameter, $diameter, 180, 90)
        $path.AddArc($X + $Size - $diameter, $Y, $diameter, $diameter, 270, 90)
        $path.AddArc($X + $Size - $diameter, $Y + $Size - $diameter, $diameter, $diameter, 0, 90)
        $path.AddArc($X, $Y + $Size - $diameter, $diameter, $diameter, 90, 90)
        $path.CloseFigure()
        $Canvas.Graphics.FillPath($brush, $path)
        # Adaptive 前景的中央 72dp 可见区：与 SVG 中 translate(18,18) scale(.72) 完全相同。
        Draw-Logo $Canvas $Vector ($X + $Size * 0.14) ($Y + $Size * 0.15) ($Size * 0.72) $Ivory
    } finally { $path.Dispose(); $brush.Dispose() }
}

function Draw-BrandText($Canvas, [string]$Text, [single]$Size, [single]$X, [single]$Y, [Drawing.Color]$Color, [switch]$Bold) {
    $style = if ($Bold) { [Drawing.FontStyle]::Bold } else { [Drawing.FontStyle]::Regular }
    $font = [Drawing.Font]::new('Microsoft YaHei', $Size, $style, [Drawing.GraphicsUnit]::Pixel)
    $brush = [Drawing.SolidBrush]::new($Color)
    try { $Canvas.Graphics.DrawString($Text, $font, $brush, $X, $Y) }
    finally { $font.Dispose(); $brush.Dispose() }
}

function Save-LogoCanvas($Canvas, [string]$Name) {
    try { $Canvas.Bitmap.Save((Join-Path $logoDirectory $Name), [Drawing.Imaging.ImageFormat]::Png) }
    finally { $Canvas.Graphics.Dispose(); $Canvas.Bitmap.Dispose() }
}

$vector = Read-LogoVector (Join-Path $logoDirectory 'qimi-symbol.svg')
$green = [Drawing.ColorTranslator]::FromHtml('#244D3C')
$ivory = [Drawing.ColorTranslator]::FromHtml('#F6F7F0')
$muted = [Drawing.ColorTranslator]::FromHtml('#69756C')
try {
    foreach ($variant in @(@('qimi-symbol.png', $green), @('qimi-symbol-inverse.png', $ivory))) {
        $canvas = New-LogoCanvas 1024 1024 ([Drawing.Color]::Transparent)
        Draw-Logo $canvas $vector 0 0 1024 $variant[1]
        Save-LogoCanvas $canvas $variant[0]
    }
    $canvas = New-LogoCanvas 1024 1024 ([Drawing.Color]::Transparent)
    Draw-LogoTile $canvas $vector 0 0 1024 $green $ivory
    Save-LogoCanvas $canvas 'qimi-app-icon.png'

    $canvas = New-LogoCanvas 1600 1000 $ivory
    Draw-Logo $canvas $vector 150 170 310 $green
    Draw-BrandText $canvas '栖密' 108 535 210 $green -Bold
    Draw-BrandText $canvas '密码留在本地，轻松留给日常。' 30 547 362 $green
    Draw-BrandText $canvas '本地密码管理' 23 549 425 $muted
    Draw-LogoTile $canvas $vector 1170 195 250 $green $ivory
    $pen = [Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#DDE3D8'), 2)
    try { $canvas.Graphics.DrawLine($pen, 160, 565, 1440, 565) } finally { $pen.Dispose() }
    Draw-BrandText $canvas '一个安心的归处' 24 160 630 $green
    Draw-BrandText $canvas '开口栖巢 · 单齿钥匙 · 森林绿' 21 160 685 $muted
    Draw-LogoTile $canvas $vector 955 637 144 $green $ivory
    Draw-LogoTile $canvas $vector 1162 682 96 $green $ivory
    Draw-LogoTile $canvas $vector 1322 730 48 $green $ivory
    Draw-BrandText $canvas '应用图标' 21 1128 816 $muted
    Save-LogoCanvas $canvas 'qimi-brand-preview.png'
} finally { foreach ($item in $vector.Paths) { $item.Path.Dispose() } }
Write-Host 'Brand exports created: two transparent symbols, app icon and brand preview.'
