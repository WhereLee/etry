$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$base = "http://127.0.0.1:8080"

function Post-Json($url, $obj, $extraHeaders) {
    $headers = @{}
    if ($script:token) { $headers["Authorization"] = "Bearer $script:token" }
    if ($extraHeaders) { $extraHeaders.GetEnumerator() | ForEach-Object { $headers[$_.Key] = $_.Value } }
    $json = $obj | ConvertTo-Json -Depth 6 -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    return Invoke-RestMethod -Uri $url -Method Post -Headers $headers -ContentType "application/json; charset=utf-8" -Body $bytes
}

function Get-Api($url) {
    $headers = @{ Authorization = "Bearer $script:token" }
    return Invoke-RestMethod -Uri $url -Method Get -Headers $headers
}

Write-Host "== 1. login =="
$login = Post-Json "$base/api/auth/login" @{ username = "buyer"; password = "demo123" } $null
$script:token = $login.data.token
Write-Host ("code={0} userId={1} role={2} tokenLen={3}" -f $login.code, $login.data.userId, $login.data.role, $login.data.token.Length)

Write-Host "== 2. products =="
$products = Get-Api "$base/api/products?page=1&size=3"
Write-Host ("total={0} returned={1}" -f $products.data.total, $products.data.records.Count)
$products.data.records | ForEach-Object { Write-Host ("  id={0} {1} price={2} available={3}" -f $_.id, $_.name, $_.pricePoints, $_.availableStock) }

Write-Host "== 3. stock before =="
$before = Get-Api "$base/api/products/6"
Write-Host ("  product6 available={0}" -f $before.data.availableStock)

Write-Host "== 4. create order (idem=smoke-001) =="
$order = Post-Json "$base/api/orders" @{ items = @(@{ productId = 6; quantity = 2 }); address = "Hangzhou Xihu Wensan Rd 100" } @{ "Idempotency-Key" = "smoke-001" }
Write-Host ("  code={0} orderNo={1} total={2} status={3} expireAt={4}" -f $order.code, $order.data.orderNo, $order.data.totalPoints, $order.data.status, $order.data.expireAt)

Write-Host "== 5. repeat same idem key =="
$again = Post-Json "$base/api/orders" @{ items = @(@{ productId = 6; quantity = 2 }); address = "Hangzhou Xihu Wensan Rd 100" } @{ "Idempotency-Key" = "smoke-001" }
Write-Host ("  code={0} orderNo={1} sameOrder={2}" -f $again.code, $again.data.orderNo, ($again.data.orderNo -eq $order.data.orderNo))

Write-Host "== 6. stock after =="
$after = Get-Api "$base/api/products/6"
Write-Host ("  product6 available={0} (should be {1})" -f $after.data.availableStock, ($before.data.availableStock - 2))

Write-Host "== 7. points =="
$points = Get-Api "$base/api/points?flowLimit=5"
Write-Host ("  balance={0} frozen={1}" -f $points.data.balance, $points.data.frozen)
$points.data.flows | ForEach-Object { Write-Host ("  flow {0} {1} balanceAfter={2}" -f $_.type, $_.amount, $_.balanceAfter) }

Write-Host "== 8. my orders =="
$orders = Get-Api "$base/api/orders?page=1&size=5"
Write-Host ("  total={0}" -f $orders.data.total)
$orders.data.records | ForEach-Object { Write-Host ("  {0} status={1} items={2}" -f $_.orderNo, $_.status, $_.items.Count) }
