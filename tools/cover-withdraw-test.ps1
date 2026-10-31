$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"
$goodPng = "c:\Users\lrs\Desktop\work\etry\tools\cover-good.png"
$fakePng = "c:\Users\lrs\Desktop\work\etry\tools\cover-fake.png"
$bigPng = "c:\Users\lrs\Desktop\work\etry\tools\cover-big.png"
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

function Upload-Cover($token, $path, $contentType) {
    $out = curl.exe -s -X POST -H "Authorization: Bearer $token" -F "file=@$path;type=$contentType" "$base/api/teacher/covers"
    return $out | ConvertFrom-Json
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

# real PNG header plus padding: magic check passes
$png = [byte[]]@(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + (New-Object byte[] 2048)
[IO.File]::WriteAllBytes($goodPng, $png)
[IO.File]::WriteAllBytes($fakePng, [Text.Encoding]::ASCII.GetBytes("this is definitely not a png"))
$big = [byte[]]@(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + (New-Object byte[] (3 * 1024 * 1024))
[IO.File]::WriteAllBytes($bigPng, $big)

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

Write-Host ""
Write-Host "== 1. cover upload =="
$cover = Upload-Cover $teacher.token $goodPng "image/png"
Check "upload ok" $cover.code 0
$coverKey = $cover.data
Check "key under own directory" ($coverKey -match "/cover/\d+/") $true

$fake = Upload-Cover $teacher.token $fakePng "image/png"
Check "fake png rejected by magic check" $fake.code 40001

$big = Upload-Cover $teacher.token $bigPng "image/png"
Check "3MB image rejected" $big.code 40001

Write-Host ""
Write-Host "== 2. cover shows up on the course =="
$courseId = (Send-Api "Post" "/api/teacher/courses" @{
    title = "Cover $sfx"; subjectId = $subjectId; coverKey = $coverKey
} $teacher.token).data
$detail = Send-Api "Get" "/api/teacher/courses/$courseId" $null $teacher.token
Check "coverUrl present" ([string]::IsNullOrEmpty($detail.data.coverUrl)) $false
Check "coverUrl points to storage" ($detail.data.coverUrl -match "cover") $true

Write-Host ""
Write-Host "== 3. someone else's cover key is rejected =="
$stolen = $coverKey -replace '/cover/\d+/', '/cover/999999/'
$bad = Send-Api "Post" "/api/teacher/courses" @{
    title = "Steal $sfx"; subjectId = $subjectId; coverKey = $stolen
} $teacher.token
Check "foreign cover key rejected" $bad.code 40001

Write-Host ""
Write-Host "== 4. withdraw a pending course =="
$chapterId = (Send-Api "Post" "/api/teacher/courses/$courseId/chapters" @{ title = "C1"; sort = 1 } $teacher.token).data
New-MinimalMp4 $tmpVideo 60
$ticket = Send-Api "Post" "/api/teacher/videos/ticket" @{ fileName = "v.mp4"; contentType = "video/mp4"; sizeBytes = 1000 } $teacher.token
$null = curl.exe -s -o NUL -X PUT -H "Content-Type: video/mp4" --upload-file $tmpVideo "$($ticket.data.uploadUrl)"
$null = Send-Api "Post" "/api/teacher/chapters/$chapterId/lessons" @{ title = "L1"; sort = 1; videoKey = $ticket.data.key } $teacher.token

$submit = Send-Api "Put" "/api/teacher/courses/$courseId/submit" $null $teacher.token
Check "submit ok" $submit.code 0
$pending = Send-Api "Get" "/api/teacher/courses/$courseId" $null $teacher.token
Check "status is pending" $pending.data.status "PENDING"

$withdraw = Send-Api "Put" "/api/teacher/courses/$courseId/withdraw" $null $teacher.token
Check "withdraw ok" $withdraw.code 0
$afterWithdraw = Send-Api "Get" "/api/teacher/courses/$courseId" $null $teacher.token
Check "back to draft" $afterWithdraw.data.status "DRAFT"

$again = Send-Api "Put" "/api/teacher/courses/$courseId/withdraw" $null $teacher.token
Check "withdraw twice rejected" $again.code 40013

$null = Send-Api "Put" "/api/teacher/courses/$courseId/submit" $null $teacher.token
$review = Send-Api "Put" "/api/admin/courses/$courseId/review" @{ approved = $true } $at
Check "can submit again after withdraw" $review.code 0

Remove-Item $goodPng, $fakePng, $bigPng, $tmpVideo -Force -ErrorAction SilentlyContinue
Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)