package com.yuge.promotion.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 核销优惠券请求
 */
@Data
public class RedeemCouponRequest {

    /**
     * 用户优惠券编号
     */
    @NotBlank(message = "用户优惠券编号不能为空")
    private String userCouponNo;

    /**
     * 订单号
     */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /**
     * 实际优惠金额
     */
    @NotNull(message = "优惠金额不能为空")
    private BigDecimal discountAmount;

    /**
     * 请求ID（幂等键）
     */
    @NotBlank(message = "请求ID不能为空")
    private String requestId;
}
