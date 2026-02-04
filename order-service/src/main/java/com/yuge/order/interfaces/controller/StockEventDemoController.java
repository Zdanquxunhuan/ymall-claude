package com.yuge.order.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.order.domain.entity.Order;
import com.yuge.order.domain.entity.OrderStateFlow;
import com.yuge.order.domain.enums.OrderStatus;
import com.yuge.order.domain.event.StockReservedEvent;
import com.yuge.order.domain.event.StockReserveFailedEvent;
import com.yuge.order.infrastructure.consumer.StockReservedConsumer;
import com.yuge.order.infrastructure.consumer.StockReserveFailedConsumer;
import com.yuge.order.infrastructure.repository.MqConsumeLogRepository;
import com.yuge.order.infrastructure.repository.OrderRepository;
import com.yuge.platform.infra.common.Result;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 库存事件消费演示控制器
 * 
 * 提供接口模拟注入重复与乱序消息，验证状态机的稳定性
 */
@Slf4j
@RestController
@RequestMapping("/demo/stock-event")
@RequiredArgsConstructor
public class StockEventDemoController {

    private final StockReservedConsumer stockReservedConsumer;
    private final StockReserveFailedConsumer stockReserveFailedConsumer;
    private final OrderRepository orderRepository;
    private final MqConsumeLogRepository mqConsumeLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * 模拟发送库存预留成功事件
     */
    @PostMapping("/stock-reserved")
    public Result<String> simulateStockReserved(@RequestBody StockReservedRequest request) {
        log.info("[Demo] Simulating StockReserved event, orderNo={}, eventId={}", 
                request.getOrderNo(), request.getEventId());

        String eventId = request.getEventId() != null ? request.getEventId() 
                : UUID.randomUUID().toString().replace("-", "");

        StockReservedEvent event = StockReservedEvent.builder()
                .eventId(eventId)
                .orderNo(request.getOrderNo())
                .items(new ArrayList<>())
                .eventTime(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString().replace("-", ""))
                .version("1.0")
                .build();

        long startTime = System.currentTimeMillis();
        stockReservedConsumer.processStockReserved(event, eventId, startTime);

        return Result.success("StockReserved event processed, eventId=" + eventId);
    }

    /**
     * 模拟发送库存预留失败事件
     */
    @PostMapping("/stock-reserve-failed")
    public Result<String> simulateStockReserveFailed(@RequestBody StockReserveFailedRequest request) {
        log.info("[Demo] Simulating StockReserveFailed event, orderNo={}, eventId={}", 
                request.getOrderNo(), request.getEventId());

        String eventId = request.getEventId() != null ? request.getEventId() 
                : UUID.randomUUID().toString().replace("-", "");

        StockReserveFailedEvent event = StockReserveFailedEvent.builder()
                .eventId(eventId)
                .orderNo(request.getOrderNo())
                .errorCode(request.getErrorCode() != null ? request.getErrorCode() : "STOCK_INSUFFICIENT")
                .errorMessage(request.getErrorMessage() != null ? request.getErrorMessage() : "库存不足")
                .requestedItems(new ArrayList<>())
                .eventTime(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString().replace("-", ""))
                .version("1.0")
                .build();

        long startTime = System.currentTimeMillis();
        stockReserveFailedConsumer.processStockReserveFailed(event, eventId, startTime);

        return Result.success("StockReserveFailed event processed, eventId=" + eventId);
    }

    /**
     * 模拟重复消息场景
     * 发送两次相同eventId的消息，验证幂等性
     */
    @PostMapping("/duplicate-test")
    public Result<DuplicateTestResult> testDuplicateMessage(@RequestBody DuplicateTestRequest request) {
        log.info("[Demo] Testing duplicate message, orderNo={}", request.getOrderNo());

        String eventId = UUID.randomUUID().toString().replace("-", "");
        DuplicateTestResult result = new DuplicateTestResult();
        result.setEventId(eventId);
        result.setOrderNo(request.getOrderNo());

        // 查询订单初始状态
        Optional<Order> orderOpt = orderRepository.findByOrderNo(request.getOrderNo());
        if (orderOpt.isEmpty()) {
            return Result.fail("ORDER_NOT_FOUND", "订单不存在: " + request.getOrderNo());
        }
        result.setInitialStatus(orderOpt.get().getStatus());

        StockReservedEvent event = StockReservedEvent.builder()
                .eventId(eventId)
                .orderNo(request.getOrderNo())
                .items(new ArrayList<>())
                .eventTime(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString().replace("-", ""))
                .version("1.0")
                .build();

        // 第一次发送
        long startTime1 = System.currentTimeMillis();
        try {
            stockReservedConsumer.processStockReserved(event, eventId, startTime1);
            result.setFirstCallResult("SUCCESS");
        } catch (Exception e) {
            result.setFirstCallResult("FAILED: " + e.getMessage());
        }

        // 第二次发送（相同eventId）
        long startTime2 = System.currentTimeMillis();
        try {
            stockReservedConsumer.processStockReserved(event, eventId, startTime2);
            result.setSecondCallResult("SUCCESS");
        } catch (Exception e) {
            result.setSecondCallResult("FAILED: " + e.getMessage());
        }

        // 查询最终状态
        Order finalOrder = orderRepository.findByOrderNo(request.getOrderNo()).orElse(null);
        result.setFinalStatus(finalOrder != null ? finalOrder.getStatus() : "UNKNOWN");

        // 查询状态流转记录
        List<OrderStateFlow> stateFlows = orderRepository.findStateFlowsByOrderNo(request.getOrderNo());
        result.setStateFlowCount(stateFlows.size());

        log.info("[Demo] Duplicate test completed, orderNo={}, initialStatus={}, finalStatus={}, stateFlowCount={}",
                request.getOrderNo(), result.getInitialStatus(), result.getFinalStatus(), result.getStateFlowCount());

        return Result.success(result);
    }

    /**
     * 模拟乱序消息场景
     * 先发送StockReserveFailed，再发送StockReserved，验证乱序处理
     */
    @PostMapping("/out-of-order-test")
    public Result<OutOfOrderTestResult> testOutOfOrderMessage(@RequestBody OutOfOrderTestRequest request) {
        log.info("[Demo] Testing out-of-order message, orderNo={}", request.getOrderNo());

        OutOfOrderTestResult result = new OutOfOrderTestResult();
        result.setOrderNo(request.getOrderNo());

        // 查询订单初始状态
        Optional<Order> orderOpt = orderRepository.findByOrderNo(request.getOrderNo());
        if (orderOpt.isEmpty()) {
            return Result.fail("ORDER_NOT_FOUND", "订单不存在: " + request.getOrderNo());
        }
        result.setInitialStatus(orderOpt.get().getStatus());

        String eventId1 = UUID.randomUUID().toString().replace("-", "");
        String eventId2 = UUID.randomUUID().toString().replace("-", "");
        result.setFirstEventId(eventId1);
        result.setSecondEventId(eventId2);

        // 第一步：先发送 StockReserveFailed（模拟先到达的消息）
        StockReserveFailedEvent failedEvent = StockReserveFailedEvent.builder()
                .eventId(eventId1)
                .orderNo(request.getOrderNo())
                .errorCode("STOCK_INSUFFICIENT")
                .errorMessage("库存不足")
                .requestedItems(new ArrayList<>())
                .eventTime(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString().replace("-", ""))
                .version("1.0")
                .build();

        long startTime1 = System.currentTimeMillis();
        try {
            stockReserveFailedConsumer.processStockReserveFailed(failedEvent, eventId1, startTime1);
            result.setFirstEventResult("SUCCESS - StockReserveFailed processed");
        } catch (Exception e) {
            result.setFirstEventResult("FAILED: " + e.getMessage());
        }

        // 查询中间状态
        Order midOrder = orderRepository.findByOrderNo(request.getOrderNo()).orElse(null);
        result.setMidStatus(midOrder != null ? midOrder.getStatus() : "UNKNOWN");

        // 第二步：再发送 StockReserved（模拟后到达的消息，应该被忽略）
        StockReservedEvent reservedEvent = StockReservedEvent.builder()
                .eventId(eventId2)
                .orderNo(request.getOrderNo())
                .items(new ArrayList<>())
                .eventTime(LocalDateTime.now().minusSeconds(10)) // 事件时间更早
                .traceId(UUID.randomUUID().toString().replace("-", ""))
                .version("1.0")
                .build();

        long startTime2 = System.currentTimeMillis();
        try {
            stockReservedConsumer.processStockReserved(reservedEvent, eventId2, startTime2);
            result.setSecondEventResult("SUCCESS - StockReserved processed (should be ignored)");
        } catch (Exception e) {
            result.setSecondEventResult("FAILED: " + e.getMessage());
        }

        // 查询最终状态
        Order finalOrder = orderRepository.findByOrderNo(request.getOrderNo()).orElse(null);
        result.setFinalStatus(finalOrder != null ? finalOrder.getStatus() : "UNKNOWN");

        // 查询状态流转记录
        List<OrderStateFlow> stateFlows = orderRepository.findStateFlowsByOrderNo(request.getOrderNo());
        result.setStateFlows(stateFlows.stream()
                .map(sf -> String.format("[%s] %s -> %s (%s) %s", 
                        sf.getEvent(), sf.getFromStatus(), sf.getToStatus(), 
                        sf.getEventId(), sf.getRemark() != null ? sf.getRemark() : ""))
                .toList());

        // 验证结果
        boolean isStable = OrderStatus.STOCK_FAILED.getCode().equals(result.getFinalStatus());
        result.setStable(isStable);
        result.setVerification(isStable 
                ? "PASS - 状态稳定在 STOCK_FAILED，乱序消息被正确忽略" 
                : "FAIL - 状态不稳定，可能存在乱序处理问题");

        log.info("[Demo] Out-of-order test completed, orderNo={}, initialStatus={}, finalStatus={}, stable={}",
                request.getOrderNo(), result.getInitialStatus(), result.getFinalStatus(), isStable);

        return Result.success(result);
    }

    /**
     * 查询订单状态和流转记录
     */
    @GetMapping("/order-status/{orderNo}")
    public Result<OrderStatusResult> getOrderStatus(@PathVariable String orderNo) {
        Optional<Order> orderOpt = orderRepository.findByOrderNo(orderNo);
        if (orderOpt.isEmpty()) {
            return Result.fail("ORDER_NOT_FOUND", "订单不存在: " + orderNo);
        }

        Order order = orderOpt.get();
        List<OrderStateFlow> stateFlows = orderRepository.findStateFlowsByOrderNo(orderNo);

        OrderStatusResult result = new OrderStatusResult();
        result.setOrderNo(orderNo);
        result.setCurrentStatus(order.getStatus());
        result.setVersion(order.getVersion());
        result.setStateFlows(stateFlows.stream()
                .map(sf -> String.format("[%s] %s -> %s (eventId=%s) %s", 
                        sf.getEvent(), sf.getFromStatus(), sf.getToStatus(), 
                        sf.getEventId(), sf.getRemark() != null ? sf.getRemark() : ""))
                .toList());

        return Result.success(result);
    }

    // ========== Request/Response DTOs ==========

    @Data
    public static class StockReservedRequest {
        private String orderNo;
        private String eventId;
    }

    @Data
    public static class StockReserveFailedRequest {
        private String orderNo;
        private String eventId;
        private String errorCode;
        private String errorMessage;
    }

    @Data
    public static class DuplicateTestRequest {
        private String orderNo;
    }

    @Data
    public static class DuplicateTestResult {
        private String orderNo;
        private String eventId;
        private String initialStatus;
        private String firstCallResult;
        private String secondCallResult;
        private String finalStatus;
        private int stateFlowCount;
    }

    @Data
    public static class OutOfOrderTestRequest {
        private String orderNo;
    }

    @Data
    public static class OutOfOrderTestResult {
        private String orderNo;
        private String initialStatus;
        private String firstEventId;
        private String firstEventResult;
        private String midStatus;
        private String secondEventId;
        private String secondEventResult;
        private String finalStatus;
        private List<String> stateFlows;
        private boolean stable;
        private String verification;
    }

    @Data
    public static class OrderStatusResult {
        private String orderNo;
        private String currentStatus;
        private Integer version;
        private List<String> stateFlows;
    }
}
