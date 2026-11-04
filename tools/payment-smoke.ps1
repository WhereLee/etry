param(
    [string]$BaseUrl = 'http://127.0.0.1:8084',
    [string]$AdminUser = 'admin',
    [Parameter(Mandatory = $true)][string]$AdminPassword,
    [string]$BuyerUser = 'student_pay',
    [Parameter(Mandatory = $true)][string]$BuyerPassword,
    [long]$SeriesId = 1,
    [switch]$IncludeAmountMismatch
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Login([string]$username, [string]$password) {
    $body = @{ username = $username; password = $password } | ConvertTo-Json
    return Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" `
        -ContentType 'application/json' -Body $body
}

function AuthHeaders($session) {
    return @{ Authorization = "Bearer $($session.token)" }
}

function Api($method, [string]$path, $headers, $body = $null) {
    $params = @{
        Method = $method
        Uri = "$BaseUrl$path"
        Headers = $headers
    }
    if ($null -ne $body) {
        $params.ContentType = 'application/json'
        $params.Body = if ($body -is [string]) { $body } else { $body | ConvertTo-Json }
    }
    return Invoke-RestMethod @params
}

$admin = Login $AdminUser $AdminPassword
$buyer = Login $BuyerUser $BuyerPassword
$adminHeaders = AuthHeaders $admin
$buyerHeaders = AuthHeaders $buyer
$results = [System.Collections.Generic.List[object]]::new()

function NewMockOrder {
    $payload = @{
        seriesId = $SeriesId
        clientRequestId = [guid]::NewGuid().ToString()
    }
    $order = Api 'Post' '/api/orders' $buyerHeaders $payload
    $pay = Api 'Post' "/api/orders/$($order.orderNo)/pay" $buyerHeaders
    if ($pay.status -ne 'PAYING') {
        throw "订单未进入支付中：$($pay.status)"
    }
    return $order.orderNo
}

function Simulate([string]$orderNo, [string]$scenario) {
    return Api 'Post' "/api/payments/mock/$orderNo/events" $buyerHeaders @{ scenario = $scenario }
}

function GetBuyerOrder([string]$orderNo) {
    return Api 'Get' "/api/orders/$orderNo" $buyerHeaders
}

function GetAdminOrder([string]$orderNo) {
    return Api 'Get' "/api/admin/orders/$orderNo" $adminHeaders
}

function AssertStatus([string]$scenario, [string]$actual, [string]$expected) {
    if ($actual -ne $expected) {
        throw "[$scenario] 期望 $expected，实际 $actual"
    }
}

function FinishFulfilledOrder([string]$orderNo) {
    Api 'Post' "/api/admin/orders/$orderNo/refund" $adminHeaders @{
        reason = 'Mock 冒烟测试清理'
        simulateFailure = $false
    } | Out-Null
    $detail = GetAdminOrder $orderNo
    AssertStatus 'refund' $detail.order.status 'REFUNDED'
}

$orderNo = NewMockOrder
Simulate $orderNo 'SUCCESS' | Out-Null
$detail = GetBuyerOrder $orderNo
AssertStatus 'SUCCESS' $detail.order.status 'FULFILLED'
FinishFulfilledOrder $orderNo
$results.Add([pscustomobject]@{ scenario = 'SUCCESS'; result = 'PASS'; orderNo = $orderNo })

$orderNo = NewMockOrder
Simulate $orderNo 'FAILED' | Out-Null
$detail = GetBuyerOrder $orderNo
AssertStatus 'FAILED' $detail.order.status 'PAY_FAILED'
Api 'Post' "/api/admin/orders/$orderNo/close" $adminHeaders | Out-Null
$results.Add([pscustomobject]@{ scenario = 'FAILED'; result = 'PASS'; orderNo = $orderNo })

$orderNo = NewMockOrder
Simulate $orderNo 'LOST_CALLBACK' | Out-Null
$detail = GetBuyerOrder $orderNo
AssertStatus 'LOST_CALLBACK-before-query' $detail.order.status 'PAYING'
Api 'Post' "/api/admin/orders/$orderNo/query" $adminHeaders | Out-Null
$detail = GetBuyerOrder $orderNo
AssertStatus 'LOST_CALLBACK-after-query' $detail.order.status 'FULFILLED'
FinishFulfilledOrder $orderNo
$results.Add([pscustomobject]@{ scenario = 'LOST_CALLBACK'; result = 'PASS'; orderNo = $orderNo })

$orderNo = NewMockOrder
Simulate $orderNo 'DUPLICATE' | Out-Null
$detail = GetBuyerOrder $orderNo
AssertStatus 'DUPLICATE' $detail.order.status 'FULFILLED'
FinishFulfilledOrder $orderNo
$results.Add([pscustomobject]@{ scenario = 'DUPLICATE'; result = 'PASS'; orderNo = $orderNo })

$orderNo = NewMockOrder
Simulate $orderNo 'FULFILLMENT_FAIL_ONCE' | Out-Null
$detail = GetBuyerOrder $orderNo
AssertStatus 'FULFILLMENT_FAIL_ONCE-initial' $detail.order.status 'PAID'
$deadline = (Get-Date).AddSeconds(45)
do {
    Start-Sleep -Seconds 3
    $detail = GetBuyerOrder $orderNo
} while ($detail.order.status -ne 'FULFILLED' -and (Get-Date) -lt $deadline)
AssertStatus 'FULFILLMENT_FAIL_ONCE-recovered' $detail.order.status 'FULFILLED'
FinishFulfilledOrder $orderNo
$results.Add([pscustomobject]@{ scenario = 'FULFILLMENT_FAIL_ONCE'; result = 'PASS'; orderNo = $orderNo })

$orderNo = NewMockOrder
Simulate $orderNo 'SUCCESS' | Out-Null
$detail = GetBuyerOrder $orderNo
AssertStatus 'REFUND_RETRY-initial' $detail.order.status 'FULFILLED'
Api 'Post' "/api/admin/orders/$orderNo/refund" $adminHeaders @{
    reason = 'Mock 退款失败演练'
    simulateFailure = $true
} | Out-Null
$detail = GetAdminOrder $orderNo
AssertStatus 'REFUND_RETRY-failed' $detail.order.status 'REFUND_FAILED'
Api 'Post' "/api/admin/orders/$orderNo/refund" $adminHeaders @{
    reason = 'Mock 退款重试'
    simulateFailure = $false
} | Out-Null
$detail = GetAdminOrder $orderNo
AssertStatus 'REFUND_RETRY-recovered' $detail.order.status 'REFUNDED'
$results.Add([pscustomobject]@{ scenario = 'REFUND_FAILURE_RETRY'; result = 'PASS'; orderNo = $orderNo })

if ($IncludeAmountMismatch) {
    $orderNo = NewMockOrder
    Simulate $orderNo 'AMOUNT_MISMATCH' | Out-Null
    $detail = GetBuyerOrder $orderNo
    AssertStatus 'AMOUNT_MISMATCH' $detail.order.status 'PAYING'
    $adminDetail = GetAdminOrder $orderNo
    if (-not ($adminDetail.events | Where-Object { $_.processStatus -eq 'FAILED' })) {
        throw '[AMOUNT_MISMATCH] 未记录失败支付事件'
    }
    Write-Warning 'AMOUNT_MISMATCH 会保留为异常订单，需要人工处理'
    $results.Add([pscustomobject]@{ scenario = 'AMOUNT_MISMATCH'; result = 'PASS'; orderNo = $orderNo })
}

$results | Format-Table -AutoSize
Write-Host 'Payment mock smoke passed.'
