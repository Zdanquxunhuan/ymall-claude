package com.yuge.promotion.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponResponse {

    private Long id;
    private String couponCode;
    private String name;
    private String type;
    private String typeDesc;
    private String status;
    private String statusDesc;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private BigDecimal discountRate;
    private BigDecimal maxDiscountAmount;
    private Integer totalQuantity;
    private Integer issuedQuantity;
    private Integer remainQuantity;
    private Integer perUserLimit;
    private String applicableScope;
    private String applicableItems;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private Integer validDays;
    private String remark;
    private LocalDateTime createdAt;
}
