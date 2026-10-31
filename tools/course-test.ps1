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
        Write-Host ("  PASS  {0,-44} {1}" -f $label, $actual)
    } else {
        $script:fail++
        Write-Host ("  FAIL  {0,-44} got={1} want={2}" -f $label, $actual, $expected)
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

# ---------- setUp: teacher + student ----------
$tName = "c$sfx"
$mk = Send-Api "Post" "/api/admin/users" @{
    username = $tName; initialPassword = "init123456"; nickname = "TeacherC"
    roleIds = @($studentRoleId, $teacherRoleId)
    teacherProfile = @{ realName = "Zhao Liu"; years = 2; subjectIds = @($subjectId) }
} $at
Check "setUp create teacher" $mk.code 0

$first = Send-Api "Post" "/api/auth/login" @{ username = $tName; password = "init123456" } $null
$null = Send-Api "Put" "/api/auth/password" @{ oldPassword = "init123456"; newPassword = "tpass123456" } $first.data.token
$tt = (Send-Api "Post" "/api/auth/login" @{ username = $tName; password = "tpass123456" } $null).data.token

$sName = "d$sfx"
$null = Send-Api "Post" "/api/admin/users" @{
    username = $sName; initialPassword = "init123456"; nickname = "StudentD"; roleIds = @($studentRoleId)
} $at
$sf = Send-Api "Post" "/api/auth/login" @{ username = $sName; password = "init123456" } $null
$null = Send-Api "Put" "/api/auth/password" @{ oldPassword = "init123456"; newPassword = "spass123456" } $sf.data.token
$st = (Send-Api "Post" "/api/auth/login" @{ username = $sName; password = "spass123456" } $null).data.token

# ---------- 1. teacher builds a course ----------
Write-Host ""
Write-Host "== 1. teacher side =="
$course = Send-Api "Post" "/api/teacher/courses" @{
    title = "Algebra 101"; summary = "basic algebra"; description = "chapter by chapter"
    subjectId = $subjectId
} $tt
Check "create course" $course.code 0
$courseId = $course.data

$chapter = Send-Api "Post" "/api/teacher/courses/$courseId/chapters" @{ title = "Chapter 1"; sort = 1 } $tt
Check "add chapter" $chapter.code 0
$chapterId = $chapter.data

# video ticket + direct upload
# real mp4 header is required now: the server reads duration from the file
New-MinimalMp4 $tmp 600
$ticket = Send-Api "Post" "/api/teacher/videos/ticket" @{
    fileName = "lesson1.mp4"; contentType = "video/mp4"; sizeBytes = 1048576
} $tt
Check "video ticket" $ticket.code 0
$videoKey = $ticket.data.key
$uploadUrl = $ticket.data.uploadUrl
Write-Host ("  key = " + $videoKey)
$putCode = curl.exe -s -o NUL -w "%{http_code}" -X PUT -H "Content-Type: video/mp4" --upload-file $tmp "$uploadUrl"
Check "direct upload to COS" $putCode "200"

$lesson = Send-Api "Post" "/api/teacher/chapters/$chapterId/lessons" @{
    title = "Lesson 1"; sort = 1; videoKey = $videoKey; durationSeconds = 600; sizeBytes = 1048576
} $tt
Check "add lesson" $lesson.code 0
$lessonId = $lesson.data

$submitEarly = Send-Api "Post" "/api/teacher/courses/$courseId/chapters" @{ title = "Chapter 0" } $tt
$sub = Send-Api "Put" "/api/teacher/courses/$courseId/submit" $null $tt
Check "submit for review" $sub.code 0

$editPending = Send-Api "Put" "/api/teacher/courses/$courseId" @{
    title = "Hacked"; subjectId = $subjectId
} $tt
Check "cannot edit while pending" $editPending.code 40013

# ---------- 2. student cannot build course ----------
Write-Host ""
Write-Host "== 2. student cannot create course =="
$byStudent = Send-Api "Post" "/api/teacher/courses" @{ title = "Nope" } $st
Check "student rejected" $byStudent.code 40302

# ---------- 3. admin review ----------
Write-Host ""
Write-Host "== 3. admin review =="
$pending = Send-Api "Get" "/api/admin/courses?status=PENDING&page=1&size=10" $null $at
$found = @($pending.data.records | Where-Object { $_.id -eq $courseId }).Count
Check "appears in pending list" $found 1

$hiddenBefore = Send-Api "Get" "/api/courses/$courseId" $null $st
Check "hidden before publish" $hiddenBefore.code 40408

$review = Send-Api "Put" "/api/admin/courses/$courseId/review" @{ approved = $true } $at
Check "approve" $review.code 0

# ---------- 4. public side ----------
Write-Host ""
Write-Host "== 4. public side =="
$list = Send-Api "Get" "/api/courses?page=1&size=10" $null $st
$inList = @($list.data.records | Where-Object { $_.id -eq $courseId }).Count
Check "visible in public list" $inList 1

$detail = Send-Api "Get" "/api/courses/$courseId" $null $st
Check "detail ok" $detail.code 0
# one extra chapter was added before submit, so expect 2 here
Check "chapter count" (@($detail.data.chapters).Count) 2
$withLesson = @($detail.data.chapters | Where-Object { @($_.lessons).Count -gt 0 })[0]
Check "lesson has video flag" ($withLesson.lessons[0].hasVideo) $true
$names = @($withLesson.lessons[0].PSObject.Properties.Name)
Check "no raw video key leaked" ($names -notcontains "videoKey") $true

$play = Send-Api "Get" "/api/courses/lessons/$lessonId/play" $null $st
Check "play url issued" $play.code 0
$playCode = curl.exe -s -o NUL -w "%{http_code}" --max-time 30 "$($play.data.playUrl)"
Check "play url reachable" $playCode "200"

# ---------- 5. offline / publish ----------
Write-Host ""
Write-Host "== 5. offline and publish again =="
$off = Send-Api "Put" "/api/admin/courses/$courseId/offline" $null $at
Check "offline" $off.code 0
$hidden = Send-Api "Get" "/api/courses/$courseId" $null $st
Check "hidden after offline" $hidden.code 40408
$playAfterOffline = Send-Api "Get" "/api/courses/lessons/$lessonId/play" $null $st
Check "play blocked after offline" $playAfterOffline.code 40408

$pub = Send-Api "Put" "/api/admin/courses/$courseId/publish" $null $at
Check "publish again" $pub.code 0
$visible = Send-Api "Get" "/api/courses/$courseId" $null $st
Check "visible again" $visible.code 0

# ---------- 6. student has no review permission ----------
Write-Host ""
Write-Host "== 6. student cannot review =="
$reviewByStudent = Send-Api "Put" "/api/admin/courses/$courseId/review" @{ approved = $true } $st
Check "review needs permission" $reviewByStudent.code 40301

Remove-Item $tmp -Force -ErrorAction SilentlyContinue
Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)
