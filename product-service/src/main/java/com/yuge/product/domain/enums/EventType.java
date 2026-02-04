package com.yuge.product.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 事件类型枚举
 */
@Getter
@AllArgsConstructor
public enum EventType {

    PRODUCT_PUBLISHED("PRODUCT_PUBLISHED", "商品发布"),
    PRODUCT_UPDATED("PRODUCT_UPDATED", "商品更新"),
    PRODUCT_OFFLINE("PRODUCT_OFFLINE", "商品下架");

    private final String code;
    private final String desc;

    public static EventType fromCode(String code) {
        for (EventType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown EventType code: " + code);
    }
}
