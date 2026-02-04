package com.yuge.order.domain.statemachine;

import com.yuge.order.domain.enums.OrderEvent;
import com.yuge.order.domain.enums.OrderStatus;
import com.yuge.platform.infra.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单状态机测试
 */
@DisplayName("订单状态机测试")
class OrderStateMachineTest {

    private OrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine();
    }

    // ========== CREATED 状态测试 ==========

    @Test
    @DisplayName("CREATED状态 + STOCK_RESERVED事件 -> STOCK_RESERVED状态")
    void shouldTransitionToStockReservedWhenStockReservedEventOnCreated() {
        OrderStatus result = stateMachine.transition(OrderStatus.CREATED, OrderEvent.STOCK_RESERVED);
        assertEquals(OrderStatus.STOCK_RESERVED, result);
    }

    @Test
    @DisplayName("CREATED状态 + STOCK_RESERVE_FAILED事件 -> STOCK_FAILED状态")
    void shouldTransitionToStockFailedWhenStockReserveFailedEventOnCreated() {
        OrderStatus result = stateMachine.transition(OrderStatus.CREATED, OrderEvent.STOCK_RESERVE_FAILED);
        assertEquals(OrderStatus.STOCK_FAILED, result);
    }

    @Test
    @DisplayName("CREATED状态 + CANCEL事件 -> CANCELED状态")
    void shouldTransitionToCanceledWhenCancelEventOnCreated() {
        OrderStatus result = stateMachine.transition(OrderStatus.CREATED, OrderEvent.CANCEL);
        assertEquals(OrderStatus.CANCELED, result);
    }

    @Test
    @DisplayName("CREATED状态可执行的事件包括STOCK_RESERVED、STOCK_RESERVE_FAILED、CANCEL")
    void shouldReturnAvailableEventsForCreatedStatus() {
        Set<OrderEvent> events = stateMachine.getAvailableEvents(OrderStatus.CREATED);
        assertEquals(3, events.size());
        assertTrue(events.contains(OrderEvent.STOCK_RESERVED));
        assertTrue(events.contains(OrderEvent.STOCK_RESERVE_FAILED));
        assertTrue(events.contains(OrderEvent.CANCEL));
    }

    // ========== STOCK_RESERVED 状态测试 ==========

    @Test
    @DisplayName("STOCK_RESERVED状态 + CANCEL事件 -> CANCELED状态")
    void shouldTransitionToCanceledWhenCancelEventOnStockReserved() {
        OrderStatus result = stateMachine.transition(OrderStatus.STOCK_RESERVED, OrderEvent.CANCEL);
        assertEquals(OrderStatus.CANCELED, result);
    }

    @Test
    @DisplayName("STOCK_RESERVED状态不允许执行STOCK_RESERVED事件")
    void shouldThrowExceptionWhenStockReservedEventOnStockReserved() {
        assertThrows(BizException.class, () -> 
            stateMachine.transition(OrderStatus.STOCK_RESERVED, OrderEvent.STOCK_RESERVED));
    }

    @Test
    @DisplayName("STOCK_RESERVED状态不允许执行STOCK_RESERVE_FAILED事件")
    void shouldThrowExceptionWhenStockReserveFailedEventOnStockReserved() {
        assertThrows(BizException.class, () -> 
            stateMachine.transition(OrderStatus.STOCK_RESERVED, OrderEvent.STOCK_RESERVE_FAILED));
    }

    // ========== STOCK_FAILED 状态测试（终态）==========

    @Test
    @DisplayName("STOCK_FAILED是终态，不允许执行任何事件")
    void shouldThrowExceptionWhenAnyEventOnStockFailed() {
        assertThrows(BizException.class, () -> 
            stateMachine.transition(OrderStatus.STOCK_FAILED, OrderEvent.CANCEL));
        assertThrows(BizException.class, () -> 
            stateMachine.transition(OrderStatus.STOCK_FAILED, OrderEvent.STOCK_RESERVED));
        assertThrows(BizException.class, () -> 
            stateMachine.transition(OrderStatus.STOCK_FAILED, OrderEvent.STOCK_RESERVE_FAILED));
    }

    @Test
    @DisplayName("STOCK_FAILED状态没有可执行的事件")
    void shouldReturnEmptyEventsForStockFailedStatus() {
        Set<OrderEvent> events = stateMachine.getAvailableEvents(OrderStatus.STOCK_FAILED);
        assertTrue(events.isEmpty());
    }

    // ========== CANCELED 状态测试（终态）==========

    @Test
    @DisplayName("CANCELED是终态，不允许执行任何事件")
    void shouldThrowExceptionWhenAnyEventOnCanceled() {
        assertThrows(BizException.class, () -> 
            stateMachine.transition(OrderStatus.CANCELED, OrderEvent.CANCEL));
        assertThrows(BizException.class, () -> 
            stateMachine.transition(OrderStatus.CANCELED, OrderEvent.STOCK_RESERVED));
        assertThrows(BizException.class, () -> 
            stateMachine.transition(OrderStatus.CANCELED, OrderEvent.STOCK_RESERVE_FAILED));
    }

    @Test
    @DisplayName("CANCELED状态没有可执行的事件")
    void shouldReturnEmptyEventsForCanceledStatus() {
        Set<OrderEvent> events = stateMachine.getAvailableEvents(OrderStatus.CANCELED);
        assertTrue(events.isEmpty());
    }

    // ========== canTransition 测试 ==========

    @Test
    @DisplayName("canTransition正确判断CREATED状态的合法转换")
    void shouldCorrectlyCheckCanTransitionForCreated() {
        assertTrue(stateMachine.canTransition(OrderStatus.CREATED, OrderEvent.STOCK_RESERVED));
        assertTrue(stateMachine.canTransition(OrderStatus.CREATED, OrderEvent.STOCK_RESERVE_FAILED));
        assertTrue(stateMachine.canTransition(OrderStatus.CREATED, OrderEvent.CANCEL));
        assertFalse(stateMachine.canTransition(OrderStatus.CREATED, OrderEvent.CREATE));
    }

    @Test
    @DisplayName("canTransition正确判断STOCK_RESERVED状态的合法转换")
    void shouldCorrectlyCheckCanTransitionForStockReserved() {
        assertTrue(stateMachine.canTransition(OrderStatus.STOCK_RESERVED, OrderEvent.CANCEL));
        assertFalse(stateMachine.canTransition(OrderStatus.STOCK_RESERVED, OrderEvent.STOCK_RESERVED));
        assertFalse(stateMachine.canTransition(OrderStatus.STOCK_RESERVED, OrderEvent.STOCK_RESERVE_FAILED));
    }

    @Test
    @DisplayName("canTransition正确判断终态不允许任何转换")
    void shouldCorrectlyCheckCanTransitionForTerminalStates() {
        // STOCK_FAILED 终态
        assertFalse(stateMachine.canTransition(OrderStatus.STOCK_FAILED, OrderEvent.CANCEL));
        assertFalse(stateMachine.canTransition(OrderStatus.STOCK_FAILED, OrderEvent.STOCK_RESERVED));
        
        // CANCELED 终态
        assertFalse(stateMachine.canTransition(OrderStatus.CANCELED, OrderEvent.CANCEL));
        assertFalse(stateMachine.canTransition(OrderStatus.CANCELED, OrderEvent.STOCK_RESERVED));
    }

    // ========== 乱序场景测试 ==========

    @Test
    @DisplayName("乱序场景：STOCK_FAILED状态收到STOCK_RESERVED事件应被拒绝")
    void shouldRejectStockReservedWhenAlreadyStockFailed() {
        // 模拟乱序：订单已经是 STOCK_FAILED，但收到了 STOCK_RESERVED 事件
        assertFalse(stateMachine.canTransition(OrderStatus.STOCK_FAILED, OrderEvent.STOCK_RESERVED));
    }

    @Test
    @DisplayName("乱序场景：STOCK_RESERVED状态收到STOCK_RESERVE_FAILED事件应被拒绝")
    void shouldRejectStockReserveFailedWhenAlreadyStockReserved() {
        // 模拟乱序：订单已经是 STOCK_RESERVED，但收到了 STOCK_RESERVE_FAILED 事件
        assertFalse(stateMachine.canTransition(OrderStatus.STOCK_RESERVED, OrderEvent.STOCK_RESERVE_FAILED));
    }

    @Test
    @DisplayName("乱序场景：CANCELED状态收到库存事件应被拒绝")
    void shouldRejectStockEventsWhenAlreadyCanceled() {
        // 模拟乱序：订单已经取消，但收到了库存事件
        assertFalse(stateMachine.canTransition(OrderStatus.CANCELED, OrderEvent.STOCK_RESERVED));
        assertFalse(stateMachine.canTransition(OrderStatus.CANCELED, OrderEvent.STOCK_RESERVE_FAILED));
    }
}
