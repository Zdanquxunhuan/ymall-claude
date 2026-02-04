package com.yuge.promotion.application;

import cn.hutool.core.util.IdUtil;
import com.yuge.platform.infra.common.ErrorCode;
import com.yuge.platform.infra.exception.BizException;
import com.yuge.promotion.api.dto.*;
import com.yuge.promotion.domain.entity.Coupon;
import com.yuge.promotion.domain.entity.CouponUser;
import com.yuge.promotion.domain.enums.CouponStatus;
import com.yuge.promotion.domain.enums.CouponType;
import com.yuge.promotion.domain.enums.UserCouponStatus;
import com.yuge.promotion.infrastructure.repository.CouponRepository;
import com.yuge.promotion.infrastructure.repository.CouponUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 优惠券应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUserRepository couponUserRepository;

    /**
     * 创建优惠券活动
     */
    @Transactional(rollbackFor = Exception.class)
    public CouponResponse createCoupon(CreateCouponRequest request) {
        // 生成优惠券编码
        String couponCode = generateCouponCode();

        Coupon coupon = new Coupon();
        coupon.setId(IdUtil.getSnowflakeNextId());
        coupon.setCouponCode(couponCode);
        coupon.setName(request.getName());
        coupon.setType(request.getType());
        coupon.setStatus(CouponStatus.DRAFT.getCode());
        coupon.setThresholdAmount(request.getThresholdAmount());
        coupon.setDiscountAmount(request.getDiscountAmount());
        coupon.setDiscountRate(request.getDiscountRate());
        coupon.setMaxDiscountAmount(request.getMaxDiscountAmount());
        coupon.setTotalQuantity(request.getTotalQuantity());
        coupon.setIssuedQuantity(0);
        coupon.setPerUserLimit(request.getPerUserLimit());
        coupon.setApplicableScope(request.getApplicableScope());
        coupon.setApplicableItems(request.getApplicableItems());
        coupon.setValidStartTime(request.getValidStartTime());
        coupon.setValidEndTime(request.getValidEndTime());
        coupon.setValidDays(request.getValidDays());
        coupon.setRemark(request.getRemark());

        couponRepository.save(coupon);
        log.info("[CouponService] Coupon created, couponCode={}, name={}", couponCode, request.getName());

        return buildCouponResponse(coupon);
    }

    /**
     * 激活优惠券
     */
    @Transactional(rollbackFor = Exception.class)
    public CouponResponse activateCoupon(String couponCode) {
        Coupon coupon = couponRepository.findByCode(couponCode)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "优惠券不存在: " + couponCode));

        if (!CouponStatus.DRAFT.getCode().equals(coupon.getStatus())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "只有草稿状态的优惠券可以激活");
        }

        coupon.setStatus(CouponStatus.ACTIVE.getCode());
        couponRepository.save(coupon);
        log.info("[CouponService] Coupon activated, couponCode={}", couponCode);

        return buildCouponResponse(coupon);
    }

    /**
     * 领取优惠券（幂等）
     */
    @Transactional(rollbackFor = Exception.class)
    public UserCouponResponse receiveCoupon(ReceiveCouponRequest request) {
        Long userId = request.getUserId();
        String couponCode = request.getCouponCode();
        String requestId = request.getRequestId();

        // 1. 幂等检查
        Optional<CouponUser> existing = couponUserRepository.findByUserIdAndReceiveRequestId(userId, requestId);
        if (existing.isPresent()) {
            log.info("[CouponService] Coupon already received, userId={}, requestId={}", userId, requestId);
            return buildUserCouponResponse(existing.get());
        }

        // 2. 查询优惠券
        Coupon coupon = couponRepository.findByCode(couponCode)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "优惠券不存在: " + couponCode));

        // 3. 校验优惠券状态
        if (!CouponStatus.ACTIVE.getCode().equals(coupon.getStatus())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "优惠券不可领取，当前状态: " + coupon.getStatus());
        }

        // 4. 校验库存
        if (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "优惠券已发完");
        }

        // 5. 校验每人限领
        int userCount = couponUserRepository.countByUserIdAndCouponId(userId, coupon.getId());
        if (userCount >= coupon.getPerUserLimit()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "已达到领取上限");
        }

        // 6. CAS扣减库存
        boolean success = couponRepository.casIncrementIssuedQuantity(
                coupon.getId(), 1, coupon.getVersion());
        if (!success) {
            throw new BizException(ErrorCode.DB_OPTIMISTIC_LOCK, "领取失败，请重试");
        }

        // 7. 创建用户优惠券
        CouponUser couponUser = new CouponUser();
        couponUser.setId(IdUtil.getSnowflakeNextId());
        couponUser.setUserCouponNo(generateUserCouponNo());
        couponUser.setUserId(userId);
        couponUser.setCouponId(coupon.getId());
        couponUser.setCouponCode(couponCode);
        couponUser.setStatus(UserCouponStatus.AVAILABLE.getCode());
        couponUser.setReceiveRequestId(requestId);
        couponUser.setReceiveTime(LocalDateTime.now());

        // 计算有效期
        if (coupon.getValidDays() != null && coupon.getValidDays() > 0) {
            couponUser.setValidStartTime(LocalDateTime.now());
            couponUser.setValidEndTime(LocalDateTime.now().plusDays(coupon.getValidDays()));
        } else {
            couponUser.setValidStartTime(coupon.getValidStartTime());
            couponUser.setValidEndTime(coupon.getValidEndTime());
        }

        couponUserRepository.save(couponUser);
        log.info("[CouponService] Coupon received, userId={}, userCouponNo={}", userId, couponUser.getUserCouponNo());

        return buildUserCouponResponse(couponUser, coupon);
    }

    /**
     * 核销优惠券（幂等）
     */
    @Transactional(rollbackFor = Exception.class)
    public UserCouponResponse redeemCoupon(RedeemCouponRequest request) {
        String userCouponNo = request.getUserCouponNo();
        String orderNo = request.getOrderNo();
        BigDecimal discountAmount = request.getDiscountAmount();

        // 1. 查询用户优惠券
        CouponUser couponUser = couponUserRepository.findByUserCouponNo(userCouponNo)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户优惠券不存在: " + userCouponNo));

        // 2. 幂等检查：已核销且订单号一致
        if (UserCouponStatus.USED.getCode().equals(couponUser.getStatus())) {
            if (orderNo.equals(couponUser.getUsedOrderNo())) {
                log.info("[CouponService] Coupon already redeemed, userCouponNo={}, orderNo={}", userCouponNo, orderNo);
                return buildUserCouponResponse(couponUser);
            }
            throw new BizException(ErrorCode.INVALID_PARAM, "优惠券已被其他订单使用");
        }

        // 3. 校验状态（可用或已锁定）
        String currentStatus = couponUser.getStatus();
        if (!UserCouponStatus.AVAILABLE.getCode().equals(currentStatus) 
                && !UserCouponStatus.LOCKED.getCode().equals(currentStatus)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "优惠券状态不可用: " + currentStatus);
        }

        // 4. 校验有效期
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(couponUser.getValidStartTime()) || now.isAfter(couponUser.getValidEndTime())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "优惠券不在有效期内");
        }

        // 5. CAS更新状态
        boolean success = couponUserRepository.casUpdateStatusForUse(
                couponUser.getId(), currentStatus, UserCouponStatus.USED.getCode(),
                orderNo, discountAmount, couponUser.getVersion());
        if (!success) {
            throw new BizException(ErrorCode.DB_OPTIMISTIC_LOCK, "核销失败，请重试");
        }

        log.info("[CouponService] Coupon redeemed, userCouponNo={}, orderNo={}, discountAmount={}",
                userCouponNo, orderNo, discountAmount);

        // 重新查询返回最新状态
        couponUser = couponUserRepository.findByUserCouponNo(userCouponNo).orElse(couponUser);
        return buildUserCouponResponse(couponUser);
    }

    /**
     * 促销试算
     */
    public PromotionApplyResult applyPromotion(PromotionApplyRequest request) {
        Long userId = request.getUserId();
        List<PromotionApplyRequest.ItemInfo> items = request.getItems();
        List<String> userCouponNos = request.getUserCouponNos();

        // 1. 计算商品原价总额
        BigDecimal originalAmount = items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. 查询用户可用优惠券
        List<CouponUser> availableCouponUsers = couponUserRepository.findAvailableByUserId(userId);

        // 3. 构建可用优惠券列表
        List<PromotionApplyResult.AvailableCoupon> availableCoupons = new ArrayList<>();
        for (CouponUser cu : availableCouponUsers) {
            Coupon coupon = couponRepository.findById(cu.getCouponId()).orElse(null);
            if (coupon == null) continue;

            boolean eligible = originalAmount.compareTo(coupon.getThresholdAmount()) >= 0;
            String ineligibleReason = eligible ? null : 
                    String.format("订单金额%.2f未达到门槛%.2f", originalAmount, coupon.getThresholdAmount());

            availableCoupons.add(PromotionApplyResult.AvailableCoupon.builder()
                    .userCouponNo(cu.getUserCouponNo())
                    .couponId(coupon.getId())
                    .couponName(coupon.getName())
                    .couponType(coupon.getType())
                    .thresholdAmount(coupon.getThresholdAmount())
                    .discountAmount(coupon.getDiscountAmount())
                    .discountRate(coupon.getDiscountRate())
                    .maxDiscountAmount(coupon.getMaxDiscountAmount())
                    .eligible(eligible)
                    .ineligibleReason(ineligibleReason)
                    .build());
        }

        // 4. 计算优惠（如果指定了优惠券）
        BigDecimal totalDiscount = BigDecimal.ZERO;
        List<PromotionApplyResult.PromotionRuleHit> hitRules = new ArrayList<>();

        if (userCouponNos != null && !userCouponNos.isEmpty()) {
            for (String userCouponNo : userCouponNos) {
                CouponUser couponUser = couponUserRepository.findByUserCouponNo(userCouponNo).orElse(null);
                if (couponUser == null || !couponUser.getUserId().equals(userId)) {
                    continue;
                }
                if (!UserCouponStatus.AVAILABLE.getCode().equals(couponUser.getStatus())
                        && !UserCouponStatus.LOCKED.getCode().equals(couponUser.getStatus())) {
                    continue;
                }

                Coupon coupon = couponRepository.findById(couponUser.getCouponId()).orElse(null);
                if (coupon == null) continue;

                // 检查门槛
                if (originalAmount.compareTo(coupon.getThresholdAmount()) < 0) {
                    continue;
                }

                // 计算优惠金额
                BigDecimal discount = calculateDiscount(coupon, originalAmount);
                totalDiscount = totalDiscount.add(discount);

                hitRules.add(PromotionApplyResult.PromotionRuleHit.builder()
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

        return PromotionApplyResult.builder()
                .userId(userId)
                .originalAmount(originalAmount)
                .totalDiscount(totalDiscount)
                .payableAmount(payableAmount)
                .hitRules(hitRules)
                .availableCoupons(availableCoupons)
                .build();
    }

    /**
     * 查询用户可用优惠券
     */
    public List<UserCouponResponse> getUserAvailableCoupons(Long userId) {
        List<CouponUser> couponUsers = couponUserRepository.findAvailableByUserId(userId);
        return couponUsers.stream()
                .map(this::buildUserCouponResponse)
                .collect(Collectors.toList());
    }

    /**
     * 查询优惠券详情
     */
    public CouponResponse getCoupon(String couponCode) {
        Coupon coupon = couponRepository.findByCode(couponCode)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "优惠券不存在: " + couponCode));
        return buildCouponResponse(coupon);
    }

    /**
     * 计算优惠金额
     */
    private BigDecimal calculateDiscount(Coupon coupon, BigDecimal orderAmount) {
        CouponType type = CouponType.of(coupon.getType());
        BigDecimal discount;

        switch (type) {
            case FULL_REDUCTION:
            case FIXED_AMOUNT:
                discount = coupon.getDiscountAmount();
                break;
            case DISCOUNT:
                // 折扣券：原价 * (1 - 折扣率)
                discount = orderAmount.multiply(BigDecimal.ONE.subtract(coupon.getDiscountRate()))
                        .setScale(2, RoundingMode.HALF_UP);
                // 封顶
                if (coupon.getMaxDiscountAmount() != null 
                        && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                    discount = coupon.getMaxDiscountAmount();
                }
                break;
            default:
                discount = BigDecimal.ZERO;
        }

        // 优惠不能超过订单金额
        if (discount.compareTo(orderAmount) > 0) {
            discount = orderAmount;
        }

        return discount;
    }

    private String buildDiscountDescription(Coupon coupon, BigDecimal discount) {
        CouponType type = CouponType.of(coupon.getType());
        switch (type) {
            case FULL_REDUCTION:
                return String.format("满%.2f减%.2f", coupon.getThresholdAmount(), discount);
            case DISCOUNT:
                return String.format("%.1f折优惠%.2f", 
                        coupon.getDiscountRate().multiply(BigDecimal.TEN), discount);
            case FIXED_AMOUNT:
                return String.format("立减%.2f", discount);
            default:
                return "优惠" + discount;
        }
    }

    private String generateCouponCode() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String random = String.valueOf((int) ((Math.random() * 900000) + 100000));
        return "CPN" + timestamp + random;
    }

    private String generateUserCouponNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.valueOf((int) ((Math.random() * 9000) + 1000));
        return "UC" + timestamp + random;
    }

    private CouponResponse buildCouponResponse(Coupon coupon) {
        CouponType type = CouponType.of(coupon.getType());
        CouponStatus status = CouponStatus.of(coupon.getStatus());

        return CouponResponse.builder()
                .id(coupon.getId())
                .couponCode(coupon.getCouponCode())
                .name(coupon.getName())
                .type(coupon.getType())
                .typeDesc(type.getDesc())
                .status(coupon.getStatus())
                .statusDesc(status.getDesc())
                .thresholdAmount(coupon.getThresholdAmount())
                .discountAmount(coupon.getDiscountAmount())
                .discountRate(coupon.getDiscountRate())
                .maxDiscountAmount(coupon.getMaxDiscountAmount())
                .totalQuantity(coupon.getTotalQuantity())
                .issuedQuantity(coupon.getIssuedQuantity())
                .remainQuantity(coupon.getTotalQuantity() - coupon.getIssuedQuantity())
                .perUserLimit(coupon.getPerUserLimit())
                .applicableScope(coupon.getApplicableScope())
                .applicableItems(coupon.getApplicableItems())
                .validStartTime(coupon.getValidStartTime())
                .validEndTime(coupon.getValidEndTime())
                .validDays(coupon.getValidDays())
                .remark(coupon.getRemark())
                .createdAt(coupon.getCreatedAt())
                .build();
    }

    private UserCouponResponse buildUserCouponResponse(CouponUser couponUser) {
        Coupon coupon = couponRepository.findById(couponUser.getCouponId()).orElse(null);
        return buildUserCouponResponse(couponUser, coupon);
    }

    private UserCouponResponse buildUserCouponResponse(CouponUser couponUser, Coupon coupon) {
        UserCouponStatus status = UserCouponStatus.of(couponUser.getStatus());

        UserCouponResponse.UserCouponResponseBuilder builder = UserCouponResponse.builder()
                .id(couponUser.getId())
                .userCouponNo(couponUser.getUserCouponNo())
                .userId(couponUser.getUserId())
                .couponId(couponUser.getCouponId())
                .couponCode(couponUser.getCouponCode())
                .status(couponUser.getStatus())
                .statusDesc(status.getDesc())
                .receiveTime(couponUser.getReceiveTime())
                .validStartTime(couponUser.getValidStartTime())
                .validEndTime(couponUser.getValidEndTime())
                .usedTime(couponUser.getUsedTime())
                .usedOrderNo(couponUser.getUsedOrderNo())
                .actualDiscountAmount(couponUser.getDiscountAmount());

        if (coupon != null) {
            builder.couponName(coupon.getName())
                    .couponType(coupon.getType())
                    .thresholdAmount(coupon.getThresholdAmount())
                    .discountAmount(coupon.getDiscountAmount())
                    .discountRate(coupon.getDiscountRate())
                    .maxDiscountAmount(coupon.getMaxDiscountAmount());
        }

        return builder.build();
    }
}
