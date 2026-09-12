#!/usr/bin/env pwsh
# Sakura Admin AI 变量提取功能测试脚本
# 用途：验证 AI 变量提取规则生成接口的功能和错误处理
#
# 使用方法：
#   1. 设置环境变量: $env:SAKURA_ADMIN_TOKEN = "your-bearer-token"
#   2. 运行脚本: .\test-ai-variable-extract.ps1
#   3. 可选参数: .\test-ai-variable-extract.ps1 -BaseUrl "http://localhost:8000"
#
# 作者: Claude Opus 5
# 日期: 2026-09-13

param(
    [string]$BaseUrl = "http://localhost:8000"
)

# 颜色输出函数
function Write-Success {
    param([string]$Message)
    Write-Host "✓ $Message" -ForegroundColor Green
}

function Write-Failure {
    param([string]$Message)
    Write-Host "✗ $Message" -ForegroundColor Red
}

function Write-Info {
    param([string]$Message)
    Write-Host "ℹ $Message" -ForegroundColor Cyan
}

# 检查 Token
if (-not $env:SAKURA_ADMIN_TOKEN) {
    Write-Failure "错误: 未设置环境变量 SAKURA_ADMIN_TOKEN"
    Write-Info "请先设置: `$env:SAKURA_ADMIN_TOKEN = 'your-token-here'"
    exit 1
}

$endpoint = "$BaseUrl/automation/ai/variable-extract-rule"
$headers = @{
    "Authorization" = "Bearer $env:SAKURA_ADMIN_TOKEN"
    "Content-Type" = "application/json; charset=utf-8"
}

Write-Host "`n========================================" -ForegroundColor Yellow
Write-Host "  Sakura Admin AI 变量提取功能测试" -ForegroundColor Yellow
Write-Host "========================================`n" -ForegroundColor Yellow
Write-Info "测试端点: $endpoint"
Write-Info "开始时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')`n"

# 测试结果统计
$results = @()

# 测试 1: AI 功能未启用（预期失败）
Write-Host "[测试 1] AI 功能未启用场景" -ForegroundColor Yellow
$body1 = @{
    rawValue = "订单号：ORD20240315001"
    instruction = "提取订单号"
    valueMasked = 0
} | ConvertTo-Json -Compress

try {
    $response1 = Invoke-RestMethod -Method Post -Uri $endpoint -Headers $headers -Body $body1 -TimeoutSec 55
    if ($response1.code -eq 400 -or $response1.success -eq $false) {
        Write-Success "预期失败响应: $($response1.msg)"
        $results += @{ Test = "AI 未启用"; Status = "PASS"; Message = $response1.msg }
    } else {
        Write-Failure "未预期的成功响应"
        $results += @{ Test = "AI 未启用"; Status = "FAIL"; Message = "应该返回失败但返回了成功" }
    }
} catch {
    Write-Info "HTTP 错误 (预期): $($_.Exception.Message)"
    $results += @{ Test = "AI 未启用"; Status = "PASS"; Message = "返回了错误响应" }
}

Start-Sleep -Seconds 1

# 测试 2: 订单号提取
Write-Host "`n[测试 2] 订单号提取" -ForegroundColor Yellow
$body2 = @{
    rawValue = "订单创建成功，订单号：ORD20240315001，请及时支付"
    instruction = "提取订单号"
    valueMasked = 0
} | ConvertTo-Json -Compress

try {
    $response2 = Invoke-RestMethod -Method Post -Uri $endpoint -Headers $headers -Body $body2 -TimeoutSec 55
    if ($response2.code -eq 200 -and $response2.data.mode -eq "regex") {
        Write-Success "成功生成规则"
        Write-Info "  Pattern: $($response2.data.pattern)"
        Write-Info "  Group: $($response2.data.group)"
        Write-Info "  提取结果: $($response2.data.extractedValue)"

        if ($response2.data.extractedValue -eq "ORD20240315001") {
            Write-Success "提取值正确"
            $results += @{ Test = "订单号提取"; Status = "PASS"; Message = "提取值: ORD20240315001" }
        } else {
            Write-Failure "提取值不正确，期望: ORD20240315001, 实际: $($response2.data.extractedValue)"
            $results += @{ Test = "订单号提取"; Status = "FAIL"; Message = "提取值不匹配" }
        }
    } else {
        Write-Failure "响应格式不正确"
        $results += @{ Test = "订单号提取"; Status = "FAIL"; Message = "响应格式错误" }
    }
} catch {
    Write-Failure "请求失败: $($_.Exception.Message)"
    $results += @{ Test = "订单号提取"; Status = "FAIL"; Message = $_.Exception.Message }
}

Start-Sleep -Seconds 1

# 测试 3: JSON 字段提取
Write-Host "`n[测试 3] JSON userId 提取" -ForegroundColor Yellow
$body3 = @{
    rawValue = '{"code":200,"data":{"userId":"U123456","username":"张三"},"message":"success"}'
    instruction = "提取 userId 的值"
    valueMasked = 0
} | ConvertTo-Json -Compress

try {
    $response3 = Invoke-RestMethod -Method Post -Uri $endpoint -Headers $headers -Body $body3 -TimeoutSec 55
    if ($response3.code -eq 200 -and $response3.data.mode -eq "regex") {
        Write-Success "成功生成规则"
        Write-Info "  Pattern: $($response3.data.pattern)"
        Write-Info "  Group: $($response3.data.group)"
        Write-Info "  提取结果: $($response3.data.extractedValue)"

        if ($response3.data.extractedValue -eq "U123456") {
            Write-Success "提取值正确"
            $results += @{ Test = "JSON 提取"; Status = "PASS"; Message = "提取值: U123456" }
        } else {
            Write-Failure "提取值不正确"
            $results += @{ Test = "JSON 提取"; Status = "FAIL"; Message = "提取值不匹配" }
        }
    } else {
        Write-Failure "响应格式不正确"
        $results += @{ Test = "JSON 提取"; Status = "FAIL"; Message = "响应格式错误" }
    }
} catch {
    Write-Failure "请求失败: $($_.Exception.Message)"
    $results += @{ Test = "JSON 提取"; Status = "FAIL"; Message = $_.Exception.Message }
}

Start-Sleep -Seconds 1

# 测试 4: 验证码提取
Write-Host "`n[测试 4] HTML 验证码提取" -ForegroundColor Yellow
$body4 = @{
    rawValue = '<div class="verify-code">您的验证码是：<span id="code">8847</span>，5分钟内有效</div>'
    instruction = "提取验证码"
    valueMasked = 0
} | ConvertTo-Json -Compress

try {
    $response4 = Invoke-RestMethod -Method Post -Uri $endpoint -Headers $headers -Body $body4 -TimeoutSec 55
    if ($response4.code -eq 200 -and $response4.data.mode -eq "regex") {
        Write-Success "成功生成规则"
        Write-Info "  Pattern: $($response4.data.pattern)"
        Write-Info "  Group: $($response4.data.group)"
        Write-Info "  提取结果: $($response4.data.extractedValue)"

        if ($response4.data.extractedValue -eq "8847") {
            Write-Success "提取值正确"
            $results += @{ Test = "验证码提取"; Status = "PASS"; Message = "提取值: 8847" }
        } else {
            Write-Failure "提取值不正确"
            $results += @{ Test = "验证码提取"; Status = "FAIL"; Message = "提取值不匹配" }
        }
    } else {
        Write-Failure "响应格式不正确"
        $results += @{ Test = "验证码提取"; Status = "FAIL"; Message = "响应格式错误" }
    }
} catch {
    Write-Failure "请求失败: $($_.Exception.Message)"
    $results += @{ Test = "验证码提取"; Status = "FAIL"; Message = $_.Exception.Message }
}

# 输出测试结果汇总
Write-Host "`n========================================" -ForegroundColor Yellow
Write-Host "  测试结果汇总" -ForegroundColor Yellow
Write-Host "========================================`n" -ForegroundColor Yellow

$results | Format-Table -Property Test, Status, Message -AutoSize

$passCount = ($results | Where-Object { $_.Status -eq "PASS" }).Count
$failCount = ($results | Where-Object { $_.Status -eq "FAIL" }).Count
$totalCount = $results.Count

Write-Host "`n总计: $totalCount 个测试" -ForegroundColor Yellow
Write-Host "通过: $passCount 个" -ForegroundColor Green
Write-Host "失败: $failCount 个" -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Red" })
Write-Info "完成时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')`n"

# 返回退出码
exit $failCount
