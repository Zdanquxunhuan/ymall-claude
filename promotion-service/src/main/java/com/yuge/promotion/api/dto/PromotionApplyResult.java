package com.yuge.promotion.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 促销试算结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionApplyResult {

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
    private List<PromotionRuleHit> hitRules;

    /**
     * 可用但未使用的优惠券
     */
    private List<AvailableCoupon> availableCoupons;

    /**
     * 促销规则命中详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionRuleHit {
        /**
         * 规则类型: COUPON
         */
        private String ruleType;

        /**
         * 规则ID（优惠券ID）
         */
        private Long ruleId;

        /**
         * 规则名称
         */
        private String ruleName;

        /**
         * 用户优惠券编号
         */
        private String userCouponNo;

        /**
         * 优惠类型: FULL_REDUCTION/DISCOUNT/FIXED_AMOUNT
         */
        private String discountType;

        /**
         * 门槛金额
         */
        private BigDecimal thresholdAmount;

        /**
         * 优惠金额
         */
        private BigDecimal discountAmount;

        /**
         * 优惠明细描述
         */
        private String description;
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
        /**
         * 是否满足使用条件
         */
        private Boolean eligible;
        /**
         * 不满足条件的原因
         */
        private String ineligibleReason;
    }
}
