package com.yuge.product.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SPU状态枚举
 */
@Getter
@AllArgsConstructor
public enum SpuStatus {

    DRAFT("DRAFT", "草稿"),
    PENDING("PENDING", "待审核"),
    PUBLISHED("PUBLISHED", "已发布"),
    OFFLINE("OFFLINE", "已下架");

    private final String code;
    private final String desc;

    public static SpuStatus fromCode(String code) {
        for (SpuStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown SpuStatus code: " + code);
    }
}
