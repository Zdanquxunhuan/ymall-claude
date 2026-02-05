package com.yuge.inventory.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存流水原因枚举
 */
@Getter
@AllArgsConstructor
public enum TxnReason {

    /**
     * 预留 - 订单创建时预留库存
     */
    RESERVE("RESERVE", "预留"),

    /**
     * 确认 - 订单支付成功确认扣减
     */
    CONFIRM("CONFIRM", "确认"),

    /**
     * 释放 - 订单取消或超时释放预留
     */
    RELEASE("RELEASE", "释放"),

    /**
     * 调整 - 手动调整库存
     */
    ADJUST("ADJUST", "调整"),

    /**
     * 入库 - 采购入库
     */
    INBOUND("INBOUND", "入库"),

    /**
     * 出库 - 发货出库
     */
    OUTBOUND("OUTBOUND", "出库"),

    /**
     * 退款回补 - 售后退款后回补库存
     */
    REFUND_RESTORE("REFUND_RESTORE", "退款回补");

    private final String code;
    private final String desc;

    /**
     * 根据code获取枚举
     */
    public static TxnReason of(String code) {
        if (code == null) {
            return null;
        }
        for (TxnReason reason : values()) {
            if (reason.getCode().equals(code)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Unknown TxnReason code: " + code);
    }
}
