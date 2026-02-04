package com.yuge.order.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Outbox事件状态枚举
 * 
 * 状态流转:
 * NEW -> SENT (发送成功)
 * NEW -> RETRY (发送失败，等待重试)
 * RETRY -> SENT (重试成功)
 * RETRY -> RETRY (重试失败，继续等待)
 * RETRY -> DEAD (超过最大重试次数)
 */
@Getter
@AllArgsConstructor
public enum OutboxStatus {

    /**
     * 新建，待发送
     */
    NEW("NEW", "待发送"),

    /**
     * 重试中
     */
    RETRY("RETRY", "重试中"),

    /**
     * 已发送成功
     */
    SENT("SENT", "已发送"),

    /**
     * 死信，超过最大重试次数
     */
    DEAD("DEAD", "死信");

    private final String code;
    private final String desc;

    public static OutboxStatus of(String code) {
        for (OutboxStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown outbox status: " + code);
    }
    
    /**
     * 是否可以被Relay Worker处理
     */
    public boolean isProcessable() {
        return this == NEW || this == RETRY;
    }
}
