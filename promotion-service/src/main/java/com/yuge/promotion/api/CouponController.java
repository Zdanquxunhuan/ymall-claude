package com.yuge.promotion.api;

import com.yuge.platform.infra.common.Result;
import com.yuge.promotion.api.dto.*;
import com.yuge.promotion.application.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 优惠券控制器
 */
@RestController
@RequestMapping("/promotion")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /**
     * 创建优惠券活动
     */
    @PostMapping("/coupon/create")
    public Result<CouponResponse> createCoupon(@Valid @RequestBody CreateCouponRequest request) {
        return Result.success(couponService.createCoupon(request));
    }

    /**
     * 激活优惠券
     */
    @PostMapping("/coupon/{couponCode}/activate")
    public Result<CouponResponse> activateCoupon(@PathVariable String couponCode) {
        return Result.success(couponService.activateCoupon(couponCode));
    }

    /**
     * 查询优惠券详情
     */
    @GetMapping("/coupon/{couponCode}")
    public Result<CouponResponse> getCoupon(@PathVariable String couponCode) {
        return Result.success(couponService.getCoupon(couponCode));
    }

    /**
     * 领取优惠券
     */
    @PostMapping("/coupon/receive")
    public Result<UserCouponResponse> receiveCoupon(@Valid @RequestBody ReceiveCouponRequest request) {
        return Result.success(couponService.receiveCoupon(request));
    }

    /**
     * 核销优惠券
     */
    @PostMapping("/coupon/redeem")
    public Result<UserCouponResponse> redeemCoupon(@Valid @RequestBody RedeemCouponRequest request) {
        return Result.success(couponService.redeemCoupon(request));
    }

    /**
     * 查询用户可用优惠券
     */
    @GetMapping("/user/{userId}/coupons")
    public Result<List<UserCouponResponse>> getUserCoupons(@PathVariable Long userId) {
        return Result.success(couponService.getUserAvailableCoupons(userId));
    }

    /**
     * 促销试算
     */
    @PostMapping("/apply")
    public Result<PromotionApplyResult> applyPromotion(@Valid @RequestBody PromotionApplyRequest request) {
        return Result.success(couponService.applyPromotion(request));
    }
}
