package com.yuge.order.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单取消事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCanceledEvent {

    /**
     * 事件ID
     */
    private String eventId;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 订单金额
     */
    private BigDecimal amount;

    /**
     * 取消原因
     */
    private String cancelReason;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 事件时间
     */
    private LocalDateTime eventTime;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 事件版本
     */
    private String version;
}
