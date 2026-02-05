#!/bin/bash
# =============================================
# 售后退款流程演示脚本
# =============================================

# 配置
AFTERSALES_URL="http://localhost:8086"
PAYMENT_URL="http://localhost:8083"
ORDER_URL="http://localhost:8081"
INVENTORY_URL="http://localhost:8082"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "============================================="
echo "       售后退款流程演示"
echo "============================================="
echo ""

# 检查服务是否启动
check_service() {
    local url=$1
    local name=$2
    if curl -s --connect-timeout 2 "$url/actuator/health" > /dev/null 2>&1; then
        echo -e "${GREEN}[OK]${NC} $name 服务已启动"
        return 0
    else
        echo -e "${RED}[FAIL]${NC} $name 服务未启动，请先启动服务"
        return 1
    fi
}

echo "1. 检查服务状态..."
echo "-------------------------------------------"
# check_service $AFTERSALES_URL "aftersales-service"
# check_service $PAYMENT_URL "payment-service"
# check_service $ORDER_URL "order-service"
# check_service $INVENTORY_URL "inventory-service"
echo ""

# 生成测试数据
ORDER_NO="ORD$(date +%Y%m%d%H%M%S)"
USER_ID=10001
SKU_ID=1001
QTY=2
REFUND_AMOUNT="199.00"

echo "2. 测试数据准备..."
echo "-------------------------------------------"
echo "订单号: $ORDER_NO"
echo "用户ID: $USER_ID"
echo "SKU ID: $SKU_ID"
echo "退款数量: $QTY"
echo "退款金额: $REFUND_AMOUNT"
echo ""

# Step 1: 申请售后
echo "3. 申请售后..."
echo "-------------------------------------------"
APPLY_RESPONSE=$(curl -s -X POST "$AFTERSALES_URL/api/aftersales/apply" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"$ORDER_NO\",
    \"userId\": $USER_ID,
    \"type\": \"REFUND\",
    \"reason\": \"商品质量问题，申请退款\",
    \"items\": [
      {
        \"orderItemId\": 1,
        \"skuId\": $SKU_ID,
        \"qty\": $QTY,
        \"refundAmount\": $REFUND_AMOUNT
      }
    ]
  }")

echo "请求: POST /api/aftersales/apply"
echo "响应: $APPLY_RESPONSE"

# 提取售后单号
AS_NO=$(echo $APPLY_RESPONSE | grep -o '"asNo":"[^"]*"' | cut -d'"' -f4)
echo -e "${GREEN}售后单号: $AS_NO${NC}"
echo ""

# Step 态
echo "4. 查询售后单状态..."
echo "-------------------------------------------"
QUERY_RESPONSE=$(curl -s -X GET "$AFTERSALES_URL/api/aftersales/$AS_NO")
echo "请求: GET /api/aftersales/$AS_NO"
echo "响应: $QUERY_RESPONSE"
echo ""

# Step 3: 审批通过
echo "5. 审批通过..."
echo "-------------------------------------------"
APPROVE_RESPONSE=$(curl -s -X POST "$AFTERSALES_URL/api/aftersales/approve" \
  -H "Content-Type: application/json" \
  -d "{
    \"asNo\": \"$AS_NO\",
    \"approvedBy\": \"admin\"
  }")

echo "请求: POST /api/aftersales/approve"
echo "响应: $APPROVE_RESPONSE"

# 提取退款单号
REFUND_NO=$(echo $APPROVE_RESPONSE | grep -o '"refundNo":"[^"]*"' | cut -d'"' -f4)
echo -e "${GREEN}退款单号: $REFUND_NO${NC}"
echo ""

# Step 4: 查询退款单状态
echo "6. 查询退款单状态..."
echo "-------------------------------------------"
if [ -n "$REFUND_NO" ]; then
    REFUND_QUERY=$(curl -s -X GET "$PAYMENT_URL/api/refunds/$REFUND_NO")
    echo "请求: GET /api/refunds/$REFUND_NO"
    echo "响应: $REFUND_QUERY"
else
    echo -e "${YELLOW}退款单号为空，跳过查询${NC}"
fi
echo ""

# Step 5: 模拟退款回调
echo "7. 模拟退款回调..."
echo "-------------------------------------------"
if [ -n "$REFUND_NO" ]; then
    AMP=$(date +%s)
    NONCE="nonce$(date +%s%N)"
    
    # 生成签名
    SIGNATURE=$(curl -s -X GET "$PAYMENT_URL/api/refunds/signature?refundNo=$REFUND_NO&callbackStatus=SUCCESS&timestamp=$TIMESTAMP&nonce=$NONCE")
    SIGNATURE=$(echo $SIGNATURE | grep -o '"data":"[^"]*"' | cut -d'"' -f4)
    
    echo "签名: $SIGNATURE"
    
    CALLBACK_RESPONSE=$(curl -s -X POST "$PAYMENT_URL/api/refunds/callback/mock" \
      -H "Content-Type: application/json" \
      -d "{
        \"refundNo\": \"$REFUND_NO\",
        \"channelRefundNo\": \"CH$(date +%Y%m%d%H%M%S)\",
        \"callbackStatus\": \"SUCCESS\",
        \"timestamp\": \"$TIMESTAMP\",
        \"nonce\": \"$NONCE\",
        \"signature\": \"$SIGNATURE\"
      }")
    
    echo "请求: POST /api/refunds/callback/mock"
    echo "响应: $CALLBACK_RESPONSE"
else
    echo -e "${YELLOW}退款单号为空，跳过回调${NC}"
fi
echo ""

# Step 6: 等待MQ消息处理
echo "8. 等待MQ消息处理（3秒）..."
echo "-------------------------------------------"
sleep 3
echo ""

# Step 7: 验证最终状态
echo "9. 验证最终状态..."
echo "-------------------------------------------"

# 查询售后单最终状态
echo "售后单状态:"
FINAL_AS=$(curl -s -X GET "$AFTERSALES_URL/api/aftersales/$AS_NO")
echo "$FINAL_AS" | python3 -m json.tool 2>/dev/null || echo "$FINAL_AS"
echo ""

# 查询订单状态
echo "订单状态:"
ORDER_STATUS=$(curl -s -X GET "$ORDER_URL/api/orders/$ORDER_NO")
echo "$ORDER_STATUS" | python3 -m json.tool 2>/dev/null || echo "$ORDER_STATUS"
echo ""

# 查询库存
echo "库存状态:"
INVENTORY_STATUS=$(curl -s -X GET "$INVENTORY_URL/api/inventory/$SKU_ID")
echo "$INVENTORY_STATUS" | python3 -m json.tool 2>/dev/null || echo "$INVENTORY_STATUS"
echo ""

echo "============================================="
echo "       演示完成"
echo "============================================="
echo ""
echo "流程总结:"
echo "1. 申请售后 -> 售后单状态: APPLIED"
echo "2. 审批通过 -> 售后单状态: APPROVED -> REFUNDING"
echo "3. 退款回调 -> 退款单状态: SUCCESS"
echo "4. MQ消费   -> 售后单状态: REFUNDED"
echo "5. MQ消费   -> 订单状态: REFUNDED/PARTIAL_REFUNDED"
echo "6. MQ消费   -> 库存回补: available + qty"
echo ""
