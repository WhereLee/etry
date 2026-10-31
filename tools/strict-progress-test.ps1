$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"
$tmp = "c:\Users\lrs\Desktop\work\etry\tools\video-test.mp4"
. "c:\Users\lrs\Desktop\work\etry\tools\lib-mp4.ps1"

# This script must run with LEARNING_STRICT_MODE=true, otherwise the first
# report already marks the lesson complete and the first assertion fails.

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

# a 4-second lesson: 90% is 3.6s, so waiting 5s is enough for the strict check
New-MinimalMp4 $tmp 4
$courseId = (Send-Api "Post" "/api/teacher/courses" @{ title = "Strict $sfx"; subjectId = $subjectId } $teacher.token).data
$chapterId = (Send-Api "Post" "/api/teacher/courses/$courseId/chapters" @{ title = "C1"; sort = 1 } $teacher.token).data
$ticket = Send-Api "Post" "/api/teacher/videos/ticket" @{ fileName = "s.mp4"; contentType = "video/mp4"; sizeBytes = 1000 } $teacher.token
$null = curl.exe -s -o NUL -X PUT -H "Content-Type: video/mp4" --upload-file $tmp "$($ticket.data.uploadUrl)"
$lessonId = (Send-Api "Post" "/api/teacher/chapters/$chapterId/lessons" @{ title = "L1"; sort = 1; videoKey = $ticket.data.key } $teacher.token).data
$null = Send-Api "Put" "/api/teacher/courses/$courseId/submit" $null $teacher.token
$null = Send-Api "Put" "/api/admin/courses/$courseId/review" @{ approved = $true } $at

Write-Host ""
Write-Host "== 1. jumping straight to the end does not complete =="
$null = Send-Api "Post" "/api/learning/progress" @{ lessonId = $lessonId; positionSeconds = 4 } $student.token
$p1 = Send-Api "Get" "/api/learning/courses/$courseId" $null $student.token
Check "position recorded" $p1.data.lessons[0].positionSeconds 4
Check "not completed on first report" $p1.data.lessons[0].completed $false
Check "course percent stays 0" $p1.data.percent 0

Write-Host ""
Write-Host "== 2. after real time passes it completes =="
Start-Sleep -Seconds 5
$null = Send-Api "Post" "/api/learning/progress" @{ lessonId = $lessonId; positionSeconds = 4 } $student.token
$p2 = Send-Api "Get" "/api/learning/courses/$courseId" $null $student.token
Check "completed after waiting" $p2.data.lessons[0].completed $true
Check "course percent 100" $p2.data.percent 100

Write-Host ""
Write-Host "== 3. review gate follows the strict result =="
$review = Send-Api "Post" "/api/courses/$courseId/reviews" @{ rating = 5; content = "ok" } $student.token
Check "review allowed now" $review.code 0

Remove-Item $tmp -Force -ErrorAction SilentlyContinue
Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)