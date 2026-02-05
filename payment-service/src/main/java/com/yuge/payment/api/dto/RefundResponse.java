package com.yuge.payment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {

    private Long id;

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
     * 退款状态
     */
    private String status;

    /**
     * 退款状态描述
     */
    private String statusDesc;

    /**
     * 退款渠道
     */
    private String channel;

    /**
     * 渠道退款流水号
     */
    private String channelRefundNo;

    /**
     * 退款成功时间
     */
    private LocalDateTime refundedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
