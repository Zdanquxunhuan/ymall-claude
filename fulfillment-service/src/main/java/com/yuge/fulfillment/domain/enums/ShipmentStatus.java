package com.yuge.fulfillment.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发货单状态枚举
 */
@Getter
@AllArgsConstructor
public enum ShipmentStatus {

    /**
     * 已创建
     */
    CREATED("CREATED", "已创建"),

    /**
     * 已发货
     */
    SHIPPED("SHIPPED", "已发货"),

    /**
     * 已签收
     */
    DELIVERED("DELIVERED", "已签收");

    private final String code;
    private final String desc;

    /**
     * 根据code获取枚举
     */
    public static ShipmentStatus of(String code) {
        for (ShipmentStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown shipment status: " + code);
    }

    /**
     * 判断是否为终态
     */
    public boolean isTerminal() {
        return this == DELIVERED;
    }

    /**
     * 判断是否可以发货
     */
    public boolean canShip() {
        return this == CREATED;
    }

    /**
     * 判断是否可以签收
     */
    public boolean canDeliver() {
        return this == SHIPPED;
    }
}
