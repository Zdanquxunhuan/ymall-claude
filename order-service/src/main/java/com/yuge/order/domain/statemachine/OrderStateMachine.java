package com.yuge.order.domain.statemachine;

import com.yuge.order.domain.enums.OrderEvent;
import com.yuge.order.domain.enums.OrderStatus;
import com.yuge.platform.infra.common.ErrorCode;
import com.yuge.platform.infra.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 订单状态机
 * 定义状态转换规则，校验状态跃迁合法性
 */
@Slf4j
@Component
public class OrderStateMachine {

    /**
     * 状态转换表
     * Map<当前状态, Map<事件, 目标状态>>
     */
    private static final Map<OrderStatus, Map<OrderEvent, OrderStatus>> TRANSITIONS = new HashMap<>();

    static {
        // CREATED 状态可以执行的事件
        Map<OrderEvent, OrderStatus> createdTransitions = new HashMap<>();
        createdTransitions.put(OrderEvent.STOCK_RESERVED, OrderStatus.STOCK_RESERVED);
        createdTransitions.put(OrderEvent.STOCK_RESERVE_FAILED, OrderStatus.STOCK_FAILED);
        createdTransitions.put(OrderEvent.CANCEL, OrderStatus.CANCELED);
        TRANSITIONS.put(OrderStatus.CREATED, createdTransitions);

        // STOCK_RESERVED 状态可以执行的事件
        Map<OrderEvent, OrderStatus> stockReservedTransitions = new HashMap<>();
        stockReservedTransitions.put(OrderEvent.CANCEL, OrderStatus.CANCELED);
        stockReservedTransitions.put(OrderEvent.PAYMENT_SUCCESS, OrderStatus.PAID);
        TRANSITIONS.put(OrderStatus.STOCK_RESERVED, stockReservedTransitions);

        // PAID 状态可以执行发货事件
        Map<OrderEvent, OrderStatus> paidTransitions = new HashMap<>();
        paidTransitions.put(OrderEvent.SHIP, OrderStatus.SHIPPED);
        TRANSITIONS.put(OrderStatus.PAID, paidTransitions);

        // SHIPPED 状态可以执行签收事件
        Map<OrderEvent, OrderStatus> shippedTransitions = new HashMap<>();
        shippedTransitions.put(OrderEvent.DELIVER, OrderStatus.DELIVERED);
        TRANSITIONS.put(OrderStatus.SHIPPED, shippedTransitions);

        // DELIVERED 状态是终态，不能执行任何事件
        TRANSITIONS.put(OrderStatus.DELIVERED, Collections.emptyMap());

        // STOCK_FAILED 状态是终态，不能执行任何事件
        TRANSITIONS.put(OrderStatus.STOCK_FAILED, Collections.emptyMap());

        // CANCELED 状态是终态，不能执行任何事件
        TRANSITIONS.put(OrderStatus.CANCELED, Collections.emptyMap());
    }

    /**
     * 检查状态转换是否合法
     *
     * @param currentStatus 当前状态
     * @param event         触发事件
     * @return 目标状态
     * @throws BizException 如果状态转换不合法
     */
    public OrderStatus transition(OrderStatus currentStatus, OrderEvent event) {
        Map<OrderEvent, OrderStatus> allowedTransitions = TRANSITIONS.get(currentStatus);
        
        if (allowedTransitions == null || !allowedTransitions.containsKey(event)) {
            log.warn("[StateMachine] Invalid transition: {} + {} -> ?", currentStatus, event);
            throw new BizException(ErrorCode.STATE_INVALID, 
                    String.format("订单状态[%s]不允许执行[%s]操作", currentStatus.getDesc(), event.getDesc()));
        }

        OrderStatus targetStatus = allowedTransitions.get(event);
        log.info("[StateMachine] Transition: {} + {} -> {}", currentStatus, event, targetStatus);
        return targetStatus;
    }

    /**
     * 检查是否可以执行某个事件
     */
    public boolean canTransition(OrderStatus currentStatus, OrderEvent event) {
        Map<OrderEvent, OrderStatus> allowedTransitions = TRANSITIONS.get(currentStatus);
        return allowedTransitions != null && allowedTransitions.containsKey(event);
    }

    /**
     * 获取当前状态可执行的所有事件
     */
    public Set<OrderEvent> getAvailableEvents(OrderStatus currentStatus) {
        Map<OrderEvent, OrderStatus> allowedTransitions = TRANSITIONS.get(currentStatus);
        return allowedTransitions != null ? allowedTransitions.keySet() : Collections.emptySet();
    }
}
