$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"
$bin = "c:\Users\lrs\Desktop\work\etry\tools\big-upload.bin"

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
        Write-Host ("  PASS  {0,-40} {1}" -f $label, $actual)
    } else {
        $script:fail++
        Write-Host ("  FAIL  {0,-40} got={1} want={2}" -f $label, $actual, $expected)
    }
}

# ---------- 1. login + me ----------
Write-Host "== 1. login (JWT_SECRET injected, service started) =="
$ok = Send-Api "Post" "/api/auth/login" @{ username = "admin"; password = "admin123" } $null
Check "admin login" $ok.code 0
$token = $ok.data.token
$me = Send-Api "Get" "/api/auth/me" $null $token
$adminId = $me.data.id
Check "me roles contains ADMIN" (@($me.data.roles) -contains "ADMIN") $true

# ---------- 2. pagination ----------
Write-Host ""
Write-Host "== 2. pagination bounds =="
$huge = Send-Api "Get" "/api/admin/users?page=99999999&size=10" $null $token
Check "page=99999999 code" $huge.code 0
Check "page=99999999 empty" (@($huge.data.records).Count) 0

$beyond = Send-Api "Get" "/api/admin/users?page=99&size=10" $null $token
Check "page=99 (past last) code" $beyond.code 0
Check "page=99 empty" (@($beyond.data.records).Count) 0

# ---------- 3. exception handlers ----------
Write-Host ""
Write-Host "== 3. exception handlers =="
$typeErr = Send-Api "Get" "/api/admin/users?page=abc" $null $token
Check "page=abc -> INVALID_PARAM" $typeErr.code 40001

$badJson = curl.exe -s -X POST -H "Content-Type: application/json" --data-binary '{"username":"a",}' "$base/api/auth/login"
Check "bad json -> INVALID_PARAM" ($badJson | ConvertFrom-Json).code 40001

if (-not (Test-Path $bin)) { fsutil file createnew $bin 3145728 | Out-Null }
$up = curl.exe -s -X POST -H "Authorization: Bearer $token" -F "file=@$bin" "$base/api/user/avatar"
Check "3MB upload -> INVALID_PARAM" ($up | ConvertFrom-Json).code 40001

# ---------- 4. last admin protection ----------
Write-Host ""
Write-Host "== 4. last admin protection =="
$roles = Send-Api "Get" "/api/admin/roles" $null $token
$studentId = ($roles.data | Where-Object { $_.code -eq "STUDENT" }).id
$adminRoleId = ($roles.data | Where-Object { $_.code -eq "ADMIN" }).id
$dropAdmin = Send-Api "Put" "/api/admin/users/$adminId/roles" @{ roleIds = @($studentId) } $token
Check "remove own ADMIN -> LAST_ADMIN" $dropAdmin.code 40012

$perms = Send-Api "Get" "/api/admin/permissions" $null $token
$keep = @($perms.data | Where-Object { $_.code -ne "role:manage" } | ForEach-Object { $_.id })
$dropManage = Send-Api "Put" "/api/admin/roles/$adminRoleId/permissions" @{ permissionIds = $keep } $token
Check "drop role:manage -> INVALID_PARAM" $dropManage.code 40001

# ---------- 5. register switch ----------
Write-Host ""
Write-Host "== 5. register.enabled switch =="
$suffix = Get-Date -Format "HHmmss"
$newUser = "z$suffix"
$close = Send-Api "Put" "/api/admin/configs/register.enabled" @{ value = "false" } $token
Check "close register" $close.code 0
$regClosed = Send-Api "Post" "/api/auth/register" @{ username = $newUser; password = "pass123456"; nickname = "N" } $null
Check "register while closed -> 40105" $regClosed.code 40105
$open = Send-Api "Put" "/api/admin/configs/register.enabled" @{ value = "true" } $token
Check "open register" $open.code 0
$regOk = Send-Api "Post" "/api/auth/register" @{ username = $newUser; password = "pass123456"; nickname = "N" } $null
Check "register while open" $regOk.code 0

# ---------- 6. audit ----------
Write-Host ""
Write-Host "== 6. audit trail =="
$wrong = Send-Api "Post" "/api/auth/login" @{ username = "admin"; password = "definitely-wrong" } $null
Check "wrong password -> LOGIN_FAILED" $wrong.code 40102

$logs = Send-Api "Get" "/api/admin/audit-logs?page=1&size=50" $null $token
$loginLogs = @($logs.data.records | Where-Object { $_.action -eq "USER_LOGIN" })
Check "USER_LOGIN recorded" ($loginLogs.Count -gt 0) $true
$failedLogs = @($loginLogs | Where-Object { $_.success -eq $false })
Check "failed login recorded" ($failedLogs.Count -gt 0) $true

$sample = $loginLogs[0].detail
Write-Host ("  detail sample: " + $sample)
Check "detail has value not class name" ($sample -match '"username"') $true
Check "password masked" ($sample -match '\*\*\*') $true
Check "password not leaked" ($sample -notmatch 'admin123|definitely-wrong') $true

$pwLogs = @($logs.data.records | Where-Object { $_.action -eq "PASSWORD_CHANGE" })
Write-Host ("  PASSWORD_CHANGE rows: " + $pwLogs.Count + " (only if password was changed)")

# reset login failure counter with one good login
$null = Send-Api "Post" "/api/auth/login" @{ username = "admin"; password = "admin123" } $null

# ---------- summary ----------
Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)
if (Test-Path $bin) { Remove-Item $bin -Force }
