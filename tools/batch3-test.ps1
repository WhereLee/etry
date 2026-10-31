$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"
$tmpVideo = "c:\Users\lrs\Desktop\work\etry\tools\video-test.mp4"
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
    New-MinimalMp4 $tmpVideo 100
    $t = Send-Api "Post" "/api/teacher/videos/ticket" @{ fileName = "$tag.mp4"; contentType = "video/mp4"; sizeBytes = 1000 } $token
    $null = curl.exe -s -o NUL -X PUT -H "Content-Type: video/mp4" --upload-file $tmpVideo "$($t.data.uploadUrl)"
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

# ---------- 1. tags ----------
Write-Host ""
Write-Host "== 1. tags =="
$tagName = "tag$sfx"
$tag = Send-Api "Post" "/api/admin/tags" @{ name = $tagName; sort = 1 } $at
Check "create tag" $tag.code 0
$tagId = $tag.data.id

$pubTags = Send-Api "Get" "/api/tags" $null $null
Check "tag visible publicly" (@($pubTags.data | Where-Object { $_.id -eq $tagId }).Count) 1

$dup = Send-Api "Post" "/api/admin/tags" @{ name = $tagName } $at
Check "duplicate name rejected" $dup.code 40017

$byStudent = Send-Api "Post" "/api/admin/tags" @{ name = "hack$sfx" } $student.token
Check "student cannot create tag" $byStudent.code 40301

# ---------- 2. course with level and tags, sequential on ----------
Write-Host ""
Write-Host "== 2. course level / tags / sequential =="
$courseId = (Send-Api "Post" "/api/teacher/courses" @{
    title = "Sequential Course $sfx"; subjectId = $subjectId
    level = "BEGINNER"; tagIds = @($tagId); sequential = $true
} $teacher.token).data
Check "course created" ($courseId -gt 0) $true

$chapterId = (Send-Api "Post" "/api/teacher/courses/$courseId/chapters" @{ title = "C1"; sort = 1 } $teacher.token).data
$key1 = Upload-Video $teacher.token "a"
$lesson1 = (Send-Api "Post" "/api/teacher/chapters/$chapterId/lessons" @{ title = "L1"; sort = 1; videoKey = $key1 } $teacher.token).data
$key2 = Upload-Video $teacher.token "b"
$lesson2 = (Send-Api "Post" "/api/teacher/chapters/$chapterId/lessons" @{ title = "L2"; sort = 2; videoKey = $key2 } $teacher.token).data

$null = Send-Api "Put" "/api/teacher/courses/$courseId/submit" $null $teacher.token
$null = Send-Api "Put" "/api/admin/courses/$courseId/review" @{ approved = $true } $at

$detail = Send-Api "Get" "/api/courses/$courseId" $null $teacher.token
Check "detail level" $detail.data.level "BEGINNER"
Check "detail tags count" (@($detail.data.tags).Count) 1
Check "detail tag name" $detail.data.tags[0].name $tagName

# bad tag id is rejected
$badTag = Send-Api "Post" "/api/teacher/courses" @{
    title = "Bad $sfx"; subjectId = $subjectId; tagIds = @(999999)
} $teacher.token
Check "unknown tag rejected" $badTag.code 40001

# ---------- 3. filtering ----------
Write-Host ""
Write-Host "== 3. filtering =="
$byTag = Send-Api "Get" "/api/courses?tagId=$tagId&page=1&size=20" $null $null
Check "filter by tag finds course" (@($byTag.data.records | Where-Object { $_.id -eq $courseId }).Count) 1

$byLevel = Send-Api "Get" "/api/courses?level=BEGINNER&page=1&size=50" $null $null
Check "filter by level finds course" (@($byLevel.data.records | Where-Object { $_.id -eq $courseId }).Count) 1

$byOtherLevel = Send-Api "Get" "/api/courses?level=ADVANCED&page=1&size=50" $null $null
Check "other level excludes course" (@($byOtherLevel.data.records | Where-Object { $_.id -eq $courseId }).Count) 0

# ---------- 4. tag in use ----------
Write-Host ""
Write-Host "== 4. tag delete guard =="
$delUsed = Send-Api "Delete" "/api/admin/tags/$tagId" $null $at
Check "cannot delete used tag" $delUsed.code 40016

# ---------- 5. sequential learning ----------
Write-Host ""
Write-Host "== 5. sequential learning =="
$skip = Send-Api "Get" "/api/courses/lessons/$lesson2/play" $null $student.token
Check "cannot skip to lesson 2" $skip.code 40015

$first = Send-Api "Get" "/api/courses/lessons/$lesson1/play" $null $student.token
Check "first lesson playable" $first.code 0

$null = Send-Api "Post" "/api/learning/progress" @{ lessonId = $lesson1; positionSeconds = 100 } $student.token

$second = Send-Api "Get" "/api/courses/lessons/$lesson2/play" $null $student.token
Check "lesson 2 open after lesson 1" $second.code 0

# ---------- 6. sequential off does not block ----------
Write-Host ""
Write-Host "== 6. non-sequential stays free =="
$freeCourse = (Send-Api "Post" "/api/teacher/courses" @{
    title = "Free Order $sfx"; subjectId = $subjectId
} $teacher.token).data
$freeChapter = (Send-Api "Post" "/api/teacher/courses/$freeCourse/chapters" @{ title = "C1"; sort = 1 } $teacher.token).data
$fk1 = Upload-Video $teacher.token "c"
$null = Send-Api "Post" "/api/teacher/chapters/$freeChapter/lessons" @{ title = "F1"; sort = 1; videoKey = $fk1 } $teacher.token
$fk2 = Upload-Video $teacher.token "d"
$freeLesson2 = (Send-Api "Post" "/api/teacher/chapters/$freeChapter/lessons" @{ title = "F2"; sort = 2; videoKey = $fk2 } $teacher.token).data
$null = Send-Api "Put" "/api/teacher/courses/$freeCourse/submit" $null $teacher.token
$null = Send-Api "Put" "/api/admin/courses/$freeCourse/review" @{ approved = $true } $at

$freePlay = Send-Api "Get" "/api/courses/lessons/$freeLesson2/play" $null $student.token
Check "non-sequential allows jump" $freePlay.code 0

Remove-Item $tmpVideo -Force -ErrorAction SilentlyContinue
Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)