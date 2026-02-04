package com.yuge.inventory.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存预留失败错误码
 */
@Getter
@AllArgsConstructor
public enum StockErrorCode {

    /**
     * 库存不足
     */
    INSUFFICIENT_STOCK("INSUFFICIENT_STOCK", "库存不足"),

    /**
     * SKU不存在
     */
    SKU_NOT_FOUND("SKU_NOT_FOUND", "SKU不存在"),

    /**
     * 仓库不存在
     */
    WAREHOUSE_NOT_FOUND("WAREHOUSE_NOT_FOUND", "仓库不存在"),

    /**
     * 库存记录不存在
     */
    INVENTORY_NOT_FOUND("INVENTORY_NOT_FOUND", "库存记录不存在"),

    /**
     * 预留记录不存在
     */
    RESERVATION_NOT_FOUND("RESERVATION_NOT_FOUND", "预留记录不存在"),

    /**
     * 预留状态不正确
     */
    INVALID_RESERVATION_STATUS("INVALID_RESERVATION_STATUS", "预留状态不正确"),

    /**
     * 并发冲突
     */
    CONCURRENT_CONFLICT("CONCURRENT_CONFLICT", "并发冲突，请重试"),

    /**
     * 系统错误
     */
    SYSTEM_ERROR("SYSTEM_ERROR", "系统错误");

    private final String code;
    private final String message;

    /**
     * 根据code获取枚举
     */
    public static StockErrorCode of(String code) {
        if (code == null) {
            return null;
        }
        for (StockErrorCode errorCode : values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return SYSTEM_ERROR;
    }
}
