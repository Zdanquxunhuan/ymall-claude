# Payment Service

支付服务 - 支付单创建、回调处理、补单对账

## 功能概述

- **支付单创建**：按 order_no 幂等创建支付单
- **回调处理**：模拟第三方支付回调，验签 + 幂等
- **事件发布**：支付成功后发布 PaymentSucceeded 事件
- **补单对账**：定时扫描超时支付单，主动查单并补推进或关闭

## 技术栈

- Java 17
- Spring Boot 3.2.1
- MyBatis-Plus 3.5.5
- RocketMQ 2.2.3
- MySQL 8.0
- Redis 7

## 快速开始

### 1. 初始化数据库

执行 SQL 脚本创建表结构：

```bash
mysql -u ymall -p ymall < deploy/mysql/init/03_payment_schema.sql
```

### 2. 启动服务

```bash
cd payment-service
mvn spring-boot:run
```

服务默认端口：`8083`

### 3. 配置说明

```yaml
payment:
  # 回调签名密钥
  callback-secret: ymall-payment-secret-key-2024
  # 支付超时时间（分钟）
  timeout-minutes: 30
  # 补单配置
  reconcile:
    scan-interval: 60          # 扫描间隔（秒）
    timeout-threshold-minutes: 5  # 超时阈值（分钟）
    batch-size: 100            # 批量大小
```

## API 接口

### 1. 创建支付单

**POST** `/payments`

按 order_no 幂等创建支付单。

**请求体：**
```json
{
  "orderNo": "ORD202401011234567890",
  "amount": 99.99,
  "channel": "MOCK"
}
```

**响应：**
```json
{
  "code": "00000",
  "message": "success",
  "data": {
    "id": 1234567890,
    "payNo": "PAY202401011234567890",
    "orderNo": "ORD202401011234567890",
    "amount": 99.99,
    "status": "INIT",
    "statusDesc": "初始化",
    "channel": "MOCK",
    "expireAt": "2024-01-01T13:00:00",
    "createdAt": "2024-01-01T12:30:00"
  }
}
```

### 2. 查询支付单

**GET** `/payments/{payNo}`

**响应：**
```json
{
  "code": "00000",
  "message": "success",
  "data": {
    "payNo": "PAY202401011234567890",
    "orderNo": "ORD202401011234567890",
    "status": "SUCCESS",
    "statusDesc": "支付成功",
    "paidAt": "2024-01-01T12:35:00"
  }
}
```

### 3. 根据订单号查询支付单

**GET** `/payments/order/{orderNo}`

### 4. 模拟支付回调

**POST** `/payments/mock-callback`

模拟第三方支付平台回调，必须验签 + 幂等。

**请求体：**
```json
{
  "payNo": "PAY202401011234567890",
  "callbackStatus": "SUCCESS",
  "channelTradeNo": "MOCK_1234567890",
  "timestamp": "1704067200000",
  "nonce": "abc123",
  "signature": "A1B2C3D4E5F6..."
}
```

**签名规则：**
```
signature = MD5(callbackStatus + nonce + payNo + timestamp + secret).toUpperCase()
```

**响应：**
```json
{
  "code": "00000",
  "message": "success",
  "data": {
    "result": "PROCESSED",
    "message": "回调处理成功",
    "payNo": "PAY202401011234567890",
    "currentStatus": "SUCCESS"
  }
}
```

### 5. 生成签名（测试用）

**GET** `/payments/generate-signature?payNo=xxx&callbackStatus=SUCCESS&timestamp=xxx&nonce=xxx`

## 数据表结构

### t_pay_order（支付单表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID |
| pay_no | VARCHAR(32) | 支付单号（唯一） |
| order_no | VARCHAR(32) | 订单号（唯一） |
| amount | DECIMAL(12,2) | 支付金额 |
| status | VARCHAR(20) | 状态：INIT/PAYING/SUCCESS/FAILED/CLOSED |
| channel | VARCHAR(32) | 支付渠道：MOCK/ALIPAY/WECHAT |
| channel_trade_no | VARCHAR(64) | 渠道交易号 |
| paid_at | DATETIME | 支付成功时间 |
| expire_at | DATETIME | 支付过期时间 |
| version | INT | 乐观锁版本号 |

### t_pay_callback_log（回调日志表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID |
| pay_no | VARCHAR(32) | 支付单号 |
| callback_id | VARCHAR(64) | 回调唯一ID（幂等键） |
| callback_status | VARCHAR(20) | 回调状态：SUCCESS/FAILED |
| raw_payload | TEXT | 原始回调报文 |
| signature_valid | TINYINT | 签名是否有效 |
| process_result | VARCHAR(20) | 处理结果：PROCESSED/IGNORED/FAILED |

### t_pay_reconcile_audit（对账审计表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID |
| pay_no | VARCHAR(32) | 支付单号 |
| action | VARCHAR(32) | 操作类型：QUERY/CLOSE/NOTIFY |
| before_status | VARCHAR(20) | 操作前状态 |
| after_status | VARCHAR(20) | 操作后状态 |
| query_result | VARCHAR(20) | 查询结果 |

## MQ 消息

### 发布的消息

| Topic | Tag | 事件 | 说明 |
|-------|-----|------|------|
| PAYMENT_TOPIC | PAYMENT_SUCCEEDED | PaymentSucceededEvent | 支付成功事件 |

### PaymentSucceededEvent 结构

```json
{
  "eventId": "abc123",
  "payNo": "PAY202401011234567890",
  "orderNo": "ORD2024010112345678amount": 99.99,
  "channel": "MOCK",
  "channelTradeNo": "MOCK_1234567890",
  "paidAt": "2024-01-01T12:35:00",
  "eventTime": "2024-01-01T12:35:00",
  "traceId": "trace-123",
  "version": "1.0"
}
```

## 一致性保证

### 1. 支付单创建幂等

- 基于 `order_no` 唯一约束
- 重复请求返回已存在的支付单

### 2. 回调幂等

- 基于 `callback_id` 唯一约束
- `callback_id = MD5(payNo + timestamp + nonce)`
- 重复回调返回 IGNORED

### 3. 状态更新原子性

- 使用 CAS 乐观锁
- `WHERE status IN ('INIT', 'PAYING')`

### 4. 补单机制

- 定时扫描超时 PAYING 状态支付单
- 模拟主动查单，根据结果补推进或关闭
- 记录对账审计日志

## 与 order-service 集成

### 订单状态流转

```
STOCK_RESERVED --[PAYMENT_SUCCESS]--> PAID
```

### order-service 消费者

`PaymentSucceededConsumer` 消费 `PAYMENT_TOPIC:PAYMENT_SUCCEEDED` 事件：

1. 幂等检查（t_mq_consume_log）
2. 状态机校验
3. CAS 更新订单状态：`STOCK_RESERVED -> PAID`
4. 记录状态流转审计

## 测试

### 运行测试

```bash
mvn test
```

### 测试支付流程

```bash
# 1. 创建支付单
curl -X POST http://localhost:8083/payments \
  -H "Content-Type: application/json" \
  -d '{"orderNo":"ORD123","amount":99.99}'

# 2. 生成签名
curl "http://localhost:8083/payments/generate-signature?payNo=PAY123&callbackStatus=SUCCESS&timestamp=1704067200000&nonce=abc123"

# 3. 模拟回调
curl -X POST http://localhost:8083/payments/mock-callback \
  -H "Content-Type: application/json" \
  -d '{
    "payNo":"PAY123",
    "callbackStatus":"SUCCESS",
    "channelTradeNo":"MOCK_123",
    "timestamp":"1704067200000",
    "nonce":"abc123",
    "signature":"生成的签名"
  }'
```

## 目录结构

```
payment-service/
├── pom.xml
├── README.md
├── flowchart.md                    # 流程图文档
├── src/
│   ├── main/
│   │   ├── java/com/yuge/payment/
│   │   │   ├── PaymentServiceApplication.java
│   │   │   ├── api/
│   │   │   │   ├── controller/
│   │   │   │   │   └── PaymentController.java
│   │   │   │   └── dto/
│   │   │   │       ├── CreatePaymentRequest.java
│   │   │   │       ├── MockCallbackRequest.java
│   │   │   │       ├── PaymentResponse.java
│   │   │   │       └── CallbackResponse.java
│   │   │   ├── application/
│   │   │   │   └── PaymentService.java
│   │   │   ├── domain/
│   │   │   │   ├── entity/
│   │   │   │   │   ├── PayOrder.java
│   │   │   │   │   ├── PayCallbackLog.java
│   │   │   │   │   └── PayReconcileAudit.java
│   │   │   │   ├── enums/
│   │   │   │   │   ├── PayStatus.java
│   │   │   │   │   ├── PayChannel.java
│   │   │   │   │   ├── CallbackProcessResult.java
│   │   │   │   │   └── ReconcileAction.java
│   │   │   │   └── event/
│   │   │   │       ├── PaymentSucceededEvent.java
│   │   │   │       └── PaymentFailedEvent.java
│   │   │   └── infrastructure/
│   │   │       ├── mapper/
│   │   │       │   ├── PayOrderMapper.java
│   │   │       │   ├── PayCallbackLogMapper.java
│   │   │       │   └── PayReconcileAuditMapper.java
│   │   │       ├── repository/
│   │   │       │   ├── PayOrderRepository.java
│   │   │       │   ├── PayCallbackLogRepository.java
│   │   │       │   └── PayReconcileAuditRepository.java
│   │   │       └── scheduler/
│   │   │           └── PaymentReconcileScheduler.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/com/yuge/payment/
│           └── PaymentServiceTest.java
```

## 扩展点

1. **真实支付渠道接入**：实现 `PayChannelAdapter` 接口对接支付宝/微信
2. **退款功能**：新增退款单表和退款流程
3. **对账文件下载**：从支付渠道下载对账文件进行核对
4. **支付限额控制**：基于用户/商户的支付限额管理
