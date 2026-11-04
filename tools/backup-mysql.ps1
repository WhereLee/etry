param(
    [string]$Database,
    [string]$DbHost,
    [int]$Port = 3306,
    [string]$User,
    [string]$Password,
    [string]$BackupDir,
    [string]$MySqlDump
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if (-not $Database) { $Database = if ($env:DB_NAME) { $env:DB_NAME } else { 'btry_edu' } }
if (-not $DbHost) { $DbHost = if ($env:DB_HOST) { $env:DB_HOST } else { '127.0.0.1' } }
if (-not $User) { $User = if ($env:DB_USER) { $env:DB_USER } else { 'root' } }
if (-not $Password) { $Password = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { 'root' } }
if (-not $BackupDir) { $BackupDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'backups' }
if (-not $MySqlDump) {
    $command = Get-Command mysqldump -ErrorAction SilentlyContinue
    if (-not $command) {
        throw 'mysqldump not found. Pass -MySqlDump or add MySQL bin to PATH.'
    }
    $MySqlDump = $command.Source
}

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$target = Join-Path $BackupDir "$Database-$stamp.sql"
$errorFile = "$target.err"
$oldPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $Password

try {
    $arguments = @(
        "--host=$DbHost",
        "--port=$Port",
        "--user=$User",
        '--single-transaction',
        '--routines',
        '--triggers',
        '--events',
        '--set-gtid-purged=OFF',
        $Database
    )
    $process = Start-Process -FilePath $MySqlDump -ArgumentList $arguments `
        -NoNewWindow -Wait -PassThru -RedirectStandardOutput $target -RedirectStandardError $errorFile
    if ($process.ExitCode -ne 0) {
        $detail = Get-Content $errorFile -Raw -ErrorAction SilentlyContinue
        throw "mysqldump failed with exit code $($process.ExitCode): $detail"
    }
    Remove-Item -LiteralPath $errorFile -Force -ErrorAction SilentlyContinue
    Write-Host "Backup created: $target"
} finally {
    if ($null -eq $oldPassword) {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
        $env:MYSQL_PWD = $oldPassword
    }
}
