package com.yuge.order.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 售后退款完成事件（从aftersales-service接收）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleRefundedEvent {

    /**
     * 事件ID
     */
    private String eventId;

    /**
     * 售后单号
     */
    private String asNo;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 退款金额
     */
    private BigDecimal refundAmount;

    /**
     * 退款单号
     */
    private String refundNo;

    /**
     * 退款明细
     */
    private List<RefundItemInfo> items;

    /**
     * 退款完成时间
     */
    private LocalDateTime refundedAt;

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
        private Long orderItemId;
        private Long skuId;
        private Integer qty;
        private BigDecimal refundAmount;
    }
}
