param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string[]]$Task = @(':vault-core:test', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug', ':app:assembleRelease', ':app:assembleDebugAndroidTest'),
    [string]$DeviceSerial,
    [switch]$DedicatedSyntheticDevice,
    [switch]$Offline,
    [switch]$WriteLocks
)
$ErrorActionPreference = 'Stop'
if (-not $AndroidSdk) { $AndroidSdk = $env:ANDROID_SDK_ROOT }
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
    throw '请通过 -JavaHome 或 JAVA_HOME 指定 JDK 17。脚本不会自动安装或切换全局工具。'
}
if (-not $AndroidSdk -or -not (Test-Path -LiteralPath (Join-Path $AndroidSdk 'platforms/android-36/android.jar'))) {
    throw '请通过 -AndroidSdk 或 ANDROID_HOME 指定含 Android 36 的 SDK。'
}
$javaVersion = (& (Join-Path $JavaHome 'bin/java.exe') -version 2>&1 | Out-String)
if ($javaVersion -notmatch 'version "17\.') { throw "需要 JDK 17，实际版本：$javaVersion" }
$projectRoot = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
$env:ANDROID_HOME = (Resolve-Path -LiteralPath $AndroidSdk).Path
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
if ($DeviceSerial) { $env:ANDROID_SERIAL = $DeviceSerial }
$gradleArguments = @($Task) + @('--no-daemon', '--console=plain')
if ($Offline) { $gradleArguments += '--offline' }
if ($WriteLocks) { $gradleArguments += '--write-locks' }
if ($Task -match 'connected') {
    if (-not $DedicatedSyntheticDevice -or -not $DeviceSerial) {
        throw '设备测试会重建合成测试库，必须显式指定 -DeviceSerial 和 -DedicatedSyntheticDevice。请使用专用模拟器。'
    }
    $gradleArguments += '-Pandroid.testInstrumentationRunnerArguments.dedicatedSyntheticDevice=true'
}
Write-Host "JDK: $env:JAVA_HOME"
Write-Host "SDK: $env:ANDROID_HOME"
Push-Location -LiteralPath $projectRoot
try {
    & (Join-Path $projectRoot 'gradlew.bat') @gradleArguments
    if ($LASTEXITCODE -ne 0) { throw "Gradle 检查失败，退出码 $LASTEXITCODE" }
} finally { Pop-Location }
