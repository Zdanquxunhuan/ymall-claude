package com.yuge.payment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    /**
     * 支付单ID
     */
    private Long id;

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
     * 支付状态
     */
    private String status;

    /**
     * 支付状态描述
     */
    private String statusDesc;

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
     * 支付过期时间
     */
    private LocalDateTime expireAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
