# ============================================================================
# 构建 PlayerTaskX 并启动 Folia 测试服
#
# 用法：
#   .\start-folia.ps1              # 增量构建（快，日常开发用）
#   .\start-folia.ps1 -Clean       # 先 clean 再构建（改过构建脚本或依赖时用）
#   .\start-folia.ps1 -Foreground  # 在当前窗口前台运行，可直接输入服务端命令
#   .\start-folia.ps1 -SkipBuild   # 只启动，不构建
#
# 说明：
#   * 之所以不再用 gradle runServer：服务器 jar 由你自行维护在 run/ 下，
#     脚本直接启动它，避免 Gradle 每次替换/重下服务端 jar。
#   * 服务端默认占用内存 2G，需要调整就改 $Memory。
#   * 服务端控制台的中文：已强制 UTF-8，若你的终端仍显示乱码，
#     在 PowerShell 里先执行 chcp 65001 或用 Windows Terminal。
# ============================================================================

[CmdletBinding()]
param(
    [switch]$Clean,
    [switch]$SkipBuild,
    [switch]$Foreground,
    [string]$Memory = "2G"
)

$ErrorActionPreference = "Stop"
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$Root      = Split-Path -Parent $MyInvocation.MyCommand.Path
$RunDir    = Join-Path $Root "run"
$ServerJar = Join-Path $RunDir "folia-26.1.2-8.jar"
$Plugins   = Join-Path $RunDir "plugins"

Write-Host "=== PlayerTaskX 构建与启动 ===" -ForegroundColor Cyan
Write-Host "项目根目录: $Root"

if (-not (Test-Path $ServerJar)) {
    Write-Host "找不到服务端 jar: $ServerJar" -ForegroundColor Red
    Write-Host "请把 Folia 服务端 jar 放到 run 目录下，或修改脚本里的 ServerJar 变量。" -ForegroundColor Yellow
    exit 1
}

# ---------------------------------------------------------------- 构建
if (-not $SkipBuild) {
    $gradlew = Join-Path $Root "gradlew.bat"
    if (-not (Test-Path $gradlew)) {
        Write-Host "找不到 $gradlew（Windows 下需要 gradlew.bat）" -ForegroundColor Red
        exit 1
    }

    # shadowJar 依赖 core 的 jar 与前端构建（processResources），因此一条命令即可。
    # 这里不拼接参数数组，避免 PowerShell 的数组 splatting 歧义
    Push-Location $Root
    try {
        if ($Clean) {
            Write-Host "开始构建: gradlew clean shadowJar" -ForegroundColor Cyan
            & $gradlew clean shadowJar --console=plain
        }
        else {
            Write-Host "开始构建: gradlew shadowJar" -ForegroundColor Cyan
            & $gradlew shadowJar --console=plain
        }
        if ($LASTEXITCODE -ne 0) {
            Write-Host "构建失败，已中止（未启动服务器）" -ForegroundColor Red
            exit $LASTEXITCODE
        }
    } finally {
        Pop-Location
    }
}
else {
    Write-Host "已跳过构建（-SkipBuild）" -ForegroundColor Yellow
}

# ---------------------------------------------------------------- 复制产物
# 只匹配 shadowJar 的 *-all.jar；那个 0 字节的普通 jar 是空壳，复制过去插件会加载失败
$artifact = Get-ChildItem (Join-Path $Root "build\libs") -Filter "*-all.jar" -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1

if (-not $artifact) {
    Write-Host "未找到构建产物 build\libs\*-all.jar" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $Plugins)) {
    New-Item -ItemType Directory -Path $Plugins -Force | Out-Null
}

# 清理历史版本，避免 plugins 里堆积多个 playerTaskX jar 导致重复加载。
# 若服务器仍在运行，jar 会被占用：这里给出明确提示而不是抛出裸异常，
# 因为「忘记关服务器」是最常见的情况，不该表现成一堆 PowerShell 报错。
$locked = $false
Get-ChildItem $Plugins -Filter "playerTaskX*.jar" -ErrorAction SilentlyContinue | ForEach-Object {
    try {
        Remove-Item $_.FullName -Force -ErrorAction Stop
        Write-Host "移除旧产物: $($_.Name)" -ForegroundColor DarkGray
    }
    catch {
        $locked = $true
    }
}

if ($locked) {
    Write-Host "无法替换 plugins 下的 playerTaskX jar：文件正被占用（服务器可能仍在运行）。" -ForegroundColor Red
    Write-Host "请先关闭正在运行的服务器，然后重新执行本脚本。" -ForegroundColor Yellow
    exit 2
}

$target = Join-Path $Plugins "playerTaskX.jar"
try {
    Copy-Item $artifact.FullName $target -Force -ErrorAction Stop
}
catch {
    Write-Host "复制产物失败: $($_.Exception.Message)" -ForegroundColor Red
    exit 2
}
Write-Host "已复制产物: $($artifact.Name) -> run\plugins\playerTaskX.jar" -ForegroundColor Green
Write-Host ("产物大小: {0:N2} MB" -f ($artifact.Length / 1MB))

# ---------------------------------------------------------------- 启动
$javaArgs = @(
    "-Xms$Memory", "-Xmx$Memory",
    "-Dfile.encoding=UTF-8",
    "-Dsun.stdout.encoding=UTF-8",
    "-Dsun.stderr.encoding=UTF-8",
    "-jar", $ServerJar,
    "nogui"
)

Write-Host "启动服务器: java $($javaArgs -join ' ')" -ForegroundColor Cyan
Write-Host "工作目录: $RunDir" -ForegroundColor DarkGray

Push-Location $RunDir
try {
    # java 会把环境警告写到 stderr（如「Advanced terminal features are not available」），
    # 而本脚本开头设了 $ErrorActionPreference = "Stop"，会把这条警告当成致命错误直接中止，
    # 表现为「服务器刚启动就退出」且日志里什么都没有。这里临时放宽，让 java 正常跑完。
    $ErrorActionPreference = "Continue"
    if ($Foreground) {
        # 前台运行：可以在本窗口直接敲服务端命令，Ctrl+C 停止
        & java @javaArgs
    }
    else {
        # 后台新开窗口运行：本脚本立即返回，方便继续开发
        Write-Host "已在独立窗口启动（加 -Foreground 可在本窗口前台运行）" -ForegroundColor Green
        Start-Process -FilePath "java" -ArgumentList $javaArgs -WorkingDirectory $RunDir | Out-Null
    }
} finally {
    Pop-Location
}
