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

$tName = "f$sfx"
$mk = Send-Api "Post" "/api/admin/users" @{
    username = $tName; initialPassword = "init123456"; nickname = "TeacherF"
    roleIds = @($studentRoleId, $teacherRoleId)
    teacherProfile = @{ realName = "Sun Qi"; years = 1; subjectIds = @($subjectId) }
} $at
Check "setUp create teacher" $mk.code 0
$teacherUserId = $mk.data

$first = Send-Api "Post" "/api/auth/login" @{ username = $tName; password = "init123456" } $null
$null = Send-Api "Put" "/api/auth/password" @{ oldPassword = "init123456"; newPassword = "tpass123456" } $first.data.token
$tt = (Send-Api "Post" "/api/auth/login" @{ username = $tName; password = "tpass123456" } $null).data.token

$courseId = (Send-Api "Post" "/api/teacher/courses" @{ title = "Geometry"; summary = "s"; subjectId = $subjectId } $tt).data
$chapterId = (Send-Api "Post" "/api/teacher/courses/$courseId/chapters" @{ title = "C1"; sort = 1 } $tt).data
# real mp4 header is required now: the server reads duration from the file
New-MinimalMp4 $tmp 300
$ticket = Send-Api "Post" "/api/teacher/videos/ticket" @{ fileName = "v.mp4"; contentType = "video/mp4"; sizeBytes = 1048576 } $tt
$null = curl.exe -s -o NUL -X PUT -H "Content-Type: video/mp4" --upload-file $tmp "$($ticket.data.uploadUrl)"
$lessonId = (Send-Api "Post" "/api/teacher/chapters/$chapterId/lessons" @{
    title = "L1"; sort = 1; videoKey = $ticket.data.key; durationSeconds = 300; sizeBytes = 1048576
} $tt).data
$null = Send-Api "Put" "/api/teacher/courses/$courseId/submit" $null $tt

# ---------- 1. admin can see content while pending ----------
Write-Host ""
Write-Host "== 1. admin can review content (was blind) =="
$pendingDetail = Send-Api "Get" "/api/admin/courses/$courseId" $null $at
Check "admin detail on pending" $pendingDetail.code 0
Check "chapters visible" (@($pendingDetail.data.chapters).Count) 1
Check "lessons visible" (@($pendingDetail.data.chapters[0].lessons).Count) 1
$preview = Send-Api "Get" "/api/admin/courses/lessons/$lessonId/play" $null $at
Check "admin preview play" $preview.code 0
$previewCode = curl.exe -s -o NUL -w "%{http_code}" --max-time 30 "$($preview.data.playUrl)"
Check "preview url reachable" $previewCode "200"

$null = Send-Api "Put" "/api/admin/courses/$courseId/review" @{ approved = $true } $at
$pub = Send-Api "Get" "/api/admin/courses/$courseId" $null $at
Check "published after review" $pub.data.status "PUBLISHED"

# ---------- 2. teacher takes it offline, then edits ----------
Write-Host ""
Write-Host "== 2. teacher offline then edit (was stuck) =="
$off = Send-Api "Put" "/api/teacher/courses/$courseId/offline" $null $tt
Check "teacher can offline" $off.code 0
$afterOff = Send-Api "Get" "/api/teacher/courses/$courseId" $null $tt
Check "status is OFFLINE" $afterOff.data.status "OFFLINE"

$edit = Send-Api "Put" "/api/teacher/courses/$courseId" @{ title = "Geometry v2"; subjectId = $subjectId } $tt
Check "can edit while offline" $edit.code 0

# ---------- 3. empty videoKey must not wipe the video ----------
Write-Host ""
Write-Host "== 3. empty videoKey keeps the video =="
$upd = Send-Api "Put" "/api/teacher/lessons/$lessonId" @{ title = "L1 renamed"; sort = 1; videoKey = "" } $tt
Check "update lesson with empty key" $upd.code 0
$afterUpd = Send-Api "Get" "/api/teacher/courses/$courseId" $null $tt
Check "video still there" ($afterUpd.data.chapters[0].lessons[0].hasVideo) $true

# ---------- 4. resubmit and re-approve ----------
Write-Host ""
Write-Host "== 4. resubmit after edit =="
$sub = Send-Api "Put" "/api/teacher/courses/$courseId/submit" $null $tt
Check "resubmit" $sub.code 0
$null = Send-Api "Put" "/api/admin/courses/$courseId/review" @{ approved = $true } $at
$again = Send-Api "Get" "/api/teacher/courses/$courseId" $null $tt
Check "published again" $again.data.status "PUBLISHED"

# ---------- 5. archived teacher cannot self-restore ----------
Write-Host ""
Write-Host "== 5. archived teacher cannot edit profile =="
$list = Send-Api "Get" "/api/admin/teachers?page=1&size=50" $null $at
$profileId = @($list.data.records | Where-Object { $_.userId -eq $teacherUserId })[0].id
$null = Send-Api "Put" "/api/admin/teachers/$profileId/archive" $null $at

$selfEdit = Send-Api "Put" "/api/teacher/profile" @{
    realName = "Sun Qi"; subjectIds = @($subjectId)
} $tt
Check "archived profile locked" $selfEdit.code 40007

Remove-Item $tmp -Force -ErrorAction SilentlyContinue
Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)