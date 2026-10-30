$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"
$tUser = "t" + (Get-Date -Format "HHmmss")

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
    Write-Host ("{0,-30} code={1}" -f $label, $resp.code)
}

Write-Host "== 1. subjects without token =="
$subs = Send-Api "Get" "/api/subjects" $null $null
Show "subjects public" $subs
$subs.data | ForEach-Object { Write-Host ("    id={0} code={1}" -f $_.id, $_.code) }
$firstSubjectId = $subs.data[0].id
$secondSubjectId = $subs.data[1].id

$admin = Send-Api "Post" "/api/auth/login" @{ username = "admin"; password = "admin123" } $null
$adminToken = $admin.data.token

Write-Host "== 2. register applicant =="
$reg = Send-Api "Post" "/api/auth/register" @{ username = $tUser; password = "pass123456"; nickname = "candidate" } $null
Show "register" $reg
$token = $reg.data.token

Write-Host "== 3. apply as teacher with 2 subjects =="
$payload = @{
    realName = "Wang"
    title = "Senior Instructor"
    bio = "8 years backend"
    years = 8
    subjectIds = @($firstSubjectId, $secondSubjectId)
}
$apply = Send-Api "Post" "/api/teacher/apply" $payload $token
Show "apply" $apply
$profileId = $apply.data.id
Write-Host ("    profileId={0} status={1} subjectCount={2}" -f $profileId, $apply.data.status, $apply.data.subjects.Count)

Write-Host "== 4. apply again expect 40006 =="
$again = Send-Api "Post" "/api/teacher/apply" $payload $token
Show "apply again" $again

Write-Host "== 5. admin pending list =="
$pending = Send-Api "Get" "/api/admin/teachers?status=PENDING" $null $adminToken
Show "pending list" $pending
Write-Host ("    total={0}" -f $pending.data.total)

Write-Host "== 6. admin approves =="
$approve = Send-Api "Put" "/api/admin/teachers/$profileId/review" @{ approved = $true } $adminToken
Show "approve" $approve

Write-Host "== 7. applicant me roles after approve =="
$me = Send-Api "Get" "/api/auth/me" $null $token
Show "me after approve" $me
Write-Host ("    roles={0}" -f ($me.data.roles -join ","))

Write-Host "== 8. public teacher detail =="
$detail = Send-Api "Get" "/api/teachers/$profileId" $null $token
Show "public detail" $detail
Write-Host ("    realName={0} status={1} subjectCount={2}" -f $detail.data.realName, $detail.data.status, $detail.data.subjects.Count)

Write-Host "== 9. delete subject in use expect 40009 =="
$del = Send-Api "Delete" "/api/admin/subjects/$firstSubjectId" $null $adminToken
Show "delete used subject" $del

Write-Host "== 10. create new subject =="
$newCode = "test_" + (Get-Date -Format "HHmmss")
$create = Send-Api "Post" "/api/admin/subjects" @{ code = $newCode; name = "Test Subject"; sort = 99 } $adminToken
Show "create subject" $create

Write-Host "== 11. delete the new subject =="
if ($create.data) {
    $del2 = Send-Api "Delete" "/api/admin/subjects/$($create.data.id)" $null $adminToken
    Show "delete new subject" $del2
}
