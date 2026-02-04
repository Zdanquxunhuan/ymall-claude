package com.yuge.pricing.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 试算响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteResponse {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 商品原价总额
     */
    private BigDecimal originalAmount;

    /**
     * 优惠总额
     */
    private BigDecimal totalDiscount;

    /**
     * 应付金额
     */
    private BigDecimal payableAmount;

    /**
     * 命中的促销规则列表
     */
    private List<PromotionHit> promotionHits;

    /**
     * 分摊明细（到订单行）
     */
    private List<AllocationDetail> allocations;

    /**
     * 可用但未使用的优惠券
     */
    private List<AvailableCoupon> availableCoupons;

    /**
     * 促销命中详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionHit {
        private String ruleType;
        private Long ruleId;
        private String ruleName;
        private String userCouponNo;
        private String discountType;
        private BigDecimal thresholdAmount;
        private BigDecimal discountAmount;
        private String description;
    }

    /**
     * 分摊明细
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllocationDetail {
        /**
         * SKU ID
         */
        private Long skuId;

        /**
         * 商品标题
         */
        private String title;

        /**
         * 数量
         */
        private Integer qty;

        /**
         * 单价
         */
        private BigDecimal unitPrice;

        /**
         * 行原价（单价*数量）
         */
        private BigDecimal lineOriginalAmount;

        /**
         * 行优惠金额
         */
        private BigDecimal lineDiscountAmount;

        /**
         * 行应付金额
         */
        private BigDecimal linePayableAmount;

        /**
         * 优惠明细（来自哪些规则）
         */
        private List<DiscountBreakdown> discountBreakdowns;
    }

    /**
     * 优惠分解
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscountBreakdown {
        private String ruleType;
        private Long ruleId;
        private String ruleName;
        private BigDecimal discountAmount;
    }

    /**
     * 可用优惠券
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailableCoupon {
        private String userCouponNo;
        private Long couponId;
        private String couponName;
        private String couponType;
        private BigDecimal thresholdAmount;
        private BigDecimal discountAmount;
        private BigDecimal discountRate;
        private BigDecimal maxDiscountAmount;
        private Boolean eligible;
        private String ineligibleReason;
    }
}
