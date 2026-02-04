-- H2 测试数据库Schema

-- 订单主表
CREATE TABLE IF NOT EXISTS `t_order` (
    `id` BIGINT NOT NULL,
    `order_no` VARCHAR(32) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    `client_request_id` VARCHAR(64) NOT NULL,
    `price_lock_no` VARCHAR(64),
    `remark` VARCHAR(500),
    `version` INT NOT NULL DEFAULT 1,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `created_by` VARCHAR(64) DEFAULT 'system',
    `updated_by` VARCHAR(64) DEFAULT 'system',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_order_no ON t_order(order_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_client_request ON t_order(user_id, client_request_id);

-- 订单明细表
CREATE TABLE IF NOT EXISTS `t_order_item` (
    `id` BIGINT AUTO_INCREMENT,
    `order_no` VARCHAR(32) NOT NULL,
    `sku_id` BIGINT NOT NULL,
    `qty` INT NOT NULL DEFAULT 1,
    `title_snapshot` VARCHAR(256) NOT NULL,
    `price_snapshot` DECIMAL(10,2) NOT NULL,
    `discount_amount` DECIMAL(10,2) DEFAULT 0.00,
    `payable_amount` DECIMAL(10,2),
    `promo_snapshot_json` TEXT,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS idx_order_item_order_no ON t_order_item(order_no);

-- 订单状态流转审计表
CREATE TABLE IF NOT EXISTS `t_order_state_flow` (
    `id` BIGINT AUTO_INCREMENT,
    `order_no` VARCHAR(32) NOT NULL,
    `from_status` VARCHAR(32),
    `to_status` VARCHAR(32) NOT NULL,
    `event` VARCHAR(64) NOT NULL,
    `event_id` VARCHAR(64),
    `operator` VARCHAR(64),
    `trace_id` VARCHAR(64),
    `remark` VARCHAR(500),
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS idx_state_flow_order_no ON t_order_state_flow(order_no);

-- Outbox事件发件箱表
CREATE TABLE IF NOT EXISTS `t_outbox_event` (
    `id` BIGINT AUTO_INCREMENT,
    `event_id` VARCHAR(64) NOT NULL,
    `biz_key` VARCHAR(128) NOT NULL,
    `topic` VARCHAR(128) NOT NULL,
    `tag` VARCHAR(64),
    `payload_json` TEXT NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `retry_count` INT NOT NULL DEFAULT 0,
    `max_retry` INT NOT NULL DEFAULT 5,
    `next_retry_at` TIMESTAMP,
    `sent_at` TIMESTAMP,
    `trace_id` VARCHAR(64),
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_outbox_event_id ON t_outbox_event(event_id);
