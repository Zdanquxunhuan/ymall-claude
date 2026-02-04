package com.yuge.promotion.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户优惠券状态
 */
@Getter
@AllArgsConstructor
public enum UserCouponStatus {

    AVAILABLE("AVAILABLE", "可用"),
    USED("USED", "已使用"),
    EXPIRED("EXPIRED", "已过期"),
    LOCKED("LOCKED", "已锁定");

    private final String code;
    private final String desc;

    public static UserCouponStatus of(String code) {
        for (UserCouponStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown UserCouponStatus: " + code);
    }
}
