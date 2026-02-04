package com.yuge.product.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SKU状态枚举
 */
@Getter
@AllArgsConstructor
public enum SkuStatus {

    DRAFT("DRAFT", "草稿"),
    PENDING("PENDING", "待审核"),
    PUBLISHED("PUBLISHED", "已发布"),
    OFFLINE("OFFLINE", "已下架");

    private final String code;
    private final String desc;

    public static SkuStatus fromCode(String code) {
        for (SkuStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown SkuStatus code: " + code);
    }

    /**
     * 是否可以发布
     */
    public boolean canPublish() {
        return this == DRAFT || this == OFFLINE;
    }

    /**
     * 是否可以下架
     */
    public boolean canOffline() {
        return this == PUBLISHED;
    }
}
