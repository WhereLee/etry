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

$courseId = (Send-Api "Post" "/api/teacher/courses" @{ title = "Sec $sfx"; subjectId = $subjectId } $teacher.token).data
$chapterId = (Send-Api "Post" "/api/teacher/courses/$courseId/chapters" @{ title = "C1"; sort = 1 } $teacher.token).data
New-MinimalMp4 $tmp 100
$ticket = Send-Api "Post" "/api/teacher/videos/ticket" @{ fileName = "v.mp4"; contentType = "video/mp4"; sizeBytes = 1000 } $teacher.token
$null = curl.exe -s -o NUL -X PUT -H "Content-Type: video/mp4" --upload-file $tmp "$($ticket.data.uploadUrl)"
$lessonId = (Send-Api "Post" "/api/teacher/chapters/$chapterId/lessons" @{ title = "L1"; sort = 1; videoKey = $ticket.data.key } $teacher.token).data
$null = Send-Api "Put" "/api/teacher/courses/$courseId/submit" $null $teacher.token
$null = Send-Api "Put" "/api/admin/courses/$courseId/review" @{ approved = $true } $at

# ---------- 1. review requires a completed lesson ----------
Write-Host ""
Write-Host "== 1. review gate =="
$null = Send-Api "Post" "/api/learning/progress" @{ lessonId = $lessonId; positionSeconds = 0 } $student.token
$tooEarly = Send-Api "Post" "/api/courses/$courseId/reviews" @{ rating = 1; content = "bad" } $student.token
Check "progress row alone is not enough" $tooEarly.code 40001

$null = Send-Api "Post" "/api/learning/progress" @{ lessonId = $lessonId; positionSeconds = 100 } $student.token
$ok = Send-Api "Post" "/api/courses/$courseId/reviews" @{ rating = 5; content = "good" } $student.token
Check "after completing a lesson" $ok.code 0

# ---------- 2. materials locked while published ----------
Write-Host ""
Write-Host "== 2. material edit lock =="
[IO.File]::WriteAllBytes($tmp, (New-Object byte[] 512))
$mt = Send-Api "Post" "/api/teacher/materials/ticket" @{ fileName = "n.pdf"; contentType = "application/pdf"; sizeBytes = 512 } $teacher.token
$null = curl.exe -s -o NUL -X PUT -H "Content-Type: application/pdf" --upload-file $tmp "$($mt.data.uploadUrl)"
$locked = Send-Api "Post" "/api/teacher/courses/$courseId/materials" @{
    title = "notes"; fileName = "n.pdf"; fileKey = $mt.data.key
} $teacher.token
Check "cannot add material when published" $locked.code 40013

# ---------- 3. object key path traversal ----------
Write-Host ""
Write-Host "== 3. key traversal =="
$null = Send-Api "Put" "/api/teacher/courses/$courseId/offline" $null $teacher.token
$traversal = Send-Api "Post" "/api/teacher/courses/$courseId/materials" @{
    title = "x"; fileName = "x.pdf"; fileKey = "material/1/../2/x.pdf"
} $teacher.token
Check "dot-dot key rejected" $traversal.code 40001

# ---------- 4. input list limits ----------
Write-Host ""
Write-Host "== 4. list limits =="
$manySubjects = 1..30 | ForEach-Object { $subjectId }
$tooMany = Send-Api "Post" "/api/teacher/apply" @{
    realName = "x"; title = "y"; bio = "z"; years = 1; subjectIds = $manySubjects
} $student.token
Check "more than 10 subjects rejected" $tooMany.code 40001

# ---------- 5. public teacher view hides login name ----------
Write-Host ""
Write-Host "== 5. public teacher view =="
$profiles = Send-Api "Get" "/api/admin/teachers?page=1&size=50" $null $at
$mine = @($profiles.data.records | Where-Object { $_.realName -eq "Teacher t$sfx" })[0]
$publicProfile = Send-Api "Get" "/api/teachers/$($mine.id)" $null $null
Check "public detail ok" $publicProfile.code 0
$fields = $publicProfile.data.PSObject.Properties.Name
Check "no username in public view" ($fields -contains "username") $false
Check "no userId in public view" ($fields -contains "userId") $false
Check "realName still present" ($fields -contains "realName") $true

Remove-Item $tmp -Force -ErrorAction SilentlyContinue
Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)