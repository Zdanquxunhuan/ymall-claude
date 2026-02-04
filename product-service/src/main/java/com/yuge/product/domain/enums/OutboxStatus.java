package com.yuge.product.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Outbox状态枚举
 */
@Getter
@AllArgsConstructor
public enum OutboxStatus {

    PENDING("PENDING", "待发送"),
    SENT("SENT", "已发送"),
    FAILED("FAILED", "发送失败");

    private final String code;
    private final String desc;

    public static OutboxStatus fromCode(String code) {
        for (OutboxStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown OutboxStatus code: " + code);
    }
}
