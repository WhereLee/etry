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
$t = $admin.data.token
$roles = Send-Api "Get" "/api/admin/roles" $null $t
$studentId = ($roles.data | Where-Object { $_.code -eq "STUDENT" }).id
$teacherId = ($roles.data | Where-Object { $_.code -eq "TEACHER" }).id
$subjects = Send-Api "Get" "/api/subjects" $null $null
$subjectId = $subjects.data[0].id
Write-Host ("setUp: studentRole=$studentId teacherRole=$teacherId subject=$subjectId")

# ---------- 1. role crud ----------
Write-Host ""
Write-Host "== 1. role create / update / delete =="
$created = Send-Api "Post" "/api/admin/roles" @{ code = "ASSISTANT"; name = "Assistant"; description = "helper" } $t
Check "create role" $created.code 0
$assistantId = $created.data.id

$dup = Send-Api "Post" "/api/admin/roles" @{ code = "ASSISTANT"; name = "Dup" } $t
Check "duplicate code rejected" $dup.code 40004

$upd = Send-Api "Put" "/api/admin/roles/$assistantId" @{ name = "Assistant2"; description = "d2" } $t
Check "update role" $upd.code 0

$delBuiltIn = Send-Api "Delete" "/api/admin/roles/$studentId" $null $t
Check "built-in role cannot delete" $delBuiltIn.code 40001

$delOk = Send-Api "Delete" "/api/admin/roles/$assistantId" $null $t
Check "unused role deleted" $delOk.code 0

# ---------- 2. create user (student) + force password change ----------
Write-Host ""
Write-Host "== 2. admin creates account, first login must change password =="
$uName = "t$sfx"
$mk = Send-Api "Post" "/api/admin/users" @{
    username = $uName; initialPassword = "init123456"; nickname = "NewUser"; roleIds = @($studentId)
} $t
Check "create user" $mk.code 0
$uid = $mk.data

$login = Send-Api "Post" "/api/auth/login" @{ username = $uName; password = "init123456" } $null
Check "login ok" $login.code 0
Check "mustChangePassword flag" $login.data.mustChangePassword $true

$blocked = Send-Api "Get" "/api/user/profile" $null $login.data.token
Check "other api blocked before change" $blocked.code 40106

$meOk = Send-Api "Get" "/api/auth/me" $null $login.data.token
Check "me still allowed" $meOk.code 0

$chg = Send-Api "Put" "/api/auth/password" @{ oldPassword = "init123456"; newPassword = "changed123" } $login.data.token
Check "change password" $chg.code 0

$relogin = Send-Api "Post" "/api/auth/login" @{ username = $uName; password = "changed123" } $null
Check "relogin with new pwd" $relogin.code 0
Check "flag cleared" $relogin.data.mustChangePassword $false
$profile = Send-Api "Get" "/api/user/profile" $null $relogin.data.token
Check "other api allowed now" $profile.code 0

# ---------- 3. admin reset password re-marks ----------
Write-Host ""
Write-Host "== 3. admin reset marks must-change again =="
$reset = Send-Api "Put" "/api/admin/users/$uid/password" @{ newPassword = "reset12345" } $t
Check "admin reset" $reset.code 0
$relogin2 = Send-Api "Post" "/api/auth/login" @{ username = $uName; password = "reset12345" } $null
Check "flag set after reset" $relogin2.data.mustChangePassword $true

# ---------- 4. create teacher in one shot ----------
Write-Host ""
Write-Host "== 4. admin creates teacher with profile =="
$tName = "e$sfx"
$mkTeacher = Send-Api "Post" "/api/admin/users" @{
    username = $tName
    initialPassword = "init123456"
    nickname = "Teacher1"
    roleIds = @($teacherId)
    teacherProfile = @{
        realName = "Zhang San"; title = "Lecturer"; bio = "bio"; years = 5; subjectIds = @($subjectId)
    }
} $t
Check "create teacher" $mkTeacher.code 0

$teachers = Send-Api "Get" "/api/admin/teachers?status=APPROVED&page=1&size=50" $null $t
$tp = @($teachers.data.records | Where-Object { $_.userId -eq $mkTeacher.data })[0]
Check "profile auto approved" $tp.status "APPROVED"
Check "subjects bound" ($tp.subjects.Count -gt 0) $true

$noProfile = Send-Api "Post" "/api/admin/users" @{
    username = "x$sfx"; initialPassword = "init123456"; nickname = "X"; roleIds = @($teacherId)
} $t
Check "teacher without profile rejected" $noProfile.code 40001

$profileNoTeacher = Send-Api "Post" "/api/admin/users" @{
    username = "y$sfx"; initialPassword = "init123456"; nickname = "Y"; roleIds = @($studentId)
    teacherProfile = @{ realName = "Li"; subjectIds = @($subjectId) }
} $t
Check "profile without teacher role rejected" $profileNoTeacher.code 40001

# ---------- 5. role in use cannot be deleted ----------
Write-Host ""
Write-Host "== 5. role in use cannot be deleted =="
$auditor = Send-Api "Post" "/api/admin/roles" @{ code = "AUDITOR"; name = "Auditor" } $t
$null = Send-Api "Post" "/api/admin/users" @{
    username = "a$sfx"; initialPassword = "init123456"; nickname = "A"; roleIds = @($auditor.data.id)
} $t
$delUsed = Send-Api "Delete" "/api/admin/roles/$($auditor.data.id)" $null $t
Check "role in use rejected" $delUsed.code 40005

Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)
