package com.yuge.inventory.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存预留状态枚举
 */
@Getter
@AllArgsConstructor
public enum ReservationStatus {

    /**
     * 已预留 - 库存已从可用转为预留
     */
    RESERVED("RESERVED", "已预留"),

    /**
     * 已确认 - 订单支付成功，预留库存已确认扣减
     */
    CONFIRMED("CONFIRMED", "已确认"),

    /**
     * 已释放 - 订单取消或超时，预留库存已释放回可用
     */
    RELEASED("RELEASED", "已释放");

    private final String code;
    private final String desc;

    /**
     * 根据code获取枚举
     */
    public static ReservationStatus of(String code) {
        if (code == null) {
            return null;
        }
        for (ReservationStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ReservationStatus code: " + code);
    }

    /**
     * 检查是否可以转换到目标状态
     */
    public boolean canTransitionTo(ReservationStatus target) {
        if (this == target) {
            return true; // 幂等
        }
        return switch (this) {
            case RESERVED -> target == CONFIRMED || target == RELEASED;
            case CONFIRMED, RELEASED -> false; // 终态不可变
        };
    }
}
