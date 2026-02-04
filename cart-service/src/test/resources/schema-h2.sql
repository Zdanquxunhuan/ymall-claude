-- H2 测试数据库Schema for cart-service

-- 购物车合并日志表
CREATE TABLE IF NOT EXISTS `t_cart_merge_log` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `anon_id` VARCHAR(64) NOT NULL,
    `merge_strategy` VARCHAR(32) NOT NULL,
    `anon_cart_snapshot` TEXT,
    `user_cart_snapshot` TEXT,
    `merged_cart_snapshot` TEXT,
    `merged_sku_count` INT DEFAULT 0,
    `conflict_sku_count` INT DEFAULT 0,
    `merge_time_ms` BIGINT DEFAULT 0,
    `remark` VARCHAR(500),
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS idx_merge_log_user_id ON t_cart_merge_log(user_id);
CREATE INDEX IF NOT EXISTS idx_merge_log_anon_id ON t_cart_merge_log(anon_id);
