# FreshMate 正式包构建脚本（Windows PowerShell 5.1+）
# 用法：.\scripts\build-release.ps1 [-SkipTests]
# 流程：单测 + assembleRelease → 拷贝 APK 到 dist\ → 计算 SHA256 → 更新 dist\manifest.json
# 前提：无必须配置。update.properties 缺失时回落入库默认更新地址（可放该文件覆盖）

param(
    # 跳过单元测试（默认跑 testDebugUnitTest 再打包）
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

# JAVA_HOME：发版构建优先 OpenJDK 21（Temurin，与 F-Droid 构建环境的 Debian trixie default-jdk
# 对齐——可复现构建哈希比对的前提）；未装则回落 Android Studio JBR 并警告
$jdk21 = "C:\Program Files\OpenJDK\21"
$javaHome = "C:\Program Files\Android\Android Studio\jbr"
if (Test-Path $jdk21) {
    $javaHome = $jdk21
}
elseif (-not (Test-Path $javaHome)) {
    throw "未找到 JDK：既无 $jdk21 也无 JBR（$javaHome），请至少安装其一"
}
else {
    Write-Warning "未找到 OpenJDK 21（$jdk21），回落 JBR——正式包哈希可能与 F-Droid 构建不一致，建议安装 Temurin 21"
}
$env:JAVA_HOME = $javaHome

# 从 app\build.gradle.kts 解析版本号（单一事实来源，脚本不重复维护版本）。
# 必须显式 -Encoding UTF8：文件无 BOM，PS5 缺省按 ANSI 读，中文注释在内存里即乱码
$gradleFile = Get-Content "app\build.gradle.kts" -Raw -Encoding UTF8
$versionName = [regex]::Match($gradleFile, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
$versionCode = [regex]::Match($gradleFile, 'versionCode\s*=\s*(\d+)').Groups[1].Value
if (-not $versionName -or -not $versionCode) {
    throw "无法从 app\build.gradle.kts 解析 versionName/versionCode"
}

# 询问是否升级版本号：输入非空且大于当前版本才写入 build.gradle.kts，否则沿用当前版本
Write-Host "当前版本: $versionName (versionCode $versionCode)"
$newVersion = Read-Host "输入新版本号（如 0.2.6；留空或更小则不升级）"
$parsedNew = $null
if ($newVersion -and [version]::TryParse($newVersion, [ref]$parsedNew)) {
    if ($parsedNew -gt [version]$versionName) {
        $newCode = [int]$versionCode + 1
        $gradleFile = $gradleFile `
            -replace 'versionCode\s*=\s*\d+', "versionCode = $newCode" `
            -replace 'versionName\s*=\s*"[^"]*"', "versionName = `"$newVersion`""
        # PS5 的 Set-Content 默认编码会毁掉文件里的中文注释——用 WriteAllText 落无 BOM UTF-8
        [System.IO.File]::WriteAllText(
            (Join-Path (Get-Location) "app\build.gradle.kts"),
            $gradleFile,
            (New-Object System.Text.UTF8Encoding($false))
        )
        $versionName = $newVersion
        $versionCode = $newCode
        Write-Host "版本已升级: $versionName (versionCode $versionCode)——记得提交 build.gradle.kts"
    }
    else {
        Write-Host "新版本 $newVersion 不大于当前版本 $versionName，不更新版本（沿用 $versionName）"
    }
}
else {
    Write-Host "输入为空或不是合法版本号，不更新版本（沿用 $versionName）"
}

# 构建：默认测试 + 打包
# 先停掉所有 Gradle 守护进程：残留守护进程（如 IDE 同步拉起的 JBR 实例）可能握着
# app\build\intermediates\lint-cache 里的 jar 句柄，导致本次构建报"另一个程序正在使用此文件"
& .\gradlew.bat --stop | Out-Null
# --no-build-cache：构建缓存可能命中旧环境（如换 JDK 前）的任务产物且键不含全部环境变量，
# 曾导致发布包 dex/baseline.prof 与 F-Droid 侧构建不一致——发布构建必须全量执行
$tasks = @("--no-build-cache", "assembleRelease")
if (-not $SkipTests) { $tasks = @("testDebugUnitTest") + $tasks }
& .\gradlew.bat @tasks
if ($LASTEXITCODE -ne 0) { throw "构建失败（gradlew exit $LASTEXITCODE）" }

# 制品 → dist\
New-Item -ItemType Directory -Force -Path "dist" | Out-Null
$apkSource = "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apkSource)) { throw "未找到 APK：$apkSource" }
$apkDest = "dist\freshmate-$versionName.apk"
Copy-Item $apkSource $apkDest -Force
$hash = (Get-FileHash $apkDest -Algorithm SHA256).Hash.ToLower()

# 更新 dist\manifest.json（保留 notes，仅刷版本/地址/哈希）
$manifestPath = "dist\manifest.json"
if (Test-Path $manifestPath) {
    $manifest = Get-Content $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $manifest.versionCode = [int]$versionCode
    $manifest.versionName = $versionName
    $manifest.apkUrl = "https://www.battor.site/freshmate/freshmate-$versionName.apk"
    $manifest.sha256 = $hash
    # PS5 的 UTF8 带带 BOM，部分 JSON 解析器（如 Android JSONObject）不接受——用 WriteAllText 落无 BOM UTF-8
    $json = $manifest | ConvertTo-Json
    [System.IO.File]::WriteAllText((Join-Path (Get-Location) $manifestPath), $json, (New-Object System.Text.UTF8Encoding($false)))
}
else {
    Write-Warning "未找到 $manifestPath，跳过 manifest 更新（首次发布请手工创建）"
}

Write-Host ""
Write-Host "==================== 发布制品就绪 ===================="
Write-Host "版本  : $versionName (versionCode $versionCode)"
Write-Host "APK   : $apkDest ($((Get-Item $apkDest).Length) bytes)"
Write-Host "SHA256: $hash"
Write-Host "manifest: $manifestPath 已刷新（notes 未动，按需手工修改）"
Write-Host "======================================================"
Write-Host "剩余步骤（手工）：上传 APK + manifest 到 battor.site；git push 由你自行执行"
