$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"
$tmpVideo = "c:\Users\lrs\Desktop\work\etry\tools\video-test.mp4"
$tmpPdf = "c:\Users\lrs\Desktop\work\etry\tools\material-test.pdf"
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

# teacher builds and publishes a course
$courseId = (Send-Api "Post" "/api/teacher/courses" @{ title = "History"; subjectId = $subjectId } $teacher.token).data
$chapterId = (Send-Api "Post" "/api/teacher/courses/$courseId/chapters" @{ title = "C1"; sort = 1 } $teacher.token).data
New-MinimalMp4 $tmpVideo 100
$ticket = Send-Api "Post" "/api/teacher/videos/ticket" @{ fileName = "v.mp4"; contentType = "video/mp4"; sizeBytes = 1000 } $teacher.token
$null = curl.exe -s -o NUL -X PUT -H "Content-Type: video/mp4" --upload-file $tmpVideo "$($ticket.data.uploadUrl)"
$null = Send-Api "Post" "/api/teacher/chapters/$chapterId/lessons" @{ title = "L1"; sort = 1; videoKey = $ticket.data.key } $teacher.token
$null = Send-Api "Put" "/api/teacher/courses/$courseId/submit" $null $teacher.token
$null = Send-Api "Put" "/api/admin/courses/$courseId/review" @{ approved = $true } $at

# ---------- 1. favorite ----------
Write-Host ""
Write-Host "== 1. favorite =="
$fav = Send-Api "Post" "/api/courses/$courseId/favorite" $null $student.token
Check "add favorite" $fav.code 0

$favAgain = Send-Api "Post" "/api/courses/$courseId/favorite" $null $student.token
Check "repeat favorite is idempotent" $favAgain.code 0

$favList = Send-Api "Get" "/api/favorites?page=1&size=10" $null $student.token
Check "favorite list total" $favList.data.total 1

$detailFav = Send-Api "Get" "/api/courses/$courseId" $null $student.token
Check "detail shows favorited" $detailFav.data.favorited $true

$detailAnon = Send-Api "Get" "/api/courses/$courseId" $null $null
Check "anonymous favorited false" $detailAnon.data.favorited $false

# draft course cannot be favorited
$draftId = (Send-Api "Post" "/api/teacher/courses" @{ title = "Draft only"; subjectId = $subjectId } $teacher.token).data
$favDraft = Send-Api "Post" "/api/courses/$draftId/favorite" $null $student.token
Check "cannot favorite draft" $favDraft.code 40408

$unfav = Send-Api "Delete" "/api/courses/$courseId/favorite" $null $student.token
Check "remove favorite" $unfav.code 0
$favList2 = Send-Api "Get" "/api/favorites?page=1&size=10" $null $student.token
Check "favorite list empty" $favList2.data.total 0

# ---------- 2. material ----------
Write-Host ""
Write-Host "== 2. material =="
[IO.File]::WriteAllBytes($tmpPdf, (New-Object byte[] 2048))

$mt = Send-Api "Post" "/api/teacher/materials/ticket" @{
    fileName = "notes.pdf"; contentType = "application/pdf"; sizeBytes = 2048
} $teacher.token
Check "material ticket" $mt.code 0
$null = curl.exe -s -o NUL -X PUT -H "Content-Type: application/pdf" --upload-file $tmpPdf "$($mt.data.uploadUrl)"

$add = Send-Api "Post" "/api/teacher/courses/$courseId/materials" @{
    title = "Chapter 1 notes"; fileName = "notes.pdf"; fileKey = $mt.data.key; sort = 1
} $teacher.token
Check "register material" $add.code 0
$materialId = $add.data

$learnerList = Send-Api "Get" "/api/courses/$courseId/materials" $null $student.token
Check "learner sees material" (@($learnerList.data).Count) 1
Check "size read from storage" ($learnerList.data[0].sizeBytes -gt 0) $true

$dlCode = curl.exe -s -o NUL -w "%{http_code}" --max-time 20 "$($learnerList.data[0].downloadUrl)"
Check "download url reachable" $dlCode "200"

$anonList = Send-Api "Get" "/api/courses/$courseId/materials" $null $null
Check "anonymous cannot list material" $anonList.code 40101

# foreign key is rejected
$foreignKey = $mt.data.key -replace '/material/\d+/', '/material/999999/'
$steal = Send-Api "Post" "/api/teacher/courses/$courseId/materials" @{
    title = "steal"; fileName = "x.pdf"; fileKey = $foreignKey
} $teacher.token
Check "foreign key rejected" $steal.code 40001

# non-document type is rejected at ticket time
$badType = Send-Api "Post" "/api/teacher/materials/ticket" @{
    fileName = "evil.exe"; contentType = "application/x-msdownload"; sizeBytes = 100
} $teacher.token
Check "exe rejected" $badType.code 40001

# teacher list works even though course is published
$teacherList = Send-Api "Get" "/api/teacher/courses/$courseId/materials" $null $teacher.token
Check "teacher list" (@($teacherList.data).Count) 1

# delete removes the record and the object
$del = Send-Api "Delete" "/api/teacher/materials/$materialId" $null $teacher.token
Check "delete material" $del.code 0
$afterDel = Send-Api "Get" "/api/courses/$courseId/materials" $null $student.token
Check "material list empty" (@($afterDel.data).Count) 0
$goneCode = curl.exe -s -o NUL -w "%{http_code}" --max-time 20 "$($learnerList.data[0].downloadUrl)"
Check "object removed from storage" ($goneCode -ne "200") $true

# ---------- 3. subject rename ----------
Write-Host ""
Write-Host "== 3. subject rename =="
$newSubject = Send-Api "Post" "/api/admin/subjects" @{
    code = "tmp$sfx"; name = "Temp Subject"; sort = 99
} $at
Check "create temp subject" $newSubject.code 0
$newSubjectId = $newSubject.data.id

$rename = Send-Api "Put" "/api/admin/subjects/$newSubjectId" @{ name = "Renamed Subject"; sort = 98 } $at
Check "rename subject" $rename.code 0

$all = Send-Api "Get" "/api/subjects" $null $null
$renamed = @($all.data | Where-Object { $_.id -eq $newSubjectId })[0]
Check "name updated" $renamed.name "Renamed Subject"

$byStudent = Send-Api "Put" "/api/admin/subjects/$newSubjectId" @{ name = "Hack" } $student.token
Check "student cannot rename" $byStudent.code 40301

$null = Send-Api "Delete" "/api/admin/subjects/$newSubjectId" $null $at

Remove-Item $tmpVideo, $tmpPdf -Force -ErrorAction SilentlyContinue
Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)