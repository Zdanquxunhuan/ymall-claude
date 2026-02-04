# Promotion Service & Pricing Service

交易前置算价与锁价服务，实现优惠券管理和价格锁定功能。

## 模块说明

### promotion-service (端口: 8086)

促销服务，负责优惠券的全生命周期管理。

**核心功能：**
- 创建优惠券活动
- 激活/暂停优惠券
- 用户领取优惠券（幂等）
- 优惠券核销（幂等）
- 促销试算

**API 列表：**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /promotion/coupon/create | 创建优惠券活动 |
| POST | /promotion/coupon/{couponCode}/activate | 激活优惠券 |
| GET | /promotion/coupon/{couponCode} | 查询优惠券详情 |
| POST | /promotion/coupon/receive | 领取优惠券 |
| POST | /promotion/coupon/redeem | 核销优惠券 |
| GET | /promotion/user/{userId}/coupons | 查询用户可用优惠券 |
| POST | /promotion/apply | 促销试算 |

### pricing-service (端口: 8087)

定价服务，负责价格试算和锁价。

**核心功能：**
- 价格试算（quote）：计算优惠和分摊
- 价格锁定（lock）：锁定价格和优惠券
- 签名验证：防止价格篡改
- 价格锁使用/取消

**API 列表：**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /pricing/quote | 试算（不锁定） |
| POST | /pricing/lock | 锁价 |
| GET | /pricing/lock/{priceLockNo} | 查询价格锁 |
| GET | /pricing/lock/{priceLockNo}/verify | 验证签名 |
| POST | /pricing/lock/{priceLockNo}/use | 使用价格锁 |
| POST | /pricing/lock/{priceLockNo}/cancel | 取消价格锁 |

## 数据库表

### promotion-service

- `t_coupon` - 优惠券模板表
- `t_coupon_user` - 用户优惠券表
- `t_coupon_action_log` - 优惠券操作日志表

### pricing-service

- `t_price_lock` - 价格锁表
- `t_price_lock_log` - 价格锁操作日志表
- `t_allocation_audit` - 分摊审计表

## 快速开始

### 1. 初始化数据库

```sql
-- 创建数据库
CREATE DATABASE ymall_promotion;
CREATE DATABASE ymall_pricing;

-- 执行建表脚本
source deploy/mysql/init/04_promotion_schema.sql
source deploy/mysql/init/05_pricing_schema.sql
```

### 2. 启动服务

```bash
# 启动促销服务
cd promotion-service
mvn spring-boot:run

# 启动定价服务
cd pricing-service
mvn spring-boot:run
```

### 3. Demo 演示

#### Step 1: 创建优惠券活动

```bash
curl -X POST http://localhost:8086/promotion/coupon/create \
  -H "Content-Type: application/json" \
  -d '{
    "name": "满100减20",
    "type": "FULL_REDUCTION",
    "thresholdAmount": 100.00,
    "discountAmount": 20.00,
    "totalQuantity": 1000,
    "perUserLimit": 3,
    "validStartTime": "2024-01-01T00:00:00",
    "validEndTime": "2024-12-31T23:59:59"
  }'
```

#### Step 2: 激活优惠券

```bash
curl -X POST http://localhost:8086/promotion/coupon/CPN20240101123456/activate
```

#### Step 3: 用户领取优惠券

```bash
curl -X POST http://localhost:8086/promotion/coupon/receive \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 10001,
    "couponCode": "CPN20240101123456",
    "requestId": "REQ001"
  }'
```

#### Step 4: 价格试算

```bash
curl -X POST http://localhost:8087/pricing/quote \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 10001,
    "items": [
      {"skuId": 1001, "qty": 2, "unitPrice": 50.00, "title": "商品A"},
      {"skuId": 1002, "qty": 1, "unitPrice": 80.00, "title": "商品B"}
    ],
    "userCouponNos": ["UC20240101123456001"]
  }'
```

**响应示例：**
```json
{
  "code": "0",
  "data": {
    "userId": 10001,
    "originalAmount": 180.00,
    "totalDiscount": 20.00,
    "payableAmount": 160.00,
    "allocations": [
      {
        "skuId": 1001,
        "title": "商品A",
        "qty": 2,
        "unitPrice": 50.00,
        "lineOriginalAmount": 100.00,
        "lineDiscountAmount": 11.11,
        "linePayableAmount": 88.89
      },
      {
        "skuId": 1002,
        "title": "商品B",
        "qty": 1,
        "unitPrice": 80.00,
        "lineOriginalAmount": 80.00,
        "lineDiscountAmount": 8.89,
        "linePayableAmount": 71.11
      }
    ]
  }
}
```

#### Step 5: 锁价

```bash
curl -X POST http://localhost:8087/pricing/lock \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 10001,
    "items": [
      {"skuId": 1001, "qty": 2, "unitPrice": 50.00, "title": "商品A"},
      {"skuId": 1002, "qty": 1, "unitPrice": 80.00, "title": "商品B"}
    ],
    "userCouponNos": ["UC20240101123456001"],
    "lockMinutes": 15
  }'
```

**响应示例：**
```json
{
  "code": "0",
  "data": {
    "priceLockNo": "PL20240101123456001",
    "signature": "a1b2c3d4e5f6...",
    "signVersion": 1,
    "payableAmount": 160.00,
    "expireAt": "2024-01-01T12:15:00",
    "allocations": [...]
  }
}
```

#### Step 6: 下单时使用价格锁

```bash
curl -X POST "http://localhost:8087/pricing/lock/PL20240101123456001/use?orderNo=ORD001&signature=a1b2c3d4e5f6..."
```

## 分摊算法

采用**按金额比例分摊**算法，确保：

1. 每行优惠 = 总优惠 × (行原价 / 总原价)
2. 最后一行使用减法，避免精度问题
3. 分摊结果可审计、可复算

**示例：**
```
商品A: 原价100, 商品B: 原价50, 总原价150
总优惠: 30

商品A分摊: 30 × (100/150) = 20.00
商品B分摊: 30 - 20.00 = 10.00

验证: 20.00 + 10.00 = 30.00 ✓
```

## 防篡改机制

价格锁使用 SHA256 签名防止篡改：

```
signature = SHA256(priceLockNo + userId + payableAmount + snapshot + secretKey)
```

下单时必须携带正确的签名才能使用价格锁。

## 状态流转

### 价格锁状态

```
LOCKED → USED (下单成功)
LOCKED → EXPIRED (超时)
LOCKED → CANCELED (取消)
```

### 用户优惠券状态

```
AVAILABLE → LOCKED (锁价)
AVAILABLE → USED (直接核销)
LOCKED → USED (下单核销)
LOCKED → AVAILABLE (解锁)
LOCKED → EXPIRED (锁过期)
```

## 配置说明

### promotion-service

```yaml
server:
  port: 8086

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ymall_promotion
```

### pricing-service

```yaml
server:
  port: 8087

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ymall_pricing

pricing:
  sign:
    secret: ymall-pricing-secret-key-2024  # 签名密钥
```

## 测试

```bash
# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=PricingServiceTest
```

## 流程图

详细流程图请参考 [flowchart.md](./pricing-service/flowchart.md)
