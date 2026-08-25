[CmdletBinding()]
param(
    [switch]$SkipPackage,

    [string]$PlaywrightSource = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
$sourceRoot = Join-Path $root 'continew-webapi\target\app'
$destinationRoot = Join-Path $root 'docker\continew-admin'
$scheduleSource = Join-Path $root 'continew-extension\continew-extension-schedule-server\target\continew-extension-schedule-server.jar'
$scheduleDestinationRoot = Join-Path $root 'docker\schedule-server'
$playwrightDestinationRoot = Join-Path $root 'docker\sakura-playwright'

if ([string]::IsNullOrWhiteSpace($PlaywrightSource)) {
    $PlaywrightSource = Join-Path $root '..\sakura-playwright'
}
$playwrightSourceRoot = [IO.Path]::GetFullPath($PlaywrightSource)

if (-not $SkipPackage) {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        throw '未找到 Maven 命令 mvn，请先配置 Maven 环境变量。'
    }

    Write-Host '开始执行 mvn clean package...'
    Push-Location $root
    try {
        & mvn -f (Join-Path $root 'pom.xml') clean package
        if ($LASTEXITCODE -ne 0) {
            throw "Maven 打包失败，退出码：$LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}
else {
    Write-Host '跳过 Maven 打包，直接复制已有 target\app 产物。'
}

if (-not (Test-Path -LiteralPath $sourceRoot -PathType Container)) {
    throw "未找到构建产物目录：$sourceRoot"
}

if (-not (Test-Path -LiteralPath $scheduleSource -PathType Leaf)) {
    throw "未找到调度服务 JAR：$scheduleSource"
}

if (-not (Test-Path -LiteralPath $playwrightSourceRoot -PathType Container)) {
    throw "未找到 sakura-playwright 源码目录：$playwrightSourceRoot。可通过 -PlaywrightSource 指定目录。"
}

foreach ($requiredFile in @('package.json', 'package-lock.json', 'src\index.js')) {
    if (-not (Test-Path -LiteralPath (Join-Path $playwrightSourceRoot $requiredFile) -PathType Leaf)) {
        throw "sakura-playwright 缺少必要文件：$(Join-Path $playwrightSourceRoot $requiredFile)"
    }
}

New-Item -ItemType Directory -Path $destinationRoot -Force | Out-Null

foreach ($item in Get-ChildItem -LiteralPath $sourceRoot -Force) {
    $destinationPath = Join-Path $destinationRoot $item.Name

    # 只清理 target\app 中同名的部署产物，保留 Dockerfile 和前端 html 目录。
    if (Test-Path -LiteralPath $destinationPath) {
        Remove-Item -LiteralPath $destinationPath -Recurse -Force
    }

    Copy-Item -LiteralPath $item.FullName -Destination $destinationRoot -Recurse -Force
}

Write-Host "部署文件已复制：$sourceRoot -> $destinationRoot"
Write-Host 'Dockerfile 和 html 目录未被覆盖。'

New-Item -ItemType Directory -Path $scheduleDestinationRoot -Force | Out-Null
Copy-Item -LiteralPath $scheduleSource -Destination $scheduleDestinationRoot -Force
Write-Host "调度服务 JAR 已复制：$scheduleSource -> $scheduleDestinationRoot"

New-Item -ItemType Directory -Path $playwrightDestinationRoot -Force | Out-Null
$playwrightCopyArgs = @(
    $playwrightSourceRoot,
    $playwrightDestinationRoot,
    '/E',
    '/COPY:DAT',
    '/DCOPY:DAT',
    '/R:2',
    '/W:1',
    '/XD',
    (Join-Path $playwrightSourceRoot '.git'),
    (Join-Path $playwrightSourceRoot '.agents'),
    (Join-Path $playwrightSourceRoot 'node_modules'),
    (Join-Path $playwrightSourceRoot 'artifacts'),
    (Join-Path $playwrightSourceRoot 'data'),
    (Join-Path $playwrightSourceRoot 'logs'),
    '/XF',
    '.env',
    '/NFL',
    '/NDL',
    '/NJH',
    '/NJS',
    '/NP'
)
& robocopy @playwrightCopyArgs
$robocopyExitCode = $LASTEXITCODE
if ($robocopyExitCode -ge 8) {
    throw "sakura-playwright 复制失败，Robocopy 退出码：$robocopyExitCode"
}
Write-Host "sakura-playwright 已复制：$playwrightSourceRoot -> $playwrightDestinationRoot"
