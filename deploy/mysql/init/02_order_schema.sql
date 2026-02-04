-- =============================================
-- 订单服务数据表 - order-service
-- =============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 订单主表
-- ----------------------------
DROP TABLE IF EXISTS `t_order`;
CREATE TABLE `t_order` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '订单总金额',
    `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT '订单状态: CREATED/STOCK_RESERVED/STOCK_FAILED/CANCELED',
    `client_request_id` VARCHAR(64) NOT NULL COMMENT '客户端请求ID(幂等键)',
    `remark` VARCHAR(500) COMMENT '备注',
    `version` INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_by` VARCHAR(64) DEFAULT 'system' COMMENT '创建人',
    `updated_by` VARCHAR(64) DEFAULT 'system' COMMENT '更新人',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_user_client_request` (`user_id`, `client_request_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单主表';

-- ----------------------------
-- 订单明细表
-- ----------------------------
DROP TABLE IF EXISTS `t_order_item`;
CREATE TABLE `t_order_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号',
    `sku_id` BIGINT NOT NULL COMMENT 'SKU ID',
    `qty` INT NOT NULL DEFAULT 1 COMMENT '购买数量',
    `title_snapshot` VARCHAR(256) NOT NULL COMMENT '商品标题快照',
    `price_snapshot` DECIMAL(10,2) NOT NULL COMMENT '商品单价快照',
    `promo_snapshot_json` TEXT COMMENT '促销信息快照(JSON)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';

-- ----------------------------
-- 订单状态流转审计表
-- ----------------------------
DROP TABLE IF EXISTS `t_order_state_flow`;
CREATE TABLE `t_order_state_flow` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号',
    `from_status` VARCHAR(32) COMMENT '原状态',
    `to_status` VARCHAR(32) NOT NULL COMMENT '目标状态',
    `event` VARCHAR(64) NOT NULL COMMENT '触发事件',
    `event_id` VARCHAR(64) COMMENT '事件ID',
    `operator` VARCHAR(64) COMMENT '操作人',
    `trace_id` VARCHAR(64) COMMENT '链路追踪ID',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_event_id` (`event_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单状态流转审计表';

-- ----------------------------
-- Outbox 事件发件箱表
-- ----------------------------
DROP TABLE IF EXISTS `t_outbox_event`;
CREATE TABLE `t_outbox_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `event_id` VARCHAR(64) NOT NULL COMMENT '事件ID(全局唯一)',
    `biz_key` VARCHAR(128) NOT NULL COMMENT '业务键(如订单号)',
    `topic` VARCHAR(128) NOT NULL COMMENT '消息主题',
    `tag` VARCHAR(64) COMMENT '消息标签',
    `payload_json` TEXT NOT NULL COMMENT '消息内容(JSON)',
    `status` VARCHAR(20) NOT NULL DEFAULT 'NEW' COMMENT '状态: NEW/RETRY/SENT/DEAD',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    `max_retry` INT NOT NULL DEFAULT 5 COMMENT '最大重试次数',
    `next_retry_at` DATETIME COMMENT '下次重试时间',
    `sent_at` DATETIME COMMENT '发送成功时间',
    `last_error` VARCHAR(500) COMMENT '最后一次错误信息',
    `trace_id` VARCHAR(64) COMMENT '链路追踪ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_id` (`event_id`),
    KEY `idx_biz_key` (`biz_key`),
    KEY `idx_status` (`status`),
    KEY `idx_status_retry` (`status`, `next_retry_at`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox事件发件箱表';

-- ----------------------------
-- MQ消费日志表（用于消费幂等）
-- ----------------------------
DROP TABLE IF EXISTS `t_mq_consume_log`;
CREATE TABLE `t_mq_consume_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `event_id` VARCHAR(64) NOT NULL COMMENT '事件ID',
    `consumer_group` VARCHAR(128) NOT NULL COMMENT '消费者组',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态: PROCESSING/SUCCESS/FAILED/IGNORED',
    `topic` VARCHAR(128) COMMENT '消息主题',
    `tag` VARCHAR(64) COMMENT '消息标签',
    `biz_key` VARCHAR(128) COMMENT '业务键',
    `result` VARCHAR(500) COMMENT '消费结果/错误信息',
    `ignored_reason` VARCHAR(500) COMMENT '忽略原因（乱序消息时记录）',
    `trace_id` VARCHAR(64) COMMENT '链路追踪ID',
    `cost_ms` BIGINT COMMENT '消费耗时(毫秒)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_consumer` (`event_id`, `consumer_group`),
    KEY `idx_status` (`status`),
    KEY `idx_biz_key` (`biz_key`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MQ消费日志表';

SET FOREIGN_KEY_CHECKS = 1;
