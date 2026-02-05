package com.yuge.aftersales.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 申请售后请求
 */
@Data
public class ApplyAfterSaleRequest {

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 售后类型: REFUND/RETURN_REFUND
     */
    private String type;

    /**
     * 申请原因
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
        /**
         * 订单明细ID
         */
        private Long orderItemId;

        /**
         * SKU ID
         */
        private Long skuId;

        /**
         * 退款数量
         */
        private Integer qty;

        /**
         * 退款金额（可选，不传则按订单快照计算）
         */
        private BigDecimal refundAmount;
    }
}
