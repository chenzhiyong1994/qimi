# 本地构建与签名入口；不创建或复制私钥，也不执行上传、推送或发布。
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Keystore,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]*$')]
    [string]$KeyAlias,
    [ValidatePattern('^[A-Za-z_][A-Za-z0-9_]*$')]
    [string]$PasswordEnvironmentVariable = 'QIMI_SIGNING_PASSWORD',
    [ValidatePattern('^[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9]+)?$')]
    [string]$BuildToolsVersion = '35.0.0',
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$releaseRoot = Join-Path $projectRoot 'output/releases'
$stagingDirectory = $null
$originalEnvironment = @{}
foreach ($name in @('JAVA_HOME', 'ANDROID_HOME', 'ANDROID_SDK_ROOT')) {
    $originalEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

function Invoke-NativeChecked {
    param([string]$Executable, [string[]]$Arguments, [string]$Description)
    $lines = @(& $Executable @Arguments 2>&1)
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        $lines | ForEach-Object { Write-Host $_ }
        throw "$Description 失败，退出码 $exitCode。"
    }
    $lines | ForEach-Object { $_.ToString() }
}

try {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($PasswordEnvironmentVariable, 'Process'))) {
        throw "签名口令环境变量 $PasswordEnvironmentVariable 未设置或为空。仅接受变量名，不接受明文口令参数。"
    }
    if (-not $AndroidSdk) { $AndroidSdk = $env:ANDROID_SDK_ROOT }
    if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe') -PathType Leaf)) {
        throw '请通过 -JavaHome 或 JAVA_HOME 指定已有 JDK 17。'
    }
    if (-not $AndroidSdk -or -not (Test-Path -LiteralPath (Join-Path $AndroidSdk 'platforms/android-36/android.jar') -PathType Leaf)) {
        throw '请通过 -AndroidSdk 或 ANDROID_HOME 指定含 Android 36 的现有 SDK。'
    }
    $resolvedJavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
    $resolvedAndroidSdk = (Resolve-Path -LiteralPath $AndroidSdk).Path
    if (-not (Test-Path -LiteralPath $Keystore -PathType Leaf)) { throw '指定的签名密钥库不存在。' }
    $resolvedKeystore = (Resolve-Path -LiteralPath $Keystore).Path
    $projectPrefix = $projectRoot.TrimEnd([char[]]'\/') + [System.IO.Path]::DirectorySeparatorChar
    if ($resolvedKeystore.StartsWith($projectPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw '签名密钥库必须保存在仓库外；本脚本不会移动或复制私钥。'
    }
    $buildTools = Join-Path $resolvedAndroidSdk "build-tools/$BuildToolsVersion"
    $zipalign = Join-Path $buildTools 'zipalign.exe'
    $apksigner = Join-Path $buildTools 'apksigner.bat'
    foreach ($tool in @($zipalign, $apksigner)) {
        if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) { throw "已有 SDK 中缺少所需工具：$tool" }
    }
    $javaVersion = @(Invoke-NativeChecked -Executable (Join-Path $resolvedJavaHome 'bin/java.exe') -Arguments @('-version') -Description 'JDK 版本检查') -join "`n"
    if ($javaVersion -notmatch 'version "17\.') { throw '签名与构建要求 JDK 17；未修改全局工具版本。' }
    # 只设置当前进程环境；android.ps1 对 SDK 环境的调整也会在 finally 恢复。
    $env:JAVA_HOME = $resolvedJavaHome
    $buildArguments = @{
        JavaHome = $resolvedJavaHome
        AndroidSdk = $resolvedAndroidSdk
        Task = @(':vault-core:test', ':app:testDebugUnitTest', ':app:lintRelease', ':app:assembleRelease')
        Offline = $Offline
    }
    & (Join-Path $PSScriptRoot 'android.ps1') @buildArguments
    if (-not $? -or $LASTEXITCODE -ne 0) { throw 'Release 构建或必要检查失败。' }
    & (Join-Path $PSScriptRoot 'check-package.ps1') -Variant 'release'
    if (-not $?) { throw 'Release 包静态检查失败。' }

    $apkDirectory = Join-Path $projectRoot 'app/build/outputs/apk/release'
    $metadataPath = Join-Path $apkDirectory 'output-metadata.json'
    if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) { throw 'Release 构建未生成 APK 元数据。' }
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    $elements = @($metadata.elements)
    if ($metadata.variantName -ne 'release' -or $metadata.artifactType.type -ne 'APK' -or
        $elements.Count -ne 1 -or @($elements[0].filters).Count -ne 0) {
        throw '仅支持元数据明确标识的单个、不拆分的 Release APK。'
    }
    $element = $elements[0]
    $versionName = [string]$element.versionName
    $versionCode = 0L
    if ($versionName -notmatch '^[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?(?:\+[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?$' -or
        -not [long]::TryParse([string]$element.versionCode, [ref]$versionCode) -or $versionCode -lt 1 -or $versionCode -gt 2100000000) {
        throw 'APK 元数据缺少合法的版本名称或版本号。'
    }
    $outputFile = [string]$element.outputFile
    if ([string]::IsNullOrWhiteSpace($outputFile) -or [System.IO.Path]::GetFileName($outputFile) -cne $outputFile -or
        [System.IO.Path]::GetExtension($outputFile) -ine '.apk') {
        throw 'APK 元数据中的产物路径不合法。'
    }
    $unsignedApk = Join-Path $apkDirectory $outputFile
    if (-not (Test-Path -LiteralPath $unsignedApk -PathType Leaf)) { throw '元数据指定的 APK 不存在。' }
    $finalDirectory = Join-Path $releaseRoot $versionName
    if (Test-Path -LiteralPath $finalDirectory) {
        throw "同版本发布目录已经存在，拒绝覆盖：$finalDirectory"
    }
    New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null
    $stagingDirectory = Join-Path $releaseRoot ('.package-release-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $stagingDirectory | Out-Null
    $alignedApk = Join-Path $stagingDirectory 'aligned-unsigned.apk'
    $artifactName = "qimi-$versionName.apk"
    $signedApk = Join-Path $stagingDirectory $artifactName
    Invoke-NativeChecked -Executable $zipalign -Arguments @('-f', '-P', '16', '4', $unsignedApk, $alignedApk) -Description 'APK 对齐'
    Invoke-NativeChecked -Executable $apksigner -Arguments @(
        'sign', '--ks', $resolvedKeystore, '--ks-key-alias', $KeyAlias,
        '--ks-pass', "env:$PasswordEnvironmentVariable", '--key-pass', "env:$PasswordEnvironmentVariable",
        '--out', $signedApk, $alignedApk
    ) -Description 'APK 签名'
    $verification = @(Invoke-NativeChecked -Executable $apksigner -Arguments @('verify', '--verbose', '--print-certs', $signedApk) -Description 'APK 签名复验')
    $verification | ForEach-Object { Write-Host $_ }
    Invoke-NativeChecked -Executable $zipalign -Arguments @('-c', '-P', '16', '4', $signedApk) -Description '签名后 APK 对齐复验'
    $certificateDigests = @($verification | Where-Object { $_ -match '^Signer #[0-9]+ certificate SHA-256 digest: [0-9a-fA-F]{64}$' })
    if ($certificateDigests.Count -ne 1) { throw '无法从签名复验结果确认唯一的签名证书 SHA-256 摘要。' }
    # 证书文件只记录公开证书摘要；不导出密钥库、私钥、口令或其他签名配置。
    Set-Content -LiteralPath (Join-Path $stagingDirectory 'certificate.txt') -Value $certificateDigests -Encoding ascii
    $apkSha256 = (Get-FileHash -LiteralPath $signedApk -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Content -LiteralPath (Join-Path $stagingDirectory 'SHA256SUMS.txt') -Value "$apkSha256  $artifactName" -Encoding ascii
    Remove-Item -LiteralPath $alignedApk
    # 所有验证成功后才将本次产物变为正式版本目录；已有版本不会被替换。
    $resolvedReleaseRoot = (Resolve-Path -LiteralPath $releaseRoot).Path
    $resolvedStaging = (Resolve-Path -LiteralPath $stagingDirectory).Path
    $resolvedFinal = [System.IO.Path]::GetFullPath($finalDirectory)
    if ((Split-Path -Parent $resolvedStaging) -ne $resolvedReleaseRoot -or
        (Split-Path -Parent $resolvedFinal) -ne $resolvedReleaseRoot) {
        throw '发布目录边界校验失败，拒绝移动产物。'
    }
    [System.IO.Directory]::Move($stagingDirectory, $finalDirectory)
    $stagingDirectory = $null
    Write-Host "Release 已完成本地签名与校验：versionName=$versionName，versionCode=$versionCode"
    [PSCustomObject]@{
        VersionName = $versionName
        VersionCode = $versionCode
        Apk = Join-Path $finalDirectory $artifactName
        Checksums = Join-Path $finalDirectory 'SHA256SUMS.txt'
        Certificate = Join-Path $finalDirectory 'certificate.txt'
    }
} finally {
    foreach ($name in $originalEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $originalEnvironment[$name], 'Process')
    }
    if ($stagingDirectory -and (Test-Path -LiteralPath $stagingDirectory)) {
        # 仅清理本次唯一临时目录；递归删除前验证绝对路径仍在发布输出根目录内。
        $resolvedStaging = (Resolve-Path -LiteralPath $stagingDirectory).Path
        $resolvedReleaseRoot = (Resolve-Path -LiteralPath $releaseRoot).Path
        if ((Split-Path -Parent $resolvedStaging) -ne $resolvedReleaseRoot -or
            (Split-Path -Leaf $resolvedStaging) -notmatch '^\.package-release-[0-9a-f]{32}$') {
            throw '临时目录边界校验失败，保留现场且拒绝清理。'
        }
        Remove-Item -LiteralPath $resolvedStaging -Recurse -Force
    }
}
