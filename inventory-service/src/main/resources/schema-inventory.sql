-- ============================================
-- Inventory Service Schema
-- 库存服务数据库表结构
-- ============================================

-- 1. 库存表
-- 记录每个SKU在每个仓库的可用库存和预留库存
CREATE TABLE IF NOT EXISTS t_inventory (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    sku_id          BIGINT          NOT NULL COMMENT 'SKU ID',
    warehouse_id    BIGINT          NOT NULL COMMENT '仓库ID',
    available_qty   INT             NOT NULL DEFAULT 0 COMMENT '可用库存数量',
    reserved_qty    INT             NOT NULL DEFAULT 0 COMMENT '预留库存数量（已被订单占用但未确认）',
    version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标记 0-未删除 1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_warehouse (sku_id, warehouse_id),
    KEY idx_warehouse_id (warehouse_id),
    KEY idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存表';

-- 2. 库存预留表
-- 记录订单对库存的预留情况，用于幂等和超时释放
CREATE TABLE IF NOT EXISTS t_inventory_reservation (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    order_no        VARCHAR(64)     NOT NULL COMMENT '订单号',
    sku_id          BIGINT          NOT NULL COMMENT 'SKU ID',
    warehouse_id    BIGINT          NOT NULL COMMENT '仓库ID',
    qty             INT             NOT NULL COMMENT '预留数量',
    status          VARCHAR(20)     NOT NULL COMMENT '状态: RESERVED-已预留, CONFIRMED-已确认, RELEASED-已释放',
    expire_at       DATETIME        NULL COMMENT '预留过期时间',
    version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标记 0-未删除 1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_sku_warehouse (order_no, sku_id, warehouse_id),
    KEY idx_order_no (order_no),
    KEY idx_status_expire (status, expire_at),
    KEY idx_sku_warehouse (sku_id, warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存预留表';

-- 3. 库存流水表
-- 记录所有库存变动，用于审计和问题排查
CREATE TABLE IF NOT EXISTS t_inventory_txn (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    txn_id          VARCHAR(64)     NOT NULL COMMENT '流水ID（UUID）',
    order_no        VARCHAR(64)     NULL COMMENT '关联订单号',
    sku_id          BIGINT          NOT NULL COMMENT 'SKU ID',
    warehouse_id    BIGINT          NOT NULL COMMENT '仓库ID',
    delta_available INT             NOT NULL DEFAULT 0 COMMENT '可用库存变化量（正数增加，负数减少）',
    delta_reserved  INT             NOT NULL DEFAULT 0 COMMENT '预留库存变化量（正数增加，负数减少）',
    available_after INT             NOT NULL COMMENT '变更后可用库存',
    reserved_after  INT             NOT NULL COMMENT '变更后预留库存',
    reason          VARCHAR(50)     NOT NULL COMMENT '变动原因: RESERVE-预留, CONFIRM-确认, RELEASE-释放, ADJUST-调整',
    remark          VARCHAR(500)    NULL COMMENT '备注',
    trace_id        VARCHAR(64)     NULL COMMENT '链路追踪ID',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_txn_id (txn_id),
    KEY idx_order_no (order_no),
    KEY idx_sku_warehouse (sku_id, warehouse_id),
    KEY idx_created_at (created_at),
    KEY idx_reason (reason)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存流水表';

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

-- 插入测试库存数据
-- INSERT INTO t_inventory (sku_id, warehouse_id, available_qty, reserved_qty) VALUES
-- (1001, 1, 100, 0),
-- (1002, 1, 200, 0),
-- (1003, 1, 50, 0),
-- (1001, 2, 80, 0),
-- (1002, 2, 150, 0);
