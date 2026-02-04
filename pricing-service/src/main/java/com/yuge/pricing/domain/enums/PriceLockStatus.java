package com.yuge.pricing.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 价格锁状态
 */
@Getter
@AllArgsConstructor
public enum PriceLockStatus {

    LOCKED("LOCKED", "已锁定"),
    USED("USED", "已使用"),
    EXPIRED("EXPIRED", "已过期"),
    CANCELED("CANCELED", "已取消");

    private final String code;
    private final String desc;

    public static PriceLockStatus of(String code) {
        for (PriceLockStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown PriceLockStatus: " + code);
    }
}
