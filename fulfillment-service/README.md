# Fulfillment Service (履约服务)

履约服务负责订单支付成功后的发货单创建、发货、签收以及物流轨迹管理。

## 功能特性

- **自动创建发货单**: 消费 `PaymentSucceeded` 事件，自动为已支付订单创建发货单
- **发货管理**: 支持发货操作，创建运单并发布 `ShipmentShipped` 事件
- **签收管理**: 支持签收操作，发布 `ShipmentDelivered` 事件
- **物流轨迹**: 支持乱序上报、自动去重、按时间排序查询
- **事件驱动**: 通过 MQ 与 order-service 联动，推进订单状态

## 技术栈

- Java 17
- Spring Boot 3.2.1
- MyBatis-Plus 3.5.5
- RocketMQ 5.1.4
- MySQL 8.0

## 快速开始

### 1. 初始化数据库

执行 `src/main/resources/schema.sql` 创建表结构：

```sql
-- 发货单表
CREATE TABLE t_shipment (...);

-- 运单表
CREATE TABLE t_waybill (...);

-- 物流轨迹表
CREATE TABLE t_logistics_track (...);

-- MQ消费日志表
CREATE TABLE t_fulfillment_mq_consume_log (...);
```

### 2. 配置

修改 `application.yml` 中的数据库和 RocketMQ 配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ymall
    username: ymall
    password: ymall123456

rocketmq:
  name-server: localhost:9876
```

### 3. 启动服务

```bash
mvn spring-boot:run
```

服务默认端口: `8084`

## API 接口

### 发货单接口

#### 发货
```http
POST /api/v1/shipments/ship
Content-Type: application/json

{
    "shipmentNo": "SH1234567890",
    "waybillNo": "SF1234567890",
    "carrier": "顺丰速运"
}
```

#### 签收
```http
POST /api/v1/shipments/deliver
Content-Type: application/json

{
    "shipmentNo": "SH1234567890"
}
```

#### 查询发货单
```http
GET /api/v1/shipments/{shipmentNo}
GET /api/v1/shipments/order/{orderNo}
```

### 物流轨迹接口

#### 上报轨迹（支持乱序去重）
```http
POST /api/v1/logistics/track
Content-Type: application/json

{
    "waybillNo": "SF1234567890",
    "nodeTime": "2024-01-15T10:30:00",
    "nodeCode": "COLLECTED",
    "content": "快件已揽收"
}
```

#### 批量上报轨迹
```http
POST /api/v1/logistics/tracks/batch
Content-Type: application/json

[
    {
        "waybillNo": "SF1234567890",
        "nodeTime": "2024-01-15T10:30:00",
        "nodeCode": "COLLECTED",
        "content": "快件已揽收"
    },
    {
        "waybillNo": "SF1234567890",
        "nodeTime": "2024-01-15T14:00:00",
        "nodeCode": "IN_TRANSIT",
        "content": "快件已到达【北京转运中心】"
    }
]
```

#### 查询轨迹
```http
GET /api/v1/logistics/waybill/{waybillNo}/tracks
GET /api/v1/logistics/shipment/{shipmentNo}/tracks
```

## Demo 演示

### 完整流程演示

1. **创建订单并支付**（通过 order-service 和 payment-service）

2. **支付成功后自动创建发货单**
   - fulfillment-service 消费 `PaymentSucceeded` 事件
   - 自动创建发货单，状态为 `CREATED`

3. **发货**
```bash
curl -X POST http://localhost:8084/api/v1/shipments/ship \
  -H "Content-Type: application/json" \
  -d '{
    "shipmentNo": "SH1234567890",
    "waybillNo": "SF1234567890",
    "carrier": "顺丰速运"
  }'
```
   - 发货单状态变为 `SHIPPED`
   - 订单状态变为 `SHIPPED`

4. **上报物流轨迹**
```bash
curl -X POST http://localhost:8084/api/v1/logistics/track \
  -H "Content-Type: application/json" \
  -d '{
    "waybillNo": "SF1234567890",
    "nodeTime": "2024-01-15T10:30:00",
    "nodeCode": "COLLECTED",
    "content": "快件已揽收"
  }'
```

5. **签收**
```bash
curl -X POST http://localhost:8084/api/v1/shipments/deliver \
  -H "Content-Type: application/json" \
  -d '{
    "shipmentNo": "SH1234567890"
  }'
```
   - 发货单状态变为 `DELIVERED`
   - 订单状态变为 `DELIVERED`（订单完成）

6. **查询订单状态**
```bash
curl http://localhost:8082/api/v1/orders/{orderNo}
```

## 状态流转

### 发货单状态
```
CREATED → SHIPPED → DELIVERED
```

### 订单状态（履约相关）
```
PAID → SHIPPED → DELIVERED
```

## MQ 事件

### 消费的事件
| Topic | Tag | 说明 |
|-------|-----|------|
| PAYMENT_TOPIC | PAYMENT_SUCCEEDED | 支付成功，创建发货单 |

### 发布的事件
| Topic | Tag | 说明 |
|-------|-----|------|
| FULFILLMENT_TOPIC | SHIPMENT_CREATED | 发货单创建 |
| FULFILLMENT_TOPIC | SHIPMENT_SHIPPED | 已发货 |
| FULFILLMENT_TOPIC | SHIPMENT_DELIVERED | 已签收 |

## 物流轨迹乱序去重

### 实现原理

1. **唯一约束**: `UNIQUE KEY (waybill_no, node_time, node_code)`
2. **INSERT IGNORE**: 重复数据自动忽略，不报错
3. **查询排序**: `ORDER BY node_time ASC`

### 特点

- 支持乱序写入：轨迹可以按任意顺序上报
- 自动去重：相同轨迹只保留一条
- 查询有序：按时间线正确展示

## 项目结构

```
fulfillment-service/
├── src/main/java/com/yuge/fulfillment/
│   ├── FulfillmentApplication.java      # 启动类
│   ├── api/
│   │   ├── controller/                  # REST 控制器
│   │   └── dto/                         # 请求/响应 DTO
│   ├── application/                     # 应用服务
│   │   ├── ShipmentService.java
│   │   └── LogisticsTrackService.java
│   ├── domain/
│   │   ├── entity/                      # 实体类
│   │   ├── enums/                       # 枚举
│   │   └── event/                       # 事件类
│   └── infrastructure/
│       ├── consumer/                    # MQ 消费者
│       ├── mapper/                      # MyBatis Mapper
│       └── repository/                  # 仓储
├── src/main/resources/
│   ├── application.yml                  # 配置文件
│   └── schema.sql                       # 数据库脚本
└── pom.xml
```

## 相关文档

- [履约链路流程图](flowchart.md)
