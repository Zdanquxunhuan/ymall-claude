package com.yuge.aftersales.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 售后类型枚举
 */
@Getter
@AllArgsConstructor
public enum AfterSaleType {

    /**
     * 仅退款
     */
    REFUND("REFUND", "仅退款"),

    /**
     * 退货退款
     */
    RETURN_REFUND("RETURN_REFUND", "退货退款");

    private final String code;
    private final String desc;

    /**
     * 根据code获取枚举
     */
    public static AfterSaleType of(String code) {
        for (AfterSaleType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown after sale type: " + code);
    }
}
