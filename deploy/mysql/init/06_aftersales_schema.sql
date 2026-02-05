-- =============================================
-- 售后服务数据表 - aftersales-service
-- =============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 售后单主表
-- ----------------------------
DROP TABLE IF EXISTS `t_after_sale`;
CREATE TABLE `t_after_sale` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `as_no` VARCHAR(32) NOT NULL COMMENT '售后单号',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `type` VARCHAR(20) NOT NULL DEFAULT 'REFUND' COMMENT '售后类型: REFUND-仅退款, RETURN_REFUND-退货退款',
    `status` VARCHAR(20) NOT NULL DEFAULT 'APPLIED' COMMENT '售后状态: APPLIED-已申请, APPROVED-已审批, REFUNDING-退款中, REFUNDED-已退款, REJECTED-已拒绝, CANCELED-已取消',
    `reason` VARCHAR(500) NOT NULL COMMENT '申请原因',
    `refund_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '退款总金额',
    `refund_no` VARCHAR(32) COMMENT '退款单号（关联payment-service）',
    `reject_reason` VARCHAR(500) COMMENT '拒绝原因',
    `approved_at` DATETIME COMMENT '审批时间',
    `approved_by` VARCHAR(64) COMMENT '审批人',
    `refunded_at` DATETIME COMMENT '退款完成时间',
    `version` INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_by` VARCHAR(64) DEFAULT 'system' COMMENT '创建人',
    `updated_by` VARCHAR(64) DEFAULT 'system' COMMENT '更新人',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_as_no` (`as_no`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_refund_no` (`refund_no`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后单主表';

-- ----------------------------
-- 售后单明细表
-- ----------------------------
DROP TABLE IF EXISTS `t_after_sale_item`;
CREATE TABLE `t_after_sale_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `as_no` VARCHAR(32) NOT NULL COMMENT '售后单号',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号',
    `order_item_id` BIGINT NOT NULL COMMENT '订单明细ID',
    `sku_id` BIGINT NOT NULL COMMENT 'SKU ID',
    `qty` INT NOT NULL DEFAULT 1 COMMENT '退款数量',
    `refund_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '退款金额（分摊后）',
    `original_price` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '原价快照',
    `payable_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '实付金额快照',
    `promo_snapshot_json` TEXT COMMENT '促销信息快照(JSON)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_as_no` (`as_no`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_order_item_id` (`order_item_id`),
    KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后单明细表';

-- ----------------------------
-- 售后状态流转审计表
-- ----------------------------
DROP TABLE IF EXISTS `t_after_sale_state_flow`;
CREATE TABLE `t_after_sale_state_flow` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `as_no` VARCHAR(32) NOT NULL COMMENT '售后单号',
    `from_status` VARCHAR(20) COMMENT '原状态',
    `to_status` VARCHAR(20) NOT NULL COMMENT '目标状态',
    `event` VARCHAR(64) NOT NULL COMMENT '触发事件',
    `event_id` VARCHAR(64) COMMENT '事件ID',
    `operator` VARCHAR(64) COMMENT '操作人',
    `trace_id` VARCHAR(64) COMMENT '链路追踪ID',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_as_no` (`as_no`),
    KEY `idx_event_id` (`event_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后状态流转审计表';

-- ----------------------------
-- MQ消费日志表（用于消费幂等）
-- ----------------------------
DROP TABLE IF EXISTS `t_aftersales_mq_consume_log`;
CREATE TABLE `t_aftersales_mq_consume_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `event_id` VARCHAR(64) NOT NULL COMMENT '事件ID',
    `consumer_group` VARCHAR(128) NOT NULL COMMENT '消费者组',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态: PROCESSING/SUCCESS/FAILED/IGNORED',
    `topic` VARCHAR(128) COMMENT '消息主题',
    `tag` VARCHAR(64) COMMENT '消息标签',
    `biz_key` VARCHAR(128) COMMENT '业务键',
    `result` VARCHAR(500) COMMENT '消费结果/错误信息',
    `ignored_reason` VARCHAR(500) COMMENT '忽略原因',
    `trace_id` VARCHAR(64) COMMENT '链路追踪ID',
    `cost_ms` BIGINT COMMENT '消费耗时(毫秒)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_consumer` (`event_id`, `consumer_group`),
    KEY `idx_status` (`status`),
    KEY `idx_biz_key` (`biz_key`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后服务MQ消费日志表';

SET FOREIGN_KEY_CHECKS = 1;
