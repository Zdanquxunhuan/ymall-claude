-- 订单服务数据库更新脚本
-- 新增字段支持锁价防篡改

-- 订单表新增 price_lock_no 字段
ALTER TABLE t_order ADD COLUMN price_lock_no VARCHAR(64) COMMENT '价格锁编号（防篡价）' AFTER client_request_id;

-- 订单明细表新增分摊字段
ALTER TABLE t_order_item ADD COLUMN discount_amount DECIMAL(10,2) DEFAULT 0.00 COMMENT '优惠金额（分摊）' AFTER price_snapshot;
ALTER TABLE t_order_item ADD COLUMN payable_amount DECIMAL(10,2) COMMENT '实付金额（分摊后）' AFTER discount_amount;
