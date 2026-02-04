-- H2 测试数据库 Schema

-- 支付单表
CREATE TABLE IF NOT EXISTS t_pay_order (
    id BIGINT NOT NULL PRIMARY KEY,
    pay_no VARCHAR(32) NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'INIT',
    channel VARCHAR(32) NOT NULL DEFAULT 'MOCK',
    channel_trade_no VARCHAR(64),
    paid_at TIMESTAMP,
    expire_at TIMESTAMP,
    close_reason VARCHAR(256),
    version INT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) DEFAULT 'system',
    updated_by VARCHAR(64) DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pay_no UNIQUE (pay_no),
    CONSTRAINT uk_order_no UNIQUE (order_no)
);

CREATE INDEX IF NOT EXISTS idx_pay_status ON t_pay_order(status);
CREATE INDEX IF NOT EXISTS idx_pay_channel ON t_pay_order(channel);

-- 支付回调日志表
CREATE TABLE IF NOT EXISTS t_pay_callback_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pay_no VARCHAR(32) NOT NULL,
    callback_id VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    channel_trade_no VARCHAR(64),
    callback_status VARCHAR(20) NOT NULL,
    raw_payload CLOB NOT NULL,
    signature VARCHAR(256),
    signature_valid TINYINT NOT NULL DEFAULT 0,
    process_result VARCHAR(20),
    process_message VARCHAR(500),
    trace_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_callback_id UNIQUE (callback_id)
);

CREATE INDEX IF NOT EXISTS idx_callback_pay_no ON t_pay_callback_log(pay_no);

-- 支付对账审计表
CREATE TABLE IF NOT EXISTS t_pay_reconcile_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pay_no VARCHAR(32) NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    action VARCHAR(32) NOT NULL,
    before_status VARCHAR(20) NOT NULL,
    after_status VARCHAR(20),
    query_result VARCHAR(20),
    remark VARCHAR(500),
    trace_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_pay_no ON t_pay_reconcile_audit(pay_no);

-- MQ消费日志表
CREATE TABLE IF NOT EXISTS t_payment_mq_consume_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    consumer_group VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    topic VARCHAR(128),
    tag VARCHAR(64),
    biz_key VARCHAR(128),
    result VARCHAR(500),
    ignored_reason VARCHAR(500),
    trace_id VARCHAR(64),
    cost_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_event_consumer UNIQUE (event_id, consumer_group)
);
