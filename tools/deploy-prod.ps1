param(
    [string]$Server = 'ubuntu@124.223.36.154',
    [switch]$SkipBuild,
    [string]$SeedFile
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = Split-Path -Parent $PSScriptRoot
$serverDir = Join-Path $root 'server'
$webDir = Join-Path $root 'web'
$deployDir = Join-Path $root 'deploy'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$tmp = Join-Path $env:TEMP ('btry_deploy_' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp | Out-Null

function Read-KeyValues([string]$Path) {
    $map = @{}
    Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
            $i = $line.IndexOf('=')
            $map[$line.Substring(0, $i).Trim()] = $line.Substring($i + 1).Trim()
        }
    }
    return $map
}

function New-HexSecret([int]$ByteCount) {
    $bytes = New-Object byte[] $ByteCount
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return ([BitConverter]::ToString($bytes) -replace '-', '').ToLowerInvariant()
}

function Write-LfFile([string]$Path, [string]$Content) {
    [IO.File]::WriteAllText($Path, ($Content -replace "`r`n", "`n"), $utf8)
}

try {
    if (-not $SkipBuild) {
        $mvn = (Get-Command mvn.cmd -ErrorAction Stop).Source
        $npm = (Get-Command npm.cmd -ErrorAction Stop).Source
        Write-Host '==> Building backend jar'
        Push-Location $serverDir
        try { & $mvn -q -DskipTests package; if ($LASTEXITCODE -ne 0) { throw 'mvn package failed' } } finally { Pop-Location }
        Write-Host '==> Building frontend with base=/learn/'
        Push-Location $webDir
        try { & $npm run build -- --base=/learn/; if ($LASTEXITCODE -ne 0) { throw 'npm build failed' } } finally { Pop-Location }
    }

    $jar = Get-ChildItem (Join-Path $serverDir 'target') -Filter 'btry-*.jar' |
        Where-Object { $_.Name -notmatch 'original' } | Select-Object -First 1
    if (-not $jar) { throw 'backend jar not found, run without -SkipBuild' }
    $dist = Join-Path $webDir 'dist'
    if (-not (Test-Path (Join-Path $dist 'index.html'))) { throw 'frontend dist not found' }

    $local = Read-KeyValues (Join-Path $serverDir '.env')
    $cos = Read-KeyValues (Join-Path $env:USERPROFILE '.cos.conf')
    foreach ($key in @('ALIPAY_APP_ID', 'ALIPAY_PRIVATE_KEY', 'ALIPAY_PUBLIC_KEY', 'ALIPAY_GATEWAY_URL', 'ALIPAY_NOTIFY_URL', 'ALIPAY_RETURN_URL')) {
        if (-not $local[$key]) { throw "server/.env missing $key" }
    }
    foreach ($key in @('secret_id', 'secret_key', 'bucket', 'region')) {
        if (-not $cos[$key]) { throw "~/.cos.conf missing $key" }
    }

    if (-not $SeedFile) {
        $SeedFile = (Get-ChildItem (Join-Path $root 'backups') -Filter '*.sql' |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
    }

    $dbPassword = New-HexSecret 16
    $redisPassword = New-HexSecret 16
    $jwtSecret = New-HexSecret 32
    $dbUrl = 'jdbc:mysql://127.0.0.1:3306/btry_edu?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'

    $lines = @(
        'SERVER_PORT=8084'
        'MANAGEMENT_PORT=9091'
        "DB_URL=`"$dbUrl`""
        'DB_USER=btry'
        "DB_PASSWORD=$dbPassword"
        'REDIS_HOST=127.0.0.1'
        'REDIS_PORT=6379'
        "REDIS_PASSWORD=$redisPassword"
        "JWT_SECRET=$jwtSecret"
        'CORS_ALLOWED_ORIGINS=https://whereri.site'
        'TRUST_FORWARDED_HEADERS=true'
        "COS_BUCKET=$($cos['bucket'])"
        "COS_REGION=$($cos['region'])"
        "COS_SECRET_ID=$($cos['secret_id'])"
        "COS_SECRET_KEY=$($cos['secret_key'])"
        'SAMPLE_VIDEO_KEY=edu/video/91/demo-real-181s-faststart-cache.mp4'
        'APP_BASE_URL=https://whereri.site/learn'
        'PAYMENT_PROVIDER=alipay'
        'PAYMENT_MOCK_ENABLED=false'
        'PAYMENT_ORDER_EXPIRE_MINUTES=15'
        'PAYMENT_QUERY_AFTER_SECONDS=60'
        'PAYMENT_RECONCILE_BATCH_SIZE=50'
        'PAYMENT_RECONCILE_INTERVAL_MS=60000'
        'PAYMENT_RECONCILE_INITIAL_DELAY_MS=30000'
        "ALIPAY_APP_ID=$($local['ALIPAY_APP_ID'])"
        "ALIPAY_SELLER_ID=$($local['ALIPAY_SELLER_ID'])"
        "ALIPAY_PRIVATE_KEY=$($local['ALIPAY_PRIVATE_KEY'])"
        "ALIPAY_PUBLIC_KEY=$($local['ALIPAY_PUBLIC_KEY'])"
        "ALIPAY_GATEWAY_URL=$($local['ALIPAY_GATEWAY_URL'])"
        "ALIPAY_NOTIFY_URL=$($local['ALIPAY_NOTIFY_URL'])"
        "ALIPAY_RETURN_URL=$($local['ALIPAY_RETURN_URL'])"
        'LOG_FILE=/opt/btry/logs/btry-prod.log'
        "ALERT_WEBHOOK_URL=$($local['ALERT_WEBHOOK_URL'])"
    )
    [IO.File]::WriteAllText((Join-Path $tmp 'btry-prod.env'), (($lines -join "`n") + "`n"), $utf8)

    Copy-Item $jar.FullName (Join-Path $tmp 'btry.jar')
    Copy-Item $SeedFile (Join-Path $tmp 'btry-seed.sql')
    Write-LfFile (Join-Path $tmp 'btry.service') (Get-Content (Join-Path $deployDir 'btry.service') -Raw)
    Write-LfFile (Join-Path $tmp 'nginx-learn.conf') (Get-Content (Join-Path $deployDir 'nginx-learn.conf') -Raw)
    Write-LfFile (Join-Path $tmp 'setup-server.sh') (Get-Content (Join-Path $deployDir 'setup-server.sh') -Raw)
    Write-LfFile (Join-Path $tmp 'btry-backup.sh') (Get-Content (Join-Path $deployDir 'btry-backup.sh') -Raw)
    Write-LfFile (Join-Path $tmp 'btry-backup.service') (Get-Content (Join-Path $deployDir 'btry-backup.service') -Raw)
    Write-LfFile (Join-Path $tmp 'btry-backup.timer') (Get-Content (Join-Path $deployDir 'btry-backup.timer') -Raw)
    Write-LfFile (Join-Path $tmp 'btry-logrotate') (Get-Content (Join-Path $deployDir 'btry-logrotate') -Raw)

    Write-Host '==> Packing frontend dist'
    tar -czf (Join-Path $tmp 'btry-web.tar.gz') -C $dist .
    if ($LASTEXITCODE -ne 0) { throw 'tar failed' }

    Write-Host "==> Uploading artifacts to $Server"
    $files = @('btry-prod.env', 'btry.jar', 'btry-web.tar.gz', 'btry-seed.sql', 'btry.service', 'nginx-learn.conf', 'setup-server.sh', 'btry-backup.sh', 'btry-backup.service', 'btry-backup.timer', 'btry-logrotate') |
        ForEach-Object { Join-Path $tmp $_ }
    & scp -q @files "${Server}:/tmp/"
    if ($LASTEXITCODE -ne 0) { throw 'scp failed' }

    Write-Host '==> Running server setup (sudo)'
    $remote = 'sudo mkdir -p /opt/btry && sudo mv /tmp/btry-prod.env /opt/btry/.env && sudo chmod 600 /opt/btry/.env && sudo mv /tmp/btry.jar /opt/btry/btry.jar && sudo bash /tmp/setup-server.sh'
    & ssh $Server $remote
    if ($LASTEXITCODE -ne 0) { throw 'server setup failed' }

    Write-Host '==> Deployment finished. Check: https://whereri.site/learn/ and /api/health'
}
finally {
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
}
