-- H2 Schema for Product Service Tests

-- 1. SPU表
CREATE TABLE IF NOT EXISTS t_spu (
    spu_id          BIGINT          NOT NULL AUTO_INCREMENT,
    title           VARCHAR(200)    NOT NULL,
    category_id     BIGINT          NOT NULL,
    brand_id        BIGINT          NULL,
    description     CLOB            NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    version         INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (spu_id)
);

-- 2. SKU表
CREATE TABLE IF NOT EXISTS t_sku (
    sku_id          BIGINT          NOT NULL AUTO_INCREMENT,
    spu_id          BIGINT          NOT NULL,
    title           VARCHAR(200)    NOT NULL,
    attrs_json      CLOB            NULL,
    price           DECIMAL(10,2)   NOT NULL DEFAULT 0.00,
    original_price  DECIMAL(10,2)   NULL,
    sku_code        VARCHAR(64)     NULL,
    bar_code        VARCHAR(64)     NULL,
    weight          DECIMAL(10,3)   NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    publish_time    TIMESTAMP       NULL,
    version         INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (sku_id)
);

-- 3. Outbox表
CREATE TABLE IF NOT EXISTS t_outbox (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    event_id        VARCHAR(64)     NOT NULL,
    event_type      VARCHAR(50)     NOT NULL,
    aggregate_type  VARCHAR(50)     NOT NULL,
    aggregate_id    BIGINT          NOT NULL,
    payload         CLOB            NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    retry_count     INT             NOT NULL DEFAULT 0,
    max_retry       INT             NOT NULL DEFAULT 3,
    next_retry_at   TIMESTAMP       NULL,
    error_message   VARCHAR(500)    NULL,
    version         INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (event_id)
);

-- 4. MQ消费日志表
CREATE TABLE IF NOT EXISTS t_mq_consume_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    event_id        VARCHAR(64)     NOT NULL,
    consumer_group  VARCHAR(100)    NOT NULL,
    topic           VARCHAR(100)    NOT NULL,
    tags            VARCHAR(100)    NULL,
    biz_key         VARCHAR(100)    NULL,
    status          VARCHAR(20)     NOT NULL,
    result          VARCHAR(500)    NULL,
    cost_ms         BIGINT          NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- 5. 幂等记录表（platform-infra需要）
CREATE TABLE IF NOT EXISTS t_idempotent_record (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    idempotent_key  VARCHAR(128)    NOT NULL,
    request_hash    VARCHAR(64)     NULL,
    response        CLOB            NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PROCESSING',
    expire_at       TIMESTAMP       NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (idempotent_key)
);
