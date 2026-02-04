# Product Service

商品服务 - SPU/SKU主数据管理与发布链路，基于Outbox模式实现最终一致性。

## 功能特性

- **SPU/SKU管理**：支持SPU（标准产品单元）和SKU（库存量单位）的创建、更新、查询
- **发布链路**：SKU发布/上架/下架，状态机管理
- **Outbox模式**：事务性发件箱，保证业务操作和事件发布的原子性
- **事件驱动**：发布ProductPublished/ProductUpdated事件，支持下游服务订阅
- **最终一致性**：通过Outbox Relay Worker实现可靠的事件投递

## 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                     Product Service                          │
├─────────────────────────────────────────────────────────────┤
│  API Layer          │  ProductController                     │
├─────────────────────────────────────────────────────────────┤
│  Application Layer  │  ProductService                        │
├─────────────────────────────────────────────────────────────┤
│  Domain Layer       │  Entity / Enum / Event                 │
├─────────────────────────────────────────────────────────────┤
│  Infrastructure     │  MySQL / RocketMQ / Outbox Relay       │
└─────────────────────────────────────────────────────────────┘
```

## 数据库表结构

### t_spu - SPU表

| 字段 | 类型 | 说明 |
|------|------|------|
| spu_id | BIGINT | SPU ID (主键) |
| title | VARCHAR(200) | SPU标题 |
| category_id | BIGINT | 分类ID |
| brand_id | BIGINT | 品牌ID |
| description | TEXT | 商品描述 |
| status | VARCHAR(20) | 状态: DRAFT/PENDING/PUBLISHED/OFFLINE |
| version | INT | 乐观锁版本号 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### t_sku - SKU表

| 字段 | 类型 | 说明 |
|------|------|------|
| sku_id | BIGINT | SKU ID (主键) |
| spu_id | BIGINT | 所属SPU ID |
| title | VARCHAR(200) | SKU标题 |
| attrs_json | JSON | 销售属性JSON |
| price | DECIMAL(10,2) | 销售价格 |
| original_price | DECIMAL(10,2) | 原价 |
| sku_code | VARCHAR(64) | SKU编码 |
| bar_code | VARCHAR(64) | 条形码 |
| weight | DECIMAL(10,3) | 重量(kg) |
| status | VARCHAR(20) | 状态: DRAFT/PENDING/PUBLISHED/OFFLINE |
| publish_time | DATETIME | 发布时间 |
| version | INT | 乐观锁版本号 |

### t_outbox - 事务性发件箱表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID |
| event_id | VARCHAR(64) | 事件ID (UUID) |
| event_type | VARCHAR(50) | 事件类型 |
| aggregate_type | VARCHAR(50) | 聚合类型: SKU/SPU |
| aggregate_id | BIGINT | 聚合ID |
| payload | JSON | 事件负载 |
| status | VARCHAR(20) | 状态: PENDING/SENT/FAILED |
| retry_count | INT | 重试次数 |
| max_retry | INT | 最大重试次数 |
| next_retry_at | DATETIME | 下次重试时间 |

## API 接口

### 创建SPU

```http
POST /products/spu
Content-Type: application/json

{
  "title": "iPhone 15",
  "categoryId": 1001,
  "brandId": 1,
  "description": "Apple iPhone 15 智能手机"
}
```

**响应示例**:
```json
{
  "code": "0",
  "message": "success",
  "data": {
    "spuId": 1,
    "title": "iPhone 15",
    "categoryId": 1001,
    "brandId": 1,
    "description": "Apple iPhone 15 智能手机",
    "status": "DRAFT",
    "statusDesc": "草稿",
    "createdAt": "2024-01-01T12:00:00",
    "updatedAt": "2024-01-01T12:00:00"
  }
}
```

### 创建SKU

```http
POST /products/sku
Content-Type: application/json

{
  "spuId": 1,
  "title": "iPhone 15 黑色 256GB",
  "attrsJson": "{\"颜色\":\"黑色\",\"容量\":\"256GB\"}",
  "price": 6999.00,
  "originalPrice": 7499.00,
  "skuCode": "IPHONE15-BLK-256"
}
```

**响应示例**:
```json
{
  "code": "0",
  "message": "success",
  "data": {
    "skuId": 1,
    "spuId": 1,
    "title": "iPhone 15 黑色 256GB",
    "attrsJson": "{\"颜色\":\"黑色\",\"容量\":\"256GB\"}",
    "price": 6999.00,
    "originalPrice": 7499.00,
    "skuCode": "IPHONE15-BLK-256",
    "status": "DRAFT",
    "statusDesc": "草稿",
    "createdAt": "2024-01-01T12:00:00"
  }
}
```

### 发布/上架SKU

```http
POST /products/sku/{skuId}/publish
```

**响应示例**:
```json
{
  "code": "0",
  "message": "success",
  "data": {
    "skuId": 1,
    "status": "PUBLISHED",
    "statusDesc": "已发布",
    "publishTime": "2024-01-01T12:00:00"
  }
}
```

### 查询SKU详情

```http
GET /products/sku/{skuId}
```

**响应示例**:
```json
{
  "code": "0",
  "message": "success",
  "data": {
    "skuId": 1,
    "spuId": 1,
    "title": "iPhone 15 黑色 256GB",
    "attrsJson": "{\"颜色\":\"黑色\",\"容量\":\"256GB\"}",
    "price": 6999.00,
    "status": "PUBLISHED",
    "statusDesc": "已发布",
    "publishTime": "2024-01-01T12:00:00",
    "spu": {
      "spuId": 1,
      "title": "iPhone 15",
      "categoryId": 1001,
      "brandId": 1
    }
  }
}
```

### 下架SKU

```http
POST /products/sku/{skuId}/offline
```

### 更新SKU

```http
PUT /products/sku/{skuId}
Content-Type: application/json

{
  "price": 6499.00
}
```

## MQ 事件

### 发布事件

| Topic | Tag | 说明 |
|-------|-----|------|
TOPIC | PRODUCT_PUBLISHED | 商品发布事件 |
| PRODUCT_TOPIC | PRODUCT_UPDATED | 商品更新事件 |
| PRODUCT_TOPIC | PRODUCT_OFFLINE | 商品下架事件 |

### 事件Schema (version: 1.0)

**ProductPublishedEvent**:
```json
{
  "eventId": "abc123...",
  "eventType": "PRODUCT_PUBLISHED",
  "version": "1.0",
  "eventTime": "2024-01-01T12:00:00",
  "source": "product-service",
  "skuId": 1001,
  "spuId": 100,
  "title": "iPhone 15 黑色 256GB",
  "attrsJson": "{\"颜色\":\"黑色\"}",
  "price": 6999.00,
  "categoryId": 1001,
  "brandId": 1,
  "skuCode": "SKU001",
  "publishTime": "2024-01-01T12:00:00"
}
```

**ProductUpdatedEvent**:
```json
{
  "eventId": "def456...",
  "eventType": "PRODUCT_UPDATED",
  "version": "1.0",
  "eventTime": "2024-01-01T13:00:00",
  "source": "product-service",
  "skuId": 1001,
  "spuId": 100,
  "title": "iPhone 15 黑色 256GB",
  "price": 6499.00,
  "status": "PUBLISHED",
  "updatedFields": ["price"]
}
```

## 核心流程

### 1. SKU发布流程 (Outbox Pattern)

```
1. 接收发布请求
2. 开启事务
   2.1 更新SKU状态为PUBLISHED
   2.2 更新SPU状态（如果是草稿）
   2.3 创建ProductPublishedEvent
   2.4 写入Outbox表
3. 提交事务
4. Outbox Relay Worker异步发送到MQ
```

### 2. Outbox Relay流程

```
1. 定时扫描Outbox表（每秒）
2. 查询PENDING状态的消息（带行锁）
3. 发送到RocketMQ
4. 成功则标记为SENT
5. 失败则重试（指数退避）
```

## 最终一致性保证

### Outbox Pattern 原理

1. **事务原子性**：业务操作和Outbox写入在同一事务
2. **消息可靠投递**：Relay Worker定时扫描，失败自动重试
3. **幂等消费**：消费者根据eventId去重
4. **并发控制**：FOR UPDATE SKIP LOCKED防止重复处理

### 重试策略

- 指数退避：2^n * 10秒
- 最大重试次数：3次
- 超过最大重试次数后标记为FAILED

## 配置说明

```yaml
product:
  outbox:
    # 是否启用Outbox Relay
    enabled: true
    # 扫描间隔（毫秒）
    interval: 1000
    # 每批处理数量
    batch-size: 100
```

## 快速开始

### 1. 创建数据库

```sql
CREATE DATABASE ymall_product;
USE ymall_product;
-- 执行 src/main/resources/schema-product.sql
```

### 2. 启动服务

```bash
mvn spring-boot:run -pl product-service
```

### 3. Demo演示

```baPU
curl -X POST "http://localhost:8083/products/spu" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "iPhone 15",
    "categoryId": 1001,
    "brandId": 1,
    "description": "Apple iPhone 15 智能手机"
  }'

# 2. 创建SKU
curl -X POST "http://localhost:8083/products/sku" \
  -H "Content-Type: application/json" \
  -d '{
    "spuId": 1,
    "title": "iPhone 15 黑色 256GB",
    "attrsJson": "{\"颜色\":\"黑色\",\"容量\":\"256GB\"}",
    "price": 6999.00,
    "skuCode": "IPHONE15-BLK-256"
  }'

# 3. 发布SKU
curl -X POST "http://localhost:8083/products/sku/1/publish"

# 4. 查询SKU详情
curl "http://localhost:8083/products/sku/1"
```

## 状态机

```
DRAFT ──publish()──▶ PUBLISHED ──offline()──▶ OFFLINE
                         ▲                       │
                         └───────publish()───────┘
```

| 状态 | 说明 | 可执行操作 |
|------|------|-----------|
| DRAFT | 草稿 | publish |
| PUBLISHED | 已发布 | update, offline |
| OFFLINE | 已下架 | publish |

## 相关文档

- [流程图](./flowchart.md)
- [SQL Schema](./src/main/resources/schema-product.sql)

## 扩展点

### 1. 审核流程

可扩展PENDING状态，实现商品审核流程：
```
DRAFT → PENDING → PUBLISHED
```

### 2. 定时上架

可扩展publish_time字段，实现定时上架功能。

### 3. 多仓库库存初始化

下游库存服务消费ProductPublished事件后，可初始化多仓库库存记录。
