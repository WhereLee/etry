$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"
$tmp = "c:\Users\lrs\Desktop\work\etry\tools\video-test.mp4"
. "c:\Users\lrs\Desktop\work\etry\tools\lib-mp4.ps1"

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

function Upload-Video($token, $tag) {
    New-MinimalMp4 $tmp 100
    $t = Send-Api "Post" "/api/teacher/videos/ticket" @{ fileName = "$tag.mp4"; contentType = "video/mp4"; sizeBytes = 1000 } $token
    $null = curl.exe -s -o NUL -X PUT -H "Content-Type: video/mp4" --upload-file $tmp "$($t.data.uploadUrl)"
    return $t.data.key
}

$sfx = Get-Date -Format "HHmmss"
$admin = Send-Api "Post" "/api/auth/login" @{ username = "admin"; password = "admin123" } $null
$at = $admin.data.token
$roles = Send-Api "Get" "/api/admin/roles" $null $at
$teacherRoleId = ($roles.data | Where-Object { $_.code -eq "TEACHER" }).id
$studentRoleId = ($roles.data | Where-Object { $_.code -eq "STUDENT" }).id
$subjectId = (Send-Api "Get" "/api/subjects" $null $null).data[0].id

function New-User($name, $withTeacher) {
    $body = @{ username = $name; initialPassword = "init123456"; nickname = $name; roleIds = @($studentRoleId) }
    if ($withTeacher) {
        $body["roleIds"] = @($studentRoleId, $teacherRoleId)
        $body["teacherProfile"] = @{ realName = "Teacher $name"; years = 1; subjectIds = @($subjectId) }
    }
    $r = Send-Api "Post" "/api/admin/users" $body $at
    $login = Send-Api "Post" "/api/auth/login" @{ username = $name; password = "init123456" } $null
    $null = Send-Api "Put" "/api/auth/password" @{ oldPassword = "init123456"; newPassword = "pass123456" } $login.data.token
    $again = Send-Api "Post" "/api/auth/login" @{ username = $name; password = "pass123456" } $null
    return @{ userId = $r.data; token = $again.data.token }
}

$teacher = New-User "t$sfx" $true
$student = New-User "s$sfx" $false

# 3 lessons, only 2 have video
$courseId = (Send-Api "Post" "/api/teacher/courses" @{ title = "R2 $sfx"; subjectId = $subjectId } $teacher.token).data
$chapterId = (Send-Api "Post" "/api/teacher/courses/$courseId/chapters" @{ title = "C1"; sort = 1 } $teacher.token).data
$k1 = Upload-Video $teacher.token "a"
$l1 = (Send-Api "Post" "/api/teacher/chapters/$chapterId/lessons" @{ title = "L1"; sort = 1; videoKey = $k1 } $teacher.token).data
$k2 = Upload-Video $teacher.token "b"
$l2 = (Send-Api "Post" "/api/teacher/chapters/$chapterId/lessons" @{ title = "L2"; sort = 2; videoKey = $k2 } $teacher.token).data
$null = Send-Api "Post" "/api/teacher/chapters/$chapterId/lessons" @{ title = "L3-empty"; sort = 3 } $teacher.token

# ---------- 1. list carries both counts ----------
Write-Host ""
Write-Host "== 1. lesson counts =="
$list = Send-Api "Get" "/api/teacher/courses?page=1&size=10" $null $teacher.token
$row = @($list.data.records | Where-Object { $_.id -eq $courseId })[0]
Check "total lessons" $row.lessonCount 3
Check "playable lessons" $row.videoLessonCount 2

$null = Send-Api "Put" "/api/teacher/courses/$courseId/submit" $null $teacher.token
$null = Send-Api "Put" "/api/admin/courses/$courseId/review" @{ approved = $true } $at

# ---------- 2. deleting a lesson clears progress ----------
Write-Host ""
Write-Host "== 2. delete lesson clears progress =="
$null = Send-Api "Post" "/api/learning/progress" @{ lessonId = $l1; positionSeconds = 100 } $student.token
$null = Send-Api "Post" "/api/learning/progress" @{ lessonId = $l2; positionSeconds = 100 } $student.token

$before = Send-Api "Get" "/api/learning/courses/$courseId" $null $student.token
Check "progress 100 before delete" $before.data.percent 100

# teacher takes it offline, deletes lesson 2, republishes
$null = Send-Api "Put" "/api/teacher/courses/$courseId/offline" $null $teacher.token
$delLesson = Send-Api "Delete" "/api/teacher/lessons/$l2" $null $teacher.token
Check "delete lesson ok" $delLesson.code 0
$null = Send-Api "Put" "/api/teacher/courses/$courseId/submit" $null $teacher.token
$null = Send-Api "Put" "/api/admin/courses/$courseId/review" @{ approved = $true } $at

$after = Send-Api "Get" "/api/learning/courses/$courseId" $null $student.token
Check "one playable lesson left" $after.data.totalLessons 1
Check "progress stays at 100 not 200" $after.data.percent 100
Check "completed lessons not inflated" $after.data.completedLessons 1

# ---------- 3. admin material endpoint validates course ----------
Write-Host ""
Write-Host "== 3. admin material guard =="
$missing = Send-Api "Get" "/api/admin/courses/999999/materials" $null $at
Check "unknown course rejected" $missing.code 40408

$ok = Send-Api "Get" "/api/admin/courses/$courseId/materials" $null $at
Check "existing course returns list" $ok.code 0

# ---------- 4. archiving teacher takes courses offline ----------
Write-Host ""
Write-Host "== 4. archive takes courses offline =="
$profileId = (Send-Api "Get" "/api/admin/teachers?page=1&size=50" $null $at).data.records |
    Where-Object { $_.realName -eq "Teacher t$sfx" } | Select-Object -First 1
Check "teacher profile found" ($profileId -ne $null) $true

$archive = Send-Api "Put" "/api/admin/teachers/$($profileId.id)/archive?archived=true" $null $at
Check "archive ok" $archive.code 0

$adminDetail = Send-Api "Get" "/api/admin/courses/$courseId" $null $at
Check "course set offline" $adminDetail.data.status "OFFLINE"

$publicList = Send-Api "Get" "/api/courses?keyword=R2%20$sfx&page=1&size=10" $null $null
Check "hidden from public list" (@($publicList.data.records | Where-Object { $_.id -eq $courseId }).Count) 0

Remove-Item $tmp -Force -ErrorAction SilentlyContinue
Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)