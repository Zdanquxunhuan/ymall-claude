package com.yuge.cart.infrastructure.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 定价服务客户端
 * 调用pricing-service进行试算和锁价
 */
public interface PricingClient {

    /**
     * 锁价
     */
    LockResult lock(LockParam param);

    /**
     * 验证价格锁
     */
    VerifyResult verify(String priceLockNo, String signature);

    /**
     * 锁价参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class LockParam {
        private Long userId;
        private List<ItemInfo> items;
        private List<String> userCouponNos;
        private Integer lockMinutes;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ItemInfo {
            private Long skuId;
            private Integer qty;
            private BigDecimal unitPrice;
            private String title;
            private Long categoryId;
        }
    }

    /**
     * 锁价结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class LockResult {
        private Boolean success;
        private String errorMessage;
        private String priceLockNo;
        private String signature;
        private Integer signVersion;
        private BigDecimal originalAmount;
        private BigDecimal totalDiscount;
        private BigDecimal payableAmount;
        private LocalDateTime lockedAt;
        private LocalDateTime expireAt;
        private List<PromotionHit> promotionHits;
        private List<AllocationDetail> allocations;
        private List<AvailableCoupon> availableCoupons;

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

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class AllocationDetail {
            private Long skuId;
            private String title;
            private Integer qty;
            private BigDecimal unitPrice;
            private BigDecimal lineOriginalAmount;
            private BigDecimal lineDiscountAmount;
            private BigDecimal linePayableAmount;
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

    /**
     * 验证结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class VerifyResult {
        private Boolean valid;
        private String errorMessage;
    }
}
