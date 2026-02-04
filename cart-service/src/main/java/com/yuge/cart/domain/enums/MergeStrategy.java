package com.yuge.cart.domain.enums;

import lombok.Getter;

/**
 * 购物车合并策略
 */
@Getter
public enum MergeStrategy {

    /**
     * 数量累加：同SKU时，数量相加
     */
    QTY_ADD("QTY_ADD", "数量累加"),

    /**
     * 以最新为准：同SKU时，以最后更新时间较新的为准
     */
    LATEST_WIN("LATEST_WIN", "以最新为准");

    private final String code;
    private final String desc;

    MergeStrategy(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static MergeStrategy of(String code) {
        for (MergeStrategy strategy : values()) {
            if (strategy.getCode().equals(code)) {
                return strategy;
            }
        }
        return QTY_ADD; // 默认数量累加
    }
}
