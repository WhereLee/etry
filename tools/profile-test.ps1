$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"
$png = "c:\Users\lrs\Desktop\work\etry\tools\avatar-test.png"
$txt = "c:\Users\lrs\Desktop\work\etry\tools\not-image.txt"

# 1x1 transparent PNG
$b64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
[IO.File]::WriteAllBytes($png, [Convert]::FromBase64String($b64))
Set-Content -Path $txt -Value "this is not an image" -Encoding ascii

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

# 头像 URL 有两种形态：local 存储是 /files/xxx 相对路径，COS 存储是完整的预签名 https 链接
function Get-FullUrl($u) {
    if ([string]::IsNullOrWhiteSpace($u)) { return $u }
    if ($u -match '^https?://') { return $u }
    return "$base$u"
}

$login = Send-Api "Post" "/api/auth/login" @{ username = "admin"; password = "admin123" } $null
$token = $login.data.token
Write-Host ("admin login code={0}" -f $login.code)

Write-Host "== 1. get profile =="
$p1 = Send-Api "Get" "/api/user/profile" $null $token
Write-Host ("    code={0} nickname={1} avatarUrl={2}" -f $p1.code, $p1.data.nickname, $p1.data.avatarUrl)

Write-Host "== 2. update nickname and bio =="
$u = Send-Api "Put" "/api/user/profile" @{ nickname = "Platform Admin"; bio = "manages the platform" } $token
Write-Host ("    code={0}" -f $u.code)
$p2 = Send-Api "Get" "/api/user/profile" $null $token
Write-Host ("    nickname={0} bio={1}" -f $p2.data.nickname, $p2.data.bio)

Write-Host "== 3. upload avatar (png) =="
$resp = curl.exe -s -X POST -H "Authorization: Bearer $token" -F "file=@$png;type=image/png" "$base/api/user/avatar"
Write-Host ("    $resp")
$avatarUrl = ($resp | ConvertFrom-Json).data.avatarUrl
Write-Host ("    avatarUrl={0}" -f $avatarUrl)

Write-Host "== 4. avatar reachable =="
$fullUrl = Get-FullUrl $avatarUrl
$code = curl.exe -s -o NUL -w "%{http_code}" "$fullUrl"
Write-Host ("    GET {0} -> HTTP {1}" -f $fullUrl, $code)

Write-Host "== 5. upload non-image expect 40001 =="
$bad = curl.exe -s -X POST -H "Authorization: Bearer $token" -F "file=@$txt;type=text/plain" "$base/api/user/avatar"
$badCode = ($bad | ConvertFrom-Json).code
Write-Host ("    code={0}" -f $badCode)

Write-Host "== 6. replace avatar, old file removed =="
$resp2 = curl.exe -s -X POST -H "Authorization: Bearer $token" -F "file=@$png;type=image/png" "$base/api/user/avatar"
$newUrl = ($resp2 | ConvertFrom-Json).data.avatarUrl
$oldCode = curl.exe -s -o NUL -w "%{http_code}" "$fullUrl"
$newCode = curl.exe -s -o NUL -w "%{http_code}" (Get-FullUrl $newUrl)
Write-Host ("    old={0} -> HTTP {1} (expect 404)" -f $fullUrl, $oldCode)
Write-Host ("    new={0} -> HTTP {1}" -f $newUrl, $newCode)
