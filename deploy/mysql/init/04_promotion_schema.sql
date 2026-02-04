-- =============================================
-- 促销服务数据表 - promotion-service
-- =============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 优惠券模板表
-- ----------------------------
DROP TABLE IF EXISTS `t_coupon`;
CREATE TABLE `t_coupon` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `coupon_code` VARCHAR(32) NOT NULL COMMENT '优惠券编码（唯一）',
    `name` VARCHAR(128) NOT NULL COMMENT '优惠券名称',
    `type` VARCHAR(32) NOT NULL COMMENT '优惠券类型: FULL_REDUCTION/DISCOUNT/FIXED_AMOUNT',
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/ACTIVE/PAUSED/EXPIRED/EXHAUSTED',
    `threshold_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '使用门槛金额（满X元可用）',
    `discount_amount` DECIMAL(12,2) COMMENT '优惠金额（满减券/固定金额券）',
    `discount_rate` DECIMAL(5,2) COMMENT '折扣比例（折扣券，如0.80表示8折）',
    `max_discount_amount` DECIMAL(12,2) COMMENT '最大优惠金额（折扣券封顶）',
    `total_quantity` INT NOT NULL DEFAULT 0 COMMENT '发行总量',
    `issued_quantity` INT NOT NULL DEFAULT 0 COMMENT '已发放数量',
    `per_user_limit` INT NOT NULL DEFAULT 1 COMMENT '每人限领数量',
    `applicable_scope` VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT '适用商品范围: ALL/CATEGORY/SKU',
    `applicable_items` TEXT COMMENT '适用商品ID列表（JSON数组）',
    `valid_start_time` DATETIME NOT NULL COMMENT '生效开始时间',
    `valid_end_time` DATETIME NOT NULL COMMENT '生效结束时间',
    `valid_days` INT COMMENT '领取后有效天数（与valid_end_time二选一）',
    `remark` VARCHAR(500) COMMENT '备注',
    `version` INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_by` VARCHAR(64) DEFAULT 'system' COMMENT '创建人',
    `updated_by` VARCHAR(64) DEFAULT 'system' COMMENT '更新人',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_coupon_code` (`coupon_code`),
    KEY `idx_status` (`status`),
    KEY `idx_type` (`type`),
    KEY `idx_valid_time` (`valid_start_time`, `valid_end_time`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板表';

-- ----------------------------
-- 用户优惠券表
-- ----------------------------
DROP TABLE IF EXISTS `t_coupon_user`;
CREATE TABLE `t_coupon_user` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `user_coupon_no` VARCHAR(32) NOT NULL COMMENT '用户优惠券编号（唯一）',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `coupon_id` BIGINT NOT NULL COMMENT '优惠券ID',
    `coupon_code` VARCHAR(32) NOT NULL COMMENT '优惠券编码（冗余）',
    `status` VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态: AVAILABLE/USED/EXPIRED/LOCKED',
    `receive_request_id` VARCHAR(64) NOT NULL COMMENT '领取请求ID（幂等键）',
    `receive_time` DATETIME NOT NULL COMMENT '领取时间',
    `valid_start_time` DATETIME NOT NULL COMMENT '生效开始时间',
    `valid_end_time` DATETIME NOT NULL COMMENT '生效结束时间',
    `used_time` DATETIME COMMENT '使用时间',
    `used_order_no` VARCHAR(32) COMMENT '使用订单号',
    `discount_amount` DECIMAL(12,2) COMMENT '实际优惠金额',
    `locked_time` DATETIME COMMENT '锁定时间（试算锁定）',
    `lock_expire_time` DATETIME COMMENT '锁定过期时间',
    `price_lock_no` VARCHAR(32) COMMENT '锁定关联的价格锁编号',
    `version` INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_coupon_no` (`user_coupon_no`),
    UNIQUE KEY `uk_user_receive_request` (`user_id`, `receive_request_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_coupon_id` (`coupon_id`),
    KEY `idx_status` (`status`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_valid_time` (`valid_start_time`, `valid_end_time`),
    KEY `idx_price_lock_no` (`price_lock_no`),
    KEY `idx_lock_expire` (`status`, `lock_expire_time`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户优惠券表';

-- ----------------------------
-- 优惠券领取/核销日志表（审计）
-- ----------------------------
DROP TABLE IF EXISTS `t_coupon_action_log`;
CREATE TABLE `t_coupon_action_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_coupon_no` VARCHAR(32) NOT NULL COMMENT '用户优惠券编号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `action` VARCHAR(32) NOT NULL COMMENT '操作类型: RECEIVE/USE/LOCK/UNLOCK/EXPIRE',
    `from_status` VARCHAR(32) COMMENT '原状态',
    `to_status` VARCHAR(32) NOT NULL COMMENT '目标状态',
    `order_no` VARCHAR(32) COMMENT '关联订单号',
    `price_lock_no` VARCHAR(32) COMMENT '关联价格锁编号',
    `discount_amount` DECIMAL(12,2) COMMENT '优惠金额',
    `request_id` VARCHAR(64) COMMENT '请求ID',
    `trace_id` VARCHAR(64) COMMENT '链路追踪ID',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_coupon_no` (`user_coupon_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_price_lock_no` (`price_lock_no`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券操作日志表';

SET FOREIGN_KEY_CHECKS = 1;
