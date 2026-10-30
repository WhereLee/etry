$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"
$stuUser = "s" + (Get-Date -Format "HHmmss")

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
    Write-Host ("{0,-34} code={1}" -f $label, $resp.code)
}

Write-Host "student = $stuUser"
Write-Host ""

Write-Host "== 1. admin login =="
$admin = Send-Api "Post" "/api/auth/login" @{ username = "admin"; password = "admin123" } $null
Show "admin login" $admin
$adminToken = $admin.data.token

Write-Host "== 2. admin me =="
$me = Send-Api "Get" "/api/auth/me" $null $adminToken
Show "admin me" $me
Write-Host ("    roles={0}" -f ($me.data.roles -join ","))
Write-Host ("    permissions={0}" -f ($me.data.permissions -join ","))

Write-Host "== 3. admin user list =="
$list = Send-Api "Get" "/api/admin/users?page=1&size=5" $null $adminToken
Show "user list" $list
Write-Host ("    total={0}" -f $list.data.total)

Write-Host "== 4. register student =="
$stu = Send-Api "Post" "/api/auth/register" @{ username = $stuUser; password = "pass123456"; nickname = "stu" } $null
Show "student register" $stu
$stuToken = $stu.data.token
$stuId = $stu.data.userId

Write-Host "== 5. student calls admin api (expect 40301) =="
$denied = Send-Api "Get" "/api/admin/users" $null $stuToken
Show "student -> admin api" $denied

Write-Host "== 6. admin lists roles =="
$roles = Send-Api "Get" "/api/admin/roles" $null $adminToken
Show "roles" $roles
$teacherRoleId = ($roles.data | Where-Object { $_.code -eq "TEACHER" }).id
Write-Host ("    TEACHER roleId={0}" -f $teacherRoleId)

Write-Host "== 7. assign TEACHER to student =="
$assign = Send-Api "Put" "/api/admin/users/$stuId/roles" @{ roleIds = @($teacherRoleId) } $adminToken
Show "assign roles" $assign

Write-Host "== 8. student me after assign (roles should change immediately) =="
$meAfter = Send-Api "Get" "/api/auth/me" $null $stuToken
Show "student me" $meAfter
Write-Host ("    roles={0}" -f ($meAfter.data.roles -join ","))

Write-Host "== 9. teacher role permissions =="
$rolePerms = Send-Api "Get" "/api/admin/roles/$teacherRoleId/permissions" $null $adminToken
Show "role permissions" $rolePerms
Write-Host ("    codes={0}" -f ($rolePerms.data -join ","))

Write-Host "== 10. disable student =="
$disable = Send-Api "Put" "/api/admin/users/$stuId/status" @{ status = "DISABLED" } $adminToken
Show "disable" $disable

Write-Host "== 11. disabled user login (expect 40103) =="
$loginDisabled = Send-Api "Post" "/api/auth/login" @{ username = $stuUser; password = "pass123456" } $null
Show "disabled login" $loginDisabled

Write-Host "== 12. disabled user existing token (expect 40103) =="
$meDisabled = Send-Api "Get" "/api/auth/me" $null $stuToken
Show "disabled me" $meDisabled

Write-Host "== 13. admin disable self (expect 40001) =="
$self = Send-Api "Put" "/api/admin/users/1/status" @{ status = "DISABLED" } $adminToken
Show "disable self" $self
