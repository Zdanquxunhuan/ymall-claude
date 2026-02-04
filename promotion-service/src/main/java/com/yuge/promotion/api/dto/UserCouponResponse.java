package com.yuge.promotion.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户优惠券响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCouponResponse {

    private Long id;
    private String userCouponNo;
    private Long userId;
    private Long couponId;
    private String couponCode;
    private String couponName;
    private String couponType;
    private String status;
    private String statusDesc;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private BigDecimal discountRate;
    private BigDecimal maxDiscountAmount;
    private LocalDateTime receiveTime;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private LocalDateTime usedTime;
    private String usedOrderNo;
    private BigDecimal actualDiscountAmount;
}
