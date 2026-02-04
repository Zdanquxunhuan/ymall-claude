# 库存事件消费 - 防乱序与幂等处理

## 概述

本模块实现了订单服务消费库存预留事件（StockReserved/StockReserveFailed）的功能，包含完整的防乱序与重复消费处理机制。

## 功能特性

### 1. 新增订单状态

| 状态 | 编码 | 说明 |
|------|------|------|
| 库存已预留 | STOCK_RESERVED | 库存预留成功，等待后续支付 |
| 库存预留失败 | STOCK_FAILED | 库存不足或预留失败，订单终止 |

### 2. 合法状态跃迁

```
CREATED ──┬── STOCK_RESERVED ──→ STOCK_RESERVED
          │
          ├── STOCK_RESERVE_FAILED ──→ STOCK_FAILED
          │
          └── CANCEL ──→ CANCELED

STOCK_RESERVED ── CANCEL ──→ CANCELED
```

### 3. 防乱序策略

- **事件携带关键信息**：`event_id`（全局唯一）、`event_time`（事件时间）
- **CAS 更新**：`WHERE status = CREATED`，仅当订单处于预期状态时才更新
- **乱序处理**：更新失败时记录为 IGNORED，并写入 `t_order_state_flow` 审计表

### 4. 重复消费幂等

基于 `t_mq_consume_log` 表，使用 `event_id + consumer_group` 唯一约束实现幂等。

## 核心组件

### 消费者

| 类名 | 说明 | Topic | Tag |
|------|------|-------|-----|
| StockReservedConsumer | 库存预留成功事件消费者 | INVENTORY_TOPIC | STOCK_RESERVED |
| StockReserveFailedConsumer | 库存预留失败事件消费者 | INVENTORY_TOPIC | STOCK_RESERVE_FAILED |

### 事件类

| 类名 | 说明 |
|------|------|
| StockReservedEvent | 库存预留成功事件 |
| StockReserveFailedEvent | 库存预留失败事件 |

### 数据表

| 表名 | 说明 |
|------|------|
| t_mq_consume_log | MQ消费日志表，用于幂等 |
| t_order_state_flow | 订单状态流转审计表 |

## 使用方式

### 1. 数据库初始化

执行 SQL 脚本更新表结构：

```sql
-- 更新 t_mq_consume_log 表，添加 ignored_reason 字段
ALTER TABLE t_mq_consume_log 
ADD COLUMN `ignored_reason` VARCHAR(500) COMMENT '忽略原因（乱序消息时记录）' AFTER `result`;

-- 更新状态枚举注释
ALTER TABLE t_mq_consume_log 
MODIFY COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' 
COMMENT '状态: PROCESSING/SUCCESS/FAILED/IGNORED';

-- 更新订单状态枚举注释
ALTER TABLE t_order 
MODIFY COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED' 
COMMENT '订单状态: CREATED/STOCK_RESERVED/STOCK_FAILED/CANCELED';
```

### 2. 启动服务

确保 RocketMQ 已启动，然后启动 order-service：

```bash
cd order-service
mvn spring-boot:run
```

### 3. 测试接口

#### 模拟库存预留成功事件

```bash
curl -X POST http://localhost:8082/demo/stock-event/stock-reserved \
  -H "Content-Type: application/json" \
  -d '{"orderNo": "ORD202401151030451234567"}'
```

#### 模拟库存预留失败事件

```bash
curl -X POST http://localhost:8082/demo/stock-event/stock-reserve-failed \
  -H "Content-Type: application/json" \
  -d '{"orderNo": "ORD20240030451234567", "errorCode": "STOCK_INSUFFICIENT", "errorMessage": "库存不足"}'
```

#### 测试重复消息（幂等性验证）

```bash
curl -X POST http://localhost:8082/demo/stock-event/duplicate-test \
  -H "Content-Type: application/json" \
  -d '{"orderNo": "ORD202401151030451234567"}'
```

响应示例：
```json
{
  "code": "SUCCESS",
  "data": {
    "orderNo": "ORD202401151030451234567",
    "eventId": "abc123...",
    "initialStatus": "CREATED",
    "firstCallResult": "SUCCESS",
    "secondCallResult": "SUCCESS",
    "finalStatus": "STOCK_RESERVED",
    "stateFlowCount": 2
  }
}
```

#### 测试乱序消息

```bash
curl -X POST http://localhost:8082/demo/stock-event/out-of-order-test \
  -H "Content-Type: application/json" \
  -d '{"orderNo": "ORD202401151030451234567"}'
```

响应示例：
```json
{
  "code": "SUCCESS",
  "data": {
    "orderNo": "ORD202401151030451234567",
    "initialStatus": "CREATED",
    "firstEventId": "event1...",
    "firstEventResult": "SUCCESS - StockReserveFailed processed",
    "midStatus": "STOCK_FAILED",
    "secondEventId": "event2...",
    "secondEventResult": "SUCCESS - StockReserved processed (should be ignored)",
    "finalStatus": "STOCK_FAILED",
    "stateFlows": [
      "[STOCK_RESERVE_FAILED] CREATED -> STOCK_FAILED (event1...) 库存预留失败: STOCK_INSUFFICIENT - 库存不足",
      "[STOCK_RESERVED] STOCK_FAILED -> STOCK_FAILED (event2...) [IGNORED] 当前状态[库存预留失败]不允许执行[库存预留成功]事件，可能是乱序消息"
    ],
    "stable": true,
    "verification": "PASS - 状态稳定在 STOCK_FAILED，乱序消息被正确忽略"
  }
}
```

#### 查询订单状态

```bash
curl http://localhost:8082/demo/stock-event/order-status/ORD202401151030451234567
```

## 设计原则

### 1. 幂等性保证

```
┌─────────────────────────────────────────────────────────────────┐
│                    幂等检查流程                                  │
├─────────────────────────────────────────────────────────────────┤
│ 1. 查询 t_mq_consume_log (event_id + consumer_group)           │
│ 2. 如果存在且状态为 SUCCESS/IGNORED，直接返回                    │
│ 3. 如果不存在，插入 PROCESSING 状态记录                          │
│ 4. 如果插入失败（唯一约束冲突），说明并发处理，重新查询            │
└─────────────────────────────────────────────────────────────────┘
```

### 2. 乱序容忍

```
┌─────────────────────────────────────────────────────────────────┐
│                    乱序处理流程                                  │
├─────────────────────────────────────────────────────────────────┤
│ 1. 状态机校验：canTransition(currentStatus, event)              │
│ 2. 如果不允许转换，说明是乱序消息                                │
│ 3. 记录 IGNORED 状态流转到 t_order_state_flow                   │
│ 4. 标记消费日志为 IGNORED                                       │
│ 5. CAS 更新失败也视为乱序，同样处理                              │
└─────────────────────────────────────────────────────────────────┘
```

### 3. 可追溯性

所有状态变更（包括被忽略的乱序消息）都记录在 `t_order_state_flow` 表中，便于问题排查和审计。

## 文件清单

```
order-service/
├── src/main/java/com/yuge/order/
│   ├── domain/
│   │   ├── entity/
│   │   │   └── MqConsumeLog.java          # MQ消费日志实体
│   │   ├── enums/
│   │   │   ├── OrderStatus.java           # 订单状态枚举（新增 STOCK_RESERVED/STOCK_FAILED）
│   │   │   ├── OrderEvent.java            # 订单事件枚举（新增 STOCK_RESERVED/STOCK_RESERVE_FAILED）
│   │   │   └── ConsumeStatus.java         # 消费状态枚举（新增 IGNORED）
│   │   ├── event/
│   │   │   ├── StockReservedEvent.java    # 库存预留成功事件
│   │   │   └── StockReserveFailedEvent.java # 库存预留失败事件
│   │   └── statemachine/
│   │       └── OrderStateMachine.java     # 订单状态机（更新转换表）
│   ├── infrastructure/
│   │   ├── consumer/
│   │   │   ├── StockReservedConsumer.java # 库存预留成功消费者
│   │   │   └── StockReserveFailedConsumer.java # 库存预留失败消费者
│   │   ├── mapper/
│   │   │   ├── OrderMapper.java           # 订单Mapper（新增 casUpdateStatusOnly）
│   │   │   └── MqConsumeLogMapper.java    # MQ消费日志Mapper（新增 markAsIgnored）
│   │   └── repository/
│   │       ├── OrderRepository.java       # 订单仓储（新增乱序处理方法）
│   │       └── MqConsumeLogRepository.java # MQ消费日志仓储（新增 markIgnored）
│   └── interfaces/controller/
│       └── StockEventDemoController.java  # 演示控制器
├── docs/
│   └── flowchart.md                       # 状态机流程图（更新）
└── README-stock-event.md                  # 本文档
```

## 注意事项

1. **事务边界**：消费者的 `processXxx` 方法使用 `@Transactional`，确保状态更新和审计日志在同一事务中
2. **消费者组隔离**：StockReserved 和 StockReserveFailed 使用不同的消费者组，避免相互影响
3. **重试策略**：消费失败时最多重试 3 次，超过后记录日志但不再抛出异常
4. **链路追踪**：事件中的 traceId 会透传到消费者，便于全链路追踪
