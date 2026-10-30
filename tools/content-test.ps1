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

function Show($label, $resp) {
    Write-Host ("{0,-32} code={1}" -f $label, $resp.code)
}

$admin = Send-Api "Post" "/api/auth/login" @{ username = "admin"; password = "admin123" } $null
$adminToken = $admin.data.token

Write-Host "== 1. create published notice (top) =="
$n1 = Send-Api "Post" "/api/admin/notices" @{
    title = "Welcome"; content = "Platform online"; status = "PUBLISHED"; isTop = $true
} $adminToken
Show "create published" $n1

Write-Host "== 2. create draft notice =="
$n2 = Send-Api "Post" "/api/admin/notices" @{
    title = "Draft only"; content = "not visible"; status = "DRAFT"; isTop = $false
} $adminToken
Show "create draft" $n2

Write-Host "== 3. public notice list without token =="
$publicList = Send-Api "Get" "/api/notices?page=1&size=10" $null $null
Show "public list (no token)" $publicList
Write-Host ("    total={0}" -f $publicList.data.total)
$publicList.data.records | ForEach-Object { Write-Host ("    [{0}] {1} top={2}" -f $_.id, $_.title, $_.isTop) }

Write-Host "== 4. public detail of draft (expect 40405) =="
$draftDetail = Send-Api "Get" "/api/notices/$($n2.data.id)" $null $null
Show "draft detail" $draftDetail

Write-Host "== 5. send message to all TEACHER =="
$send = Send-Api "Post" "/api/admin/messages" @{
    title = "Welcome teacher"; content = "Please complete your profile"; roleCode = "TEACHER"
} $adminToken
Show "send to TEACHER" $send
Write-Host ("    sentCount={0}" -f $send.data)

Write-Host "== 6. send to a specific user (admin himself) =="
$send2 = Send-Api "Post" "/api/admin/messages" @{
    title = "Self ping"; content = "hello"; receiverIds = @(1)
} $adminToken
Show "send to user 1" $send2

Write-Host "== 7. admin unread count =="
$unread = Send-Api "Get" "/api/messages/unread-count" $null $adminToken
Show "unread" $unread
Write-Host ("    unreadCount={0}" -f $unread.data)

Write-Host "== 8. admin inbox =="
$inbox = Send-Api "Get" "/api/messages?page=1&size=10" $null $adminToken
Show "inbox" $inbox
Write-Host ("    total={0}" -f $inbox.data.total)

Write-Host "== 9. read one message =="
if ($inbox.data.records.Count -gt 0) {
    $mid = $inbox.data.records[0].id
    $read = Send-Api "Put" "/api/messages/$mid/read" $null $adminToken
    Show "mark read" $read
}

Write-Host "== 10. unread after read =="
$unread2 = Send-Api "Get" "/api/messages/unread-count" $null $adminToken
Write-Host ("    unreadCount={0}" -f $unread2.data)

Write-Host "== 11. read all =="
$readAll = Send-Api "Put" "/api/messages/read-all" $null $adminToken
Show "mark all read" $readAll
$unread3 = Send-Api "Get" "/api/messages/unread-count" $null $adminToken
Write-Host ("    unreadCount after all={0}" -f $unread3.data)
