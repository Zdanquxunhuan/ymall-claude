# Cart Service - 购物车服务

## 概述

购物车服务是 ymall-claude 电商平台的核心服务之一，负责管理用户购物车、游客购物车合并、结算锁价等功能。

## 核心功能

### 1. 购物车管理
- **Redis 存储**: 使用 Redis Hash 结构存储购物车数据
- **支持登录用户和游客**: 
  - 登录用户: `cart:{userId}`
  - 游客用户: `cart:anon:{anonId}`
- **购物车操作**: addItem / updateQty / checkItem / removeItem / clear

### 2. 购物车合并
- **场景**: 用户登录后，将游客购物车合并到用户购物车
- **冲突策略**:
  - `QTY_ADD` (默认): 同SKU数量累加，上限99
  - `LATEST_WIN`: 同SKU以最后更新时间较新的为准
- **合并日志**: 记录到 `t_cart_merge_log` 表

### 3. 结算锁价
- **库存校验**: 调用 inventory-service 校验可售库存
- **促销计算**: 调用 pricing-service 计算优惠并锁价
- **返回结果**: priceLockNo + signature，用于下单

## API 接口

### 购物车操作

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /cart | 获取购物车 |
| POST | /cart/items | 添加商品 |
| PUT | /cart/items/qty | 更新数量 |
| PUT | /cart/items/check | 更新选中状态 |
| PUT | /cart/items/check-all | 全选/取消全选 |
| DELETE | /cart/items/{skuId} | 移除单个商品 |
| DELETE | /cart/items | 批量移除商品 |
| DELETE | /cart | 清空购物车 |

### 合并与结算

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /cart/merge | 合并购物车 |
| POST | /cart/checkout | 结算（锁价） |

## 请求头说明

| Header | 描述 | 必填 |
|--------|------|------|
| X-User-Id | 登录用户ID | 登录用户必填 |
| X-Anon-Id | 游客ID | 游客必填 |

## 数据结构

### CartItem (购物车商品项)

```json
{
  "skuId": 10001,
  "spuId": 1001,
  "title": "商品标题",
  "imageUrl": "https://...",
  "unitPrice": 99.00,
  "qty": 2,
  "checked": true,
  "skuAttrs": "颜色:红色;尺码:XL",
  "categoryId": 100,
  "warehouseId": 1,
  "addedAt": "2024-01-01T10:00:00",
  "updatedAt": "2024-01-01T12:00:00"
}
```

### CheckoutResponse (结算响应)

```json
{
  "canOrder": true,
  "priceLockNo": "PL202401011000001234",
  "signature": "sha256...",
  "signVersion": 1,
  "expireAt": "2024-01-01T10:15:00",
  "originalAmount": 198.00,
  "totalDiscount": 20.00,
  "payableAmount": 178.00,
  "items": [...],
  "promotionHits": [...],
  "stockCheckResults": [...]
}
```

## 配置说明

```yaml
cart:
  expire-days: 30        # 购物车过期时间（天）
  max-items: 100         # 单个购物车最大商品种类数
  max-qty-per-sku: 99    # 单个SKU最大购买数量

service:
  pricing:
    url: http://localhost:8084
  inventory:
    url: http://localhost:8082
```

## 数据库表

### t_cart_merge_log (购物车合并日志)

```sql
CREATE TABLE t_cart_merge_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    anon_id VARCHAR(64) NOT NULL,
    merge_strategy VARCHAR(32) NOT NULL,
    anon_cart_snapshot TEXT,
    user_cart_snapshot TEXT,
    merged_cart_snapshot TEXT,
    merged_sku_count INT DEFAULT 0,
    conflict_sku_count INT DEFAULT 0,
    merge_time_ms BIGINT DEFAULT 0,
    remark VARCHAR(500),
    created_at DATETIME NOT NULL
);
```

## 使用示例

### 1. 添加商品到购物车

```bash
curl -X POST http://localhost:8083/cart/items \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1001" \
  -d '{
    "skuId": 10001,
    "title": "测试商品",
    "unitPrice": 99.00,
    "qty": 2
  }'
```

### 2. 合并购物车

```bash
curl -X POST http://localhost:8083/cart/merge \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1001" \
  -d '{
    "anonId": "anon-uuid-123",
    "mergeStrategy": "QTY_ADD"
  }'
```

### 3. 结算

```bash
curl -X POST http://localhost:8083/cart/checkout \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1001" \
  -d '{
    "userCouponNos": ["COUPON001"],
    "lockMinutes": 15
  }'
```

### 4. 下单（使用锁价结果）

```bash
curl -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{
    "clientRequestId": "req-uuid-123",
    "userId": 1001,
    "priceLockNo": "PL202401011000001234",
    "signature": "sha256...",
    "items": [
      {"skuId": 10001, "qty": 2, "title": "测试商品", "price": 99.00}
    ]
  }'
```

## 流程图

详见 [flowchart.md](./flowchart.md)

## 依赖服务

- **Redis**: 购物车数据存储
- **MySQL**: 合并日志存储
- **pricing-service**: 促销计算、锁价
- **inventory-service**: 库存校验

## 启动

```bash
# 确保依赖服务已启动
# MySQL, Redis, pricing-service, inventory-service

cd cart-service
mvn spring-boot:run
```

服务端口: 8083
