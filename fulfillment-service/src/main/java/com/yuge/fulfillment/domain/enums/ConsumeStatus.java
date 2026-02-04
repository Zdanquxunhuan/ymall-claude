package com.yuge.fulfillment.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消费状态枚举
 */
@Getter
@AllArgsConstructor
public enum ConsumeStatus {

    /**
     * 处理中
     */
    PROCESSING("PROCESSING", "处理中"),

    /**
     * 成功
     */
    SUCCESS("SUCCESS", "成功"),

    /**
     * 失败
     */
    FAILED("FAILED", "失败"),

    /**
     * 已忽略（乱序/重复）
     */
    IGNORED("IGNORED", "已忽略");

    private final String code;
    private final String desc;

    /**
     * 根据code获取枚举
     */
    public static ConsumeStatus of(String code) {
        for (ConsumeStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown consume status: " + code);
    }
}
