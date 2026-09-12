#!/usr/bin/env bash
# Sakura Admin AI 变量提取功能测试脚本
# 用途：验证 AI 变量提取规则生成接口的功能和错误处理
#
# 使用方法：
#   1. 设置环境变量: export SAKURA_ADMIN_TOKEN="your-bearer-token"
#   2. 添加执行权限: chmod +x test-ai-variable-extract.sh
#   3. 运行脚本: ./test-ai-variable-extract.sh
#   4. 可选参数: ./test-ai-variable-extract.sh http://localhost:8000
#
# 作者: Claude Opus 5
# 日期: 2026-09-13

set -e

BASE_URL="${1:-http://localhost:8000}"
ENDPOINT="${BASE_URL}/automation/ai/variable-extract-rule"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 输出函数
success() {
    echo -e "${GREEN}✓ $1${NC}"
}

failure() {
    echo -e "${RED}✗ $1${NC}"
}

info() {
    echo -e "${CYAN}ℹ $1${NC}"
}

# 检查 Token
if [ -z "$SAKURA_ADMIN_TOKEN" ]; then
    failure "错误: 未设置环境变量 SAKURA_ADMIN_TOKEN"
    info "请先设置: export SAKURA_ADMIN_TOKEN='your-token-here'"
    exit 1
fi

# 检查 curl
if ! command -v curl &> /dev/null; then
    failure "错误: 未找到 curl 命令"
    exit 1
fi

# 检查 jq（可选）
HAS_JQ=false
if command -v jq &> /dev/null; then
    HAS_JQ=true
fi

echo -e "\n${YELLOW}========================================"
echo "  Sakura Admin AI 变量提取功能测试"
echo "========================================${NC}\n"
info "测试端点: $ENDPOINT"
info "开始时间: $(date '+%Y-%m-%d %H:%M:%S')\n"

# 测试结果统计
PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0

# 测试 1: AI 功能未启用（预期失败）
echo -e "${YELLOW}[测试 1] AI 功能未启用场景${NC}"
((TOTAL_COUNT++))

RESPONSE1=$(curl -s -w "\n%{http_code}" -X POST "$ENDPOINT" \
    -H "Authorization: Bearer $SAKURA_ADMIN_TOKEN" \
    -H "Content-Type: application/json; charset=utf-8" \
    -d '{
        "rawValue": "订单号：ORD20240315001",
        "instruction": "提取订单号",
        "valueMasked": 0
    }')

HTTP_CODE1=$(echo "$RESPONSE1" | tail -n1)
BODY1=$(echo "$RESPONSE1" | sed '$d')

if [ "$HTTP_CODE1" -ge 400 ] || echo "$BODY1" | grep -q '"success":false'; then
    success "预期失败响应 (HTTP $HTTP_CODE1)"
    if [ "$HAS_JQ" = true ]; then
        MSG=$(echo "$BODY1" | jq -r '.msg // .message // "未知错误"')
        info "  消息: $MSG"
    fi
    ((PASS_COUNT++))
else
    failure "未预期的成功响应"
    ((FAIL_COUNT++))
fi

sleep 1

# 测试 2: 订单号提取
echo -e "\n${YELLOW}[测试 2] 订单号提取${NC}"
((TOTAL_COUNT++))

RESPONSE2=$(curl -s -w "\n%{http_code}" -X POST "$ENDPOINT" \
    -H "Authorization: Bearer $SAKURA_ADMIN_TOKEN" \
    -H "Content-Type: application/json; charset=utf-8" \
    -d '{
        "rawValue": "订单创建成功，订单号：ORD20240315001，请及时支付",
        "instruction": "提取订单号",
        "valueMasked": 0
    }')

HTTP_CODE2=$(echo "$RESPONSE2" | tail -n1)
BODY2=$(echo "$RESPONSE2" | sed '$d')

if [ "$HTTP_CODE2" -eq 200 ] && echo "$BODY2" | grep -q '"mode":"regex"'; then
    success "成功生成规则"
    if [ "$HAS_JQ" = true ]; then
        PATTERN=$(echo "$BODY2" | jq -r '.data.pattern')
        GROUP=$(echo "$BODY2" | jq -r '.data.group')
        EXTRACTED=$(echo "$BODY2" | jq -r '.data.extractedValue // ""')
        info "  Pattern: $PATTERN"
        info "  Group: $GROUP"
        info "  提取结果: $EXTRACTED"

        if [ "$EXTRACTED" = "ORD20240315001" ]; then
            success "提取值正确"
            ((PASS_COUNT++))
        else
            failure "提取值不正确，期望: ORD20240315001, 实际: $EXTRACTED"
            ((FAIL_COUNT++))
        fi
    else
        info "  (安装 jq 以查看详细输出)"
        ((PASS_COUNT++))
    fi
else
    failure "响应格式不正确 (HTTP $HTTP_CODE2)"
    ((FAIL_COUNT++))
fi

sleep 1

# 测试 3: JSON 字段提取
echo -e "\n${YELLOW}[测试 3] JSON userId 提取${NC}"
((TOTAL_COUNT++))

RESPONSE3=$(curl -s -w "\n%{http_code}" -X POST "$ENDPOINT" \
    -H "Authorization: Bearer $SAKURA_ADMIN_TOKEN" \
    -H "Content-Type: application/json; charset=utf-8" \
    -d '{
        "rawValue": "{\"code\":200,\"data\":{\"userId\":\"U123456\",\"username\":\"张三\"},\"message\":\"success\"}",
        "instruction": "提取 userId 的值",
        "valueMasked": 0
    }')

HTTP_CODE3=$(echo "$RESPONSE3" | tail -n1)
BODY3=$(echo "$RESPONSE3" | sed '$d')

if [ "$HTTP_CODE3" -eq 200 ] && echo "$BODY3" | grep -q '"mode":"regex"'; then
    success "成功生成规则"
    if [ "$HAS_JQ" = true ]; then
        PATTERN=$(echo "$BODY3" | jq -r '.data.pattern')
        GROUP=$(echo "$BODY3" | jq -r '.data.group')
        EXTRACTED=$(echo "$BODY3" | jq -r '.data.extractedValue // ""')
        info "  Pattern: $PATTERN"
        info "  Group: $GROUP"
        info "  提取结果: $EXTRACTED"

        if [ "$EXTRACTED" = "U123456" ]; then
            success "提取值正确"
            ((PASS_COUNT++))
        else
            failure "提取值不正确"
            ((FAIL_COUNT++))
        fi
    else
        ((PASS_COUNT++))
    fi
else
    failure "响应格式不正确 (HTTP $HTTP_CODE3)"
    ((FAIL_COUNT++))
fi

sleep 1

# 测试 4: 验证码提取
echo -e "\n${YELLOW}[测试 4] HTML 验证码提取${NC}"
((TOTAL_COUNT++))

RESPONSE4=$(curl -s -w "\n%{http_code}" -X POST "$ENDPOINT" \
    -H "Authorization: Bearer $SAKURA_ADMIN_TOKEN" \
    -H "Content-Type: application/json; charset=utf-8" \
    -d '{
        "rawValue": "<div class=\"verify-code\">您的验证码是：<span id=\"code\">8847</span>，5分钟内有效</div>",
        "instruction": "提取验证码",
        "valueMasked": 0
    }')

HTTP_CODE4=$(echo "$RESPONSE4" | tail -n1)
BODY4=$(echo "$RESPONSE4" | sed '$d')

if [ "$HTTP_CODE4" -eq 200 ] && echo "$BODY4" | grep -q '"mode":"regex"'; then
    success "成功生成规则"
    if [ "$HAS_JQ" = true ]; then
        PATTERN=$(echo "$BODY4" | jq -r '.data.pattern')
        GROUP=$(echo "$BODY4" | jq -r '.data.group')
        EXTRACTED=$(echo "$BODY4" | jq -r '.data.extractedValue // ""')
        info "  Pattern: $PATTERN"
        info "  Group: $GROUP"
        info "  提取结果: $EXTRACTED"

        if [ "$EXTRACTED" = "8847" ]; then
            success "提取值正确"
            ((PASS_COUNT++))
        else
            failure "提取值不正确"
            ((FAIL_COUNT++))
        fi
    else
        ((PASS_COUNT++))
    fi
else
    failure "响应格式不正确 (HTTP $HTTP_CODE4)"
    ((FAIL_COUNT++))
fi

# 输出测试结果汇总
echo -e "\n${YELLOW}========================================"
echo "  测试结果汇总"
echo "========================================${NC}\n"

echo -e "总计: ${YELLOW}$TOTAL_COUNT${NC} 个测试"
echo -e "通过: ${GREEN}$PASS_COUNT${NC} 个"
if [ $FAIL_COUNT -eq 0 ]; then
    echo -e "失败: ${GREEN}$FAIL_COUNT${NC} 个"
else
    echo -e "失败: ${RED}$FAIL_COUNT${NC} 个"
fi
info "完成时间: $(date '+%Y-%m-%d %H:%M:%S')\n"

# 返回退出码
exit $FAIL_COUNT
