# aftersales-service 售后服务

售后服务模块，实现退款最小闭环：申请售后 -> 退款 -> 订单退款态 -> 库存回补。

## 功能特性

- 售后申请与审批
- 售后状态机管理
- 退款单创建与回调处理
- 订单状态同步（REFUNDED/PARTIAL_REFUNDED）
- 库存自动回补

## 技术架构

```
aftersales-service
├── api/                    # API层
│   ├── controller/         # REST控制器
│   └── dto/                # 请求/响应DTO
├── application/            # 应用层
│   └── AfterSaleService    # 售后业务逻辑
├── domain/                 # 领域层
│   ├── entity/             # 实体
│   ├── enums/              # 枚举
│   ├── event/              # 领域事件
│   └── statemachine/       # 状态机
└── infrastructure/         # 基础设施层
    ├── client/             # 外部服务客户端
    ├── consumer/           # MQ消费者
    ├── mapper/             # MyBatis Mapper
    └── repository/         # 仓储实现
```

## 数据库表

| 表名 | 说明 |
|------|------|
| t_after_sale | 售后单主表 |
| t_after_sale_item | 售后单明细表 |
| t_after_sale_state_flow | 售后状态流转审计表 |
| t_aftersales_mq_consume_log | MQ消费日志表 |

## 售后状态流转

```
APPLIED(已申请) 
  ├── APPROVED(已审批) -> REFUNDING(退款中) -> REFUNDED(已退款)
  ├── REJECTED(已拒绝)
  └── CANCELED(已取消)
```

## API接口

### 申请售后
```http
POST /api/aftersales/apply
Content-Type: application/json

{
  "orderNo": "ORD20240101000001",
  "userId": 10001,
  "type": "REFUND",
  "reason": "商品质量问题",
  "items": [
    {
      "orderItemId": 1,
      "skuId": 1001,
      "qty": 1,
      "refundAmount": 99.00
    }
  ]
}
```

### 审批通过
```http
POST /api/aftersales/approve
Content-Type: application/json

{
  "asNo": "AS20240101000001",
  "approvedBy": "admin"
}
```

### 审批拒绝
```http
POST /api/aftersales/reject
Content-Type: application/json

{
  "asNo": "AS20240101000001",
  "rejectReason": "不符合退款条件",
  "approvedBy": "admin"
}
```

### 取消售后
```http
POST /api/aftersales/{asNo}/cancel?userId=10001
```

### 查询售后单
```http
GET /api/aftersales/{asNo}
GET /api/aftersales/order/{orderNo}
GET /api/aftersales/user/{userId}
```

## MQ事件

### 发送事件
| Topic | Tag | 说明 |
|-------|-----|------|
| AFTERSALES_TOPIC | AFTERSALE_REFUNDED | 售后退款完成事件 |

### 消费事件
| Topic | Tag | 说明 |
|-------|-----|------|
| PAYMENT_TOPIC | REFUND_SUCCEEDED | 退款成功事件 |

## 配置说明

```yaml
server:
  port: 8086

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ymall_aftersales

rocketmq:
  name-server: localhost:9876
  producer:
    group: aftersales-producer-group

aftersales:
  refund-timeout-hours: 72  # 退款超时时间
```

## 依赖服务

- **payment-service**: 创建退款单、处理退款回调
- **order-service**: 更新订单退款状态
- **inventory-service**: 库存回补

## 快速开始

### 1. 初始化数据库
```bash
mysql -u root -p < deploy/mysql/init/06_aftersales_schema.sql
```

### 2. 启动服务
```bash
cd aftersales-service
mvn spring-boot:run
```

### 3. 测试退款流程
```bash
# 1. 申请售后
curl -X POST http://localhost:8086/api/aftersales/apply \
  -H "Content-Type: application/json" \
  -d '{"orderNo":"ORD001","userId":1,"type":"REFUND","reason":"测试退款","items":[{"orderItemId":1,"skuId":1001,"qty":1,"refundAmount":99.00}]}'

# 2. 审批通过
curl -X POST http://localhost:8086/api/aftersales/approve \
  -H "Content-Type: application/json" \
  -d '{"asNo":"AS20240101,"approvedBy":"admin"}'

# 3. 模拟退款回调（在payment-service）
curl -X POST http://localhost:8083/api/refunds/callback/mock \
  -H "Content-Type: application/json" \
  -d '{"refundNo":"RF001","channelRefundNo":"CH001","callbackStatus":"SUCCESS","timestamp":"1704067200","nonce":"abc123","signature":"..."}'
```

## 幂等设计

| 场景 | 幂等键 | 策略 |
|------|--------|------|
| 售后申请 | orderNo + status | 同一订单只能有一个进行中的售后 |
| 退款单创建 | orderNo | 唯一约束 |
| 退款回调 | callbackId | MD5(refundNo+timestamp+nonce) |
| MQ消费 | eventId + consumerGroup | 唯一约束 |

## 补偿机制

1. **退款失败**: 状态回退到APPROVED，支持重试
2. **消息发送失败**: 定时任发
3. **库存回补失败**: 对账任务修复

## 监控指标

- 售后申请量
- 退款成功率
- 平均退款时长
- 库存回补成功率

## 相关文档

- [售后流程图](./flowchart.md)
- [数据库设计](../deploy/mysql/init/06_aftersales_schema.sql)
