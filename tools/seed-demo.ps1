$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"
$tmpVideo = "c:\Users\lrs\Desktop\work\etry\tools\seed-video.mp4"
$tmpPng = "c:\Users\lrs\Desktop\work\etry\tools\seed-cover.png"
. "c:\Users\lrs\Desktop\work\etry\tools\lib-mp4.ps1"

# Idempotent demo seed: creates tags, one teacher, and two published courses.
# Safe to run repeatedly; it skips what already exists by title / tag name.

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

$admin = Send-Api "Post" "/api/auth/login" @{ username = "admin"; password = "admin123" } $null
$at = $admin.data.token
$roles = Send-Api "Get" "/api/admin/roles" $null $at
$teacherRoleId = ($roles.data | Where-Object { $_.code -eq "TEACHER" }).id
$studentRoleId = ($roles.data | Where-Object { $_.code -eq "STUDENT" }).id
$subjectId = (Send-Api "Get" "/api/subjects" $null $null).data[0].id

Write-Host "== tags =="
$tagNames = @("零基础", "实战", "面试", "进阶")
$tagIds = @()
$existingTags = Send-Api "Get" "/api/tags" $null $null
foreach ($name in $tagNames) {
    $hit = @($existingTags.data | Where-Object { $_.name -eq $name })[0]
    if ($hit) {
        $tagIds += $hit.id
        Write-Host "  reuse tag $name"
        continue
    }
    $created = Send-Api "Post" "/api/admin/tags" @{ name = $name; sort = $tagIds.Count + 1 } $at
    $tagIds += $created.data.id
    Write-Host "  create tag $name"
}

Write-Host "== teacher =="
$teacherName = "teacher"
$login = $null
try {
    $login = Send-Api "Post" "/api/auth/login" @{ username = $teacherName; password = "teacher123" } $null
} catch {
    $login = $null
}

if ($null -eq $login -or $login.code -ne 0) {
    $users = Send-Api "Get" "/api/admin/users?page=1&size=50&keyword=$teacherName" $null $at
    if (@($users.data.records | Where-Object { $_.username -eq $teacherName }).Count -eq 0) {
        $null = Send-Api "Post" "/api/admin/users" @{
            username = $teacherName; initialPassword = "init123456"; nickname = "李老师"
            roleIds = @($studentRoleId, $teacherRoleId)
            teacherProfile = @{ realName = "李老师"; title = "高级讲师"; years = 6; subjectIds = @($subjectId) }
        } $at
        Write-Host "  created teacher account"
    }
    $first = Send-Api "Post" "/api/auth/login" @{ username = $teacherName; password = "init123456" } $null
    $null = Send-Api "Put" "/api/auth/password" @{ oldPassword = "init123456"; newPassword = "teacher123" } $first.data.token
    $login = Send-Api "Post" "/api/auth/login" @{ username = $teacherName; password = "teacher123" } $null
    Write-Host "  password set to teacher123"
}
$tt = $login.data.token

# Draw a real PNG. The old 8-byte-header trick passes the backend magic check
# but browsers cannot decode it, so covers render as blank grey blocks.
Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap 480, 270
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::FromArgb(37, 99, 235))
$font = New-Object System.Drawing.Font("Segoe UI", 28, [System.Drawing.FontStyle]::Bold)
$brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
$g.DrawString("etry", $font, $brush, 24, 100)
$g.Dispose()
$bmp.Save($tmpPng, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
New-MinimalMp4 $tmpVideo 240

function New-Course($title, $summary, $description, $level, $tagId, $lessons) {
    $existing = Send-Api "Get" "/api/teacher/courses?page=1&size=50" $null $tt
    $hit = @($existing.data.records | Where-Object { $_.title -eq $title })[0]
    if ($hit) {
        Write-Host "  skip existing course $title (id=$($hit.id), status=$($hit.status))"
        return $hit.id
    }

    $coverKey = $null
    try {
        $out = curl.exe -s -X POST -H "Authorization: Bearer $tt" -F "file=@$tmpPng;type=image/png" "$base/api/teacher/covers"
        $coverKey = ($out | ConvertFrom-Json).data
    } catch {
        $coverKey = $null
    }

    $courseId = (Send-Api "Post" "/api/teacher/courses" @{
        title = $title; summary = $summary; description = $description
        subjectId = $subjectId; level = $level; tagIds = @($tagId)
        coverKey = $coverKey; sequential = $false
    } $tt).data

    $chapterId = (Send-Api "Post" "/api/teacher/courses/$courseId/chapters" @{ title = "第一章 入门"; sort = 1 } $tt).data
    $sort = 1
    foreach ($lessonTitle in $lessons) {
        $ticket = Send-Api "Post" "/api/teacher/videos/ticket" @{ fileName = "v.mp4"; contentType = "video/mp4"; sizeBytes = 1000 } $tt
        $null = curl.exe -s -o NUL -X PUT -H "Content-Type: video/mp4" --upload-file $tmpVideo "$($ticket.data.uploadUrl)"
        $null = Send-Api "Post" "/api/teacher/chapters/$chapterId/lessons" @{
            title = $lessonTitle; sort = $sort; videoKey = $ticket.data.key
        } $tt
        $sort++
    }

    $null = Send-Api "Put" "/api/teacher/courses/$courseId/submit" $null $tt
    $null = Send-Api "Put" "/api/admin/courses/$courseId/review" @{ approved = $true } $at
    Write-Host "  created and published $title (id=$courseId)"
    return $courseId
}

Write-Host "== courses =="
$c1 = New-Course "Java 基础入门" "从语法到面向对象，零基础也能跟上" `
    "本课程面向零基础学员，覆盖 Java 语法、面向对象、集合与异常处理。" "BEGINNER" $tagIds[0] `
    @("开发环境与第一个程序", "变量与数据类型", "面向对象基础")
$c2 = New-Course "Vue 3 实战" "用组合式 API 做一个完整的中后台页面" `
    "以真实项目为线索，讲清响应式、路由、状态管理和请求封装。" "INTERMEDIATE" $tagIds[1] `
    @("组合式 API 入门", "路由与状态管理")

Remove-Item $tmpVideo, $tmpPng -Force -ErrorAction SilentlyContinue
Write-Host ""
Write-Host "done. teacher account: teacher / teacher123"