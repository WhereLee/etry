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

$sfx = Get-Date -Format "HHmmss"
$admin = Send-Api "Post" "/api/auth/login" @{ username = "admin"; password = "admin123" } $null
$at = $admin.data.token
$roles = Send-Api "Get" "/api/admin/roles" $null $at
$teacherRoleId = ($roles.data | Where-Object { $_.code -eq "TEACHER" }).id
$studentRoleId = ($roles.data | Where-Object { $_.code -eq "STUDENT" }).id
$subjectId = (Send-Api "Get" "/api/subjects" $null $null).data[0].id

# setUp: teacher with 2 video lessons + 1 empty lesson
$tName = "l$sfx"
$mk = Send-Api "Post" "/api/admin/users" @{
    username = $tName; initialPassword = "init123456"; nickname = "TeacherL"
    roleIds = @($studentRoleId, $teacherRoleId)
    teacherProfile = @{ realName = "Qian Ba"; years = 2; subjectIds = @($subjectId) }
} $at
$first = Send-Api "Post" "/api/auth/login" @{ username = $tName; password = "init123456" } $null
$null = Send-Api "Put" "/api/auth/password" @{ oldPassword = "init123456"; newPassword = "tpass123456" } $first.data.token
$tt = (Send-Api "Post" "/api/auth/login" @{ username = $tName; password = "tpass123456" } $null).data.token

$courseId = (Send-Api "Post" "/api/teacher/courses" @{ title = "Physics"; summary = "p"; subjectId = $subjectId } $tt).data
$chapterId = (Send-Api "Post" "/api/teacher/courses/$courseId/chapters" @{ title = "C1"; sort = 1 } $tt).data

# 100s mp4: the progress test asserts against the duration read by the server
New-MinimalMp4 $tmp 100
function New-Lesson($title, $sort, $withVideo) {
    $videoKey = $null
    if ($withVideo) {
        $ticket = Send-Api "Post" "/api/teacher/videos/ticket" @{ fileName = "v.mp4"; contentType = "video/mp4"; sizeBytes = 1048576 } $tt
        $null = curl.exe -s -o NUL -X PUT -H "Content-Type: video/mp4" --upload-file $tmp "$($ticket.data.uploadUrl)"
        $videoKey = $ticket.data.key
    }
    $body = @{ title = $title; sort = $sort; durationSeconds = 100 }
    if ($videoKey) { $body["videoKey"] = $videoKey; $body["sizeBytes"] = 1048576 }
    return (Send-Api "Post" "/api/teacher/chapters/$chapterId/lessons" $body $tt).data
}

$lesson1 = New-Lesson "L1" 1 $true
$lesson2 = New-Lesson "L2" 2 $true
$lessonEmpty = New-Lesson "L3-no-video" 3 $false

$null = Send-Api "Put" "/api/teacher/courses/$courseId/submit" $null $tt
$null = Send-Api "Put" "/api/admin/courses/$courseId/review" @{ approved = $true } $at

# student account
$sName = "m$sfx"
$null = Send-Api "Post" "/api/admin/users" @{
    username = $sName; initialPassword = "init123456"; nickname = "StudentM"; roleIds = @($studentRoleId)
} $at
$sf = Send-Api "Post" "/api/auth/login" @{ username = $sName; password = "init123456" } $null
$null = Send-Api "Put" "/api/auth/password" @{ oldPassword = "init123456"; newPassword = "spass123456" } $sf.data.token
$st = (Send-Api "Post" "/api/auth/login" @{ username = $sName; password = "spass123456" } $null).data.token

# ---------- 1. progress reporting ----------
Write-Host ""
Write-Host "== 1. progress =="
$r1 = Send-Api "Post" "/api/learning/progress" @{ lessonId = $lesson1; positionSeconds = 50 } $st
Check "report 50 percent" $r1.code 0

$p1 = Send-Api "Get" "/api/learning/courses/$courseId" $null $st
Check "course progress code" $p1.code 0
Check "no lesson completed yet" $p1.data.completedLessons 0
Check "percent is 0" $p1.data.percent 0
Check "position recorded" $p1.data.lessons[0].positionSeconds 50

$r2 = Send-Api "Post" "/api/learning/progress" @{ lessonId = $lesson1; positionSeconds = 100 } $st
Check "report 100 percent" $r2.code 0
$p2 = Send-Api "Get" "/api/learning/courses/$courseId" $null $st
Check "lesson1 completed" $p2.data.lessons[0].completed $true
Check "completed count 1" $p2.data.completedLessons 1

# rewind must not clear the completed flag
$r3 = Send-Api "Post" "/api/learning/progress" @{ lessonId = $lesson1; positionSeconds = 10 } $st
Check "report rewind" $r3.code 0
$p3 = Send-Api "Get" "/api/learning/courses/$courseId" $null $st
Check "completed flag kept" $p3.data.lessons[0].completed $true
Check "position moved back" $p3.data.lessons[0].positionSeconds 10

$null = Send-Api "Post" "/api/learning/progress" @{ lessonId = $lesson2; positionSeconds = 100 } $st
$p4 = Send-Api "Get" "/api/learning/courses/$courseId" $null $st
# only the 2 lessons with video count: an empty lesson can never be completed,
# so counting it would make the course percent never reach 100
Check "2 of 2 graded lessons done" $p4.data.completedLessons 2
Check "percent 100" $p4.data.percent 100

# ---------- 2. my courses ----------
Write-Host ""
Write-Host "== 2. my courses =="
$mine = Send-Api "Get" "/api/learning/my-courses?limit=10" $null $st
Check "my courses code" $mine.code 0
$row = @($mine.data | Where-Object { $_.courseId -eq $courseId })[0]
Check "course in my list" ($row -ne $null) $true
Check "total lessons" $row.totalLessons 2
Check "completed lessons" $row.completedLessons 2

# ---------- 3. guards ----------
Write-Host ""
Write-Host "== 3. guards =="
$bad = Send-Api "Post" "/api/learning/progress" @{ lessonId = 99999999; positionSeconds = 10 } $st
Check "unknown lesson rejected" $bad.code 40410
$noVideo = Send-Api "Post" "/api/learning/progress" @{ lessonId = $lessonEmpty; positionSeconds = 10 } $st
Check "lesson without video rejected" $noVideo.code 40001

# ---------- 4. stats ----------
Write-Host ""
Write-Host "== 4. stats =="
$ov = Send-Api "Get" "/api/admin/stats/overview" $null $at
Check "overview code" $ov.code 0
Check "course total >= 1" ($ov.data.courseTotal -ge 1) $true
Check "published >= 1" ($ov.data.coursePublished -ge 1) $true
Check "learning records >= 2" ($ov.data.learningRecords -ge 2) $true
Check "completed lessons >= 2" ($ov.data.lessonCompleted -ge 2) $true
Check "user total >= 3" ($ov.data.userTotal -ge 3) $true
Check "teacher active >= 1" ($ov.data.teacherActive -ge 1) $true

$top = Send-Api "Get" "/api/admin/stats/top-courses?limit=5" $null $at
Check "top courses code" $top.code 0
$myCourse = @($top.data | Where-Object { $_.courseId -eq $courseId })[0]
Check "course appears in top" ($myCourse -ne $null) $true
Check "one learner" $myCourse.learners 1

$byStudent = Send-Api "Get" "/api/admin/stats/overview" $null $st
Check "stats needs permission" $byStudent.code 40301

Remove-Item $tmp -Force -ErrorAction SilentlyContinue
Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)