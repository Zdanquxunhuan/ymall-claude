package com.yuge.pricing.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 锁价响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LockResponse {

    /**
     * 价格锁编号
     */
    private String priceLockNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 状态
     */
    private String status;

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
     * 签名（防篡改）
     */
    private String signature;

    /**
     * 签名版本
     */
    private Integer signVersion;

    /**
     * 锁定时间
     */
    private LocalDateTime lockedAt;

    /**
     * 过期时间
     */
    private LocalDateTime expireAt;

    /**
     * 命中的促销规则列表
     */
    private List<PromotionHit> promotionHits;

    /**
     * 分摊明细（到订单行）
     */
    private List<AllocationDetail> allocations;

    /**
     * 使用的优惠券编号列表
     */
    private List<String> usedCouponNos;

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
        private Long skuId;
        private String title;
        private Integer qty;
        private BigDecimal unitPrice;
        private BigDecimal lineOriginalAmount;
        private BigDecimal lineDiscountAmount;
        private BigDecimal linePayableAmount;
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
}
