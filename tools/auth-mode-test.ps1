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

$sfx = Get-Date -Format "HHmmss"
$admin = Send-Api "Post" "/api/auth/login" @{ username = "admin"; password = "admin123" } $null
$at = $admin.data.token
$userName = "reg$sfx"

Write-Host ""
Write-Host "== 1. register closed by default =="
$blocked = Send-Api "Post" "/api/auth/register" @{
    username = $userName; password = "pass123456"; nickname = $userName
} $null
Check "register disabled by default" $blocked.code 40105

Write-Host ""
Write-Host "== 2. open it from the config endpoint =="
$open = Send-Api "Put" "/api/admin/configs/register.enabled" @{ value = "true" } $at
Check "config updated" $open.code 0

$first = Send-Api "Post" "/api/auth/register" @{
    username = $userName; password = "pass123456"; nickname = $userName
} $null
Check "register works after enabling" $first.code 0
Check "new user is student" ($first.data.roles -contains "STUDENT") $true

Write-Host ""
Write-Host "== 3. duplicate name does not reveal itself =="
$dup = Send-Api "Post" "/api/auth/register" @{
    username = $userName; password = "pass123456"; nickname = $userName
} $null
Check "duplicate returns neutral code" $dup.code 40018

Write-Host ""
Write-Host "== 4. close it again =="
$close = Send-Api "Put" "/api/admin/configs/register.enabled" @{ value = "false" } $at
Check "config restored" $close.code 0
$blockedAgain = Send-Api "Post" "/api/auth/register" @{
    username = "reg2$sfx"; password = "pass123456"; nickname = "x"
} $null
Check "register disabled again" $blockedAgain.code 40105

Write-Host ""
Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)