param(
    [string]$RedisHome,
    [switch]$SkipRedis,
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = Split-Path -Parent $PSScriptRoot
$serverDir = Join-Path $root 'server'
$webDir = Join-Path $root 'web'
$logDir = Join-Path $root 'logs'
$cmdExe = if ($env:ComSpec) { $env:ComSpec } else { Join-Path $env:SystemRoot 'System32\cmd.exe' }

# Load server/.env (git-ignored) into this process; child processes inherit it.
$envFile = Join-Path $serverDir '.env'
if (Test-Path -LiteralPath $envFile) {
    Get-Content -LiteralPath $envFile -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
            $index = $line.IndexOf('=')
            $name = $line.Substring(0, $index).Trim()
            $value = $line.Substring($index + 1).Trim()
            if ($name) { Set-Item -Path "Env:$name" -Value $value }
        }
    }
    Write-Host "==> Loaded environment from $envFile"
}

if (-not $RedisHome) {
    $RedisHome = if ($env:REDIS_HOME) { $env:REDIS_HOME } else { 'F:\Redis' }
}

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Test-TcpPort {
    param([int]$Port)

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync('127.0.0.1', $Port)
        if (-not $task.Wait(750)) { return $false }
        return $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Wait-TcpPort {
    param([int]$Port, [int]$Timeout)

    $deadline = (Get-Date).AddSeconds($Timeout)
    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPort -Port $Port) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Wait-Health {
    param([int]$Timeout)

    $deadline = (Get-Date).AddSeconds($Timeout)
    while ((Get-Date) -lt $deadline) {
        try {
            $health = Invoke-RestMethod -Uri 'http://127.0.0.1:9091/actuator/health' -TimeoutSec 2
            if ($health.status -eq 'UP') { return $true }
        } catch {
            # The backend may still be starting or Flyway may still be migrating.
        }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Start-LoggedCommand {
    param(
        [string]$Name,
        [string]$Command,
        [string]$WorkingDirectory
    )

    $stdout = Join-Path $logDir "$Name.out.log"
    $stderr = Join-Path $logDir "$Name.err.log"
    return Start-Process -FilePath $cmdExe `
        -ArgumentList @('/d', '/c', $Command) `
        -WorkingDirectory $WorkingDirectory `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -PassThru
}

$started = [ordered]@{}
$redisExe = Join-Path $RedisHome 'redis-server.exe'
$redisConfig = Join-Path $RedisHome 'redis.windows.conf'

if (-not (Test-TcpPort -Port 6379)) {
    if ($SkipRedis) {
        throw 'Redis is not listening on 127.0.0.1:6379 and -SkipRedis was requested.'
    }
    if (-not (Test-Path -LiteralPath $redisExe)) {
        throw "Redis executable not found: $redisExe. Set REDIS_HOME or pass -RedisHome."
    }
    if (-not (Test-Path -LiteralPath $redisConfig)) {
        throw "Redis config not found: $redisConfig."
    }

    Write-Host "==> Starting Redis from $RedisHome"
    $redisProcess = Start-Process -FilePath $redisExe `
        -ArgumentList @("`"$redisConfig`"") `
        -WorkingDirectory $RedisHome `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logDir 'redis.out.log') `
        -RedirectStandardError (Join-Path $logDir 'redis.err.log') `
        -PassThru
    $started.RedisPid = $redisProcess.Id
    if (-not (Wait-TcpPort -Port 6379 -Timeout 20)) {
        throw 'Redis did not start within 20 seconds. See logs/redis.err.log.'
    }
} else {
    Write-Host '==> Redis already listening on 6379; skip start'
}

if (-not (Test-TcpPort -Port 8084)) {
    $mvn = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (-not $mvn) { throw 'mvn.cmd not found in PATH.' }

    Write-Host '==> Starting backend on 8084'
    $backendCommand = '"{0}" -q spring-boot:run' -f $mvn.Source
    $backendProcess = Start-LoggedCommand -Name 'backend' -Command $backendCommand -WorkingDirectory $serverDir
    $started.BackendWrapperPid = $backendProcess.Id
} else {
    Write-Host '==> Backend already listening on 8084; skip start'
}

if (-not (Test-TcpPort -Port 5175)) {
    $npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if (-not $npm) { throw 'npm.cmd not found in PATH.' }

    if (-not (Test-Path -LiteralPath (Join-Path $webDir 'node_modules'))) {
        Write-Host '==> Installing frontend dependencies with npm ci'
        Push-Location $webDir
        try {
            & $npm.Source ci
            if ($LASTEXITCODE -ne 0) { throw "npm ci failed with exit code $LASTEXITCODE" }
        } finally {
            Pop-Location
        }
    }

    Write-Host '==> Starting frontend on 5175'
    $frontendCommand = '"{0}" run dev' -f $npm.Source
    $frontendProcess = Start-LoggedCommand -Name 'frontend' -Command $frontendCommand -WorkingDirectory $webDir
    $started.FrontendWrapperPid = $frontendProcess.Id
} else {
    Write-Host '==> Frontend already listening on 5175; skip start'
}

if (-not (Wait-TcpPort -Port 8084 -Timeout $TimeoutSeconds)) {
    throw "Backend did not listen on 8084 within $TimeoutSeconds seconds. See logs/backend.err.log."
}
if (-not (Wait-TcpPort -Port 5175 -Timeout $TimeoutSeconds)) {
    throw "Frontend did not listen on 5175 within $TimeoutSeconds seconds. See logs/frontend.err.log."
}
if (-not (Wait-Health -Timeout $TimeoutSeconds)) {
    throw "Actuator health did not report UP within $TimeoutSeconds seconds. See logs/backend.out.log and logs/backend.err.log."
}

$state = [ordered]@{
    StartedAt = (Get-Date).ToString('o')
    BackendPort = 8084
    FrontendPort = 5175
    ManagementPort = 9091
    Health = 'UP'
    StartedProcesses = $started
}
$statePath = Join-Path $logDir 'start-local.state.json'
$state | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $statePath -Encoding utf8

Write-Host ''
Write-Host 'Local environment is ready.'
Write-Host '  Frontend: http://127.0.0.1:5175'
Write-Host '  Backend:  http://127.0.0.1:8084'
Write-Host '  Health:   http://127.0.0.1:9091/actuator/health'
Write-Host "  State:    $statePath"
