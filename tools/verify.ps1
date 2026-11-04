param(
    [switch]$SkipNpmInstall
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = Split-Path -Parent $PSScriptRoot

Write-Host '==> Backend tests'
Push-Location (Join-Path $root 'server')
try {
    & mvn.cmd -q test
    if ($LASTEXITCODE -ne 0) {
        throw "Backend tests failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

Write-Host '==> Frontend build'
Push-Location (Join-Path $root 'web')
try {
    if (-not $SkipNpmInstall) {
        & npm.cmd ci
        if ($LASTEXITCODE -ne 0) {
            throw "npm ci failed with exit code $LASTEXITCODE"
        }
    }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

Write-Host 'Verification passed.'
