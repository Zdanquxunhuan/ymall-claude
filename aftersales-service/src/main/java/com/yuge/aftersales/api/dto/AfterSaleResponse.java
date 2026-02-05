package com.yuge.aftersales.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 售后单响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleResponse {

    private Long id;

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
     * 售后类型
     */
    private String type;

    /**
     * 售后类型描述
     */
    private String typeDesc;

    /**
     * 售后状态
     */
    private String status;

    /**
     * 售后状态描述
     */
    private String statusDesc;

    /**
     * 申请原因
     */
    private String reason;

    /**
     * 退款总金额
     */
    private BigDecimal refundAmount;

    /**
     * 退款单号
     */
    private String refundNo;

    /**
     * 拒绝原因
     */
    private String rejectReason;

    /**
     * 审批时间
     */
    private LocalDateTime approvedAt;

    /**
     * 审批人
     */
    private String approvedBy;

    /**
     * 退款完成时间
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

    /**
     * 退款明细
     */
    private List<AfterSaleItemResponse> items;

    /**
     * 售后明细响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AfterSaleItemResponse {
        private Long id;
        private Long orderItemId;
        private Long skuId;
        private Integer qty;
        private BigDecimal refundAmount;
        private BigDecimal originalPrice;
        private BigDecimal payableAmount;
    }
}
