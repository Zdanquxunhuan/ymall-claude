package com.yuge.promotion.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 优惠券状态
 */
@Getter
@AllArgsConstructor
public enum CouponStatus {

    DRAFT("DRAFT", "草稿"),
    ACTIVE("ACTIVE", "生效中"),
    PAUSED("PAUSED", "已暂停"),
    EXPIRED("EXPIRED", "已过期"),
    EXHAUSTED("EXHAUSTED", "已发完");

    private final String code;
    private final String desc;

    public static CouponStatus of(String code) {
        for (CouponStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown CouponStatus: " + code);
    }
}
