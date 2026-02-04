package com.yuge.fulfillment.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付成功事件（从payment-service消费）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSucceededEvent {

    /**
     * 事件ID
     */
    private String eventId;

    /**
     * 支付单号
     */
    private String payNo;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 支付金额
     */
    private BigDecimal amount;

    /**
     * 支付渠道
     */
    private String channel;

    /**
     * 渠道交易号
     */
    private String channelTradeNo;

    /**
     * 支付成功时间
     */
    private LocalDateTime paidAt;

    /**
     * 事件时间
     */
    private LocalDateTime eventTime;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 版本号
     */
    private String version;
}
