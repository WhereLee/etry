$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"
$dir = "c:\Users\lrs\Desktop\work\etry\tools"
$tmpFile = Join-Path $dir "chunked-body.json"

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
        Write-Host ("  PASS  {0,-46} {1}" -f $label, $actual)
    } else {
        $script:fail++
        Write-Host ("  FAIL  {0,-46} got={1} want={2}" -f $label, $actual, $expected)
    }
}

$sfx = Get-Date -Format "HHmmss"
$admin = Send-Api "Post" "/api/auth/login" @{ username = "admin"; password = "admin123" } $null
$adminToken = $admin.data.token
$adminId = (Send-Api "Get" "/api/auth/me" $null $adminToken).data.id

# ---------- 10. admin reset password / kick / disable-bumps-token ----------
Write-Host "== 10. admin ops =="
$uName = "r$sfx"
$null = Send-Api "Post" "/api/auth/register" @{ username = $uName; password = "pass123456"; nickname = "N" } $null
$uLogin = Send-Api "Post" "/api/auth/login" @{ username = $uName; password = "pass123456" } $null
$uToken = $uLogin.data.token
$uId = $uLogin.data.userId

$reset = Send-Api "Put" "/api/admin/users/$uId/password" @{ newPassword = "newpass789" } $adminToken
Check "admin reset password" $reset.code 0
$staleAfterReset = Send-Api "Get" "/api/auth/me" $null $uToken
Check "old token dead after reset" $staleAfterReset.code 40101
$relogin = Send-Api "Post" "/api/auth/login" @{ username = $uName; password = "newpass789" } $null
Check "new password works" $relogin.code 0
$uToken2 = $relogin.data.token

$kick = Send-Api "Put" "/api/admin/users/$uId/kick" $null $adminToken
Check "admin kick" $kick.code 0
$staleAfterKick = Send-Api "Get" "/api/auth/me" $null $uToken2
Check "token dead after kick" $staleAfterKick.code 40101

$uToken3 = (Send-Api "Post" "/api/auth/login" @{ username = $uName; password = "newpass789" } $null).data.token
$null = Send-Api "Put" "/api/admin/users/$uId/status" @{ status = "DISABLED" } $adminToken
$null = Send-Api "Put" "/api/admin/users/$uId/status" @{ status = "ACTIVE" } $adminToken
$staleAfterReenable = Send-Api "Get" "/api/auth/me" $null $uToken3
Check "token dead after disable+enable" $staleAfterReenable.code 40101

# ---------- 11. admin notice detail ----------
Write-Host ""
Write-Host "== 11. admin notice detail =="
$draft = Send-Api "Post" "/api/admin/notices" @{
    title = "Draft-$sfx"; content = "draft body text"; status = "DRAFT"; isTop = $false
} $adminToken
Check "create draft" $draft.code 0
$draftId = $draft.data.id
$adminDetail = Send-Api "Get" "/api/admin/notices/$draftId" $null $adminToken
Check "admin can read draft" $adminDetail.code 0
Check "draft body returned" ($adminDetail.data.content -eq "draft body text") $true
$publicDetail = Send-Api "Get" "/api/notices/$draftId" $null $null
Check "public still hides draft" $publicDetail.code 40405

# ---------- 12. body length limit ----------
Write-Host ""
Write-Host "== 12. content size limit =="
$longText = "a" * 20001
$tooLong = Send-Api "Post" "/api/admin/notices" @{
    title = "Long-$sfx"; content = $longText; status = "DRAFT"; isTop = $false
} $adminToken
Check "20001 chars rejected" $tooLong.code 40001

# ---------- 1. page total not zero ----------
Write-Host ""
Write-Host "== 1. past-last-page keeps total =="
$beyond = Send-Api "Get" "/api/admin/users?page=99&size=10" $null $adminToken
Check "page=99 code" $beyond.code 0
Check "page=99 empty records" (@($beyond.data.records).Count) 0
Check "page=99 total kept" ($beyond.data.total -gt 0) $true

# ---------- 2. chunked json ----------
Write-Host ""
Write-Host "== 2. chunked json body =="
[IO.File]::WriteAllText($tmpFile, '{"username":"admin","password":"admin123"}', [System.Text.Encoding]::UTF8)
$chunked = curl.exe -s -X POST -H "Content-Type: application/json" -H "Transfer-Encoding: chunked" `
    --data-binary "@$tmpFile" "$base/api/auth/login"
$chunkedObj = $chunked | ConvertFrom-Json
Write-Host ("  chunked response code = " + $chunkedObj.code)
Check "chunked json rejected" $chunkedObj.code 40001

# ---------- 3. password with quote ----------
Write-Host ""
Write-Host "== 3. password masking with quote =="
$qName = "q$sfx"
$qPwd = 'pa"ss123'
$null = Send-Api "Post" "/api/auth/register" @{ username = $qName; password = $qPwd; nickname = "N" } $null
$null = Send-Api "Post" "/api/auth/login" @{ username = $qName; password = $qPwd } $null
$logs = Send-Api "Get" "/api/admin/audit-logs?action=USER_LOGIN&page=1&size=20" $null $adminToken
$qLog = @($logs.data.records | Where-Object { $_.detail -like "*$qName*" })[0]
Write-Host ("  detail: " + $qLog.detail)
Check "quote password masked" ($qLog.detail -notmatch 'ss123') $true

# ---------- 5. login audit has operator ----------
Write-Host ""
Write-Host "== 5. login audit identity =="
$adminLog = @($logs.data.records | Where-Object { $_.detail -like "*admin*" })[0]
Write-Host ("  username=" + $adminLog.username + " userId=" + $adminLog.userId)
Check "login audit username filled" ($adminLog.username -ne $null) $true
Check "login audit userId filled" ($adminLog.userId -ne $null) $true

# ---------- 9. message receivers ----------
Write-Host ""
Write-Host "== 9. message receivers =="
$badReceiver = Send-Api "Post" "/api/admin/messages" @{
    title = "T"; content = "C"; receiverIds = @(99999999)
} $adminToken
Check "unknown receiver rejected" $badReceiver.code 40401
$tooMany = Send-Api "Post" "/api/admin/messages" @{
    title = "T"; content = "C"; receiverIds = @(1..501)
} $adminToken
Check "501 receivers rejected" $tooMany.code 40001

# ---------- 13. teacher page public ----------
Write-Host ""
Write-Host "== 13. teacher detail public =="
$teacherAnon = Send-Api "Get" "/api/teachers/999999" $null $null
Check "anonymous reaches teacher api" $teacherAnon.code 40403
$applyAnon = Send-Api "Get" "/api/teacher/profile" $null $null
Check "teacher self api still protected" $applyAnon.code 40101

Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)
Remove-Item $tmpFile -Force -ErrorAction SilentlyContinue
