# Inventory Service

库存服务 - 基于 Redis Lua 原子预扣的高并发防超卖解决方案。

## 功能特性

- **Redis Lua 原子预扣**：使用 Lua 脚本保证库存检查和扣减的原子性，支持高并发场景
- **三阶段库存管理**：TryReserve（预留）→ Confirm（确认）→ Release（释放）
- **多层幂等保障**：MQ消费幂等 + Redis幂等标记 + DB唯一约束
- **事件驱动架构**：消费 OrderCreated 事件，发布 StockReserved/StockReserveFailed 事件
- **超时自动释放**：支持预留超时扫描任务，自动释放过期预留
- **完整流水记录**：所有库存变动记录流水，支持审计和问题排查

## 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                     Inventory Service                        │
├─────────────────────────────────────────────────────────────┤
│  API Layer          │  InventoryController                   │
├─────────────────────────────────────────────────────────────┤
│  Application Layer  │  InventoryService                      │
├─────────────────────────────────────────────────────────────┤
│  Domain Layer       │  Entity / Enum / Event                 │
├─────────────────────────────────────────────────────────────┤
│  Infrastructure     │  Redis (Lua) / MySQL / RocketMQ        │
└─────────────────────────────────────────────────────────────┘
```

## 数据库表结构

### t_inventory - 库存表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| sku_id | BIGINT | SKU ID |
| warehouse_id | BIGINT | 仓库ID |
| available_qty | INT | 可用库存 |
| reserved_qty | INT | 预留库存 |
| version | INT | 乐观锁版本号 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| deleted | TINYINT | 逻辑删除标记 |

**唯一约束**: `uk_sku_warehouse (sku_id, warehouse_id)`

### t_inventory_reservation - 库存预留表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| order_no | VARCHAR(64) | 订单号 |
| sku_id | BIGINT | SKU ID |
| warehouse_id | BIGINT | 仓库ID |
| qty | INT | 预留数量 |
| status | VARCHAR(20) | 状态: RESERVED/CONFIRMED/RELEASED |
| expire_at | DATETIME | 预留过期时间 |
| version | INT | 乐观锁版本号 |

**唯一约束**: `uk_order_sku_warehouse (order_no, sku_id, warehouse_id)`

### t_inventory_txn - 库存流水表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| txn_id | VARCHAR(64) | 流水ID |
| order_no | VARCHAR(64) | 关联订单号 |
| sku_id | BIGINT | SKU ID |
| warehouse_id | BIGINT | 仓库ID |
| delta_available | INT | 可用库存变化量 |
| delta_reserved | INT | 预留库存变化量 |
| available_after | INT | 变更后可用库存 |
| reserved_after | INT | 变更后预留库存 |
| reason | VARCHAR(50) | 变动原因 |
| trace_id | VARCHAR(64) | 链路追踪ID |

## Redis Key 设计

| Key Pattern | 说明 | 示例 |
|-------------|------|------|
| `inv:{warehouseId}:{skuId}` | 可用库存 | `inv:1:1001` = 100 |
| `inv:reserved:{orderNo}:{warehouseId}:{skuId}` | 预留幂等标记 | `inv:reserved:ORD001:1:1001` = 5 |

## API 接口

### 查询库存

```http
GET /inventory/{skuId}?warehouseId=1
```

**响应示例**:
```json
{
  "code": "0",
  "message": "success",
  "data": {
    "id": 1,
    "skuId": 1001,
    "warehouseId": 1,
    "availableQty": 100,
    "reservedQty": 10,
    "totalQty": 110,
    "redisAvailableQty": 100,
    "updatedAt": "2024-01-01T12:00:00"
  }
}
```

### 查询订单预留记录

```http
GET /inventory/reservations?orderNo=ORD20240101001
```

**响应示例**:
```json
{
  "code": "0",
  "message": "success",
  "data": [
    {
      "id": 1,
      "orderNo": "ORD20240101001",
      "skuId": 1001,
      "warehouseId": 1,
      "qty": 5,
      "status": "RESERVED",
      "statusDesc": "已预留",
      "expireAt": "2024-01-01T12:30:00",
      "expired": false
    }
  ]
}
```

### 同步库存到Redis

```http
POST /inventory/{skuId}/sync?warehouseId=1
```

### 手动释放预留

```http
POST /inventory/reservations/release?orderNo=ORD20240101001&reason=手动释放
```

### 手动确认预留

```http
POST /inventory/reservations/confirm?orderNo=ORD20240101001
```

## MQ 事件

### 消费事件

| Topic | Tag | 说明 |
|-------|-----|------|
| ORDER_TOPIC | ORDER_CREATED | 订单创建事件 |

### 发布事件

| Topic | Tag | 说明 |
|-------|-----|------|
| INVENTORY_TOPIC | STOCK_RESERVED | 库存预留成功 |
| INVENTORY_TOPIC | STOCK_RESERVE_FAILED | 库存预留失败 |

## 核心流程

### 1. 库存预留 (TryReserve)

```
1. MQ消费幂等检查 (t_mq_consume_log)
2. 业务幂等检查 (t_inventory_reservation)
3. Redis Lua 原子预扣
4. 落库 reservation 记录
5. CAS 更新 DB 库存
6. 记录流水
7. 发布 StockReserved/StockReserveFailed 事件
```

### 2. 库存确认 (Confirm)

```
1. 查询 RESERVED 状态的预留记录
2. CAS 更新预留状态为 CONFIRMED
3. CAS 更新 DB 库存 (reserved 减少)
4. 记录流水
```

### 3. 库存释放 (Release)

```
1. 查询 RESERVED 状态的预留记录
2. CAS 更新预留状态为 RELEASED
3. Redis Lua 归还库存
4. CAS 更新 DB 库存 (available 增加, reserved 减少)
5. 记录流水
```

## 幂等策略

### 三层幂等保障

1. **MQ消费层**: `t_mq_consume_log` 表记录消费状态，唯一约束 `(event_id, consumer_group)`
2. **Redis层**: 幂等标记 `inv:reserved:{orderNo}:{warehouseId}:{skuId}`，带24小时过期
3. **DB层**: `t_inventory_reservation` 唯一约束 `(order_no, sku_id, warehouse_id)`

### Lua 脚本幂等

```lua
-- 预留时检查幂等标记
if EXISTS(reservedKey) then
    return 0  -- 已预留，幂等返回
end

-- 释放时检查幂等标记
if not EXISTS(reservedKey) then
    return -1  -- 已释放或未预留，幂等返回
end
```

## 高并发防超卖

### 原理

- Redis Lua 脚本在 Redis 中串行执行，保证原子性
- 检查库存和扣减库存在同一个 Lua 脚本中完成
- 无需分布式锁，无锁竞争开销

### 性能

- 单机 Redis 可支撑 10万+ QPS
- Lua 脚本执行时间 < 1ms

### 数据一致性

- Redis 作为库存控制主节点
- MySQL 作为持久化存储
- 通过对账任务保证最终一致性

## 配置说明

```yaml
inventory:
  reservation:
    # 预留过期时间（分钟）
    expire-minutes: 30
    timeout:
      # 是否启用超时释放任务
      enabled: false
      # 每批处理数量
      batch-size: 100
      # 执行周期
      cron: "0 * * * * ?"
```

## 快速开始

### 1. 创建数据库

```sql
CREATE DATABASE ymall_inventory;
USE ymall_inventory;
-- 执行 src/main/resources/schema-inventory.sql
```

### 2. 初始化库存数据

```sql
INSERT INTO t_inventory (sku_id, warehouse_id, available_qty, reserved_qty) VALUES
(1001, 1, 100, 0),
(1002, 1, 200, 0);
```

### 3. 同步库存到 Redis

```bash
curl -X POST "http://localhost:8082/inventory/1001/sync?warehouseId=1"
curl -X POST "http://localhost:8082/inventory/1002/sync?warehouseId=1"
```

### 4. 启动服务

```bash
mvn spring-boot:run -pl inventory-service
```

## 测试验证

### 高并发测试

使用压测工具（如 JMeter、wrk）模拟高并发下单：

```bash
# 100个并发，每个请求预留1个库存
wrk -t10 -c100 -d30s http://localhost:8081/order/create
```

验证：
1. 库存不会超卖（available_qty 不会变为负数）
2. 预留记录数量正确
3. 流水记录完整

### 幂等测试

```bash
# 重复发送相同订单的预留请求
curl -X POST "http://localhost:8082/inventory/reserve" \
  -H "Content-Type: application/json" \
  -d '{"orderNo":"ORD001","skuId":1001,"warehouseId":1,"qty":5}'

# 第二次请求应该幂等返回，不重复扣减
curl -X POST "http://localhost:8082/inventory/reserve" \
  -H "Content-Type: application/json" \
  -d '{"orderNo":"ORD001","skuId":1001,"warehouseId":1,"qty":5}'
```

## 扩展点

### 1. 分布式调度

生产环境建议使用 XXL-JOB 替代本地定时任务：

```java
@XxlJob("releaseExpiredReservations")
public void releaseExpiredReservations() {
    // 超时释放逻辑
}
```

### 2. 订单状态检查

超时释放前检查订单状态：

```java
private boolean shouldRelease(InventoryReservation reservation) {
    OrderStatus status = orderServiceClient.getOrderStatus(reservation.getOrderNo());
    if (status == OrderStatus.PAID) {
        // 订单已支付，应该确认而非释放
        inventoryService.confirmReservation(reservation.getOrderNo());
        return false;
    }
    return true;
}
```

### 3. 多仓库路由

根据用户地址选择最近仓库：

```java
Long warehouseId = warehouseRouter.selectWarehouse(userId, skuId);
```

## 注意事项

1. **Redis 持久化**: 建议开启 AOF 持久化，防止数据丢失
2. **库存同步**: 服务启动时需要将 DB 库存同步到 Redis
3. **对账任务**: 定期对比 Redis 和 DB 库存，修复不一致
4. **监控告警**: 监控库存预留失败率、超时释放数量等指标

## 相关文档

- [流程图](./flowchart.md)
- [SQL Schema](./src/main/resources/schema-inventory.sql)
- [Lua Scripts](./src/main/resources/lua/)
