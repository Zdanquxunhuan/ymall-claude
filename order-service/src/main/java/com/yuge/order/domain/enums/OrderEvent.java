package com.yuge.order.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单事件枚举
 */
@Getter
@AllArgsConstructor
public enum OrderEvent {

    /**
     * 创建订单
     */
    CREATE("CREATE", "创建订单"),

    /**
     * 库存预留成功
     */
    STOCK_RESERVED("STOCK_RESERVED", "库存预留成功"),

    /**
     * 库存预留失败
     */
    STOCK_RESERVE_FAILED("STOCK_RESERVE_FAILED", "库存预留失败"),

    /**
     * 取消订单
     */
    CANCEL("CANCEL", "取消订单"),

    /**
     * 支付成功
     */
    PAYMENT_SUCCESS("PAYMENT_SUCCESS", "支付成功"),

    /**
     * 发货
     */
    SHIP("SHIP", "发货"),

    /**
     * 签收
     */
    DELIVER("DELIVER", "签收");

    private final String code;
    private final String desc;

    public static OrderEvent of(String code) {
        for (OrderEvent event : values()) {
            if (event.getCode().equals(code)) {
                return event;
            }
        }
        throw new IllegalArgumentException("Unknown order event: " + code);
    }
}
