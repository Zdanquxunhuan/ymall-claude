package com.yuge.aftersales.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款成功事件（从payment-service接收）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundSucceededEvent {

    /**
     * 事件ID
     */
    private String eventId;

    /**
     * 退款单号
     */
    private String refundNo;

    /**
     * 原支付单号
     */
    private String payNo;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 售后单号
     */
    private String asNo;

    /**
     * 退款金额
     */
    private BigDecimal amount;

    /**
     * 退款渠道
     */
    private String channel;

    /**
     * 渠道退款流水号
     */
    private String channelRefundNo;

    /**
     * 退款完成时间
     */
    private LocalDateTime refundedAt;

    /**
     * 退款明细
     */
    private List<RefundItemInfo> items;

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

    /**
     * 退款明细信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundItemInfo {
        private Long skuId;
        private Integer qty;
        private BigDecimal refundAmount;
    }
}
