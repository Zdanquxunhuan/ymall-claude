-- H2 测试数据库 Schema

-- 价格锁表
CREATE TABLE IF NOT EXISTS `t_price_lock` (
    `id` BIGINT NOT NULL,
    `price_lock_no` VARCHAR(32) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'LOCKED',
    `original_amount` DECIMAL(12,2) NOT NULL,
    `total_discount` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    `payable_amount` DECIMAL(12,2) NOT NULL,
    `snapshot_json` CLOB NOT NULL,
    `allocation_json` CLOB NOT NULL,
    `coupon_nos_json` CLOB,
    `signature` VARCHAR(128) NOT NULL,
    `sign_version` INT NOT NULL DEFAULT 1,
    `locked_at` TIMESTAMP NOT NULL,
    `expire_at` TIMESTAMP NOT NULL,
    `used_at` TIMESTAMP,
    `used_order_no` VARCHAR(32),
    `version` INT NOT NULL DEFAULT 1,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

CREATE UNIQUE INDEX IF NOT EXISTS `uk_price_lock_no` ON `t_price_lock` (`price_lock_no`);
CREATE INDEX IF NOT EXISTS `idx_user_id` ON `t_price_lock` (`user_id`);
CREATE INDEX IF NOT EXISTS `idx_status` ON `t_price_lock` (`status`);
