$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"
$dir = "c:\Users\lrs\Desktop\work\etry\tools"
$fakePng = Join-Path $dir "fake-image.png"
$realPng = Join-Path $dir "real-avatar.png"
$bigJson = Join-Path $dir "big-body.json"

function Send-Api($method, $path, $obj, $token) {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    if ($obj -ne $null) {
        $json = $obj | ConvertTo-Json -Depth 6 -Compress
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
        return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers $headers `
            -ContentType "application/json; charset=utf-8" -Body $bytes
    }
    return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers $headers
}

$script:pass = 0
$script:fail = 0
function Check($label, $actual, $expected) {
    if ($actual -eq $expected) {
        $script:pass++
        Write-Host ("  PASS  {0,-44} {1}" -f $label, $actual)
    } else {
        $script:fail++
        Write-Host ("  FAIL  {0,-44} got={1} want={2}" -f $label, $actual, $expected)
    }
}

# ---------- 1. security headers ----------
Write-Host "== 1. security headers =="
$heads = curl.exe -s -I "$base/api/health" 2>&1 | Out-String
Check "X-Content-Type-Options" ($heads -match "X-Content-Type-Options: nosniff") $true
Check "X-Frame-Options" ($heads -match "X-Frame-Options: DENY") $true
Check "Referrer-Policy" ($heads -match "Referrer-Policy: no-referrer") $true
Check "CSP on /api" ($heads -match "Content-Security-Policy: default-src 'none'") $true

$fileHeads = curl.exe -s -I "$base/files/none.png" 2>&1 | Out-String
Check "no CSP on /files" ($fileHeads -notmatch "Content-Security-Policy") $true

# ---------- 2. upload magic number ----------
Write-Host ""
Write-Host "== 2. upload content sniffing =="
$admin = Send-Api "Post" "/api/auth/login" @{ username = "admin"; password = "admin123" } $null
$token = $admin.data.token

Set-Content -Path $fakePng -Value "this is plain text pretending to be a png image, long enough" -Encoding ascii
$fakeResp = curl.exe -s -X POST -H "Authorization: Bearer $token" -F "file=@$fakePng;type=image/png" "$base/api/user/avatar"
$fakeCode = ($fakeResp | ConvertFrom-Json).code
Check "text pretending to be png -> rejected" $fakeCode 40001

$b64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
[IO.File]::WriteAllBytes($realPng, [Convert]::FromBase64String($b64))
$realResp = curl.exe -s -X POST -H "Authorization: Bearer $token" -F "file=@$realPng;type=image/png" "$base/api/user/avatar"
$realCode = ($realResp | ConvertFrom-Json).code
Check "real png -> accepted" $realCode 0

# ---------- 3. audit ip cannot be spoofed ----------
Write-Host ""
Write-Host "== 3. audit ip spoofing =="
$null = curl.exe -s -X POST -H "X-Forwarded-For: 6.6.6.6" -H "Content-Type: application/json" `
    --data-binary '{"username":"admin","password":"admin123"}' "$base/api/auth/login"

$logs = Send-Api "Get" "/api/admin/audit-logs?action=USER_LOGIN&page=1&size=5" $null $token
$latest = @($logs.data.records)[0]
Write-Host ("  latest USER_LOGIN ip = " + $latest.ip)
Check "forged XFF ignored" ($latest.ip -ne "6.6.6.6") $true
Check "ip is real remote addr" $latest.ip "127.0.0.1"

# ---------- 4. LIKE wildcard ----------
Write-Host ""
Write-Host "== 4. LIKE wildcard escaping =="
$pct = Send-Api "Get" "/api/admin/users?keyword=%25&page=1&size=10" $null $token
Check "keyword=% matches nothing" $pct.data.total 0

$under = Send-Api "Get" "/api/admin/users?keyword=_&page=1&size=10" $null $token
Check "keyword=_ matches nothing" $under.data.total 0

$normal = Send-Api "Get" "/api/admin/users?keyword=admin&page=1&size=10" $null $token
Check "keyword=admin still works" ($normal.data.total -gt 0) $true

# ---------- 5. oversized json ----------
Write-Host ""
Write-Host "== 5. oversized json body =="
$payload = '{"username":"' + ("a" * 1200000) + '","password":"x"}'
[IO.File]::WriteAllText($bigJson, $payload, [System.Text.Encoding]::UTF8)
$bigResp = curl.exe -s -X POST -H "Content-Type: application/json" --data-binary "@$bigJson" "$base/api/auth/login"
$bigObj = $bigResp | ConvertFrom-Json
Check "1.2MB json rejected" $bigObj.code 40001
# expected message decoded from base64 so this script stays pure ASCII (PS 5.1 reads no-BOM utf8 as ANSI)
$expectMsg = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String("6K+35rGC5L2T6L+H5aSn"))
Check "rejection reason" $bigObj.message $expectMsg

Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)
Remove-Item $fakePng, $realPng, $bigJson -Force -ErrorAction SilentlyContinue
