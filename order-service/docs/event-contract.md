# 订单服务事件契约文档

## 概述

订单服务通过 Outbox 模式发布领域事件，保证业务操作与事件发布的最终一致性。

## 事件通道

| 属性 | 值 |
|------|-----|
| Topic | `ORDER_TOPIC` |
| NameServer | `localhost:9876` |
| 消息模式 | 至少一次投递 (At Least Once) |

## 事件列表

### 1. OrderCreated - 订单创建事件

#### 基本信息

| 属性 | 值 |
|------|-----|
| Topic | `ORDER_TOPIC` |
| Tag | `ORDER_CREATED` |
| Key | `{orderNo}` |
| Version | `1.0` |

#### Payload Schema

```json
{
  "eventId": "string, 事件唯一ID",
  "orderNo": "string, 订单号",
  "userId": "long, 用户ID",
  "amount": "decimal, 订单总金额",
  "status": "string, 订单状态(CREATED)",
  "items": [
    {
      "skuId": "long, SKU ID",
      "qty": "int, 购买数量",
      "title": "string, 商品标题",
      "price": "decimal, 商品单价"
    }
  ],
  "eventTime": "datetime, 事件发生时间",
  "traceId": "string, 链路追踪ID",
  "version": "string, 事件版本"
}
```

#### 示例

```json
{
  "eventId": "a1b2c3d4e5f6g7h8i9j0",
  "orderNo": "ORD202401151030451234567",
  "userId": 10001,
  "amount": 299.98,
  "status": "CREATED",
  "items": [
    {
      "skuId": 1001,
      "qty": 2,
      "title": "iPhone 15 Pro",
      "price": 149.99
    }
  ],
  "eventTime": "2024-01-15T10:30:45",
  "traceId": "trace-123456789",
  "version": "1.0"
}
```

#### 消费者处理建议

- **库存服务**: 预扣库存
- **支付服务**: 创建待支付记录
- **通知服务**: 发送订单创建通知

---

### 2. OrderCanceled - 订单取消事件

#### 基本信息

| 属性 | 值 |
|------|-----|
| Topic | `ORDER_TOPIC` |
| Tag | `ORDER_CANCELED` |
| Key | `{orderNo}` |
| Version | `1.0` |

#### Payload Schema

```json
{
  "eventId": "string, 事件唯一ID",
  "orderNo": "string, 订单号",
  "userId": "long, 用户ID",
  "amount": "decimal, 订单总金额",
  "cancelReason": "string, 取消原因(可选)",
  "operator": "string, 操作人(可选)",
  "eventTime": "datetime, 事件发生时间",
  "traceId": "string, 链路追踪ID",
  "version": "string, 事件版本"
}
```

#### 示例

```json
{
  "eventId": "b2c3d4e5f6g7h8i9j0k1",
  "orderNo": "ORD202401151030451234567",
  "userId": 10001,
  "amount": 299.98,
  "cancelReason": "用户主动取消",
  "operator": "user_10001",
  "eventTime": "2024-01-15T11:00:00",
  "traceId": "trace-987654321",
  "version": "1.0"
}
```

#### 消费者处理建议

- **库存服务**: 释放预扣库存
- **支付服务**: 关闭支付单
- **通知服务**: 发送订单取消通知
- **优惠券服务**: 退还已使用优惠券

---

## Outbox 表结构

```sql
CREATE TABLE `t_outbox_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `event_id` VARCHAR(64) NOT NULL COMMENT '事件ID',
    `biz_key` VARCHAR(128) NOT NULL COMMENT '业务键(订单号)',
    `topic` VARCHAR(128) NOT NULL COMMENT '消息主题',
    `tag` VARCHAR(64) COMMENT '消息标签',
    `payload_json` TEXT NOT NULL COMMENT '消息内容(JSON)',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
    `retry_count` INT NOT NULL DEFAULT 0,
    `max_retry` INT NOT NULL DEFAULT 5,
    `next_retry_at` DATETIME,
    `sent_at` DATETIME,
    `trace_id` VARCHAR(64),
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_id` (`event_id`)
);
```

## 消费幂等

消费者必须实现幂等处理，推荐方案：

1. **基于 eventId 去重**: 消费前检查 eventId 是否已处理
2. **业务幂等**: 基于业务状态判断是否需要处理

```java
// 示例：消费者幂等处理
public void onMessage(OrderCreatedEvent event) {
    // 1. 检查是否已处理
    if (isEventProcessed(event.getEventId())) {
        log.info("Event already processed, eventId={}", event.getEventId());
        return;
    }
    
    // 2. 执行业务逻辑
    processOrder(event);
    
    // 3. 标记已处理
    markEventProcessed(event.getEventId());
}
```

## 版本兼容性

| 版本 | 变更说明 | 兼容性 |
|------|---------|--------|
| 1.0 | 初始版本 | - |

### 版本升级策略

1. **新增字段**: 向后兼容，消费者忽略未知字段
2. **删除字段**: 提前通知消费者，过渡期保留字段
3. **修改字段类型**: 发布新版本，双写过渡

## 监控告警

### 关键指标

| 指标 | 阈值 | 告警级别 |
|------|------|---------|
| Outbox 积压数量 | > 1000 | WARNING |
| Outbox 积压数量 | > 5000 | CRITICAL |
| 事件发送失败率 | > 1% | WARNING |
| 事件发送延迟 | > 5s | WARNING |

### 告警处理

1. **积压告警**: 检查 Relay 服务状态，扩容或重启
2. **失败告警**: 检查 MQ 连接，查看失败原因
3. **延迟告警**: 检查数据库性能，优化查询
