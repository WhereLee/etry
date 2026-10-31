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
        Write-Host ("  PASS  {0,-46} {1}" -f $label, $actual)
    } else {
        $script:fail++
        Write-Host ("  FAIL  {0,-46} got={1} want={2}" -f $label, $actual, $expected)
    }
}

$sfx = Get-Date -Format "HHmmss"
$admin = Send-Api "Post" "/api/auth/login" @{ username = "admin"; password = "admin123" } $null
$at = $admin.data.token
$roles = Send-Api "Get" "/api/admin/roles" $null $at
$teacherRoleId = ($roles.data | Where-Object { $_.code -eq "TEACHER" }).id
$studentRoleId = ($roles.data | Where-Object { $_.code -eq "STUDENT" }).id

# ---------- setup: a subject, and a teacher bound to it ----------
Write-Host ""
Write-Host "== subject rename propagates to teacher detail =="
$code = "sx$sfx"
$subject = Send-Api "Post" "/api/admin/subjects" @{ code = $code; name = "Original Name"; sort = 77 } $at
Check "create subject" $subject.code 0
$subjectId = $subject.data.id

$tName = "t$sfx"
$null = Send-Api "Post" "/api/admin/users" @{
    username = $tName; initialPassword = "init123456"; nickname = $tName
    roleIds = @($studentRoleId, $teacherRoleId)
    teacherProfile = @{ realName = "Teacher $tName"; years = 2; subjectIds = @($subjectId) }
} $at

$profiles = Send-Api "Get" "/api/admin/teachers?page=1&size=50" $null $at
$profile = @($profiles.data.records | Where-Object { $_.realName -eq "Teacher $tName" })[0]
Check "teacher profile created" ($profile -ne $null) $true

$before = Send-Api "Get" "/api/teachers/$($profile.id)" $null $null
Check "public detail code" $before.code 0
Check "subject name before rename" $before.data.subjects[0].name "Original Name"

# ---------- rename ----------
$rename = Send-Api "Put" "/api/admin/subjects/$subjectId" @{ name = "Renamed Name"; sort = 76 } $at
Check "rename ok" $rename.code 0

$after = Send-Api "Get" "/api/teachers/$($profile.id)" $null $null
Check "subject name after rename" $after.data.subjects[0].name "Renamed Name"
Check "subject id unchanged" $after.data.subjects[0].id $subjectId

# course detail also reflects the new subject ordering/filter
$null = Send-Api "Put" "/api/admin/subjects/$subjectId" @{ name = "Original Name"; sort = 77 } $at
$null = Send-Api "Delete" "/api/admin/subjects/$subjectId" $null $at

Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)