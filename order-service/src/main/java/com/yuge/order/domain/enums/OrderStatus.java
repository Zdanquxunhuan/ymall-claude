package com.yuge.order.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 订单状态枚举
 * V2版本：CREATED -> STOCK_RESERVED/STOCK_FAILED -> ...
 */
@Getter
@AllArgsConstructor
public enum OrderStatus {

    /**
     * 已创建（初始状态）
     */
    CREATED("CREATED", "已创建"),

    /**
     * 库存已预留
     */
    STOCK_RESERVED("STOCK_RESERVED", "库存已预留"),

    /**
     * 库存预留失败
     */
    STOCK_FAILED("STOCK_FAILED", "库存预留失败"),

    /**
     * 已支付
     */
    PAID("PAID", "已支付"),

    /**
     * 已发货
     */
    SHIPPED("SHIPPED", "已发货"),

    /**
     * 已签收/已完成
     */
    DELIVERED("DELIVERED", "已签收"),

    /**
     * 已取消
     */
    CANCELED("CANCELED", "已取消"),

    /**
     * 已退款（全额退款）
     */
    REFUNDED("REFUNDED", "已退款"),

    /**
     * 部分退款
     */
    PARTIAL_REFUNDED("PARTIAL_REFUNDED", "部分退款");

    private final String code;
    private final String desc;

    /**
     * 根据code获取枚举
     */
    public static OrderStatus of(String code) {
        for (OrderStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown order status: " + code);
    }

    /**
     * 获取可以取消的状态列表
     */
    public static List<OrderStatus> getCancelableStatuses() {
        return Arrays.asList(CREATED, STOCK_RESERVED);
    }

    /**
     * 判断当前状态是否可以取消
     */
    public boolean canCancel() {
        return getCancelableStatuses().contains(this);
    }

    /**
     * 判断是否为终态
     */
    public boolean isTerminal() {
        return this == CANCELED || this == STOCK_FAILED || this == DELIVERED || this == REFUNDED;
    }
}
