package com.yuge.cart.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 结算响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResponse {

    /**
     * 是否可下单
     */
    private Boolean canOrder;

    /**
     * 不可下单原因（如果canOrder=false）
     */
    private String failReason;

    /**
     * 价格锁编号（用于下单）
     */
    private String priceLockNo;

    /**
     * 签名（用于下单校验）
     */
    private String signature;

    /**
     * 签名版本
     */
    private Integer signVersion;

    /**
     * 锁价过期时间
     */
    private LocalDateTime expireAt;

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
     * 结算商品明细
     */
    private List<CheckoutItem> items;

    /**
     * 命中的促销规则
     */
    private List<PromotionHit> promotionHits;

    /**
     * 可用优惠券列表
     */
    private List<AvailableCoupon> availableCoupons;

    /**
     * 库存校验结果
     */
    private List<StockCheckResult> stockCheckResults;

    /**
     * 结算商品项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckoutItem {
        private Long skuId;
        private String title;
        private String imageUrl;
        private Integer qty;
        private BigDecimal unitPrice;
        private BigDecimal lineOriginalAmount;
        private BigDecimal lineDiscountAmount;
        private BigDecimal linePayableAmount;
        private String skuAttrs;
        private Long warehouseId;
        
        /**
         * 库存是否充足
         */
        private Boolean stockSufficient;
        
        /**
         * 可售数量
         */
        private Integer availableQty;
    }

    /**
     * 促销命中
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

    /**
     * 库存校验结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockCheckResult {
        private Long skuId;
        private Long warehouseId;
        private Integer requestQty;
        private Integer availableQty;
        private Boolean sufficient;
        private String message;
    }
}
