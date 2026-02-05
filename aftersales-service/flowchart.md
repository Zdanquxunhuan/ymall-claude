# 售后退款流程图

## 1. 售后状态机

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          售后状态流转图                                   │
└─────────────────────────────────────────────────────────────────────────┘

                              ┌──────────┐
                              │  APPLIED │ (已申请)
                              └────┬─────┘
                                   │
              ┌────────────────────┼────────────────────┐
              │                    │                    │
              ▼                    ▼                    ▼
       ┌──────────┐         ┌──────────┐         ┌──────────┐
       │ APPROVED │         │ REJECTED │         │ CANCELED │
       │ (已审批)  │         │ (已拒绝)  │         │ (已取消)  │
       └────┬─────┘         └──────────┘         └──────────┘
            │                 [终态]               [终态]
            │
            ▼
     ┌────────────┐
     │ REFUNDING  │ (退款中)
     └─────┬──────┘
           │
     ┌─────┴─────┐
     │           │
     ▼           ▼
┌──────────┐  ┌──────────┐
│ REFUNDED │  │ APPROVED │ (退款失败可重试)
│ (已退款)  │  └──────────┘
└──────────┘
  [终态]
```

## 2. 退款完整流程

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                              退款最小闭环流程                                              │
└─────────────────────────────────────────────────────────────────────────────────────────┘

用户                aftersales-service        payment-service         order-service        inventory-service
 │                        │                        │                       │                      │
 │  1. 申请售后            │                        │                       │                      │
 │───────────────────────>│                        │                       │                      │
 │                        │                        │                       │                      │
 │                        │ 创建售后单(APPLIED)     │                       │                      │
 │                        │ 保存售后明细            │                       │                      │
 │                        │                        │                       │                      │
 │  2. 审批通过            │                        │                       │                      │
 │───────────────────────>│                        │                       │                      │
 │                        │                        │                       │                      │
 │                        │ 更新状态(APPROVED)      │                       │                      │
 │                        │                        │                       │                      │
 │                        │  3. 创建退款单          │                       │                      │
 │                        │───────────────────────>│                       │                      │
 │                        │                        │                       │                      │
 │                        │                        │ 创建退款单(INIT)       │                      │
 │                        │                        │ 更新状态(REFUNDING)    │                      │
 │                        │                        │                       │                      │
 │                        │<───────────────────────│                       │                      │
 │                        │      返回refundNo      │                       │                      │
 │                        │                        │                       │                      │
 │                        │ 更新状态(REFUNDING)     │                       │                      │
 │                        │                        │                       │                      │
 │                        │                        │  4. 模拟退款回调       │                      │
 │                        │                        │<──────────────────────│                      │
 │                        │                        │   (验签+幂等)          │                      │
 │                        │                        │                       │                      │
 │                        │                        │ 更新状态(SUCCESS)      │                      │
 │                        │                        │                       │                      │
 │                        │                        │  5. 发送RefundSucceeded事件                   │
 │                        │                        │───────────────────────────────────────────────>
 │                        │                        │                       │                      │
 │                        │  6. 消费RefundSucceeded │                       │                      │
 │                        │<───────────────────────│                       │                      │
 │                        │                        │                       │                      │
 │                        │ 更新状态(REFUNDED)      │                       │                      │
 │                        │                        │                       │                      │
 │                        │  7. 发送AfterSaleRefunded事件                                          │
 │                        │────────────────────────────────────────────────────────────────────────>
 │                        │                        │                       │                      │
 │                        │                        │  8. 消费事件           │                      │
 │                        │                        │       更新订单状态     │                      │
 │                        │                        │       (REFUNDED)      │                      │
 │                        │                        │                       │                      │
 │                        │                        │                       │  9. 消费事件          │
 │                        │                        │                       │     库存回补          │
 │                        │                        │                       │     (available+qty)   │
 │                        │                        │                       │     记录txn流水       │
 │                        │                        │                       │                      │
 └────────────────────────┴────────────────────────┴───────────────────────┴──────────────────────┘
```

## 3. 退款幂等设计

### 3.1 售后申请幂等
- **策略**: 同一订单同时只能有一个APPLIED状态的售后单
- **实现**: 申请前检查是否存在进行中的售后单

### 3.2 退款单创建幂等
- **策略**: 按order_no唯一约束
- **实现**: 
  ```sql
  UNIQUE KEY `uk_order_no` (`order_no`)
  ```
- **处理**: 重复创建时返回已存在的退款单

### 3.3 退款回调幂等
- **策略**: 按callback_id唯一约束
- **实现**:
  ```
  callback_id = MD5(refundNo + timestamp + nonce)
  ```
- **处理**: 重复回调时返回IGNORED

### 3.4 MQ消费幂等
- **策略**: 按event_id + consumer_group唯一约束
- **实现**: t_mq_consume_log表
- **处理**: 重复消费时跳过处理

## 4. 补偿点设计

### 4.1 退款失败补偿
```
场景: 退款回调返回FAILED
补偿: 
  1. 售后单状态回退到APPROVED
  2. 支持手动重试发起退款
  3. 定时任务扫描REFUNDING超时单
```

### 4.2 消息发送失败补偿
```
场景: RefundSucceeded事件发送失败
补偿:
  1. 定时任务扫描SUCCESS状态但未发送事件的退款单
  2. 重新发送事件
```

### 4.3 库存回补失败补偿
```
场景: 库存回补DB成功但Redis失败
补偿:
  1. 对账任务比对DB和Redis库存
  2. 自动修复差异
```

## 5. 数据一致性保证

### 5.1 事务边界
```
aftersales-service:
  - 售后单创建 + 明细创建 (同一事务)
  - 状态更新 + 状态流转记录 (同一事务)

payment-service:
  - 退款单创建 (单独事务)
  - 回调处理 + 日志记录 (同一事务)

inventory-service:
  - 库存更新 + 流水记录 (同一事务)
```

### 5.2 最终一致性
```
通过MQ事件驱动实现跨服务最终一致性:
  RefundSucceeded -> aftersales状态更新
  AfterSaleRefunded -> order状态更新 + inventory回补
```

## 6. 监控告警点

| 监控项 | 阈值 | 告警级别 |
|--------|------|----------|
| REFUNDING状态超过2小时 | >10单 | WARNING |
| 退款回调验签失败 | >5次/分钟 | ERROR |
| 库存回补失败 | 任意 | ERROR |
| MQ消费失败 | >3次重试 | ERROR |

## 7. 关键SQL

### 7.1 售后单状态更新(CAS)
```sql
UPDATE t_after_sale 
SET status = #{toStatus}, version = version + 1, updated_at = NOW() 
WHERE as_no = #{asNo} AND status = #{fromStatus} AND version = #{version} AND deleted = 0
```

### 7.2 退款单状态更新(CAS)
```sql
UPDATE t_refund_order 
SET status = 'SUCCESS', channel_refund_no = #{channelRefundNo}, refunded_at = NOW(), 
    version = version + 1, updated_at = NOW() 
WHERE refund_no = #{refundNo} AND status IN ('INIT', 'REFUNDING') AND deleted = 0
```

### 7.3 库存回补
```sql
UPDATE t_inventory 
SET available_qty = available_qty + #{qty}, version = version + 1, updated_at = NOW() 
WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId} AND version = #{version} AND deleted = 0
```
