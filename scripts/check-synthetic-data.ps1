param(
    [Parameter(Mandatory = $true)][string]$DeviceSerial,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [switch]$DedicatedSyntheticDevice
)
$ErrorActionPreference = 'Stop'
if (-not $DedicatedSyntheticDevice -or $DeviceSerial -notmatch '^emulator-\d+$') {
    throw '仅可对已执行 S1 合成夹具的专用 debug 模拟器做扫描。'
}
if ($PSVersionTable.PSVersion -lt [version]'7.4') { throw '二进制 ADB 重定向需要 PowerShell 7.4+。' }
if (-not $AndroidSdk) { throw '请指定 ANDROID_HOME 或 -AndroidSdk。' }
$adb = Join-Path $AndroidSdk 'platform-tools/adb.exe'
$projectRoot = Split-Path -Parent $PSScriptRoot
$package = 'com.localpasswordmanager.app.dev'
$evidence = Join-Path $projectRoot 'output/verification'
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
$archive = Join-Path $evidence 'app-private.tar'
& $adb -s $DeviceSerial exec-out run-as $package tar -cf - . > $archive
if ($LASTEXITCODE -ne 0) { throw '读取 debug 私有目录失败。' }
$extract = Join-Path $evidence ('app-private-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $extract | Out-Null
[System.Formats.Tar.TarFile]::ExtractToDirectory($archive, $extract, $false)
$packageIdentity = & $adb -s $DeviceSerial shell cmd package list packages -U $package | Out-String
$uidMatch = [regex]::Match($packageIdentity, 'package:' + [regex]::Escape($package) + ' uid:(\d+)')
if (-not $uidMatch.Success) { throw '未解析到目标 APP UID，拒绝扫描其他 APP 日志。' }
$log = Join-Path $evidence 'app-uid.log'
& $adb -s $DeviceSerial logcat -d "--uid=$($uidMatch.Groups[1].Value)" > $log
if ($LASTEXITCODE -ne 0) { throw 'APP UID 日志读取失败。' }
$markers = @('Synthetic-Master', 'SecretProbe71', 'NotesProbe84', 'Synthetic 合成资料',
    '合成User', 'synthetic.example.test', 'SearchBundleProbe23', 'UnsavedSecretProbe95')
$files = @(Get-ChildItem -LiteralPath $extract -Recurse -File) + @(Get-Item -LiteralPath $log)
$findings = @()
foreach ($file in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    foreach ($encoding in @([System.Text.Encoding]::UTF8, [System.Text.Encoding]::Unicode, [System.Text.Encoding]::BigEndianUnicode)) {
        $text = $encoding.GetString($bytes)
        foreach ($marker in $markers) {
            if ($text.Contains($marker, [System.StringComparison]::Ordinal)) {
                $findings += "$($file.FullName): synthetic marker ($($encoding.WebName))"
            }
        }
    }
}
if ($findings.Count) { throw ($findings -join [Environment]::NewLine) }
Write-Host "Synthetic scan passed: $($files.Count) private/log files, 8 markers, UTF-8 / UTF-16LE / UTF-16BE, zero matches."
Write-Host '扫描不证明密码学安全，不覆盖系统/输入法或接收方保存的副本。'
