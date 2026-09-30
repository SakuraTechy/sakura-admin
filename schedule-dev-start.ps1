param(
    [int] $Port = 8001,
    [switch] $SkipPackage
)

$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($root)) {
    $root = (Get-Location).Path
}

$module = "continew-extension/continew-extension-schedule-server"
$jarPath = Join-Path $root "continew-extension\continew-extension-schedule-server\target\continew-extension-schedule-server.jar"
$logPath = Join-Path $root "logs\schedule-server"

if (-not $SkipPackage) {
    & mvn -f "$root\pom.xml" -pl $module -am package -DskipTests "-Dspotless.apply.skip=true"
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

if (-not (Test-Path -LiteralPath $jarPath)) {
    Write-Error "Missing schedule server JAR. Run without -SkipPackage once: $jarPath"
}

New-Item -ItemType Directory -Force -Path $logPath | Out-Null

Write-Host "Starting Sakura schedule server: http://localhost:$Port"
Write-Host "Log file: $logPath\continew-admin-schedule-server.log"

& java -jar $jarPath `
    "--spring.profiles.active=dev" `
    "--server.port=$Port" `
    "--logging.file.path=$logPath"
exit $LASTEXITCODE
