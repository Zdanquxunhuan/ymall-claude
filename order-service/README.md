# Order Service - 订单服务

订单域 V1 版本，实现订单创建、取消功能，支持幂等、状态机、Outbox 模式。

## 功能特性

| 功能 | 说明 |
|------|------|
| 创建订单 | 幂等创建，支持 clientRequestId 去重 |
| 取消订单 | 幂等取消，CAS 状态更新 |
| 状态机 | CREATED -> CANCELED，合法跃迁校验 |
| Outbox | 事务内写 Outbox，保证最终一致性 |
| 状态审计 | 所有状态变更写入 t_order_state_flow |

## API 接口

### 1. 创建订单

```bash
POST /orders
Content-Type: application/json

{
  "clientRequestId": "unique-request-id",
  "userId": 10001,
  "items": [
    {
      "skuId": 1001,
      "qty": 2,
      "title": "iPhone 15 Pro",
      "price": 7999.00
    }
  ],
  "remark": "测试订单"
}

# 响应
{
  "code": "00000",
  "message": "成功",
  "data": {
    "id": 1234567890,
    "orderNo": "ORD202401151030451234567",
    "userId": 10001,
    "amount": 15998.00,
    "status": "CREATED",
    "statusDesc": "已创建",
    "items": [...],
    "createdAt": "2024-01-15T10:30:45"
  },
  "traceId": "xxx"
}
```

### 2. 查询订单

```bash
GET /orders/{orderNo}

# 响应
{
  "code": "00000",
  "data": {
    "orderNo": "ORD202401151030451234567",
    "status": "CREATED",
    ...
  }
}
```

### 3. 取消订单

```bash
POST /orders/{orderNo}/cancel
Content-Type: application/json
X-Idempotency-Key: cancel-unique-key

{
  "cancelReason": "用户主动取消",
  "operator": "user_10001"
}

# 响应
{
  "code": "00000",
  "data": {
    "orderNo": "ORD202401151030451234567",
    "status": "CANCELED",
    "statusDesc": "已取消"
  }
}
```

## 数据表

| 表名 | 说明 |
|------|------|
| t_order | 订单主表 |
| t_order_item | 订单明细表 |
| t_order_state_flow | 状态流转审计表 |
| t_outbox_event | Outbox 事件发件箱 |

## 状态机

```
         CREATE
(初始) ─────────▶ CREATED ─────────▶ CANCELED (终态)
                            CANCEL
```

## 幂等设计

### 创建订单幂等

1. **Redis 层**: `@Idempotent` 注解，key = `order:create:{clientRequestId}`
2. **DB 层**: 唯一索引 `uk_user_client_request(user_id, client_request_id)`

### 取消订单幂等

1. **Redis 层**: `@Idempotent` 注解，key = `order:cancel:{X-Idempotency-Key}`
2. **业务层**: 状态判断，已取消订单直接返回

## Outbox 模式

```
┌─────────────────────────────────────────┐
│            @Transactional               │
│  ┌─────────────┐    ┌─────────────┐    │
│  │ 业务操作     │───▶│ 写 Outbox   │    │
│  │ INSERT/UPDATE│    │ INSERT      │    │
│  └─────────────┘    └─────────────┘    │
│                          │              │
└──────────────────────────┼──────────────┘
                           │ COMMIT
                           ▼
                    ┌─────────────┐
                    │ Relay 投递   │ (后续实现)
                    │ 到 RocketMQ │
                    └─────────────┘
```

## 事件契约

| 事件 | Topic | Tag | 说明 |
|------|-------|-----|------|
| OrderCreated | ORDER_TOPIC | ORDER_CREATED | 订单创建事件 |
| OrderCanceled | ORDER_TOPIC | ORDER_CANCELED | 订单取消事件 |

详见 [event-contract.md](docs/event-contract.md)

## 快速启动

```bash
# 1. 启动基础设施
docker-compose up -d

# 2. 初始化数据库
mysql -h localhost -u ymall -pymall123456 ymall < deploy/mysql/init/02_order_schema.sql

# 3. 编译
mvn clean install -DskipTests

# 4. 启动服务
cd order-service
mvn spring-boot:run

# 5. 健康检查
curl http://localhost:8082/orders/health
```

## 测试

### 单元测试

```bash
cd order-service
mvn test
```

### 集成测试

```bash
mvn test -Dtest=OrderIntegrationTest
```

### 压测

```bash
# 创建订单压测 (500 RPS, 60s)
k6 run k6/order-create-test.js

# 综合测试 (幂等/取消/非法状态)
k6 run k6/order-comprehensive-test.js
```

## 压测指标

| 场景 | 指标 | 预期 |
|------|------|------|
| 创建订单 | 500 RPS, 60s | 错误率 < 0.1% |
| 幂等创建 | 1000 并发相同 key | 成功率 >= 99.9%，结果一致 |
| 重复取消 | 同一订单多次取消 | 幂等返回 |

## 目录结构

```
order-service/
├── src/main/java/com/yuge/order/
│   ├── api/                    # Controller 层
│   │   ├── OrderController.java
│   │   └── dto/                # 请求/响应 DTO
│   ├── application/            # Application 层
│   │   └── OrderService.java
│   ├── domain/                 # Domain 层
│   │   ├── entity/             # 实体
│   │   ├── enums/              # 枚举
│   │   ├── event/              # 领域事件
│   │   └── statemachine/       # 状态机
│   └── infrastructure/         # Infrastructure 层
│       ├── mapper/             # MyBatis Mapper
│       └── repository/         # 仓储
├── src/main/resources/
│   └── application.yml
├── src/test/
│   ├── java/                   # 测试代码
│   └── resources/              # 测试配置
├── docs/
│   ├── event-contract.md       # 事件契约文档
│   └── flowchart.md            # 流程图
└── pom.xml
```

## 配置说明

```yaml
server:
  port: 8082

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ymall
    username: ymall
    password: ymall123456

  data:
    redis:
      host: localhost
      port: 6379
      password: redis123456

rocketmq:
  name-server: localhost:9876
  producer:
    group: order-producer-group
```
