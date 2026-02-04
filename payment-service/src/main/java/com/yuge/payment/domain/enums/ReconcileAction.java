package com.yuge.payment.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 对账操作类型枚举
 */
@Getter
@AllArgsConstructor
public enum ReconcileAction {

    /**
     * 主动查单
     */
    QUERY("QUERY", "主动查单"),

    /**
     * 关闭支付单
     */
    CLOSE("CLOSE", "关闭支付单"),

    /**
     * 补发通知
     */
    NOTIFY("NOTIFY", "补发通知");

    private final String code;
    private final String desc;
}
