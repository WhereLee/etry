$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"
$user = "t" + (Get-Date -Format "HHmmss")
$pwd1 = "pass123456"
$pwd2 = "newpass456"

function Send-Api($method, $path, $obj, $token) {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    if ($obj -ne $null) {
        $json = $obj | ConvertTo-Json -Depth 5 -Compress
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
        return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers $headers `
            -ContentType "application/json; charset=utf-8" -Body $bytes
    }
    return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers $headers
}

function Show($label, $resp) {
    $roles = "-"
    if ($resp.data -and $resp.data.roles) { $roles = ($resp.data.roles -join ",") }
    Write-Host ("{0,-32} code={1,-6} roles={2}" -f $label, $resp.code, $roles)
}

Write-Host "user = $user"
Write-Host ""

Write-Host "== 1. register =="
$reg = Send-Api "Post" "/api/auth/register" @{ username = $user; password = $pwd1; nickname = "tester" } $null
Show "register" $reg
$token1 = $reg.data.token

Write-Host "== 2. me with token =="
$me = Send-Api "Get" "/api/auth/me" $null $token1
Show "me(token1)" $me
Write-Host ("    nickname={0} status={1}" -f $me.data.nickname, $me.data.status)

Write-Host "== 3. change password =="
$chg = Send-Api "Put" "/api/auth/password" @{ oldPassword = $pwd1; newPassword = $pwd2 } $token1
Show "changePassword" $chg

Write-Host "== 4. old token should be rejected =="
$meOld = Send-Api "Get" "/api/auth/me" $null $token1
Show "me(old token)" $meOld

Write-Host "== 5. login with new password =="
$login2 = Send-Api "Post" "/api/auth/login" @{ username = $user; password = $pwd2 } $null
Show "login(new pwd)" $login2
$token2 = $login2.data.token

Write-Host "== 6. logout =="
$out = Send-Api "Post" "/api/auth/logout" $null $token2
Show "logout" $out

Write-Host "== 7. token after logout should be rejected =="
$meOut = Send-Api "Get" "/api/auth/me" $null $token2
Show "me(after logout)" $meOut

Write-Host "== 8. wrong password 5 times =="
for ($i = 1; $i -le 5; $i++) {
    $bad = Send-Api "Post" "/api/auth/login" @{ username = $user; password = "wrongpass" } $null
    Show ("  wrong #$i") $bad
}

Write-Host "== 9. correct password after lock =="
$locked = Send-Api "Post" "/api/auth/login" @{ username = $user; password = $pwd2 } $null
Show "login(correct, locked)" $locked
