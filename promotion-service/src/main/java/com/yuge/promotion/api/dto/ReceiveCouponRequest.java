package com.yuge.promotion.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 领取优惠券请求
 */
@Data
public class ReceiveCouponRequest {

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 优惠券编码
     */
    @NotBlank(message = "优惠券编码不能为空")
    private String couponCode;

    /**
     * 领取请求ID（幂等键）
     */
    @NotBlank(message = "请求ID不能为空")
    private String requestId;
}
