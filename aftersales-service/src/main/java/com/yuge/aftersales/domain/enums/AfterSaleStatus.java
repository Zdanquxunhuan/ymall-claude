package com.yuge.aftersales.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 售后状态枚举
 */
@Getter
@AllArgsConstructor
public enum AfterSaleStatus {

    /**
     * 已申请（初始状态）
     */
    APPLIED("APPLIED", "已申请"),

    /**
     * 已审批（待退款）
     */
    APPROVED("APPROVED", "已审批"),

    /**
     * 退款中
     */
    REFUNDING("REFUNDING", "退款中"),

    /**
     * 已退款（终态）
     */
    REFUNDED("REFUNDED", "已退款"),

    /**
     * 已拒绝（终态）
     */
    REJECTED("REJECTED", "已拒绝"),

    /**
     * 已取消（终态）
     */
    CANCELED("CANCELED", "已取消");

    private final String code;
    private final String desc;

    /**
     * 根据code获取枚举
     */
    public static AfterSaleStatus of(String code) {
        for (AfterSaleStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown after sale status: " + code);
    }

    /**
     * 获取可以取消的状态列表
     */
    public static List<AfterSaleStatus> getCancelableStatuses() {
        return Arrays.asList(APPLIED);
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
        return this == REFUNDED || this == REJECTED || this == CANCELED;
    }

    /**
     * 判断是否可以审批
     */
    public boolean canApprove() {
        return this == APPLIED;
    }

    /**
     * 判断是否可以拒绝
     */
    public boolean canReject() {
        return this == APPLIED;
    }
}
