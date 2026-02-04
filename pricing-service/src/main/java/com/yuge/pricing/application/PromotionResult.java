package com.yuge.pricing.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 促销试算结果（内部模型）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionResult {

    private Long userId;
    private BigDecimal originalAmount;
    private BigDecimal totalDiscount;
    private BigDecimal payableAmount;
    private List<RuleHit> hitRules;
    private List<AvailableCoupon> availableCoupons;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleHit {
        private String ruleType;
        private Long ruleId;
        private String ruleName;
        private String userCouponNo;
        private String discountType;
        private BigDecimal thresholdAmount;
        private BigDecimal discountAmount;
        private String description;
    }

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
