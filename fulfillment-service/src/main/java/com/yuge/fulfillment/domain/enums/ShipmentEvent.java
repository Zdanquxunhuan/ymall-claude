package com.yuge.fulfillment.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发货单事件枚举
 */
@Getter
@AllArgsConstructor
public enum ShipmentEvent {

    /**
     * 创建发货单
     */
    CREATE("CREATE", "创建发货单"),

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

    /**
     * 根据code获取枚举
     */
    public static ShipmentEvent of(String code) {
        for (ShipmentEvent event : values()) {
            if (event.getCode().equals(code)) {
                return event;
            }
        }
        throw new IllegalArgumentException("Unknown shipment event: " + code);
    }
}
