-- =============================================
-- 支付服务数据表 - payment-service
-- =============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 支付单表
-- ----------------------------
DROP TABLE IF EXISTS `t_pay_order`;
CREATE TABLE `t_pay_order` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `pay_no` VARCHAR(32) NOT NULL COMMENT '支付单号',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号',
    `amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '支付金额',
    `status` VARCHAR(20) NOT NULL DEFAULT 'INIT' COMMENT '支付状态: INIT/PAYING/SUCCESS/FAILED/CLOSED',
    `channel` VARCHAR(32) NOT NULL DEFAULT 'MOCK' COMMENT '支付渠道: MOCK/ALIPAY/WECHAT',
    `channel_trade_no` VARCHAR(64) COMMENT '渠道交易号',
    `paid_at` DATETIME COMMENT '支付成功时间',
    `expire_at` DATETIME COMMENT '支付过期时间',
    `close_reason` VARCHAR(256) COMMENT '关闭原因',
    `version` INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_by` VARCHAR(64) DEFAULT 'system' COMMENT '创建人',
    `updated_by` VARCHAR(64) DEFAULT 'system' COMMENT '更新人',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pay_no` (`pay_no`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_status` (`status`),
    KEY `idx_channel` (`channel`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_status_expire` (`status`, `expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付单表';

-- ----------------------------
-- 支付回调日志表
-- ----------------------------
DROP TABLE IF EXISTS `t_pay_callback_log`;
CREATE TABLE `t_pay_callback_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `pay_no` VARCHAR(32) NOT NULL COMMENT '支付单号',
    `callback_id` VARCHAR(64) NOT NULL COMMENT '回调唯一ID（用于幂等）',
    `channel` VARCHAR(32) NOT NULL COMMENT '支付渠道',
    `channel_trade_no` VARCHAR(64) COMMENT '渠道交易号',
    `callback_status` VARCHAR(20) NOT NULL COMMENT '回调状态: SUCCESS/FAILED',
    `raw_payload` TEXT NOT NULL COMMENT '原始回调报文',
    `signature` VARCHAR(256) COMMENT '签名',
    `signature_valid` TINYINT NOT NULL DEFAULT 0 COMMENT '签名是否有效: 0-无效 1-有效',
    `process_result` VARCHAR(20) COMMENT '处理结果: PROCESSED/IGNORED/FAILED',
    `process_message` VARCHAR(500) COMMENT '处理结果说明',
    `trace_id` VARCHAR(64) COMMENT '链路追踪ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_callback_id` (`callback_id`),
    KEY `idx_pay_no` (`pay_no`),
    KEY `idx_channel` (`channel`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付回调日志表';

-- ----------------------------
-- 支付对账审计表
-- ----------------------------
DROP TABLE IF EXISTS `t_pay_reconcile_audit`;
CREATE TABLE `t_pay_reconcile_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `pay_no` VARCHAR(32) NOT NULL COMMENT '支付单号',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号',
    `action` VARCHAR(32) NOT NULL COMMENT '操作类型: QUERY/CLOSE/NOTIFY',
    `before_status` VARCHAR(20) NOT NULL COMMENT '操作前状态',
    `after_status` VARCHAR(20) COMMENT '操作后状态',
    `query_result` VARCHAR(20) COMMENT '查询结果: SUCCESS/FAILED/NOT_FOUND/PAYING',
    `remark` VARCHAR(500) COMMENT '备注',
    `trace_id` VARCHAR(64) COMMENT '链路追踪ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_pay_no` (`pay_no`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_action` (`action`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付对账审计表';

-- ----------------------------
-- MQ消费日志表（用于消费幂等）
-- ----------------------------
DROP TABLE IF EXISTS `t_payment_mq_consume_log`;
CREATE TABLE `t_payment_mq_consume_log` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付服务MQ消费日志表';

-- ----------------------------
-- 退款单表
-- ----------------------------
DROP TABLE IF EXISTS `t_refund_order`;
CREATE TABLE `t_refund_order` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `refund_no` VARCHAR(32) NOT NULL COMMENT '退款单号',
    `pay_no` VARCHAR(32) NOT NULL COMMENT '原支付单号',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号',
    `as_no` VARCHAR(32) COMMENT '售后单号',
    `amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '退款金额',
    `status` VARCHAR(20) NOT NULL DEFAULT 'INIT' COMMENT '退款状态: INIT/REFUNDING/SUCCESS/FAILED',
    `channel` VARCHAR(32) NOT NULL DEFAULT 'MOCK' COMMENT '退款渠道: MOCK/ALIPAY/WECHAT',
    `channel_refund_no` VARCHAR(64) COMMENT '渠道退款流水号',
    `refund_reason` VARCHAR(500) COMMENT '退款原因',
    `refunded_at` DATETIME COMMENT '退款成功时间',
    `items_json` TEXT COMMENT '退款明细JSON',
    `version` INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_by` VARCHAR(64) DEFAULT 'system' COMMENT '创建人',
    `updated_by` VARCHAR(64) DEFAULT 'system' COMMENT '更新人',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_pay_no` (`pay_no`),
    KEY `idx_as_no` (`as_no`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款单表';

-- ----------------------------
-- 退款回调日志表
-- ----------------------------
DROP TABLE IF EXISTS `t_refund_callback_log`;
CREATE TABLE `t_refund_callback_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `refund_no` VARCHAR(32) NOT NULL COMMENT '退款单号',
    `callback_id` VARCHAR(64) NOT NULL COMMENT '回调唯一ID（用于幂等）',
    `channel` VARCHAR(32) NOT NULL COMMENT '退款渠道',
    `channel_refund_no` VARCHAR(64) COMMENT '渠道退款流水号',
    `callback_status` VARCHAR(20) NOT NULL COMMENT '回调状态: SUCCESS/FAILED',
    `raw_payload` TEXT NOT NULL COMMENT '原始回调报文',
    `signature` VARCHAR(256) COMMENT '签名',
    `signature_valid` TINYINT NOT NULL DEFAULT 0 COMMENT '签名是否有效: 0-无效 1-有效',
    `process_result` VARCHAR(20) COMMENT '处理结果: PROCESSED/IGNORED/FAILED',
    `process_message` VARCHAR(500) COMMENT '处理结果说明',
    `trace_id` VARCHAR(64) COMMENT '链路追踪ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_callback_id` (`callback_id`),
    KEY `idx_refund_no` (`refund_no`),
    KEY `idx_channel` (`channel`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款回调日志表';

SET FOREIGN_KEY_CHECKS = 1;
