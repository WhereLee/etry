param(
    [switch]$IncludeRedis,
    [int]$TimeoutSeconds = 20
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = Split-Path -Parent $PSScriptRoot
$webDir = Join-Path $root 'web'

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

function Wait-PortClosed {
    param([int]$Port, [int]$Timeout)

    $deadline = (Get-Date).AddSeconds($Timeout)
    while ((Get-Date) -lt $deadline) {
        if (-not (Test-TcpPort -Port $Port)) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Get-ListenerPids {
    param([int]$Port)

    return @(
        Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique
    )
}

$targets = @(
    [pscustomobject]@{ Name = 'Frontend'; Port = 5175; AllowedNames = @('node'); RequireRootHint = $true },
    [pscustomobject]@{ Name = 'Backend'; Port = 8084; AllowedNames = @('java', 'javaw'); RequireRootHint = $false }
)
if ($IncludeRedis) {
    $targets += [pscustomobject]@{ Name = 'Redis'; Port = 6379; AllowedNames = @('redis-server'); RequireRootHint = $false }
}

foreach ($target in $targets) {
    $pids = @(Get-ListenerPids -Port $target.Port)
    if ($pids.Count -eq 0) {
        Write-Host "==> $($target.Name) is not listening on $($target.Port); skip"
        continue
    }

    foreach ($processId in $pids) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction SilentlyContinue
        if (-not $process) { continue }

        $processName = [System.IO.Path]::GetFileNameWithoutExtension($process.Name)
        if ($target.AllowedNames -notcontains $processName) {
            throw "Port $($target.Port) is owned by $($process.Name) (PID $processId), not a recognized $($target.Name) process."
        }
        if ($target.RequireRootHint -and $process.CommandLine -notlike "*$webDir*") {
            throw "Port $($target.Port) is owned by PID $processId but its command line does not reference $webDir."
        }

        Write-Host "==> Stopping $($target.Name) PID $processId on port $($target.Port)"
        Stop-Process -Id $processId -Force
    }

    if (-not (Wait-PortClosed -Port $target.Port -Timeout $TimeoutSeconds)) {
        throw "Port $($target.Port) did not close within $TimeoutSeconds seconds."
    }
}

$statePath = Join-Path $root 'logs/start-local.state.json'
if (Test-Path -LiteralPath $statePath) {
    Remove-Item -LiteralPath $statePath -Force
}

Write-Host 'Local application processes stopped.'
