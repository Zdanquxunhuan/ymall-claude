package com.yuge.payment.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 退款状态枚举
 */
@Getter
@AllArgsConstructor
public enum RefundStatus {

    /**
     * 初始状态
     */
    INIT("INIT", "待退款"),

    /**
     * 退款中
     */
    REFUNDING("REFUNDING", "退款中"),

    /**
     * 退款成功
     */
    SUCCESS("SUCCESS", "退款成功"),

    /**
     * 退款失败
     */
    FAILED("FAILED", "退款失败");

    private final String code;
    private final String desc;

    /**
     * 根据code获取枚举
     */
    public static RefundStatus of(String code) {
        for (RefundStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown refund status: " + code);
    }

    /**
     * 是否可以接收回调
     */
    public boolean canReceiveCallback() {
        return this == INIT || this == REFUNDING;
    }

    /**
     * 是否为终态
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }
}
