package com.yuge.payment.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 回调处理结果枚举
 */
@Getter
@AllArgsConstructor
public enum CallbackProcessResult {

    /**
     * 已处理（状态已更新）
     */
    PROCESSED("PROCESSED", "已处理"),

    /**
     * 已忽略（重复回调或状态不允许）
     */
    IGNORED("IGNORED", "已忽略"),

    /**
     * 处理失败
     */
    FAILED("FAILED", "处理失败");

    private final String code;
    private final String desc;
}
