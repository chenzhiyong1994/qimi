param(
    [Parameter(Mandatory = $true)][string]$DeviceSerial,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [switch]$DedicatedSyntheticDevice
)
$ErrorActionPreference = 'Stop'
if (-not $DedicatedSyntheticDevice -or $DeviceSerial -notmatch '^emulator-\d+$') {
    throw '此检查重建 debug 合成库，必须显式指定专用模拟器与 -DedicatedSyntheticDevice。'
}
if (-not $AndroidSdk) { throw '请指定 ANDROID_HOME 或 -AndroidSdk。' }
$adb = Join-Path $AndroidSdk 'platform-tools/adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw "ADB 不存在：$adb" }
$package = 'com.localpasswordmanager.app.dev'
$runner = "$package.test/androidx.test.runner.AndroidJUnitRunner"
$projectRoot = Split-Path -Parent $PSScriptRoot
foreach ($apk in @('app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
    & $adb -s $DeviceSerial install -r (Join-Path $projectRoot $apk)
    if ($LASTEXITCODE -ne 0) { throw '请先构建 debug 和 androidTest 安装包。' }
}
function Invoke-SyntheticInstrumentation([string]$TestClass) {
    $result = & $adb -s $DeviceSerial shell am instrument -w -r -e dedicatedSyntheticDevice true -e class $TestClass $runner 2>&1 | Out-String
    Write-Host $result
    if ($LASTEXITCODE -ne 0 -or $result -notmatch 'OK \(1 test\)' -or $result -match 'FAILURES|INSTRUMENTATION_FAILED|shortMsg=') {
        throw "独立设备检查未通过：$TestClass"
    }
}
Invoke-SyntheticInstrumentation 'com.localpasswordmanager.app.S1UiTest#savedFieldsSurviveSearchBackgroundLockAndActivityRecreation'
& $adb -s $DeviceSerial shell am force-stop $package
if ($LASTEXITCODE -ne 0) { throw 'force-stop 失败。' }
$remainingPid = & $adb -s $DeviceSerial shell pidof $package
if ($remainingPid) { throw '原应用进程仍存在，不能声明已重开。' }
Invoke-SyntheticInstrumentation 'com.localpasswordmanager.app.ProcessRestartProbe#opensPersistedVaultInANewProcess'
Write-Host 'Process restart check passed: stopped original app process, authenticated from disk in a new process, and preserved file bytes.'
