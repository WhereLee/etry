$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"
$png = "c:\Users\lrs\Desktop\work\etry\tools\avatar-test.png"
$b64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
[IO.File]::WriteAllBytes($png, [Convert]::FromBase64String($b64))

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
$subjects = Send-Api "Get" "/api/subjects" $null $null
$subjectId = $subjects.data[0].id

# ---------- setUp: create a teacher ----------
$tName = "g$sfx"
$mk = Send-Api "Post" "/api/admin/users" @{
    username = $tName; initialPassword = "init123456"; nickname = "TeacherG"
    roleIds = @($studentRoleId, $teacherRoleId)
    teacherProfile = @{ realName = "Wang Wu"; title = "Lecturer"; years = 3; subjectIds = @($subjectId) }
} $at
Check "setUp create teacher" $mk.code 0
$teacherUserId = $mk.data

$list = Send-Api "Get" "/api/admin/teachers?page=1&size=50" $null $at
$profile = @($list.data.records | Where-Object { $_.userId -eq $teacherUserId })[0]
$profileId = $profile.id
Write-Host ("setUp: userId=$teacherUserId profileId=$profileId")

# ---------- 1. teacher avatar ----------
Write-Host ""
Write-Host "== 1. teacher avatar =="
$first = Send-Api "Post" "/api/auth/login" @{ username = $tName; password = "init123456" } $null
$null = Send-Api "Put" "/api/auth/password" @{ oldPassword = "init123456"; newPassword = "tpass123456" } $first.data.token
$login = Send-Api "Post" "/api/auth/login" @{ username = $tName; password = "tpass123456" } $null
$tt = $login.data.token

$up = curl.exe -s -X POST -H "Authorization: Bearer $tt" -F "file=@$png;type=image/png" "$base/api/teacher/avatar"
$upObj = $up | ConvertFrom-Json
Check "upload teacher avatar" $upObj.code 0
$url1 = $upObj.data.avatarUrl
Write-Host ("  avatarUrl head = " + $url1.Substring(0, [Math]::Min(48, $url1.Length)))
Check "avatarUrl not a raw key" ($url1 -notmatch "^avatar/") $true

$publicDetail = Send-Api "Get" "/api/teachers/$profileId" $null $null
Check "public detail returns avatar" ($publicDetail.data.avatarUrl -ne $null) $true

# replace again, old file should be gone from the profile record
$up2 = curl.exe -s -X POST -H "Authorization: Bearer $tt" -F "file=@$png;type=image/png" "$base/api/teacher/avatar"
$url2 = ($up2 | ConvertFrom-Json).data.avatarUrl
Check "avatar replaced" ($url2 -ne $url1) $true

# ---------- 2. archive / restore ----------
Write-Host ""
Write-Host "== 2. teacher archive and restore =="
$arch = Send-Api "Put" "/api/admin/teachers/$profileId/archive" $null $at
Check "archive" $arch.code 0

$afterArchive = Send-Api "Get" "/api/admin/teachers?status=ARCHIVED&page=1&size=50" $null $at
$found = @($afterArchive.data.records | Where-Object { $_.id -eq $profileId }).Count
Check "listed as archived" $found 1

$meAfter = Send-Api "Get" "/api/auth/me" $null $tt
Check "TEACHER role removed" (@($meAfter.data.roles) -contains "TEACHER") $false
Check "STUDENT role kept" (@($meAfter.data.roles) -contains "STUDENT") $true

$publicAfter = Send-Api "Get" "/api/teachers/$profileId" $null $null
Check "public detail hides archived" $publicAfter.code 40403

$reArch = Send-Api "Put" "/api/admin/teachers/$profileId/archive" $null $at
Check "archive twice is idempotent" $reArch.code 0

$restore = Send-Api "Put" "/api/admin/teachers/$profileId/restore" $null $at
Check "restore" $restore.code 0

$relogin = Send-Api "Post" "/api/auth/login" @{ username = $tName; password = "tpass123456" } $null
$meBack = Send-Api "Get" "/api/auth/me" $null $relogin.data.token
Check "TEACHER role back" (@($meBack.data.roles) -contains "TEACHER") $true

$publicBack = Send-Api "Get" "/api/teachers/$profileId" $null $null
Check "public detail visible again" $publicBack.code 0

$restoreAgain = Send-Api "Put" "/api/admin/teachers/$profileId/restore" $null $at
Check "restore on active rejected" $restoreAgain.code 40007

Remove-Item $png -Force -ErrorAction SilentlyContinue
Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)
