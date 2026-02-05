package com.yuge.aftersales.domain.statemachine;

import com.yuge.aftersales.domain.enums.AfterSaleStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.*;

/**
 * 售后状态机
 * 
 * 状态流转：
 * APPLIED -> APPROVED (审批通过)
 * APPLIED -> REJECTED (审批拒绝)
 * APPLIED -> CANCELED (用户取消)
 * APPROVED -> REFUNDING (发起退款)
 * REFUNDING -> REFUNDED (退款成功)
 * REFUNDING -> APPROVED (退款失败，可重试)
 */
public class AfterSaleStateMachine {

    /**
     * 状态转换事件
     */
    @Getter
    @AllArgsConstructor
    public enum Event {
        APPROVE("APPROVE", "审批通过"),
        REJECT("REJECT", "审批拒绝"),
        CANCEL("CANCEL", "用户取消"),
        START_REFUND("START_REFUND", "发起退款"),
        REFUND_SUCCESS("REFUND_SUCCESS", "退款成功"),
        REFUND_FAILED("REFUND_FAILED", "退款失败");

        private final String code;
        private final String desc;
    }

    /**
     * 状态转换定义
     */
    private static final Map<AfterSaleStatus, Map<Event, AfterSaleStatus>> TRANSITIONS = new HashMap<>();

    static {
        // APPLIED状态的转换
        Map<Event, AfterSaleStatus> appliedTransitions = new HashMap<>();
        appliedTransitions.put(Event.APPROVE, AfterSaleStatus.APPROVED);
        appliedTransitions.put(Event.REJECT, AfterSaleStatus.REJECTED);
        appliedTransitions.put(Event.CANCEL, AfterSaleStatus.CANCELED);
        TRANSITIONS.put(AfterSaleStatus.APPLIED, appliedTransitions);

        // APPROVED状态的转换
        Map<Event, AfterSaleStatus> approvedTransitions = new HashMap<>();
        approvedTransitions.put(Event.START_REFUND, AfterSaleStatus.REFUNDING);
        TRANSITIONS.put(AfterSaleStatus.APPROVED, approvedTransitions);

        // REFUNDING状态的转换
        Map<Event, AfterSaleStatus> refundingTransitions = new HashMap<>();
        refundingTransitions.put(Event.REFUND_SUCCESS, AfterSaleStatus.REFUNDED);
        refundingTransitions.put(Event.REFUND_FAILED, AfterSaleStatus.APPROVED); // 退款失败回到APPROVED可重试
        TRANSITIONS.put(AfterSaleStatus.REFUNDING, refundingTransitions);
    }

    /**
     * 检查状态转换是否合法
     *
     * @param currentStatus 当前状态
     * @param event         触发事件
     * @return 是否合法
     */
    public static boolean canTransition(AfterSaleStatus currentStatus, Event event) {
        Map<Event, AfterSaleStatus> transitions = TRANSITIONS.get(currentStatus);
        return transitions != null && transitions.containsKey(event);
    }

    /**
     * 获取目标状态
     *
     * @param currentStatus 当前状态
     * @param event         触发事件
     * @return 目标状态，如果转换不合法返回null
     */
    public static AfterSaleStatus getNextStatus(AfterSaleStatus currentStatus, Event event) {
        Map<Event, AfterSaleStatus> transitions = TRANSITIONS.get(currentStatus);
        if (transitions == null) {
            return null;
        }
        return transitions.get(event);
    }

    /**
     * 执行状态转换
     *
     * @param currentStatus 当前状态
     * @param event         触发事件
     * @return 目标状态
     * @throws IllegalStateException 如果转换不合法
     */
    public static AfterSaleStatus transition(AfterSaleStatus currentStatus, Event event) {
        AfterSaleStatus nextStatus = getNextStatus(currentStatus, event);
        if (nextStatus == null) {
            throw new IllegalStateException(
                    String.format("Invalid state transition: %s + %s", currentStatus, event));
        }
        return nextStatus;
    }

    /**
     * 获取当前状态可用的事件列表
     *
     * @param currentStatus 当前状态
     * @return 可用事件列表
     */
    public static List<Event> getAvailableEvents(AfterSaleStatus currentStatus) {
        Map<Event, AfterSaleStatus> transitions = TRANSITIONS.get(currentStatus);
        if (transitions == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(transitions.keySet());
    }
}
