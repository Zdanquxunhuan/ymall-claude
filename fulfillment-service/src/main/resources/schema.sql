-- =====================================================
-- 履约服务表结构 (fulfillment-service)
-- =====================================================

-- 发货单表
CREATE TABLE IF NOT EXISTS `t_shipment` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `shipment_no` VARCHAR(64) NOT NULL COMMENT '发货单号',
    `order_no` VARCHAR(64) NOT NULL COMMENT '订单号',
    `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT '状态: CREATED-已创建, SHIPPED-已发货, DELIVERED-已签收',
    `shipped_at` DATETIME DEFAULT NULL COMMENT '发货时间',
    `delivered_at` DATETIME DEFAULT NULL COMMENT '签收时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` VARCHAR(64) DEFAULT 'SYSTEM' COMMENT '创建人',
    `updated_by` VARCHAR(64) DEFAULT 'SYSTEM' COMMENT '更新人',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_shipment` (`order_no`, `shipment_no`),
    UNIQUE KEY `uk_shipment_no` (`shipment_no`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发货单表';

-- 运单表
CREATE TABLE IF NOT EXISTS `t_waybill` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `waybill_no` VARCHAR(64) NOT NULL COMMENT '运单号',
    `shipment_no` VARCHAR(64) NOT NULL COMMENT '发货单号',
    `carrier` VARCHAR(64) NOT NULL COMMENT '承运商',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` VARCHAR(64) DEFAULT 'SYSTEM' COMMENT '创建人',
    `updated_by` VARCHAR(64) DEFAULT 'SYSTEM' COMMENT '更新人',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_waybill_no` (`waybill_no`),
    KEY `idx_shipment_no` (`shipment_no`),
    KEY `idx_carrier` (`carrier`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运单表';

-- 物流轨迹表
CREATE TABLE IF NOT EXISTS `t_logistics_track` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `waybill_no` VARCHAR(64) NOT NULL COMMENT '运单号',
    `node_time` DATETIME NOT NULL COMMENT '节点时间',
    `node_code` VARCHAR(64) NOT NULL COMMENT '节点编码',
    `content` VARCHAR(512) NOT NULL COMMENT '轨迹内容',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_waybill_node` (`waybill_no`, `node_time`, `node_code`),
    KEY `idx_waybill_no` (`waybill_no`),
    KEY `idx_node_time` (`node_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物流轨迹表';

-- MQ消费日志表（用于幂等）
CREATE TABLE IF NOT EXISTS `t_fulfillment_mq_consume_log` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `event_id` VARCHAR(64) NOT NULL COMMENT '事件ID',
    `consumer_group` VARCHAR(128) NOT NULL COMMENT '消费者组',
    `topic` VARCHAR(128) DEFAULT NULL COMMENT '主题',
    `tags` VARCHAR(128) DEFAULT NULL COMMENT '标签',
    `biz_key` VARCHAR(128) DEFAULT NULL COMMENT '业务键',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态: PROCESSING-处理中, SUCCESS-成功, FAILED-失败, IGNORED-已忽略',
    `result_msg` VARCHAR(512) DEFAULT NULL COMMENT '处理结果消息',
    `ignored_reason` VARCHAR(512) DEFAULT NULL COMMENT '忽略原因',
    `cost_ms` BIGINT DEFAULT NULL COMMENT '处理耗时(毫秒)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_consumer` (`event_id`, `consumer_group`),
    KEY `idx_biz_key` (`biz_key`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MQ消费日志表';
