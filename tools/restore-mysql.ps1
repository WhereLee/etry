param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [string]$Database,
    [string]$DbHost,
    [int]$Port = 3306,
    [string]$User,
    [string]$Password,
    [string]$MySql,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if (-not $Force) {
    throw 'Restore overwrites database objects. Re-run with -Force after verifying the backup file.'
}
if (-not (Test-Path -LiteralPath $BackupFile)) {
    throw "Backup file not found: $BackupFile"
}

if (-not $Database) { $Database = if ($env:DB_NAME) { $env:DB_NAME } else { 'btry_edu' } }
if (-not $DbHost) { $DbHost = if ($env:DB_HOST) { $env:DB_HOST } else { '127.0.0.1' } }
if (-not $User) { $User = if ($env:DB_USER) { $env:DB_USER } else { 'root' } }
if (-not $Password) { $Password = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { 'root' } }
if (-not $MySql) {
    $command = Get-Command mysql -ErrorAction SilentlyContinue
    if (-not $command) {
        throw 'mysql client not found. Pass -MySql or add MySQL bin to PATH.'
    }
    $MySql = $command.Source
}

$oldPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $Password

try {
    $arguments = @("--host=$DbHost", "--port=$Port", "--user=$User", $Database)
    $process = Start-Process -FilePath $MySql -ArgumentList $arguments `
        -NoNewWindow -Wait -PassThru -RedirectStandardInput (Resolve-Path -LiteralPath $BackupFile)
    if ($process.ExitCode -ne 0) {
        throw "mysql restore failed with exit code $($process.ExitCode)"
    }
    Write-Host "Restore completed: $BackupFile -> $Database"
} finally {
    if ($null -eq $oldPassword) {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
        $env:MYSQL_PWD = $oldPassword
    }
}
