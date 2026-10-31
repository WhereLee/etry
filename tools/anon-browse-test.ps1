$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"

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

# admin only used to find a published course and to check that logged-in paths still work
$admin = Send-Api "Post" "/api/auth/login" @{ username = "admin"; password = "admin123" } $null
$at = $admin.data.token

$published = Send-Api "Get" "/api/admin/courses?status=PUBLISHED&page=1&size=10" $null $at
if (@($published.data.records).Count -eq 0) {
    Write-Host "no published course found; run feedback-test.ps1 first"
    exit 1
}
$courseId = $published.data.records[0].id
$detail = Send-Api "Get" "/api/admin/courses/$courseId" $null $at
$lessonId = $null
foreach ($ch in $detail.data.chapters) {
    foreach ($ls in $ch.lessons) {
        if ($ls.hasVideo) { $lessonId = $ls.id; break }
    }
    if ($lessonId) { break }
}
Write-Host ("setUp: courseId=$courseId lessonId=$lessonId")

# ---------- 1. anonymous can read list and detail ----------
Write-Host ""
Write-Host "== 1. anonymous read =="
$listNoToken = Send-Api "Get" "/api/courses?page=1&size=10" $null $null
Check "anonymous list" $listNoToken.code 0
Check "list has records" (@($listNoToken.data.records).Count -gt 0) $true

$detailNoToken = Send-Api "Get" "/api/courses/$courseId" $null $null
Check "anonymous detail" $detailNoToken.code 0
Check "chapters visible" (@($detailNoToken.data.chapters).Count -gt 0) $true
Check "rating fields present" ($detailNoToken.data.PSObject.Properties.Name -contains "ratingAvg") $true
Check "no video key in payload" (($detailNoToken.data | ConvertTo-Json -Depth 6) -notmatch "videoKey") $true

# ---------- 2. everything else still requires login ----------
Write-Host ""
Write-Host "== 2. write and private paths still need login =="
$play = Send-Api "Get" "/api/courses/lessons/$lessonId/play" $null $null
Check "play needs login" $play.code 40101

$reviews = Send-Api "Get" "/api/courses/$courseId/reviews" $null $null
Check "review list needs login" $reviews.code 40101

$questions = Send-Api "Get" "/api/courses/$courseId/questions" $null $null
Check "question list needs login" $questions.code 40101

$submit = Send-Api "Post" "/api/courses/$courseId/reviews" @{ rating = 1; content = "spam" } $null
Check "posting review needs login" $submit.code 40101

$teacherCourse = Send-Api "Get" "/api/teacher/courses" $null $null
Check "teacher api needs login" $teacherCourse.code 40101

$adminApi = Send-Api "Get" "/api/admin/courses" $null $null
Check "admin api needs login" $adminApi.code 40101

# ---------- 3. prefix is not leaked ----------
Write-Host ""
Write-Host "== 3. path prefix not leaked =="
$fakePlay = Send-Api "Get" "/api/courses/lessons" $null $null
Check "courses/lessons not public" $fakePlay.code 40101
$fakeSub = Send-Api "Get" "/api/courses/$courseId/nonsense" $null $null
Check "courses/{id}/xxx not public" $fakeSub.code 40101

# ---------- 4. logged-in still works ----------
Write-Host ""
Write-Host "== 4. logged-in unchanged =="
$detailWithToken = Send-Api "Get" "/api/courses/$courseId" $null $at
Check "detail with token" $detailWithToken.code 0
$playWithToken = Send-Api "Get" "/api/courses/lessons/$lessonId/play" $null $at
Check "play with token" $playWithToken.code 0

Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)