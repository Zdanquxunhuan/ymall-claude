# Search Service

商品搜索服务 - 消费商品发布事件构建索引并提供搜索接口。

## 功能特性

- **实时索引**：消费 `ProductPublished`/`ProductUpdated` 事件，实时更新搜索索引
- **双索引实现**：
  - 默认：内存索引（ConcurrentHashMap + 倒排索引）
  - 可选：Elasticsearch（通过配置开关启用）
- **幂等消费**：按 `eventId` 去重，重复事件不会导致数据异常
- **全文搜索**：支持关键词搜索、分类/品牌过滤、价格范围、排序、分页

## 快速开始

### 1. 启动依赖服务

```bash
# 启动 RocketMQ (必需)
docker run -d --name rmqnamesrv -p 9876:9876 apache/rocketmq:5.1.0 sh mqnamesrv
docker run -d --name rmqbroker -p 10911:10911 -p 10909:10909 \
  -e "NAMESRV_ADDR=host.docker.internal:9876" \
  apache/rocketmq:5.1.0 sh mqbroker -n host.docker.internal:9876

# 启动 Redis (必需，用于幂等)
docker run -d --name redis -p 6379:6379 redis:7

# 启动 Elasticsearch (可选)
docker run -d --name elasticsearch -p 9200:9200 -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  elasticsearch:8.11.0
```

### 2. 配置

默认使用内存索引，无需额外配置。如需使用 Elasticsearch：

```yaml
# application.yml
search:
  index-type: elasticsearch  # 切换为 ES
  elasticsearch:
    enabled: true
    hosts: localhost:9200
    index-name: product_index
```

### 3. 启动服务

```bash
cd search-service
mvn spring-boot:run
```

服务默认运行在 `http://localhost:8086`

## API 接口

### 商品搜索

```http
GET /search?q={keyword}&categoryId={categoryId}&sort={sortField}&order={order}&pageNum={page}&pageSize={size}
```

**参数说明：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| q | String | 否 | 搜索关键词 |
| categoryId | Long | 否 | 分类ID过滤 |
| brandId | Long | 否 | 品牌ID过滤 |
| minPrice | BigDecimal | 否 | 最低价格 |
| maxPrice | BigDecimal | 否 | 最高价格 |
| sort | String | 否 | 排序字段：price, publishTime |
| order | String | 否 | 排序方向：asc, desc（默认desc） |
| pageNum | Integer | 否 | 页码（默认1） |
| pageSize | Integer | 否 | 每页大小（默认20，最大100） |

**响应示例：**

```json
{
  "code": "00000",
  "message": "success",
  "data"tems": [
      {
        "skuId": 1001,
        "spuId": 100,
        "title": "iPhone 15 Pro 256GB 黑色",
        "price": 8999.00,
        "categoryId": 1,
        "brandId": 1,
        "status": "PUBLISHED",
        "publishTime": "2024-01-15T10:30:00"
      }
    ],
    "total": 100,
    "pageNum": 1,
    "pageSize": 20,
    "totalPages": 5,
    "took": 15
  },
  "traceId": "abc123",
  "timestamp": 1705312200000
}
```

### 获取单个商品

```http
GET /search/{skuId}
```

### 获取索引统计

```http
GET /search/stats
```

**响应示例：**

```json
{
  "code": "00000",
  "message": "success",
  "data": {
    "documentCount": 1000
  }
}
```

## 架构设计

### 索引构建流程

```
Product Service                    Search Service
     |                                  |
     |  1. 发布商品                      |
     v                                  |
+---------+                             |
| Outbox  |---- RocketMQ ------------> Consumer
| Pattern |    PRODUCT_TOPIC            |
+---------+                             v
                                  +-----------+
                                  | 幂等检查   |
                                  | (eventId) |
                                  +-----+-----+
                                        |
                                        v
                                  +-----------+
                                  | 构建文档   |
                                  | 写入索引   |
                                  +-----------+
```

### 幂等机制

1. **第一层（ConsumerTemplate）**：Redis 消息ID去重
   - Key: `mq:PRODUCT_TOPIC:{messageId}`
   - TTL: 24小时

2. **第二层（业务层）**：eventId 去重
   - 每个文档记录 `lastEventId`
   - 相同 eventId 的事件直接跳过

### 内存索引结构

```
primaryIndex:    ConcurrentHashMap<skuId, ProductDocument>
invertedIndex:   ConcurrentHashMap<term, Set<skuId>>
categoryIndex:   ConcurrentHashMap<categoryId, Set<skuId>>
brandIndex:      ConcurrentHashMap<brandId, Set<skuId>>
eventIdMap:      ConcurrentHashMap<skuId, lastEventId>
```

## 配置说明

```yaml
search:
  # 索引类型: memory (默认) 或 elasticsearch
  index-type: memory
  
  # Elasticsearch 配置
  elasticsearch:
    enabled: false
    hosts: localhost:9200
    username: 
    password: 
    index-name: product_index
    connect-timeout: 5000
    socket-timeout: 30000
    
  # 索引重建配置 (占位，待实现)
  rebuild:
    enabled: false
    batch-size: 100
    cron: "0 0 3 * * ?"
```

## Demo 演示

### 1. 发布商品后搜索

```bash
# 1. 通过 product-service 发布商品
curl -X POST http://localhost:8081/api/sku/1001/publish

# 2. 等待消息消费（通常 < 1秒）

# 3. 搜索商品
curl "http://localhost:8086/search?q=iPhone"

# 预期结果：能搜索到刚发布的商品
```

### 2. 重复事件不重复写入

```bash
# 模拟重复消费同一事件
# 第一次消费：正常写入索引
# 第二次消费：检测到 eventId 已处理，跳过

# 查看日志：
# [ProductEventConsumer] Event already processed, skipping: skuId=1001, eventId=evt_xxx
```

### 3. 搜索功能测试

```bash
# 关键词搜索
curl "http://localhost:8086/search?q=手机"

# 分类过滤
curl "http://localhost:8086/search?categoryId=1"

# 价格排序
curl "http://localhost:8086/search?q=手机&sort=price&order=asc"

# 价格范围
curl "http://localhost:8086/search?minPrice=1000&maxPrice=5000"

# 分页
curl "http://localhost:8086/search?q=手机&pageNum=2&pageSize=10"
```

## 项目结构

```
search-service/
├── pom.xml
├── README.md
├── flowchart.md
└── src/
    ├── main/
    │   ├── java/com/yuge/search/
    │   │   ├── SearchServiceApplication.java
    │   │   ├── api/
    │   │   │   └── SearchController.java
    │   │   ├── config/
    │   │   │   └── SearchProperties.java
    │   │   ├── domain/
    │   │   │   ├── event/
    │   │   │   │   ├── ProductPublishedEvent.java
    │   │   │   │   └── ProductUpdatedEvent.java
    │   │   │   ├── model/
    │   │   │   │   ├── ProductDocument.java
    │   │   │   │   ├── SearchRequest.java
    │   │   │   │   └── SearchResult.java
    │   │   │   └── service/
    │   │   │       └── SearchIndexService.java
    │   │   └── infrastructure/
    │   │       ├── index/
    │   │       │   ├── MemorySearchIndexService.java
    │   │       │   └── ElasticsearchIndexService.java
    │   │       └── mq/
    │   │           └── ProductEventConsumer.java
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/com/yuge/search/
            ├── MemorySearchIndexServiceTest.java
            └── SearchControllerTest.java
```

## 待实现功能

- [ ] 索引重建任务（全量同步）
- [ ] 搜索建议/自动补全
- [ ] 搜索结果高亮
- [ ] 聚合统计（分类、品牌、价格区间）
- [ ] 同义词/近义词支持
- [ ] 搜索日志与分析

## 相关文档

- [flowchart.md](./flowchart.md) - 索引流程图
- [platform-infra](../platform-infra/README.md) - 基础设施层
- [product-service](../product-service/README.md) - 商品服务
