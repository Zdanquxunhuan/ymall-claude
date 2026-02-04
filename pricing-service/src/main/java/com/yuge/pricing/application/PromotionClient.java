package com.yuge.pricing.application;

import com.yuge.pricing.api.dto.QuoteRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 促销服务客户端（模拟实现，实际应通过HTTP/RPC调用promotion-service）
 * 
 * 在实际生产环境中，这里应该使用Feign/RestTemplate调用promotion-service
 * 当前为演示目的，使用内存模拟
 */
@Slf4j
@Component
public class PromotionClient {

    // 模拟优惠券数据存储
    private static final Map<String, MockCoupon> COUPON_STORE = new ConcurrentHashMap<>();
    private static final Map<String, MockUserCoupon> USER_COUPON_STORE = new ConcurrentHashMap<>();

    /**
     * 调用促销服务试算
     */
    public PromotionResult applyPromotion(Long userId, List<QuoteRequest.ItemInfo> items, List<String> userCouponNos) {
        // 1. 计算商品原价总额
        BigDecimal originalAmount = items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. 查询用户可用优惠券
        List<PromotionResult.AvailableCoupon> availableCoupons = new ArrayList<>();
        for (MockUserCoupon uc : USER_COUPON_STORE.values()) {
            if (uc.getUserId().equals(userId) && "AVAILABLE".equals(uc.getStatus())) {
                MockCoupon coupon = COUPON_STORE.get(uc.getCouponCode());
                if (coupon != null) {
                    boolean eligible = originalAmount.compareTo(coupon.getThresholdAmount()) >= 0;
                    availableCoupons.add(PromotionResult.AvailableCoupon.builder()
                            .userCouponNo(uc.getUserCouponNo())
                            .couponId(coupon.getId())
                            .couponName(coupon.getName())
                            .couponType(coupon.getType())
                            .thresholdAmount(coupon.getThresholdAmount())
                            .discountAmount(coupon.getDiscountAmount())
                            .discountRate(coupon.getDiscountRate())
                            .maxDiscountAmount(coupon.getMaxDiscountAmount())
                            .eligible(eligible)
                            .ineligibleReason(eligible ? null : 
                                    String.format("订单金额%.2f未达到门槛%.2f", originalAmount, coupon.getThresholdAmount()))
                            .build());
                }
            }
        }

        // 3. 计算优惠
        BigDecimal totalDiscount = BigDecimal.ZERO;
        List<PromotionResult.RuleHit> hitRules = new ArrayList<>();

        if (userCouponNos != null && !userCouponNos.isEmpty()) {
            for (String userCouponNo : userCouponNos) {
                MockUserCoupon uc = USER_COUPON_STORE.get(userCouponNo);
                if (uc == null || !uc.getUserId().equals(userId)) continue;
                if (!"AVAILABLE".equals(uc.getStatus()) && !"LOCKED".equals(uc.getStatus())) continue;

                MockCoupon coupon = COUPON_STORE.get(uc.getCouponCode());
                if (coupon == null) continue;

                // 检查门槛
                if (originalAmount.compareTo(coupon.getThresholdAmount()) < 0) continue;

                // 计算优惠金额
                BigDecimal discount = calculateDiscount(coupon, originalAmount);
                totalDiscount = totalDiscount.add(discount);

                hitRules.add(PromotionResult.RuleHit.builder()
                        .ruleType("COUPON")
                        .ruleId(coupon.getId())
                        .ruleName(coupon.getName())
                        .userCouponNo(userCouponNo)
                        .discountType(coupon.getType())
                        .thresholdAmount(coupon.getThresholdAmount())
                        .discountAmount(discount)
                        .description(buildDiscountDescription(coupon, discount))
                        .build());
            }
        }

        BigDecimal payableAmount = originalAmount.subtract(totalDiscount);
        if (payableAmount.compareTo(BigDecimal.ZERO) < 0) {
            payableAmount = BigDecimal.ZERO;
        }

        return PromotionResult.builder()
                .userId(userId)
                .originalAmount(originalAmount)
                .totalDiscount(totalDiscount)
                .payableAmount(payableAmount)
                .hitRules(hitRules)
                .availableCoupons(availableCoupons)
                .build();
    }

    /**
     * 锁定优惠券
     */
    public void lockCoupons(List<String> userCouponNos, String priceLockNo, LocalDateTime expireAt) {
        for (String userCouponNo : userCouponNos) {
            MockUserCoupon uc = USER_COUPON_STORE.get(userCouponNo);
            if (uc != null && "AVAILABLE".equals(uc.getStatus())) {
                uc.setStatus("LOCKED");
                uc.setPriceLockNo(priceLockNo);
                log.info("[PromotionClient] Coupon locked, userCouponNo={}, priceLockNo={}", userCouponNo, priceLockNo);
            }
        }
    }

    /**
     * 解锁优惠券
     */
    public void unlockCoupons(List<String> userCouponNos, String priceLockNo) {
        for (String userCouponNo : userCouponNos) {
            MockUserCoupon uc = USER_COUPON_STORE.get(userCouponNo);
            if (uc != null && "LOCKED".equals(uc.getStatus()) && priceLockNo.equals(uc.getPriceLockNo())) {
                uc.setStatus("AVAILABLE");
                uc.setPriceLockNo(null);
                log.info("[PromotionClient] Coupon unlocked, userCouponNo={}", userCouponNo);
            }
        }
    }

    // ========== 模拟数据管理方法（用于测试） ==========

    /**
     * 添加模拟优惠券
     */
    public void addMockCoupon(MockCoupon coupon) {
        COUPON_STORE.put(coupon.getCouponCode(), coupon);
    }

    /**
     * 添加模拟用户优惠券
     */
    public void addMockUserCoupon(MockUserCoupon userCoupon) {
        USER_COUPON_STORE.put(userCoupon.getUserCouponNo(), userCoupon);
    }

    /**
     * 清除模拟数据
     */
    public void clearMockData() {
        COUPON_STORE.clear();
        USER_COUPON_STORE.clear();
    }

    private BigDecimal calculateDiscount(MockCoupon coupon, BigDecimal orderAmount) {
        BigDecimal discount;
        switch (coupon.getType()) {
            case "FULL_REDUCTION":
            case "FIXED_AMOUNT":
                discount = coupon.getDiscountAmount();
                break;
            case "DISCOUNT":
                discount = orderAmount.multiply(BigDecimal.ONE.subtract(coupon.getDiscountRate()))
                        .setScale(2, RoundingMode.HALF_UP);
                if (coupon.getMaxDiscountAmount() != null 
                        && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                    discount = coupon.getMaxDiscountAmount();
                }
                break;
            default:
                discount = BigDecimal.ZERO;
        }
        if (discount.compareTo(orderAmount) > 0) {
            discount = orderAmount;
        }
        return discount;
    }

    private String buildDiscountDescription(MockCoupon coupon, BigDecimal discount) {
        switch (coupon.getType()) {
            case "FULL_REDUCTION":
                return String.format("满%.2f减%.2f", coupon.getThresholdAmount(), discount);
            case "DISCOUNT":
                return String.format("%.1f折优惠%.2f", 
                        coupon.getDiscountRate().multiply(BigDecimal.TEN), discount);
            case "FIXED_AMOUNT":
                return String.format("立减%.2f", discount);
            default:
                return "优惠" + discount;
        }
    }

    /**
     * 模拟优惠券
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MockCoupon {
        private Long id;
        private String couponCode;
        private String name;
        private String type;
        private BigDecimal thresholdAmount;
        private BigDecimal discountAmount;
        private BigDecimal discountRate;
        private BigDecimal maxDiscountAmount;
    }

    /**
     * 模拟用户优惠券
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MockUserCoupon {
        private Long id;
        private String userCouponNo;
        private Long userId;
        private String couponCode;
        private String status;
        private String priceLockNo;
    }
}
