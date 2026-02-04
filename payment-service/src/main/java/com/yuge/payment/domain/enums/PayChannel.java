package com.yuge.payment.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付渠道枚举
 */
@Getter
@AllArgsConstructor
public enum PayChannel {

    /**
     * 模拟支付（测试用）
     */
    MOCK("MOCK", "模拟支付"),

    /**
     * 支付宝
     */
    ALIPAY("ALIPAY", "支付宝"),

    /**
     * 微信支付
     */
    WECHAT("WECHAT", "微信支付");

    private final String code;
    private final String desc;

    public static PayChannel of(String code) {
        for (PayChannel channel : values()) {
            if (channel.getCode().equals(code)) {
                return channel;
            }
        }
        throw new IllegalArgumentException("Unknown pay channel: " + code);
    }
}
