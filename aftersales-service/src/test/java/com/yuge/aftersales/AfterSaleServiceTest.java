package com.yuge.aftersales;

import com.yuge.aftersales.api.dto.*;
import com.yuge.aftersales.application.AfterSaleService;
import com.yuge.aftersales.domain.entity.AfterSale;
import com.yuge.aftersales.domain.enums.AfterSaleStatus;
import com.yuge.aftersales.domain.enums.AfterSaleType;
import com.yuge.aftersales.domain.statemachine.AfterSaleStateMachine;
import com.yuge.aftersales.infrastructure.repository.AfterSaleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 售后服务集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
class AfterSaleServiceTest {

    @Autowired
    private AfterSaleService afterSaleService;

    @Autowired
    private AfterSaleRepository afterSaleRepository;

    @Test
    void testStateMachineTransitions() {
        // 测试状态机转换
        
        // APPLIED -> APPROVED
        assertTrue(AfterSaleStateMachine.canTransition(
                AfterSaleStatus.APPLIED, AfterSaleStateMachine.Event.APPROVE));
        assertEquals(AfterSaleStatus.APPROVED, 
                AfterSaleStateMachine.getNextStatus(AfterSaleStatus.APPLIED, AfterSaleStateMachine.Event.APPROVE));

        // APPLIED -> REJECTED
        assertTrue(AfterSaleStateMachine.canTransition(
                AfterSaleStatus.APPLIED, AfterSaleStateMachine.Event.REJECT));
        assertEquals(AfterSaleStatus.REJECTED, 
                AfterSaleStateMachine.getNextStatus(AfterSaleStatus.APPLIED, AfterSaleStateMachine.Event.REJECT));

        // APPLIED -> CANCELED
        assertTrue(AfterSaleStateMachine.canTransition(
                AfterSaleStatus.APPLIED, AfterSaleStateMachine.Event.CANCEL));
        assertEquals(AfterSaleStatus.CANCELED, 
                AfterSaleStateMachine.getNextStatus(AfterSaleStatus.APPLIED, AfterSaleStateMachine.Event.CANCEL));

        // APPROVED -> REFUNDING
        assertTrue(AfterSaleStateMachine.canTransition(
                AfterSaleStatus.APPROVED, AfterSaleStateMachine.Event.START_REFUND));
        assertEquals(AfterSaleStatus.REFUNDING, 
                AfterSaleStateMachine.getNextStatus(AfterSaleStatus.APPROVED, AfterSaleStateMachine.Event.START_REFUND));

        // REFUNDING -> REFUNDED
        assertTrue(AfterSaleStateMachine.canTransition(
                AfterSaleStatus.REFUNDING, AfterSaleStateMachine.Event.REFUND_SUCCESS));
        assertEquals(AfterSaleStatus.REFUNDED, 
                AfterSaleStateMachine.getNextStatus(AfterSaleStatus.REFUNDING, AfterSaleStateMachine.Event.REFUND_SUCCESS));

        // Invalid transitions
        assertFalse(AfterSaleStateMachine.canTransition(
                AfterSaleStatus.REFUNDED, AfterSaleStateMachine.Event.APPROVE));
        assertFalse(AfterSaleStateMachine.canTransition(
                AfterSaleStatus.REJECTED, AfterSaleStateMachine.Event.START_REFUND));
    }

    @Test
    void testAfterSaleStatusMethods() {
        // 测试状态枚举方法
        
        // canCancel
        assertTrue(AfterSaleStatus.APPLIED.canCancel());
        assertFalse(AfterSaleStatus.APPROVED.canCancel());
        assertFalse(AfterSaleStatus.REFUNDED.canCancel());

        // canApprove
        assertTrue(AfterSaleStatus.APPLIED.canApprove());
        assertFalse(AfterSaleStatus.APPROVED.canApprove());

        // canReject
        assertTrue(AfterSaleStatus.APPLIED.canReject());
        assertFalse(AfterSaleStatus.REFUNDING.canReject());

        // isTerminal
        assertTrue(AfterSaleStatus.REFUNDED.isTerminal());
        assertTrue(AfterSaleStatus.REJECTED.isTerminal());
        assertTrue(AfterSaleStatus.CANCELED.isTerminal());
        assertFalse(AfterSaleStatus.APPLIED.isTerminal());
        assertFalse(AfterSaleStatus.APPROVED.isTerminal());
        assertFalse(AfterSaleStatus.REFUNDING.isTerminal());
    }

    @Test
    void testAfterSaleTypeEnum() {
        assertEquals(AfterSaleType.REFUND, AfterSaleType.of("REFUND"));
        assertEquals(AfterSaleType.RETURN_REFUND, AfterSaleType.of("RETURN_REFUND"));
        
        assertThrows(IllegalArgumentException.class, () -> AfterSaleType.of("INVALID"));
    }

    @Test
    void testAvailableEvents() {
        // APPLIED状态可用事件
        List<AfterSaleStateMachine.Event> appliedEvents = 
                AfterSaleStateMachine.getAvailableEvents(AfterSaleStatus.APPLIED);
        assertTrue(appliedEvents.contains(AfterSaleStateMachine.Event.APPROVE));
        assertTrue(appliedEvents.contains(AfterSaleStateMachine.Event.REJECT));
        assertTrue(appliedEvents.contains(AfterSaleStateMachine.Event.CANCEL));

        // APPROVED状态可用事件
        List<AfterSaleStateMachine.Event> approvedEvents = 
                AfterSaleStateMachine.getAvailableEvents(AfterSaleStatus.APPROVED);
        assertTrue(approvedEvents.contains(AfterSaleStateMachine.Event.START_REFUND));
        assertEquals(1, approvedEvents.size());

        // REFUNDED状态无可用事件（终态）
        List<AfterSaleStateMachine.Event> refundedEvents = 
                AfterSaleStateMachine.getAvailableEvents(AfterSaleStatus.REFUNDED);
        assertTrue(refundedEvents.isEmpty());
    }
}
