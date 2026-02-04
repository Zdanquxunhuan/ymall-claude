-- 购物车服务数据库初始化脚本
-- Database: ymall_cart

CREATE DATABASE IF NOT EXISTS ymall_cart DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ymall_cart;

-- 购物车合并日志表
CREATE TABLE IF NOT EXISTS t_cart_merge_log (
    id BIGINT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    anon_id VARCHAR(64) NOT NULL COMMENT '游客ID',
    merge_strategy VARCHAR(32) NOT NULL COMMENT '合并策略: QTY_ADD(数量累加), LATEST_WIN(以最新为准)',
    anon_cart_snapshot TEXT COMMENT '合并前游客车快照(JSON)',
    user_cart_snapshot TEXT COMMENT '合并前用户车快照(JSON)',
    merged_cart_snapshot TEXT COMMENT '合并后用户车快照(JSON)',
    merged_sku_count INT DEFAULT 0 COMMENT '合并的SKU数量',
    conflict_sku_count INT DEFAULT 0 COMMENT '冲突的SKU数量',
    merge_time_ms BIGINT DEFAULT 0 COMMENT '合并耗时(毫秒)',
    remark VARCHAR(500) COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_anon_id (anon_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车合并日志表';
