-- ymall 数据库初始化脚本
-- 创建时间: 2024

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 幂等记录表（可选，用于持久化幂等记录）
-- ----------------------------
DROP TABLE IF EXISTS `t_idempotent_record`;
CREATE TABLE `t_idempotent_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `idempotent_key` VARCHAR(128) NOT NULL COMMENT '幂等键',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态: PROCESSING/SUCCESS/FAILED',
    `result` TEXT COMMENT '处理结果(JSON)',
    `result_type` VARCHAR(256) COMMENT '结果类型',
    `trace_id` VARCHAR(64) COMMENT '链路追踪ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `expire_at` DATETIME COMMENT '过期时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_idempotent_key` (`idempotent_key`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_expire_at` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='幂等记录表';

-- ----------------------------
-- 演示业务表
-- ----------------------------
DROP TABLE IF EXISTS `t_demo_order`;
CREATE TABLE `t_demo_order` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `order_no` VARCHAR(64) NOT NULL COMMENT '订单号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '订单金额',
    `status` VARCHAR(20) NOT NULL DEFAULT 'CREATED' COMMENT '订单状态',
    `remark` VARCHAR(500) COMMENT '备注',
    `version` INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_by` VARCHAR(64) DEFAULT 'system' COMMENT '创建人',
    `updated_by` VARCHAR(64) DEFAULT 'system' COMMENT '更新人',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='演示订单表';

-- ----------------------------
-- 状态流转审计表
-- ----------------------------
DROP TABLE IF EXISTS `t_state_flow`;
CREATE TABLE `t_state_flow` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `biz_type` VARCHAR(32) NOT NULL COMMENT '业务类型: ORDER/PAYMENT/INVENTORY等',
    `biz_id` BIGINT NOT NULL COMMENT '业务ID',
    `biz_no` VARCHAR(64) COMMENT '业务编号',
    `from_state` VARCHAR(32) COMMENT '原状态',
    `to_state` VARCHAR(32) NOT NULL COMMENT '目标状态',
    `event` VARCHAR(64) COMMENT '触发事件',
    `operator` VARCHAR(64) COMMENT '操作人',
    `trace_id` VARCHAR(64) COMMENT '链路追踪ID',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_biz` (`biz_type`, `biz_id`),
    KEY `idx_biz_no` (`biz_no`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='状态流转审计表';

-- ----------------------------
-- Outbox 消息发件箱表
-- ----------------------------
DROP TABLE IF EXISTS `t_outbox_message`;
CREATE TABLE `t_outbox_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `message_id` VARCHAR(64) NOT NULL COMMENT '消息ID',
    `topic` VARCHAR(128) NOT NULL COMMENT '消息主题',
    `tag` VARCHAR(64) COMMENT '消息标签',
    `business_key` VARCHAR(128) COMMENT '业务键',
    `payload` TEXT NOT NULL COMMENT '消息内容(JSON)',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SENT/FAILED',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    `max_retry` INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    `next_retry_at` DATETIME COMMENT '下次重试时间',
    `sent_at` DATETIME COMMENT '发送时间',
    `trace_id` VARCHAR(64) COMMENT '链路追踪ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_id` (`message_id`),
    KEY `idx_status` (`status`),
    KEY `idx_next_retry` (`status`, `next_retry_at`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox消息发件箱表';

SET FOREIGN_KEY_CHECKS = 1;
