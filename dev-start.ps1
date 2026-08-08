param(
    [int] $Port = 8000,
    [switch] $SkipPackage,
    [switch] $WithSchedule,
    [int] $ScheduleHttpPort = 8001,
    [int] $ScheduleNettyPort = 1788
)

$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($root)) {
    $root = (Get-Location).Path
}

$javaCommand = Get-Command "java" -ErrorAction Stop
$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$javaSettings = (& $javaCommand.Source -XshowSettings:properties -version 2>&1 | Out-String)
$ErrorActionPreference = $previousErrorActionPreference
if ($javaSettings -notmatch '(?m)^\s*java\.version\s*=\s*(\S+)') {
    throw "Unable to determine the Java version from $($javaCommand.Source)"
}
$javaVersion = $Matches[1]
$javaMajorVersion = if ($javaVersion.StartsWith("1.")) {
    [int] $javaVersion.Split('.')[1]
} else {
    [int] $javaVersion.Split('.')[0]
}
if ($javaMajorVersion -lt 17) {
    throw "JDK 17 or later is required. Current Java version: $javaVersion"
}
if ($javaSettings -notmatch '(?m)^\s*java\.home\s*=\s*(.+?)\s*$') {
    throw "Unable to determine java.home from $($javaCommand.Source)"
}
$javaHome = $Matches[1].Trim()
$javaExecutable = Join-Path $javaHome "bin\java.exe"
if (-not (Test-Path -LiteralPath $javaExecutable)) {
    throw "Java executable not found: $javaExecutable"
}
$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"

if (-not $SkipPackage) {
    $buildProjects = if ($WithSchedule) {
        "continew-webapi,continew-extension/continew-extension-schedule-server"
    } else {
        "continew-webapi"
    }
    & mvn -f "$root\pom.xml" "-pl" $buildProjects "-am" "package" "-DskipTests" "-Dspotless.apply.skip=true" "-Ddevtools=true"
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
if ($WithSchedule) {
    # SnailJob 1.4.0 keeps static client threads that cannot survive a DevTools context restart.
    $dependencyJars = @($dependencyJars | Where-Object { $_ -notlike "*spring-boot-devtools-*.jar" })
} elseif (-not $devtoolsJar) {
    Write-Warning "spring-boot-devtools was not found. Run without -SkipPackage once to enable restart support."
}

$classPath = ($moduleClassPaths + $dependencyJars) -join ";"
$argFile = "$root\continew-webapi\target\dev-run.argfile"
$scheduleProcess = $null

function Test-TcpPort {
    param([string] $HostName, [int] $TargetPort)
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connection = $client.ConnectAsync($HostName, $TargetPort)
        return $connection.Wait(500) -and $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Wait-TcpPort {
    param([string] $HostName, [int] $TargetPort, [int] $TimeoutSeconds = 45)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-TcpPort -HostName $HostName -TargetPort $TargetPort) {
            return
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for ${HostName}:$TargetPort"
}

if ($WithSchedule) {
    if ([string]::IsNullOrWhiteSpace($env:SCHEDULE_ENABLED)) {
        $env:SCHEDULE_ENABLED = "true"
    }
    if ([string]::IsNullOrWhiteSpace($env:SCHEDULE_HOST)) {
        $env:SCHEDULE_HOST = "127.0.0.1"
    }
    if ([string]::IsNullOrWhiteSpace($env:SCHEDULE_PORT)) {
        $env:SCHEDULE_PORT = [string] $ScheduleNettyPort
    }
    if ([string]::IsNullOrWhiteSpace($env:SCHEDULE_API_URL)) {
        $env:SCHEDULE_API_URL = "http://127.0.0.1:$ScheduleHttpPort/snail-job"
    }

    if (-not (Test-TcpPort -HostName "127.0.0.1" -TargetPort $ScheduleHttpPort)) {
        $scheduleJar = "$root\continew-extension\continew-extension-schedule-server\target\continew-extension-schedule-server.jar"
        if (-not (Test-Path -LiteralPath $scheduleJar)) {
            Write-Error "Missing schedule server jar. Run without -SkipPackage once: $scheduleJar"
        }
        $scheduleArgFile = "$root\continew-extension\continew-extension-schedule-server\target\dev-run.argfile"
        @(
            "-jar",
            $scheduleJar,
            "--server.port=$ScheduleHttpPort",
            "--snail-job.netty-port=$ScheduleNettyPort"
        ) | Set-Content -LiteralPath $scheduleArgFile -Encoding ASCII
        $scheduleProcess = Start-Process -FilePath $javaExecutable `
            -ArgumentList "@$scheduleArgFile" `
            -WindowStyle Hidden `
            -RedirectStandardOutput "$root\continew-extension\continew-extension-schedule-server\target\dev-stdout.log" `
            -RedirectStandardError "$root\continew-extension\continew-extension-schedule-server\target\dev-stderr.log" `
            -PassThru
        Wait-TcpPort -HostName "127.0.0.1" -TargetPort $ScheduleHttpPort
        Wait-TcpPort -HostName "127.0.0.1" -TargetPort $ScheduleNettyPort
        Write-Host "Schedule server started: http://localhost:$ScheduleHttpPort/snail-job"
    } else {
        Wait-TcpPort -HostName "127.0.0.1" -TargetPort $ScheduleNettyPort
        Write-Host "Using the existing schedule server on port $ScheduleHttpPort"
    }
}
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
if ($WithSchedule) {
    Write-Host "Live restart is disabled because SnailJob client state is not DevTools-restart safe."
} else {
    Write-Host "For live restart, enable IDE auto-build or run: mvn -pl continew-webapi -am compile -DskipTests '-Dspotless.apply.skip=true'"
}
Write-Host "Log file: $logPath\sakura-admin.log"
Write-Host "Press Ctrl+C to stop"

try {
    & $javaExecutable "@$argFile"
    exit $LASTEXITCODE
} finally {
    if ($scheduleProcess -and -not $scheduleProcess.HasExited) {
        Stop-Process -Id $scheduleProcess.Id
    }
}
