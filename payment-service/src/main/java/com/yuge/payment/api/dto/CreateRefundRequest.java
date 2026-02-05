package com.yuge.payment.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建退款请求
 */
@Data
public class CreateRefundRequest {

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
     * 退款原因
     */
    private String reason;

    /**
     * 退款明细
     */
    private List<RefundItemRequest> items;

    /**
     * 退款明细请求
     */
    @Data
    public static class RefundItemRequest {
        private Long skuId;
        private Integer qty;
        private BigDecimal refundAmount;
    }
}
