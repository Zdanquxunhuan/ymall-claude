-- ============================================
-- Product Service Schema
-- 商品服务数据库表结构
-- ============================================

-- 1. SPU表 (Standard Product Unit - 标准产品单元)
-- SPU是商品信息聚合的最小单位，如iPhone 15
CREATE TABLE IF NOT EXISTS t_spu (
    spu_id          BIGINT          NOT NULL AUTO_INCREMENT COMMENT 'SPU ID',
    title           VARCHAR(200)    NOT NULL COMMENT 'SPU标题',
    category_id     BIGINT          NOT NULL COMMENT '分类ID',
    brand_id        BIGINT          NULL COMMENT '品牌ID',
    description     TEXT            NULL COMMENT '商品描述',
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT-草稿, PENDING-待审核, PUBLISHED-已发布, OFFLINE-已下架',
    version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标记 0-未删除 1-已删除',
    PRIMARY KEY (spu_id),
    KEY idx_category_id (category_id),
    KEY idx_brand_id (brand_id),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SPU表';

-- 2. SKU表 (Stock Keeping Unit - 库存量单位)
-- SKU是库存进出计量的基本单元，如iPhone 15 黑色 256GB
CREATE TABLE IF NOT EXISTS t_sku (
    sku_id          BIGINT          NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
    spu_id          BIGINT          NOT NULL COMMENT '所属SPU ID',
    title           VARCHAR(200)    NOT NULL COMMENT 'SKU标题',
    attrs_json      JSON            NULL COMMENT '销售属性JSON，如{"颜色":"黑色","容量":"256GB"}',
    price           DECIMAL(10,2)   NOT NULL DEFAULT 0.00 COMMENT '销售价格',
    original_price  DECIMAL(10,2)   NULL COMMENT '原价',
    sku_code        VARCHAR(64)     NULL COMMENT 'SKU编码',
    bar_code        VARCHAR(64)     NULL COMMENT '条形码',
    weight          DECIMAL(10,3)   NULL COMMENT '重量(kg)',
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT-草稿, PENDING-待审核, PUBLISHED-已发布, OFFLINE-已下架',
    publish_time    DATETIME        NULL COMMENT '发布时间',
    version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标记 0-未删除 1-已删除',
    PRIMARY KEY (sku_id),
    KEY idx_spu_id (spu_id),
    KEY idx_sku_code (sku_code),
    KEY idx_status (status),
    KEY idx_publish_time (publish_time),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SKU表';

-- 3. Outbox表 (事务性发件箱)
-- 用于实现可靠的事件发布，保证业务操作和事件发布的原子性
CREATE TABLE IF NOT EXISTS t_outbox (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    event_id        VARCHAR(64)     NOT NULL COMMENT '事件ID（UUID）',
    event_type      VARCHAR(50)     NOT NULL COMMENT '事件类型: PRODUCT_PUBLISHED, PRODUCT_UPDATED',
    aggregate_type  VARCHAR(50)     NOT NULL COMMENT '聚合类型: SKU, SPU',
    aggregate_id    BIGINT          NOT NULL COMMENT '聚合ID',
    payload         JSON            NOT NULL COMMENT '事件负载（JSON格式）',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING-待发送, SENT-已发送, FAILED-发送失败',
    retry_count     INT             NOT NULL DEFAULT 0 COMMENT '重试次数',
    max_retry       INT             NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    next_retry_at   DATETIME        NULL COMMENT '下次重试时间',
    error_message   VARCHAR(500)    NULL COMMENT '错误信息',
    version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_id (event_id),
    KEY idx_status_next_retry (status, next_retry_at),
    KEY idx_aggregate (aggregate_type, aggregate_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事务性发件箱表';

-- 4. MQ消费日志表（用于消费幂等）
CREATE TABLE IF NOT EXISTS t_mq_consume_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    event_id        VARCHAR(64)     NOT NULL COMMENT '事件ID（消息唯一标识）',
    consumer_group  VARCHAR(100)    NOT NULL COMMENT '消费者组',
    topic           VARCHAR(100)    NOT NULL COMMENT '消息主题',
    tags            VARCHAR(100)    NULL COMMENT '消息标签',
    biz_key         VARCHAR(100)    NULL COMMENT '业务键',
    status          VARCHAR(20)     NOT NULL COMMENT '消费状态: PROCESSING-处理中, SUCCESS-成功, FAILED-失败',
    result          VARCHAR(500)    NULL COMMENT '消费结果/错误信息',
    cost_ms         BIGINT          NULL COMMENT '消费耗时(毫秒)',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_consumer (event_id, consumer_group),
    KEY idx_biz_key (biz_key),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MQ消费日志表';

-- ============================================
-- 初始化测试数据（可选）
-- ============================================

-- 插入测试SPU数据
-- INSERT INTO t_spu (title, category_id, brand_id, description, status) VALUES
-- ('iPhone 15', 1001, 1, 'Apple iPhone 15 智能手机', 'PUBLISHED'),
-- ('MacBook Pro 14', 1002, 1, 'Apple MacBook Pro 14英寸笔记本电脑', 'PUBLISHED');

-- 插入测试SKU数据
-- INSERT INTO t_sku (spu_id, title, attrs_json, price, status) VALUES
-- (1, 'iPhone 15 黑色 128GB', '{"颜色":"黑色","容量":"128GB"}', 5999.00, 'PUBLISHED'),
-- (1, 'iPhone 15 白色 256GB', '{"颜色":"白色","容量":"256GB"}', 6999.00, 'PUBLISHED'),
-- (2, 'MacBook Pro 14 M3 16GB 512GB', '{"芯片":"M3","内存":"16GB","存储":"512GB"}', 12999.00, 'PUBLISHED');
