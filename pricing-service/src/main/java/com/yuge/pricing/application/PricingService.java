package com.yuge.pricing.application;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.platform.infra.common.ErrorCode;
import com.yuge.platform.infra.exception.BizException;
import com.yuge.pricing.api.dto.*;
import com.yuge.pricing.domain.entity.PriceLock;
import com.yuge.pricing.domain.enums.PriceLockStatus;
import com.yuge.pricing.infrastructure.repository.PriceLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 定价应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {

    private final PriceLockRepository priceLockRepository;
    private final PromotionClient promotionClient;
    private final ObjectMapper objectMapper;

    @Value("${pricing.sign.secret:ymall-pricing-secret-key}")
    private String signSecret;

    private static final int CURRENT_SIGN_VERSION = 1;

    /**
     * 试算（不锁定）
     */
    public QuoteResponse quote(QuoteRequest request) {
        Long userId = request.getUserId();
        List<QuoteRequest.ItemInfo> items = request.getItems();
        List<String> userCouponNos = request.getUserCouponNos();

        // 1. 计算商品原价总额
        BigDecimal originalAmount = items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. 调用促销服务试算
        PromotionResult promotionResult = promotionClient.applyPromotion(userId, items, userCouponNos);

        // 3. 计算分摊
        List<QuoteResponse.AllocationDetail> allocations = calculateAllocation(
                items, promotionResult.getTotalDiscount(), promotionResult.getHitRules());

        // 4. 构建响应
        return QuoteResponse.builder()
                .userId(userId)
                .originalAmount(originalAmount)
                .totalDiscount(promotionResult.getTotalDiscount())
                .payableAmount(promotionResult.getPayableAmount())
                .promotionHits(convertPromotionHits(promotionResult.getHitRules()))
                .allocations(allocations)
                .availableCoupons(convertAvailableCoupons(promotionResult.getAvailableCoupons()))
                .build();
    }

    /**
     * 锁价
     */
    @Transactional(rollbackFor = Exception.class)
    public LockResponse lock(LockRequest request) {
        Long userId = request.getUserId();
        List<LockRequest.ItemInfo> items = request.getItems();
        List<String> userCouponNos = request.getUserCouponNos();
        int lockMinutes = request.getLockMinutes() != null ? request.getLockMinutes() : 15;

        // 1. 计算商品原价总额
        BigDecimal originalAmount = items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. 调用促销服务试算
        List<QuoteRequest.ItemInfo> quoteItems = items.stream()
                .map(item -> {
                    QuoteRequest.ItemInfo qi = new QuoteRequest.ItemInfo();
                    qi.setSkuId(item.getSkuId());
                    qi.setQty(item.getQty());
                    qi.setUnitPrice(item.getUnitPrice());
                    qi.setTitle(item.getTitle());
                    qi.setCategoryId(item.getCategoryId());
                    return qi;
                })
                .collect(Collectors.toList());

        PromotionResult promotionResult = promotionClient.applyPromotion(userId, quoteItems, userCouponNos);

        // 3. 计算分摊
        List<LockResponse.AllocationDetail> allocations = calculateAllocationForLock(
                items, promotionResult.getTotalDiscount(), promotionResult.getHitRules());

        // 4. 生成价格锁编号
        String priceLockNo = generatePriceLockNo();

        // 5. 构建快照
        LockSnapshot snapshot = LockSnapshot.builder()
                .userId(userId)
                .items(items)
                .userCouponNos(userCouponNos)
                .originalAmount(originalAmount)
                .totalDiscount(promotionResult.getTotalDiscount())
                .payableAmount(promotionResult.getPayableAmount())
                .hitRules(promotionResult.getHitRules())
                .allocations(allocations)
                .build();

        // 6. 生成签名
        String signature = generateSignature(priceLockNo, userId, promotionResult.getPayableAmount(), snapshot);

        // 7. 保存价格锁
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = now.plusMinutes(lockMinutes);

        PriceLock priceLock = new PriceLock();
        priceLock.setId(IdUtil.getSnowflakeNextId());
        priceLock.setPriceLockNo(priceLockNo);
        priceLock.setUserId(userId);
        priceLock.setStatus(PriceLockStatus.LOCKED.getCode());
        priceLock.setOriginalAmount(originalAmount);
        priceLock.setTotalDiscount(promotionResult.getTotalDiscount());
        priceLock.setPayableAmount(promotionResult.getPayableAmount());
        priceLock.setSnapshotJson(toJson(snapshot));
        priceLock.setAllocationJson(toJson(allocations));
        priceLock.setCouponNosJson(toJson(userCouponNos));
        priceLock.setSignature(signature);
        priceLock.setSignVersion(CURRENT_SIGN_VERSION);
        priceLock.setLockedAt(now);
        priceLock.setExpireAt(expireAt);

        priceLockRepository.save(priceLock);

        // 8. 锁定优惠券（如果有）
        if (userCouponNos != null && !userCouponNos.isEmpty()) {
            promotionClient.lockCoupons(userCouponNos, priceLockNo, expireAt);
        }

        log.info("[PricingService] Price locked, priceLockNo={}, userId={}, payableAmount={}, expireAt={}",
                priceLockNo, userId, promotionResult.getPayableAmount(), expireAt);

        // 9. 构建响应
        return LockResponse.builder()
                .priceLockNo(priceLockNo)
                .userId(userId)
                .status(PriceLockStatus.LOCKED.getCode())
                .originalAmount(originalAmount)
                .totalDiscount(promotionResult.getTotalDiscount())
                .payableAmount(promotionResult.getPayableAmount())
                .signature(signature)
                .signVersion(CURRENT_SIGN_VERSION)
                .lockedAt(now)
                .expireAt(expireAt)
                .promotionHits(convertPromotionHitsForLock(promotionResult.getHitRules()))
                .allocations(allocations)
                .usedCouponNos(userCouponNos)
                .build();
    }

    /**
     * 查询价格锁
     */
    public LockResponse getPriceLock(String priceLockNo) {
        PriceLock priceLock = priceLockRepository.findByPriceLockNo(priceLockNo)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "价格锁不存在: " + priceLockNo));

        return buildLockResponse(priceLock);
    }

    /**
     * 验证价格锁签名
     */
    public boolean verifySignature(String priceLockNo, String signature) {
        PriceLock priceLock = priceLockRepository.findByPriceLockNo(priceLockNo)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "价格锁不存在: " + priceLockNo));

        return signature.equals(priceLock.getSignature());
    }

    /**
     * 使用价格锁（下单时调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public LockResponse usePriceLock(String priceLockNo, String orderNo, String signature) {
        PriceLock priceLock = priceLockRepository.findByPriceLockNo(priceLockNo)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "价格锁不存在: " + priceLockNo));

        // 1. 验证签名
        if (!signature.equals(priceLock.getSignature())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "签名验证失败");
        }

        // 2. 检查状态
        if (!PriceLockStatus.LOCKED.getCode().equals(priceLock.getStatus())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "价格锁状态不可用: " + priceLock.getStatus());
        }

        // 3. 检查是否过期
        if (LocalDateTime.now().isAfter(priceLock.getExpireAt())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "价格锁已过期");
        }

        // 4. CAS更新状态
        boolean success = priceLockRepository.casUse(priceLock.getId(), orderNo, priceLock.getVersion());
        if (!success) {
            throw new BizException(ErrorCode.DB_OPTIMISTIC_LOCK, "价格锁使用失败，请重试");
        }

        log.info("[PricingService] Price lock used, priceLockNo={}, orderNo={}", priceLockNo, orderNo);

        // 重新查询返回最新状态
        priceLock = priceLockRepository.findByPriceLockNo(priceLockNo).orElse(priceLock);
        return buildLockResponse(priceLock);
    }

    /**
     * 取消价格锁
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelPriceLock(String priceLockNo) {
        PriceLock priceLock = priceLockRepository.findByPriceLockNo(priceLockNo)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "价格锁不存在: " + priceLockNo));

        if (!PriceLockStatus.LOCKED.getCode().equals(priceLock.getStatus())) {
            log.info("[PricingService] Price lock already not locked, priceLockNo={}, status={}",
                    priceLockNo, priceLock.getStatus());
            return;
        }

        boolean success = priceLockRepository.casCancel(priceLock.getId(), priceLock.getVersion());
        if (success) {
            // 解锁优惠券
            List<String> couponNos = fromJson(priceLock.getCouponNosJson(), new TypeReference<List<String>>() {});
            if (couponNos != null && !couponNos.isEmpty()) {
                promotionClient.unlockCoupons(couponNos, priceLockNo);
            }
            log.info("[PricingService] Price lock canceled, priceLockNo={}", priceLockNo);
        }
    }

    /**
     * 计算分摊（按金额比例分摊优惠）
     */
    private List<QuoteResponse.AllocationDetail> calculateAllocation(
            List<QuoteRequest.ItemInfo> items,
            BigDecimal totalDiscount,
            List<PromotionResult.RuleHit> hitRules) {

        // 计算总金额
        BigDecimal totalAmount = items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<QuoteResponse.AllocationDetail> allocations = new ArrayList<>();
        BigDecimal allocatedDiscount = BigDecimal.ZERO;

        for (int i = 0; i < items.size(); i++) {
            QuoteRequest.ItemInfo item = items.get(i);
            BigDecimal lineOriginalAmount = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQty()));

            // 按比例分摊优惠（最后一行用减法避免精度问题）
            BigDecimal lineDiscount;
            if (i == items.size() - 1) {
                lineDiscount = totalDiscount.subtract(allocatedDiscount);
            } else {
                lineDiscount = totalAmount.compareTo(BigDecimal.ZERO) > 0
                        ? totalDiscount.multiply(lineOriginalAmount).divide(totalAmount, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                allocatedDiscount = allocatedDiscount.add(lineDiscount);
            }

            BigDecimal linePayable = lineOriginalAmount.subtract(lineDiscount);
            if (linePayable.compareTo(BigDecimal.ZERO) < 0) {
                linePayable = BigDecimal.ZERO;
            }

            // 构建优惠分解
            List<QuoteResponse.DiscountBreakdown> breakdowns = new ArrayList<>();
            if (hitRules != null && !hitRules.isEmpty() && lineDiscount.compareTo(BigDecimal.ZERO) > 0) {
                // 简化处理：将所有规则的优惠按比例分配到每行
                for (PromotionResult.RuleHit rule : hitRules) {
                    BigDecimal ruleLineDiscount = totalDiscount.compareTo(BigDecimal.ZERO) > 0
                            ? rule.getDiscountAmount().multiply(lineDiscount).divide(totalDiscount, 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    breakdowns.add(QuoteResponse.DiscountBreakdown.builder()
                            .ruleType(rule.getRuleType())
                            .ruleId(rule.getRuleId())
                            .ruleName(rule.getRuleName())
                            .discountAmount(ruleLineDiscount)
                            .build());
                }
            }

            allocations.add(QuoteResponse.AllocationDetail.builder()
                    .skuId(item.getSkuId())
                    .title(item.getTitle())
                    .qty(item.getQty())
                    .unitPrice(item.getUnitPrice())
                    .lineOriginalAmount(lineOriginalAmount)
                    .lineDiscountAmount(lineDiscount)
                    .linePayableAmount(linePayable)
                    .discountBreakdowns(breakdowns)
                    .build());
        }

        return allocations;
    }

    /**
     * 计算分摊（锁价用）
     */
    private List<LockResponse.AllocationDetail> calculateAllocationForLock(
            List<LockRequest.ItemInfo> items,
            BigDecimal totalDiscount,
            List<PromotionResult.RuleHit> hitRules) {

        BigDecimal totalAmount = items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LockResponse.AllocationDetail> allocations = new ArrayList<>();
        BigDecimal allocatedDiscount = BigDecimal.ZERO;

        for (int i = 0; i < items.size(); i++) {
            LockRequest.ItemInfo item = items.get(i);
            BigDecimal lineOriginalAmount = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQty()));

            BigDecimal lineDiscount;
            if (i == items.size() - 1) {
                lineDiscount = totalDiscount.subtract(allocatedDiscount);
            } else {
                lineDiscount = totalAmount.compareTo(BigDecimal.ZERO) > 0
                        ? totalDiscount.multiply(lineOriginalAmount).divide(totalAmount, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                allocatedDiscount = allocatedDiscount.add(lineDiscount);
            }

            BigDecimal linePayable = lineOriginalAmount.subtract(lineDiscount);
            if (linePayable.compareTo(BigDecimal.ZERO) < 0) {
                linePayable = BigDecimal.ZERO;
            }

            List<LockResponse.DiscountBreakdown> breakdowns = new ArrayList<>();
            if (hitRules != null && !hitRules.isEmpty() && lineDiscount.compareTo(BigDecimal.ZERO) > 0) {
                for (PromotionResult.RuleHit rule : hitRules) {
                    BigDecimal ruleLineDiscount = totalDiscount.compareTo(BigDecimal.ZERO) > 0
                            ? rule.getDiscountAmount().multiply(lineDiscount).divide(totalDiscount, 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    breakdowns.add(LockResponse.DiscountBreakdown.builder()
                            .ruleType(rule.getRuleType())
                            .ruleId(rule.getRuleId())
                            .ruleName(rule.getRuleName())
                            .discountAmount(ruleLineDiscount)
                            .build());
                }
            }

            allocations.add(LockResponse.AllocationDetail.builder()
                    .skuId(item.getSkuId())
                    .title(item.getTitle())
                    .qty(item.getQty())
                    .unitPrice(item.getUnitPrice())
                    .lineOriginalAmount(lineOriginalAmount)
                    .lineDiscountAmount(lineDiscount)
                    .linePayableAmount(linePayable)
                    .discountBreakdowns(breakdowns)
                    .build());
        }

        return allocations;
    }

    /**
     * 生成签名
     */
    private String generateSignature(String priceLockNo, Long userId, BigDecimal payableAmount, LockSnapshot snapshot) {
        String content = String.format("%s|%d|%s|%s|%s",
                priceLockNo,
                userId,
                payableAmount.toPlainString(),
                toJson(snapshot),
                signSecret);
        return DigestUtil.sha256Hex(content);
    }

    private String generatePriceLockNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.valueOf((int) ((Math.random() * 9000) + 1000));
        return "PL" + timestamp + random;
    }

    private List<QuoteResponse.PromotionHit> convertPromotionHits(List<PromotionResult.RuleHit> hitRules) {
        if (hitRules == null) return new ArrayList<>();
        return hitRules.stream()
                .map(rule -> QuoteResponse.PromotionHit.builder()
                        .ruleType(rule.getRuleType())
                        .ruleId(rule.getRuleId())
                        .ruleName(rule.getRuleName())
                        .userCouponNo(rule.getUserCouponNo())
                        .discountType(rule.getDiscountType())
                        .thresholdAmount(rule.getThresholdAmount())
                        .discountAmount(rule.getDiscountAmount())
                        .description(rule.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    private List<LockResponse.PromotionHit> convertPromotionHitsForLock(List<PromotionResult.RuleHit> hitRules) {
        if (hitRules == null) return new ArrayList<>();
        return hitRules.stream()
                .map(rule -> LockResponse.PromotionHit.builder()
                        .ruleType(rule.getRuleType())
                        .ruleId(rule.getRuleId())
                        .ruleName(rule.getRuleName())
                        .userCouponNo(rule.getUserCouponNo())
                        .discountType(rule.getDiscountType())
                        .thresholdAmount(rule.getThresholdAmount())
                        .discountAmount(rule.getDiscountAmount())
                        .description(rule.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    private List<QuoteResponse.AvailableCoupon> convertAvailableCoupons(List<PromotionResult.AvailableCoupon> coupons) {
        if (coupons == null) return new ArrayList<>();
        return coupons.stream()
                .map(c -> QuoteResponse.AvailableCoupon.builder()
                        .userCouponNo(c.getUserCouponNo())
                        .couponId(c.getCouponId())
                        .couponName(c.getCouponName())
                        .couponType(c.getCouponType())
                        .thresholdAmount(c.getThresholdAmount())
                        .discountAmount(c.getDiscountAmount())
                        .discountRate(c.getDiscountRate())
                        .maxDiscountAmount(c.getMaxDiscountAmount())
                        .eligible(c.getEligible())
                        .ineligibleReason(c.getIneligibleReason())
                        .build())
                .collect(Collectors.toList());
    }

    private LockResponse buildLockResponse(PriceLock priceLock) {
        List<LockResponse.AllocationDetail> allocations = fromJson(
                priceLock.getAllocationJson(),
                new TypeReference<List<LockResponse.AllocationDetail>>() {});

        List<String> couponNos = fromJson(
                priceLock.getCouponNosJson(),
                new TypeReference<List<String>>() {});

        LockSnapshot snapshot = fromJson(priceLock.getSnapshotJson(), new TypeReference<LockSnapshot>() {});
        List<LockResponse.PromotionHit> promotionHits = snapshot != null
                ? convertPromotionHitsForLock(snapshot.getHitRules())
                : new ArrayList<>();

        return LockResponse.builder()
                .priceLockNo(priceLock.getPriceLockNo())
                .userId(priceLock.getUserId())
                .status(priceLock.getStatus())
                .originalAmount(priceLock.getOriginalAmount())
                .totalDiscount(priceLock.getTotalDiscount())
                .payableAmount(priceLock.getPayableAmount())
                .signature(priceLock.getSignature())
                .signVersion(priceLock.getSignVersion())
                .lockedAt(priceLock.getLockedAt())
                .expireAt(priceLock.getExpireAt())
                .promotionHits(promotionHits)
                .allocations(allocations)
                .usedCouponNos(couponNos)
                .build();
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "JSON序列化失败");
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "JSON反序列化失败");
        }
    }

    /**
     * 锁价快照
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LockSnapshot {
        private Long userId;
        private List<LockRequest.ItemInfo> items;
        private List<String> userCouponNos;
        private BigDecimal originalAmount;
        private BigDecimal totalDiscount;
        private BigDecimal payableAmount;
        private List<PromotionResult.RuleHit> hitRules;
        private List<LockResponse.AllocationDetail> allocations;
    }
}
