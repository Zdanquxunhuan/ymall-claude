package com.yuge.product.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 聚合类型枚举
 */
@Getter
@AllArgsConstructor
public enum AggregateType {

    SPU("SPU", "SPU"),
    SKU("SKU", "SKU");

    private final String code;
    private final String desc;

    public static AggregateType fromCode(String code) {
        for (AggregateType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown AggregateType code: " + code);
    }
}
