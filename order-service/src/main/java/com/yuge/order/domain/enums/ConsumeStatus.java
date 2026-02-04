package com.yuge.order.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * MQ消费状态枚举
 */
@Getter
@AllArgsConstructor
public enum ConsumeStatus {

    /**
     * 处理中
     */
    PROCESSING("PROCESSING", "处理中"),

    /**
     * 消费成功
     */
    SUCCESS("SUCCESS", "消费成功"),

    /**
     * 消费失败
     */
    FAILED("FAILED", "消费失败"),

    /**
     * 已忽略（乱序消息）
     */
    IGNORED("IGNORED", "已忽略");

    private final String code;
    private final String desc;

    public static ConsumeStatus of(String code) {
        for (ConsumeStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown consume status: " + code);
    }
}
