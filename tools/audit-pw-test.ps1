$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"

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
        Write-Host ("  PASS  {0,-42} {1}" -f $label, $actual)
    } else {
        $script:fail++
        Write-Host ("  FAIL  {0,-42} got={1} want={2}" -f $label, $actual, $expected)
    }
}

$admin = Send-Api "Post" "/api/auth/login" @{ username = "admin"; password = "admin123" } $null
$adminToken = $admin.data.token

# a fresh user, so changing password here does not disturb the demo admin
$name = "w" + (Get-Date -Format "HHmmss")
$null = Send-Api "Post" "/api/auth/register" @{ username = $name; password = "pass123456"; nickname = "N" } $null
$login = Send-Api "Post" "/api/auth/login" @{ username = $name; password = "pass123456" } $null
$userToken = $login.data.token
Write-Host ("user = " + $name)

Write-Host ""
Write-Host "== 1. change password is audited =="
$chg = Send-Api "Put" "/api/auth/password" @{ oldPassword = "pass123456"; newPassword = "newpass789" } $userToken
Check "change password" $chg.code 0

$stale = Send-Api "Get" "/api/auth/me" $null $userToken
Check "old token rejected" $stale.code 40101

Write-Host ""
Write-Host "== 2. logout is audited =="
$re = Send-Api "Post" "/api/auth/login" @{ username = $name; password = "newpass789" } $null
$userToken2 = $re.data.token
$out = Send-Api "Post" "/api/auth/logout" $null $userToken2
Check "logout" $out.code 0
$afterLogout = Send-Api "Get" "/api/auth/me" $null $userToken2
Check "token blacklisted" $afterLogout.code 40101

Write-Host ""
Write-Host "== 3. audit rows for this user =="
$logs = Send-Api "Get" "/api/admin/audit-logs?page=1&size=50" $null $adminToken
$mine = @($logs.data.records | Where-Object { $_.detail -like "*$name*" -or ($_.detail -like "*$name*") })
$pwActions = @($logs.data.records | Where-Object { $_.action -eq "PASSWORD_CHANGE" })
$outActions = @($logs.data.records | Where-Object { $_.action -eq "USER_LOGOUT" })
Check "PASSWORD_CHANGE recorded" ($pwActions.Count -gt 0) $true
Check "USER_LOGOUT recorded" ($outActions.Count -gt 0) $true
if ($pwActions.Count -gt 0) { Write-Host ("  PASSWORD_CHANGE detail: " + $pwActions[0].detail) }
if ($outActions.Count -gt 0) { Write-Host ("  USER_LOGOUT  detail: " + $outActions[0].detail) }
Check "password not leaked in audit" (($pwActions[0].detail) -notmatch 'pass123456|newpass789') $true

Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)
