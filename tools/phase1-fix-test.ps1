$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"
$mp4 = "c:\Users\lrs\Desktop\work\etry\tools\probe-120s.mp4"
$fake = "c:\Users\lrs\Desktop\work\etry\tools\probe-fake.mp4"

# Build a minimal but structurally valid mp4: ftyp + moov/mvhd with the given duration.
function U32($n) {
    $bytes = New-Object byte[] 4
    $bytes[0] = ($n -shr 24) -band 0xFF
    $bytes[1] = ($n -shr 16) -band 0xFF
    $bytes[2] = ($n -shr 8) -band 0xFF
    $bytes[3] = $n -band 0xFF
    return , $bytes
}

function New-MinimalMp4($path, $durationSeconds) {
    $ms = New-Object System.IO.MemoryStream
    $w = New-Object System.IO.BinaryWriter($ms)
    # ftyp (32 bytes)
    $w.Write((U32 0x20)); $w.Write([Text.Encoding]::ASCII.GetBytes("ftyp"))
    $w.Write([Text.Encoding]::ASCII.GetBytes("isom")); $w.Write((U32 0x200))
    $w.Write([Text.Encoding]::ASCII.GetBytes("isomiso2avc1mp41"))
    # moov (116 bytes)
    $w.Write((U32 0x74)); $w.Write([Text.Encoding]::ASCII.GetBytes("moov"))
    # mvhd (108 bytes)
    $w.Write((U32 0x6C)); $w.Write([Text.Encoding]::ASCII.GetBytes("mvhd"))
    $w.Write((U32 0))   # version + flags
    $w.Write((U32 0))   # creation time
    $w.Write((U32 0))   # modification time
    $ts = 1000
    $w.Write((U32 $ts))
    $w.Write((U32 ($durationSeconds * $ts)))
    $w.Write((New-Object byte[] 80))
    $w.Flush()
    [IO.File]::WriteAllBytes($path, $ms.ToArray())
}

New-MinimalMp4 $mp4 120
[IO.File]::WriteAllBytes($fake, (New-Object byte[] 65536))

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
        Write-Host ("  PASS  {0,-48} {1}" -f $label, $actual)
    } else {
        $script:fail++
        Write-Host ("  FAIL  {0,-48} got={1} want={2}" -f $label, $actual, $expected)
    }
}

$sfx = Get-Date -Format "HHmmss"
$admin = Send-Api "Post" "/api/auth/login" @{ username = "admin"; password = "admin123" } $null
$at = $admin.data.token
$roles = Send-Api "Get" "/api/admin/roles" $null $at
$teacherRoleId = ($roles.data | Where-Object { $_.code -eq "TEACHER" }).id
$studentRoleId = ($roles.data | Where-Object { $_.code -eq "STUDENT" }).id
$subjectId = (Send-Api "Get" "/api/subjects" $null $null).data[0].id

$tName = "s$sfx"
$null = Send-Api "Post" "/api/admin/users" @{
    username = $tName; initialPassword = "init123456"; nickname = "TeacherS"
    roleIds = @($studentRoleId, $teacherRoleId)
    teacherProfile = @{ realName = "Zhou Jiu"; years = 1; subjectIds = @($subjectId) }
} $at
$first = Send-Api "Post" "/api/auth/login" @{ username = $tName; password = "init123456" } $null
$null = Send-Api "Put" "/api/auth/password" @{ oldPassword = "init123456"; newPassword = "tpass123456" } $first.data.token
$tt = (Send-Api "Post" "/api/auth/login" @{ username = $tName; password = "tpass123456" } $null).data.token

$courseId = (Send-Api "Post" "/api/teacher/courses" @{ title = "Chemistry"; subjectId = $subjectId } $tt).data
$chapterId = (Send-Api "Post" "/api/teacher/courses/$courseId/chapters" @{ title = "C1"; sort = 1 } $tt).data

# ---------- 1. duration comes from the file, not the client ----------
Write-Host ""
Write-Host "== 1. server reads duration from mp4 =="
$sz = (Get-Item $mp4).Length
$ticket = Send-Api "Post" "/api/teacher/videos/ticket" @{
    fileName = "v.mp4"; contentType = "video/mp4"; sizeBytes = $sz
} $tt
$null = curl.exe -s -o NUL -X PUT -H "Content-Type: video/mp4" --upload-file $mp4 "$($ticket.data.uploadUrl)"
$key = $ticket.data.key

# client tries to lie: durationSeconds=1 in the body is now ignored/absent
$lesson = Send-Api "Post" "/api/teacher/chapters/$chapterId/lessons" @{
    title = "L1"; sort = 1; videoKey = $key
} $tt
Check "create lesson with real mp4" $lesson.code 0

$detail = Send-Api "Get" "/api/teacher/courses/$courseId" $null $tt
$dur = $detail.data.chapters[0].lessons[0].durationSeconds
Check "duration read as 120s" $dur 120

# ---------- 2. non-mp4 is rejected ----------
Write-Host ""
Write-Host "== 2. non-mp4 rejected =="
$fakeSize = (Get-Item $fake).Length
$t2 = Send-Api "Post" "/api/teacher/videos/ticket" @{
    fileName = "fake.mp4"; contentType = "video/mp4"; sizeBytes = $fakeSize
} $tt
$null = curl.exe -s -o NUL -X PUT -H "Content-Type: video/mp4" --upload-file $fake "$($t2.data.uploadUrl)"
$bad = Send-Api "Post" "/api/teacher/chapters/$chapterId/lessons" @{
    title = "L-fake"; sort = 2; videoKey = $t2.data.key
} $tt
Check "fake video rejected" $bad.code 40001

# ---------- 3. object key ownership ----------
Write-Host ""
Write-Host "== 3. video key ownership =="
$foreign = $key -replace '/video/\d+/', '/video/999999/'
Check "foreign key built" ($foreign -ne $key) $true
$stolen = Send-Api "Post" "/api/teacher/chapters/$chapterId/lessons" @{
    title = "L2"; sort = 3; videoKey = $foreign
} $tt
Check "foreign key rejected" $stolen.code 40001

# ---------- 4. published course cannot be deleted ----------
Write-Host ""
Write-Host "== 4. published course is not deletable =="
$null = Send-Api "Put" "/api/teacher/courses/$courseId/submit" $null $tt
$null = Send-Api "Put" "/api/admin/courses/$courseId/review" @{ approved = $true } $at
$del = Send-Api "Delete" "/api/teacher/courses/$courseId" $null $tt
Check "delete after publish rejected" $del.code 40013

$null = Send-Api "Put" "/api/teacher/courses/$courseId/offline" $null $tt
$del2 = Send-Api "Delete" "/api/teacher/courses/$courseId" $null $tt
Check "delete after offline rejected" $del2.code 40013

# ---------- 5. list aggregates correctly ----------
Write-Host ""
Write-Host "== 5. list still correct after batch assemble =="
$list = Send-Api "Get" "/api/teacher/courses?page=1&size=10" $null $tt
$row = @($list.data.records | Where-Object { $_.id -eq $courseId })[0]
Check "list code" $list.code 0
Check "lesson count correct" $row.lessonCount 1
Check "teacher name resolved" $row.teacherName "Zhou Jiu"

# ---------- 6. draft course is deletable ----------
Write-Host ""
Write-Host "== 6. draft course is deletable =="
$draftId = (Send-Api "Post" "/api/teacher/courses" @{ title = "Temp"; subjectId = $subjectId } $tt).data
$delDraft = Send-Api "Delete" "/api/teacher/courses/$draftId" $null $tt
Check "draft deletable" $delDraft.code 0

Remove-Item $mp4, $fake -Force -ErrorAction SilentlyContinue
Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)