-- =============================================
-- 定价服务数据表 - pricing-service
-- =============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 价格锁表
-- ----------------------------
DROP TABLE IF EXISTS `t_price_lock`;
CREATE TABLE `t_price_lock` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `price_lock_no` VARCHAR(32) NOT NULL COMMENT '价格锁编号（唯一）',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `status` VARCHAR(32) NOT NULL DEFAULT 'LOCKED' COMMENT '状态: LOCKED/USED/EXPIRED/CANCELED',
    `original_amount` DECIMAL(12,2) NOT NULL COMMENT '商品原价总额',
    `total_discount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '优惠总额',
    `payable_amount` DECIMAL(12,2) NOT NULL COMMENT '应付金额',
    `snapshot_json` MEDIUMTEXT NOT NULL COMMENT '锁价快照（JSON）',
    `allocation_json` MEDIUMTEXT NOT NULL COMMENT '分摊明细（JSON）',
    `coupon_nos_json` TEXT COMMENT '使用的优惠券编号列表（JSON）',
    `signature` VARCHAR(128) NOT NULL COMMENT '签名（防篡改）',
    `sign_version` INT NOT NULL DEFAULT 1 COMMENT '签名版本',
    `locked_at` DATETIME NOT NULL COMMENT '锁定时间',
    `expire_at` DATETIME NOT NULL COMMENT '过期时间',
    `used_at` DATETIME COMMENT '使用时间',
    `used_order_no` VARCHAR(32) COMMENT '使用的订单号',
    `version` INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_price_lock_no` (`price_lock_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_status_expire` (`status`, `expire_at`),
    KEY `idx_used_order_no` (`used_order_no`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='价格锁表';

-- ----------------------------
-- 价格锁操作日志表（审计）
-- ----------------------------
DROP TABLE IF EXISTS `t_price_lock_log`;
CREATE TABLE `t_price_lock_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `price_lock_no` VARCHAR(32) NOT NULL COMMENT '价格锁编号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `action` VARCHAR(32) NOT NULL COMMENT '操作类型: CREATE/USE/CANCEL/EXPIRE',
    `from_status` VARCHAR(32) COMMENT '原状态',
    `to_status` VARCHAR(32) NOT NULL COMMENT '目标状态',
    `order_no` VARCHAR(32) COMMENT '关联订单号',
    `payable_amount` DECIMAL(12,2) COMMENT '应付金额',
    `trace_id` VARCHAR(64) COMMENT '链路追踪ID',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_price_lock_no` (`price_lock_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='价格锁操作日志表';

-- ----------------------------
-- 分摊审计表（可复算验证）
-- ----------------------------
DROP TABLE IF EXISTS `t_allocation_audit`;
CREATE TABLE `t_allocation_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `price_lock_no` VARCHAR(32) NOT NULL COMMENT '价格锁编号',
    `order_no` VARCHAR(32) COMMENT '订单号',
    `sku_id` BIGINT NOT NULL COMMENT 'SKU ID',
    `qty` INT NOT NULL COMMENT '数量',
    `unit_price` DECIMAL(12,2) NOT NULL COMMENT '单价',
    `line_original_amount` DECIMAL(12,2) NOT NULL COMMENT '行原价',
    `line_discount_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '行优惠金额',
    `line_payable_amount` DECIMAL(12,2) NOT NULL COMMENT '行应付金额',
    `discount_breakdown_json` TEXT COMMENT '优惠分解明细（JSON）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_price_lock_no` (`price_lock_no`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_sku_id` (`sku_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分摊审计表';

SET FOREIGN_KEY_CHECKS = 1;
