-- H2 Test Schema for aftersales-service

CREATE TABLE IF NOT EXISTS t_after_sale (
    id BIGINT NOT NULL,
    as_no VARCHAR(32) NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'REFUND',
    status VARCHAR(20) NOT NULL DEFAULT 'APPLIED',
    reason VARCHAR(500) NOT NULL,
    refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    refund_no VARCHAR(32),
    reject_reason VARCHAR(500),
    approved_at TIMESTAMP,
    approved_by VARCHAR(64),
    refunded_at TIMESTAMP,
    version INT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) DEFAULT 'system',
    updated_by VARCHAR(64) DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_as_no ON t_after_sale(as_no);
CREATE INDEX IF NOT EXISTS idx_order_no ON t_after_sale(order_no);
CREATE INDEX IF NOT EXISTS idx_user_id ON t_after_sale(user_id);
CREATE INDEX IF NOT EXISTS idx_status ON t_after_sale(status);

CREATE TABLE IF NOT EXISTS t_after_sale_item (
    id BIGINT AUTO_INCREMENT,
    as_no VARCHAR(32) NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    order_item_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    qty INT NOT NULL DEFAULT 1,
    refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    original_price DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    payable_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    promo_snapshot_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_item_as_no ON t_after_sale_item(as_no);
CREATE INDEX IF NOT EXISTS idx_item_order_no ON t_after_sale_item(order_no);

CREATE TABLE IF NOT EXISTS t_after_sale_state_flow (
    id BIGINT AUTO_INCREMENT,
    as_no VARCHAR(32) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    event VARCHAR(64) NOT NULL,
    event_id VARCHAR(64),
    operator VARCHAR(64),
    trace_id VARCHAR(64),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_flow_as_no ON t_after_sale_state_flow(as_no);
