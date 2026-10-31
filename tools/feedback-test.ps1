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

function New-User($name, $withTeacher) {
    $roleIds = @($studentRoleId)
    $body = @{ username = $name; initialPassword = "init123456"; nickname = $name; roleIds = $roleIds }
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
$other = New-User "o$sfx" $false

# teacher builds a published course with one lesson
$courseId = (Send-Api "Post" "/api/teacher/courses" @{ title = "Biology"; subjectId = $subjectId } $teacher.token).data
$chapterId = (Send-Api "Post" "/api/teacher/courses/$courseId/chapters" @{ title = "C1"; sort = 1 } $teacher.token).data
New-MinimalMp4 $tmp 100
$ticket = Send-Api "Post" "/api/teacher/videos/ticket" @{ fileName = "v.mp4"; contentType = "video/mp4"; sizeBytes = 1000 } $teacher.token
$null = curl.exe -s -o NUL -X PUT -H "Content-Type: video/mp4" --upload-file $tmp "$($ticket.data.uploadUrl)"
$lessonId = (Send-Api "Post" "/api/teacher/chapters/$chapterId/lessons" @{ title = "L1"; sort = 1; videoKey = $ticket.data.key } $teacher.token).data
$null = Send-Api "Put" "/api/teacher/courses/$courseId/submit" $null $teacher.token
$null = Send-Api "Put" "/api/admin/courses/$courseId/review" @{ approved = $true } $at

# ---------- 1. review requires learning ----------
Write-Host ""
Write-Host "== 1. review =="
$tooEarly = Send-Api "Post" "/api/courses/$courseId/reviews" @{ rating = 5; content = "nice" } $student.token
Check "review before learning rejected" $tooEarly.code 40001

$null = Send-Api "Post" "/api/learning/progress" @{ lessonId = $lessonId; positionSeconds = 100 } $student.token

$r1 = Send-Api "Post" "/api/courses/$courseId/reviews" @{ rating = 5; content = "great course" } $student.token
Check "review after learning" $r1.code 0

$detail = Send-Api "Get" "/api/courses/$courseId" $null $student.token
Check "rating avg on detail" $detail.data.ratingAvg 5
Check "rating count on detail" $detail.data.ratingCount 1

# same user submits again: should update, not add
$r2 = Send-Api "Post" "/api/courses/$courseId/reviews" @{ rating = 4; content = "changed my mind" } $student.token
Check "resubmit ok" $r2.code 0
$detail2 = Send-Api "Get" "/api/courses/$courseId" $null $student.token
Check "still one review" $detail2.data.ratingCount 1
Check "rating updated to 4" $detail2.data.ratingAvg 4

$listR = Send-Api "Get" "/api/courses/$courseId/reviews?page=1&size=10" $null $student.token
Check "review list" $listR.data.total 1
Check "nickname resolved" ($listR.data.records[0].nickname -ne $null) $true

$mine = Send-Api "Get" "/api/courses/$courseId/reviews/mine" $null $student.token
Check "my review echo" $mine.data.rating 4

# ---------- 2. qa ----------
Write-Host ""
Write-Host "== 2. qa =="
$q = Send-Api "Post" "/api/courses/$courseId/questions" @{ content = "How does mitosis work?" } $student.token
Check "ask" $q.code 0
$questionId = $q.data

$boxBefore = Send-Api "Get" "/api/teacher/dashboard" $null $teacher.token
Check "unanswered counted" $boxBefore.data.summary.unansweredQuestions 1

$a = Send-Api "Post" "/api/questions/$questionId/answers" @{ content = "Chapter 3 explains it" } $teacher.token
Check "answer" $a.code 0

$qd = Send-Api "Get" "/api/questions/$questionId" $null $student.token
Check "detail answers count" (@($qd.data.answers).Count) 1

$boxAfter = Send-Api "Get" "/api/teacher/dashboard" $null $teacher.token
Check "unanswered cleared" $boxAfter.data.summary.unansweredQuestions 0

$qList = Send-Api "Get" "/api/courses/$courseId/questions?page=1&size=10" $null $student.token
Check "question list" $qList.data.total 1
Check "answer count in list" $qList.data.records[0].answerCount 1

$resolve = Send-Api "Put" "/api/questions/$questionId/resolve?resolved=true" $null $student.token
Check "asker can resolve" $resolve.code 0

$resolveByOther = Send-Api "Put" "/api/questions/$questionId/resolve?resolved=false" $null $other.token
Check "outsider cannot resolve" $resolveByOther.code 40301

$resolveByTeacher = Send-Api "Put" "/api/questions/$questionId/resolve?resolved=true" $null $teacher.token
Check "teacher can resolve" $resolveByTeacher.code 0

# ---------- 3. dashboard ----------
Write-Host ""
Write-Host "== 3. dashboard =="
$box = Send-Api "Get" "/api/teacher/dashboard" $null $teacher.token
Check "dashboard code" $box.code 0
Check "course total" $box.data.summary.courseTotal 1
Check "published courses" $box.data.summary.publishedCourses 1
Check "learners" $box.data.summary.learnerTotal 1
Check "completers" $box.data.summary.completerTotal 1
Check "teacher rating avg" $box.data.summary.ratingAvg 4
Check "per-course completion rate" $box.data.courses[0].completionRate 100

$byStudent = Send-Api "Get" "/api/teacher/dashboard" $null $student.token
Check "student cannot view dashboard" $byStudent.code 40302

Remove-Item $tmp -Force -ErrorAction SilentlyContinue
Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)