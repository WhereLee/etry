$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$base = "http://127.0.0.1:8080"
$uname = "au" + (Get-Date -Format "HHmmss")
$oldPwd = "demo123"
$newPwd = "newpass456"

function Send-Api($method, $url, $obj, $token) {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    if ($obj -ne $null) {
        $json = $obj | ConvertTo-Json -Depth 5 -Compress
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
        return Invoke-RestMethod -Uri $url -Method $method -Headers $headers -ContentType "application/json; charset=utf-8" -Body $bytes
    }
    return Invoke-RestMethod -Uri $url -Method $method -Headers $headers
}

function Show($label, $resp) {
    Write-Host ("{0,-34} code={1} msg={2}" -f $label, $resp.code, $resp.message)
}

Write-Host "== 1. register $uname =="
$reg = Send-Api "Post" "$base/api/auth/register" @{ username = $uname; password = $oldPwd; nickname = "auth user" } $null
Show "register" $reg
$tokenA = $reg.data.token

Write-Host "== 2. /me with token A =="
$me = Send-Api "Get" "$base/api/auth/me" $null $tokenA
Show "me(tokenA)" $me
Write-Host ("  nickname={0} role={1}" -f $me.data.nickname, $me.data.role)

Write-Host "== 3. business api with token A =="
$products = Send-Api "Get" "$base/api/products?page=1&size=1" $null $tokenA
Show "products(tokenA)" $products

Write-Host "== 4. logout with token A =="
$out = Send-Api "Post" "$base/api/auth/logout" $null $tokenA
Show "logout" $out

Write-Host "== 5. reuse token A after logout (should be rejected) =="
$afterOut = Send-Api "Get" "$base/api/auth/me" $null $tokenA
Show "me(tokenA after logout)" $afterOut

Write-Host "== 6. login again =="
$login = Send-Api "Post" "$base/api/auth/login" @{ username = $uname; password = $oldPwd } $null
Show "login" $login
$tokenB = $login.data.token

Write-Host "== 7. change password =="
$chg = Send-Api "Put" "$base/api/auth/password" @{ oldPassword = $oldPwd; newPassword = $newPwd } $tokenB
Show "changePassword" $chg

Write-Host "== 8. use token B after password change (should be rejected) =="
$afterChg = Send-Api "Get" "$base/api/auth/me" $null $tokenB
Show "me(tokenB after change)" $afterChg

Write-Host "== 9. login with old password (should fail) =="
$oldLogin = Send-Api "Post" "$base/api/auth/login" @{ username = $uname; password = $oldPwd } $null
Show "login(oldPwd)" $oldLogin

Write-Host "== 10. login with new password =="
$newLogin = Send-Api "Post" "$base/api/auth/login" @{ username = $uname; password = $newPwd } $null
Show "login(newPwd)" $newLogin
$tokenC = $newLogin.data.token

Write-Host "== 11. /me with token C =="
$meC = Send-Api "Get" "$base/api/auth/me" $null $tokenC
Show "me(tokenC)" $meC

Write-Host "== 12. admin path with normal user token (should be forbidden) =="
$admin = Send-Api "Get" "$base/api/admin/ping" $null $tokenC
Show "admin-path(tokenC)" $admin
