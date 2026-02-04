package com.yuge.payment.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付状态枚举
 */
@Getter
@AllArgsConstructor
public enum PayStatus {

    /**
     * 初始化（支付单已创建）
     */
    INIT("INIT", "初始化"),

    /**
     * 支付中（已调用支付渠道）
     */
    PAYING("PAYING", "支付中"),

    /**
     * 支付成功
     */
    SUCCESS("SUCCESS", "支付成功"),

    /**
     * 支付失败
     */
    FAILED("FAILED", "支付失败"),

    /**
     * 已关闭（超时关闭或手动关闭）
     */
    CLOSED("CLOSED", "已关闭");

    private final String code;
    private final String desc;

    public static PayStatus of(String code) {
        for (PayStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown pay status: " + code);
    }

    /**
     * 判断是否为终态
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CLOSED;
    }

    /**
     * 判断是否可以接收回调
     */
    public boolean canReceiveCallback() {
        return this == INIT || this == PAYING;
    }
}
