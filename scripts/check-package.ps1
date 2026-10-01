param([string]$Variant = 'release')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$manifestFile = Join-Path $projectRoot "app/build/intermediates/merged_manifests/$Variant/process$($Variant.Substring(0,1).ToUpper()+$Variant.Substring(1))Manifest/AndroidManifest.xml"
if (-not (Test-Path -LiteralPath $manifestFile)) { throw "请先构建 $Variant 安装包：$manifestFile" }
[xml]$manifest = Get-Content -LiteralPath $manifestFile -Raw
$ns = 'http://schemas.android.com/apk/res/android'
$permissions = @($manifest.manifest.'uses-permission') | ForEach-Object { if ($_ -is [System.Xml.XmlElement]) { $_.GetAttribute('name', $ns) } }
if ($permissions -contains 'android.permission.INTERNET') { throw '最终 Manifest 含 INTERNET 权限。' }
$app = $manifest.manifest.application
if ($app.GetAttribute('allowBackup', $ns) -ne 'false') { throw '最终 Manifest 未禁用备份。' }
if ($app.GetAttribute('fullBackupContent', $ns) -ne '@xml/backup_rules') { throw '旧版排除规则缺失。' }
if ($app.GetAttribute('dataExtractionRules', $ns) -ne '@xml/data_extraction_rules') { throw '新版排除规则缺失。' }
$providers = @($app.provider) | Where-Object { $_ -is [System.Xml.XmlElement] }
if (@($providers | Where-Object { $_.GetAttribute('exported', $ns) -eq 'true' }).Count -gt 0) { throw '发现导出的 provider，请审查外部边界。' }
Write-Host "Package checks passed ($Variant): no INTERNET, backup disabled, both exclusion rules, no exported provider."
Write-Host '这些静态检查不替代云备份、D2D 或厂商迁移实测。'
