package com.yuge.product.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * MQ消费状态枚举
 */
@Getter
@AllArgsConstructor
public enum ConsumeStatus {

    PROCESSING("PROCESSING", "处理中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败");

    private final String code;
    private final String desc;

    public static ConsumeStatus fromCode(String code) {
        for (ConsumeStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ConsumeStatus code: " + code);
    }
}
