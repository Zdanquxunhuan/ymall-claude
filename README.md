# YMall-Claude 电商平台底座

高并发电商平台基础设施，基于 Java 17 + Spring Boot 3 + MyBatis-Plus + RocketMQ + Redis 构建。

## 项目结构

```
ymall-claude/
├── platform-infra/          # 平台基础设施层
│   ├── common/              # 统一错误码、Result
│   ├── exception/           # 异常处理
│   ├── trace/               # 链路追踪
│   ├── mybatis/             # MyBatis-Plus配置
│   ├── idempotent/          # 幂等组件
│   ├── ratelimit/           # 限流组件
│   └── mq/                  # MQ模板
├── demo-service/            # 演示服务
│   ├── api/                 # Controller层
│   ├── application/         # Service层
│   ├── domain/              # 领域模型
│   └── infrastructure/      # 基础设施层
├── order-service/           # 订单服务
│   ├── api/                 # Controller层
│   ├── application/         # Service层
│   ├── domain/              # 领域模型（含Outbox）
│   └── infrastructure/      # 基础设施层（含Relay Worker）
├── deploy/                  # 部署配置
│   ├── mysql/init/          # MySQL初始化脚本
│   └── rocketmq/            # RocketMQ配置
├── k6/                      # 压测脚本
├── docs/                    # 文档
└── docker-compose.yml       # Docker编排
```

## 核心功能

| 功能 | 说明 |
|------|------|
| 统一错误码 | A开头用户错误、B开头系统错误、C开头第三方错误 |
| 统一返回体 | Result<T> 包含 code、message、data、traceId |
| 链路追踪 | Filter生成traceId，MDC透传，响应头回传 |
| 幂等组件 | Redis实现，支持Header/Body/Param多种幂等键来源 |
| 限流组件 | Redis Lua令牌桶算法，支持API/用户/API+用户维度 |
| MyBatis-Plus | 乐观锁、逻辑删除、字段自动填充 |
| MQ模板 | Producer/Consumer模板，自动注入traceId |
| **Transactional Outbox** | 事务发件箱模式，保证业务与消息的最终一致性 |
| **Outbox Relay Worker** | 可水平扩展的消息投递器，支持指数退避重试 |
| **消费幂等** | 基于DB的消费幂等，防止重复消费 |

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose

### 2. 启动基础设施

```bash
# 启动 MySQL、Redis、RocketMQ
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

等待所有服务健康检查通过（约1-2分钟）。

### 3. 编译项目

```bash
mvn clean install -DskipTests
```

### 4. 启动应用

```bash
# 启动订单服务（包含 Outbox Relay）
cd order-service
mvn spring-boot:run
```

或者使用 IDE 运行 `OrderApplication.java`

### 5. 验证服务

```bash
# 健康检查
curl http://localhost:8082/actuator/health
```

## Outbox Relay 演示

### 创建订单并观察消息投递
```bash
# 1. 创建订单
curl -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: demo-trace-001" \
  -d '{
    "clientRequestId": "client-req-001",
    "userId": 10001,
    "items": [
      {
        "skuId": 1001,
        "qty": 2,
        "titleSnapshot": "iPhone 15 Pro",
        "priceSnapshot": 7999.00
      }
    ],
    "remark": "Outbox演示订单"
  }'

# 响应示例
{
  "code": "00000",
  "message": "成功",
  "data": {
    "orderNo": "ORD20240115103045123456",
    "userId": 10001,
    "amount": 15998.00,
    "status": D",
    "items": [...]
  },
  "traceId": "demo-trace-001"
}
```

### 观察日志（1秒内）

```
# 1. Outbox 事件写入
[Outbox] Event saved, eventId=xxx, bizKey=ORD..., topic=ORDER_TOPIC, tag=ORDER_CREATED

# 2. Relay Worker 投递
[OutboxRelay] Processing event, eventId=xxx, bizKey=ORD..., retryCount=0
[OutboxRelay] Event sent successfully, eventId=xxx, msgId=xxx

# 3. Consumer 消费
[OrderCreatedConsumer] Received message, eventId=xxx, bizKey=ORD...
[OrderCreatedConsumer] ========================================
[OrderCreatedConsumer] Processing OrderCreated event
[OrderCreatedConsumer] Event ID: xxx
[OrderCreatedConsumer] Business Key (Order No): ORD...
[OrderCreatedConsumer] ========================================
[OrderCreatedConsumer] Message consumed successfully, eventId=xxx, costMs=xxx
```

### 可靠性演示：Broker 故障恢复

```bash
# 1. 停止 RocketMQ Broker
docker-compose stop broker

# 2. 创建订单（消息会写入 Outbox，但投递失败）
curl -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "clientRequestId": "client-req-002",
    "userId": 10002,
    "items": [{"skuId": 1002, "qty": 1, "titleSnapshot": "Test", "priceSnapshot": 100}]
  }'

# 3. 查看 Outbox 状态（应为 RETRY）
docker exec -it ymall-mysql mysql -uymall -pymall123456 -e \
  "SELECT event_id, status, retry_count, next_retry_at FROM ymall.t_outbox_event ORDER BY id DESC LIMIT 5"

# 4. 启动 Broker
docker-compose start broker

# 5. 等待 next_retry_at 到达后，消息自动投递
# 观察日志：
[OutboxRelay] Event sent successfully, eventId=xxx
[OrderCreatedConsumer] Message consumed successfully, eventId=xxx
```

### 查看 Outbox 状态

```bash
# 查看各状态事件数量
docker exec -it ymall-mysql mysql -uymall -pymall123456 -e \
  "SELECT status, COUNT(*) as cnt FROM ymall.t_outbox_event GROUP BY status"

# 查看死信事件（需要人工介入）
docker exec -it ymall-mysql mysql -uymall -pymall123456 -e \
  "SELECT * FROM ymall.t_outbox_event WHERE status='DEAD'"

# 查看消费日志
docker exec -it ymall-mysql mysql -uymall -pymall123456 -e \
  "SELECT event_id, consumer_group, status, cost_ms FROM ymall.t_mq_consume_log ORDER BY id DESC LIMIT 10"
```

## API 接口

### 订单服务

#### 创建订单

```bash
curl -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: trace-001" \
  -d '{
    "clientRequestId": "unique-client-request-id",
    "userId": 10001,
    "items": [
      {
        "skuId": 1001,
        "qty": 2,
        "titleSnapshot": "商品名称",
        "priceSnapshot": 99.99
      }
    ],
    "remark": "备注"
  }'
```

#### 取消订单

```bash
curl -X POST http://localhost:8082/api/orders/cancel \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo": "ORD20240115103045123456",
    "reason": "用户取消"
  }'
```

#### 查询订单

```bash
curl http://localhost:8082/api/orders/ORD20240115103045123456
```

### Demo 服务

#### 幂等接口 (Header方式)

```bash
curl -X POST http://localhost:8081/demo/idempotent \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: test-key-001" \
  -d '{
    "clientRequestId": "req-001",
    "userId": 10001,
    "amount": 99.99
  }'
```

#### 限流接口

```bash
curl http://localhost:8081/demo/ratelimit
```

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 00000 | 成功 |
| A0100 | 参数校验失败 |
| A0301 | 资源不存在 |
| A0400 | 幂等键缺失 |
| A0401 | 请求正在处理中，请勿重复提交 |
| A0500 | 请求过于频繁，请稍后重试 |
| B0001 | 系统内部错误 |
| B0103 | 数据已被修改，请刷新后重试（乐观锁） |

## 压测

### 安装 k6

```bash
# macOS
brew install k6

# Windows (使用 chocolatey)
choco install k6

# Linux
sudo apt-get update
sudo apt-get install k6
```

### 运行压测

```bash
# Outbox Relay 压测
k6 run k6/outbox-relay-test.js

# 幂等接口压测
k6 run k6/idempotent-test.js

# 限流接口压测
k6 run k6/ratelimit-test.js
```

### 压测指标

| 场景 | 指标 | 预期 |
|------|------|------|
| 订单创建 | 100 VU, 30s | 成功率>=99.9%，P95<500ms |
| Outbox 投递 | 创建后1s内 | 消息投递成功 |
| 消费幂等 | 重复消息 | 自动跳过，不重复处理 |
| Broker故障 | 停止后恢复 | 消息最终投递成功 |

## Outbox Relay 配置

```yaml
# application.yml
outbox:
  relay:
    batch-size: 100          # 每次处理的批量大小
    poll-interval: 1000      # 轮询间隔（毫秒）
    base-retry-interval: 5   # 基础重试间隔（秒）
    max-retry-interval: 3600 # 最大重试间隔（秒）
```

### 指数退避策略

| 重试次数 | 延迟时间 |
|----------|----------|
| 1 | 5s |
| 2 | 10s |
| 3 | 20s |
| 4 | 40s |
| 5 | 80s |
| ... | ... |
| max | 3600s (1小时) |

## 常见故障排查

### 1. Outbox 消息积压

```bash
# 检查 NEW 状态消息数量
docker exec -it ymall-mysql mysql -uymall -pymall123456 -e \
  "SELECT COUNT(*) FROM ymall.t_outbox_event WHERE status='NEW'"

# 可能原因：
# - Relay Worker 未启动
# - RocketMQ 连接失败
# - 数据库连接池耗尽
```

### 2. 消息投递失败（RETRY 状态）

```bash
# 查看失败原因
docker exec -it ymall-mysql mysql -uymall -pymall123456 -e \
  "SELECT event_id, retry_count, last_error, next_retry_at FROM ymall.t_outbox_event WHERE status='RETRY'"

# 可能原因：
# - RocketMQ Broker 不可用
# - Topic 不存在
# - 网络问题
```

### 3. 死信消息（DEAD 状态）

```bash
# 查看死信消息
docker exec -it ymall-mysql mysql -uymall -pymall123456 -e \
  "SELECT * FROM ymall.t_outbox_event WHERE status='DEAD'"

# 处理方式：
# 1. 分析 last_error 确定原因
# 2. 修复问题后手动重置状态为 NEW
# UPDATE t_outbox_event SET status='NEW', retry_count=0 WHERE event_id='xxx'
```

### 4. 消费重复

```bash
# 检查消费日志
docker exec -it ymall-mysql mysql -uymall -pymall123456 -e \
  "SELECT event_id, COUNT(*) as cnt FROM ymall.t_mq_consume_log GROUP BY event_id HAVING cnt > 1"

# 正常情况下不应有重复记录（唯一索引保证）
```

## 技术栈

- Java 17
- Spring Boot 3.2.1
- MyBatis-Plus 3.5.5
- RocketMQ 5.1.4
- Redis 7
- MySQL 8.0
- Hutool 5.8.24
- k6 (压测)

## License

MIT License
