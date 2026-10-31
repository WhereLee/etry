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

# Build an mp4 whose moov sits at the very end, beyond the 64KB head probe.
# This is what ffmpeg and most cameras produce, and it used to fail to parse.
function New-TailMoovMp4($path, $seconds) {
    $ms = New-Object System.IO.MemoryStream
    $w = New-Object System.IO.BinaryWriter($ms)
    $w.Write((U32 0x20)); $w.Write([Text.Encoding]::ASCII.GetBytes("ftyp"))
    $w.Write([Text.Encoding]::ASCII.GetBytes("isom")); $w.Write((U32 0x200))
    $w.Write([Text.Encoding]::ASCII.GetBytes("isomiso2avc1mp41"))
    $mdatSize = 3 * 1024 * 1024
    $w.Write((U32 ($mdatSize + 8))); $w.Write([Text.Encoding]::ASCII.GetBytes("mdat"))
    $w.Write((New-Object byte[] $mdatSize))
    $w.Write((U32 0x74)); $w.Write([Text.Encoding]::ASCII.GetBytes("moov"))
    $w.Write((U32 0x6C)); $w.Write([Text.Encoding]::ASCII.GetBytes("mvhd"))
    $w.Write((U32 0)); $w.Write((U32 0)); $w.Write((U32 0))
    $w.Write((U32 1000)); $w.Write((U32 ($seconds * 1000)))
    $w.Write((New-Object byte[] 80))
    $w.Flush()
    [IO.File]::WriteAllBytes($path, $ms.ToArray())
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
    $pwd = Send-Api "Put" "/api/auth/password" @{ oldPassword = "init123456"; newPassword = "pass123456" } $login.data.token
    if (-not $pwd.data.needRelogin) { throw "changePassword did not report needRelogin" }
    $again = Send-Api "Post" "/api/auth/login" @{ username = $name; password = "pass123456" } $null
    return @{ userId = $r.data; token = $again.data.token; firstPwd = $pwd }
}

# ---------- 1. mp4 with moov at the tail ----------
Write-Host ""
Write-Host "== 1. moov at tail =="
$teacher = New-User "t$sfx" $true
Check "change password reports needRelogin" $teacher.firstPwd.data.needRelogin $true

New-TailMoovMp4 $tmp 120
$ticket = Send-Api "Post" "/api/teacher/videos/ticket" @{ fileName = "tail.mp4"; contentType = "video/mp4"; sizeBytes = 1000 } $teacher.token
$null = curl.exe -s -o NUL -X PUT -H "Content-Type: video/mp4" --upload-file $tmp "$($ticket.data.uploadUrl)"

$courseId = (Send-Api "Post" "/api/teacher/courses" @{ title = "Tail Moov $sfx"; subjectId = $subjectId } $teacher.token).data
$chapterId = (Send-Api "Post" "/api/teacher/courses/$courseId/chapters" @{ title = "C1"; sort = 1 } $teacher.token).data
$lesson = Send-Api "Post" "/api/teacher/chapters/$chapterId/lessons" @{ title = "L1"; sort = 1; videoKey = $ticket.data.key } $teacher.token
Check "lesson created from tail-moov mp4" $lesson.code 0
$detail = Send-Api "Get" "/api/teacher/courses/$courseId" $null $teacher.token
Check "duration parsed as 120s" $detail.data.chapters[0].lessons[0].durationSeconds 120

# ---------- 2. sequential course rejects lesson without video ----------
Write-Host ""
Write-Host "== 2. sequential requires every lesson to have video =="
$seqCourse = (Send-Api "Post" "/api/teacher/courses" @{
    title = "Seq $sfx"; subjectId = $subjectId; sequential = $true
} $teacher.token).data
$seqChapter = (Send-Api "Post" "/api/teacher/courses/$seqCourse/chapters" @{ title = "C1"; sort = 1 } $teacher.token).data
$null = Send-Api "Post" "/api/teacher/chapters/$seqChapter/lessons" @{ title = "S1"; sort = 1; videoKey = $ticket.data.key } $teacher.token
$null = Send-Api "Post" "/api/teacher/chapters/$seqChapter/lessons" @{ title = "S2-no-video"; sort = 2 } $teacher.token
$submitSeq = Send-Api "Put" "/api/teacher/courses/$seqCourse/submit" $null $teacher.token
Check "sequential submit blocked by empty lesson" $submitSeq.code 40001

# ---------- 3. resubmit keeps first published time ----------
Write-Host ""
Write-Host "== 3. resubmit keeps published_at =="
$null = Send-Api "Put" "/api/teacher/courses/$courseId/submit" $null $teacher.token
$null = Send-Api "Put" "/api/admin/courses/$courseId/review" @{ approved = $true } $at
$firstPublish = (Send-Api "Get" "/api/admin/courses/$courseId" $null $at).data.publishedAt

$null = Send-Api "Put" "/api/teacher/courses/$courseId/offline" $null $teacher.token
$null = Send-Api "Put" "/api/teacher/courses/$courseId" @{
    title = "Tail Moov $sfx v2"; subjectId = $subjectId
} $teacher.token
$null = Send-Api "Put" "/api/teacher/courses/$courseId/submit" $null $teacher.token
$null = Send-Api "Put" "/api/admin/courses/$courseId/review" @{ approved = $true } $at
$secondPublish = (Send-Api "Get" "/api/admin/courses/$courseId" $null $at).data.publishedAt
Check "published_at unchanged after resubmit" ($firstPublish -eq $secondPublish) $true

# ---------- 4. published course can never be deleted ----------
Write-Host ""
Write-Host "== 4. delete guard =="
$null = Send-Api "Put" "/api/teacher/courses/$courseId/offline" $null $teacher.token
$null = Send-Api "Put" "/api/teacher/courses/$courseId/submit" $null $teacher.token
$null = Send-Api "Put" "/api/admin/courses/$courseId/review" @{ approved = $false; rejectReason = "no" } $at
$delRejected = Send-Api "Delete" "/api/teacher/courses/$courseId" $null $teacher.token
Check "rejected but once-published cannot delete" $delRejected.code 40013

$freshDraft = (Send-Api "Post" "/api/teacher/courses" @{ title = "Never $sfx"; subjectId = $subjectId } $teacher.token).data
$delDraft = Send-Api "Delete" "/api/teacher/courses/$freshDraft" $null $teacher.token
Check "never-published draft deletable" $delDraft.code 0

# ---------- 5. subject delete guard ----------
Write-Host ""
Write-Host "== 5. subject guard =="
$newSubject = Send-Api "Post" "/api/admin/subjects" @{ code = "sb$sfx"; name = "Subject $sfx"; sort = 90 } $at
$newSubjectId = $newSubject.data.id
$null = Send-Api "Post" "/api/teacher/courses" @{
    title = "UsesSubject $sfx"; subjectId = $newSubjectId
} $teacher.token
$delSubject = Send-Api "Delete" "/api/admin/subjects/$newSubjectId" $null $at
Check "subject in use cannot delete" $delSubject.code 40009

# ---------- 6. sequential cannot be bypassed via progress report ----------
Write-Host ""
Write-Host "== 6. progress report respects sequence =="
$student = New-User "s$sfx" $false
$seqCourse2 = (Send-Api "Post" "/api/teacher/courses" @{
    title = "Seq2 $sfx"; subjectId = $subjectId; sequential = $true
} $teacher.token).data
$seqCh2 = (Send-Api "Post" "/api/teacher/courses/$seqCourse2/chapters" @{ title = "C1"; sort = 1 } $teacher.token).data
$s1 = (Send-Api "Post" "/api/teacher/chapters/$seqCh2/lessons" @{ title = "P1"; sort = 1; videoKey = $ticket.data.key } $teacher.token).data
$s2 = (Send-Api "Post" "/api/teacher/chapters/$seqCh2/lessons" @{ title = "P2"; sort = 2; videoKey = $ticket.data.key } $teacher.token).data
$null = Send-Api "Put" "/api/teacher/courses/$seqCourse2/submit" $null $teacher.token
$null = Send-Api "Put" "/api/admin/courses/$seqCourse2/review" @{ approved = $true } $at

$bypass = Send-Api "Post" "/api/learning/progress" @{ lessonId = $s2; positionSeconds = 120 } $student.token
Check "cannot report lesson 2 first" $bypass.code 40015

$null = Send-Api "Post" "/api/learning/progress" @{ lessonId = $s1; positionSeconds = 120 } $student.token
$afterFirst = Send-Api "Post" "/api/learning/progress" @{ lessonId = $s2; positionSeconds = 120 } $student.token
Check "lesson 2 reportable after lesson 1" $afterFirst.code 0

# ---------- 7. favorite list carries level ----------
Write-Host ""
Write-Host "== 7. favorite list carries level =="
$null = Send-Api "Post" "/api/courses/$seqCourse2/favorite" $null $student.token
$favList = Send-Api "Get" "/api/favorites?page=1&size=10" $null $student.token
Check "favorite list ok" $favList.code 0
Check "favorite row has status" ($favList.data.records[0].PSObject.Properties.Name -contains "status") $true

Remove-Item $tmp -Force -ErrorAction SilentlyContinue
Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)