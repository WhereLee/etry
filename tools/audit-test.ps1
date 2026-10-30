$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"
$stuUser = "a" + (Get-Date -Format "HHmmss")

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

function Show($label, $resp) {
    Write-Host ("{0,-32} code={1}" -f $label, $resp.code)
}

$admin = Send-Api "Post" "/api/auth/login" @{ username = "admin"; password = "admin123" } $null
$adminToken = $admin.data.token
Show "admin login" $admin

Write-Host "== 1. configs list =="
$cfgs = Send-Api "Get" "/api/admin/configs" $null $adminToken
Show "configs" $cfgs
$cfgs.data | ForEach-Object { Write-Host ("    {0} = {1}" -f $_.key, $_.value) }

Write-Host "== 2. update config =="
$upd = Send-Api "Put" "/api/admin/configs/site.name" @{ value = "启明课堂" } $adminToken
Show "update site.name" $upd
$after = Send-Api "Get" "/api/admin/configs" $null $adminToken
$name = ($after.data | Where-Object { $_.key -eq "site.name" }).value
Write-Host ("    site.name now = {0}" -f $name)

Write-Host "== 3. update unknown key (expect 40407) =="
$bad = Send-Api "Put" "/api/admin/configs/not.exist" @{ value = "x" } $adminToken
Show "unknown key" $bad

Write-Host "== 4. register target user =="
$stu = Send-Api "Post" "/api/auth/register" @{ username = $stuUser; password = "pass123456"; nickname = "audit-target" } $null
Show "register" $stu
$stuId = $stu.data.userId

Write-Host "== 5. admin disables user (should write audit) =="
$dis = Send-Api "Put" "/api/admin/users/$stuId/status" @{ status = "DISABLED" } $adminToken
Show "disable user" $dis

Write-Host "== 6. query audit logs =="
$logs = Send-Api "Get" "/api/admin/audit-logs?page=1&size=10" $null $adminToken
Show "audit list" $logs
Write-Host ("    total={0}" -f $logs.data.total)
$logs.data.records | Select-Object -First 5 | ForEach-Object {
    Write-Host ("    {0} by {1} target={2}:{3} success={4} cost={5}ms ip={6}" -f `
        $_.action, $_.username, $_.targetType, $_.targetId, $_.success, $_.costMs, $_.ip)
}

Write-Host "== 7. filter audit by action =="
$filtered = Send-Api "Get" "/api/admin/audit-logs?action=USER_STATUS_CHANGE" $null $adminToken
Show "audit filter" $filtered
Write-Host ("    matched={0}" -f $filtered.data.total)

Write-Host "== 8. student cannot read audit logs (expect 40301) =="
$stu2 = Send-Api "Post" "/api/auth/login" @{ username = $stuUser; password = "pass123456" } $null
Write-Host ("    disabled user login code={0}" -f $stu2.code)
