package com.yuge.promotion.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 优惠券类型
 */
@Getter
@AllArgsConstructor
public enum CouponType {

    FULL_REDUCTION("FULL_REDUCTION", "满减券"),
    DISCOUNT("DISCOUNT", "折扣券"),
    FIXED_AMOUNT("FIXED_AMOUNT", "固定金额券");

    private final String code;
    private final String desc;

    public static CouponType of(String code) {
        for (CouponType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown CouponType: " + code);
    }
}
