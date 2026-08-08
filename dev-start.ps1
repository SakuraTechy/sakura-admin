param(
    [int] $Port = 8000,
    [switch] $SkipPackage
)

$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($root)) {
    $root = (Get-Location).Path
}

if (-not $SkipPackage) {
    & mvn -f "$root\pom.xml" -pl continew-webapi -am package -DskipTests "-Dspotless.apply.skip=true" "-Ddevtools=true"
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

$moduleClassPaths = @(
    "$root\continew-webapi\target\classes",
    "$root\continew-common\target\classes",
    "$root\continew-project\target\classes",
    "$root\continew-module-system\target\classes",
    "$root\continew-automation\target\classes",
    "$root\continew-test\target\classes",
    "$root\continew-plugin\continew-plugin-schedule\target\classes",
    "$root\continew-plugin\continew-plugin-open\target\classes",
    "$root\continew-plugin\continew-plugin-generator\target\classes"
)

$missingClassPaths = @($moduleClassPaths | Where-Object { -not (Test-Path -LiteralPath $_) })
if ($missingClassPaths.Count -gt 0) {
    Write-Error ("Missing compiled classes. Run without -SkipPackage once:`n" + ($missingClassPaths -join "`n"))
}

$libPath = "$root\continew-webapi\target\app\lib"
if (-not (Test-Path -LiteralPath $libPath)) {
    Write-Error "Missing dependency lib directory. Run without -SkipPackage once: $libPath"
}

$dependencyJars = @(Get-ChildItem -LiteralPath $libPath -Filter "*.jar" |
    Where-Object { $_.Name -notlike "continew-*-3.6.0-SNAPSHOT.jar" } |
    Sort-Object -Property FullName |
    ForEach-Object { $_.FullName })

$devtoolsJar = $dependencyJars | Where-Object { $_ -like "*spring-boot-devtools-*.jar" } | Select-Object -First 1
if (-not $devtoolsJar) {
    Write-Warning "spring-boot-devtools was not found. Run without -SkipPackage once to enable restart support."
}

$classPath = ($moduleClassPaths + $dependencyJars) -join ";"
$argFile = "$root\continew-webapi\target\dev-run.argfile"
$logPath = "$root\logs"

# Use a Java @argfile to avoid the Windows command-line length limit.
@(
    "-cp",
    $classPath,
    "top.continew.admin.ContiNewAdminApplication",
    "--server.port=$Port",
    "--logging.file.path=$logPath"
) | Set-Content -LiteralPath $argFile -Encoding ASCII

Write-Host "Starting Sakura Admin dev mode: http://localhost:$Port/doc.html"
Write-Host "Log file: $logPath\sakura-admin.log"
Write-Host "For live restart, enable IDE auto-build or run: mvn -pl continew-webapi -am compile -DskipTests '-Dspotless.apply.skip=true'"

& java "@$argFile"
exit $LASTEXITCODE
