package com.yuge.order.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.order.domain.entity.MqConsumeLog;
import com.yuge.order.domain.entity.Order;
import com.yuge.order.domain.enums.ConsumeStatus;
import com.yuge.order.domain.enums.OrderEvent;
import com.yuge.order.domain.enums.OrderStatus;
import com.yuge.order.domain.event.StockReservedEvent;
import com.yuge.order.domain.statemachine.OrderStateMachine;
import com.yuge.order.infrastructure.repository.MqConsumeLogRepository;
import com.yuge.order.infrastructure.repository.OrderRepository;
import com.yuge.platform.infra.mq.BaseEvent;
import com.yuge.platform.infra.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 库存预留成功事件消费者
 * 
 * 消费 StockReserved 事件，推进订单状态：CREATED -> STOCK_RESERVED
 * 
 * 防乱序策略：
 * 1. 事件携带 event_id 与 event_time
 * 2. 订单更新用 CAS：where status=CREATED
 * 3. 更新失败（说明已被其他事件推进）则记录为 IGNORED，并写一条 state_flow（标注 ignored_reason）
 * 
 * 重复消费幂等：基于 t_mq_consume_log（event_id + consumer_group unique）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "INVENTORY_TOPIC",
        selectorExpression = "STOCK_RESERVED",
        consumerGroup = "order-stock-reserved-consumer-group"
)
public class StockReservedConsumer implements RocketMQListener<MessageExt> {

    private static final String CONSUMER_GROUP = "order-stock-reserved-consumer-group";

    private final MqConsumeLogRepository mqConsumeLogRepository;
    private final OrderRepository orderRepository;
    private final OrderStateMachine orderStateMachine;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageExt messageExt) {
        String msgId = messageExt.getMsgId();
        String topic = messageExt.getTopic();
        String tags = messageExt.getTags();
        int reconsumeTimes = messageExt.getReconsumeTimes();
        long startTime = System.currentTimeMillis();

        // 1. 解析消息
        BaseEvent baseEvent = parseBaseEvent(messageExt);
        if (baseEvent == null) {
            log.error("[StockReservedConsumer] Failed to parse message, msgId={}", msgId);
            return;
        }

        String eventId = baseEvent.getMessageId();
        String bizKey = baseEvent.getBusinessKey();
        String traceId = baseEvent.getTraceId();

        // 2. 设置 traceId
        if (traceId != null && !traceId.isEmpty()) {
            TraceContext.setTraceId(traceId);
            MDC.put(TraceContext.TRACE_ID_MDC_KEY, traceId);
        }

        try {
            log.info("[StockReservedConsumer] Received message, eventId={}, bizKey={}, msgId={}, reconsumeTimes={}",
                    eventId, bizKey, msgId, reconsumeTimes);

            // 3. 幂等检查
            Optional<MqConsumeLog> existingRecord = mqConsumeLogRepository.tryAcquire(
                    eventId, CONSUMER_GROUP, topic, tags, bizKey);

            if (existingRecord.isPresent()) {
                MqConsumeLog record = existingRecord.get();
                if (ConsumeStatus.SUCCESS.getCode().equals(record.getStatus()) ||
                    ConsumeStatus.IGNORED.getCode().equals(record.getStatus())) {
                    log.info("[StockReservedConsumer] Message already processed, eventId={}, status={}",
                            eventId, record.getStatus());
                    return;
                } else if (ConsumeStatus.PROCESSING.getCode().equals(record.getStatus())) {
                    log.info("[StockReservedConsumer] Message is being processed, eventId={}", eventId);
                    throw new RuntimeException("Message is being processed by another instance");
                }
            }

            // 4. 解析库存预留成功事件
            StockReservedEvent stockEvent = parseStockReservedEvent(baseEvent.getPayload());
            if (stockEvent == null) {
                log.error("[StockReservedConsumer] Failed to parse StockReservedEvent, eventId={}", eventId);
                mqConsumeLogRepository.markFailed(eventId, CONSUMER_GROUP, "解析事件失败",
                        System.currentTimeMillis() - startTime);
                return;
            }

            // 5. 处理库存预留成功事件
            processStockReserved(stockEvent, eventId, startTime);

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startTime;
            log.error("[StockReservedConsumer] Failed to consume message, eventId={}, error={}",
                    eventId, e.getMessage(), e);

            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }
            mqConsumeLogRepository.markFailed(eventId, CONSUMER_GROUP, errorMsg, costMs);

            if (reconsumeTimes < 3) {
                throw new RuntimeException("消费失败，触发重试", e);
            } else {
                log.error("[StockReservedConsumer] Max retry reached, eventId={}", eventId);
            }
        } finally {
            TraceContext.clear();
            MDC.remove(TraceContext.TRACE_ID_MDC_KEY);
        }
    }

    /**
     * 处理库存预留成功事件
     */
    @Transactional(rollbackFor = Exception.class)
    public void processStockReserved(StockReservedEvent event, String eventId, long startTime) {
        String orderNo = event.getOrderNo();

        log.info("[StockReservedConsumer] Processing StockReserved, orderNo={}, eventId={}",
                orderNo, eventId);

        // 1. 查询订单
        Optional<Order> orderOpt = orderRepository.findByOrderNo(orderNo);
        if (orderOpt.isEmpty()) {
            log.error("[StockReservedConsumer] Order not found, orderNo={}", orderNo);
            mqConsumeLogRepository.markFailed(eventId, CONSUMER_GROUP, "订单不存在: " + orderNo,
                    System.currentTimeMillis() - startTime);
            return;
        }

        Order order = orderOpt.get();
        OrderStatus currentStatus = OrderStatus.of(order.getStatus());

        // 2. 检查状态机是否允许转换
        if (!orderStateMachine.canTransition(currentStatus, OrderEvent.STOCK_RESERVED)) {
            // 状态不允许转换，可能是乱序消息
            String ignoredReason = String.format("当前状态[%s]不允许执行[%s]事件，可能是乱序消息",
                    currentStatus.getDesc(), OrderEvent.STOCK_RESERVED.getDesc());
            
            log.warn("[StockReservedConsumer] Out-of-order message detected, orderNo={}, currentStatus={}, eventId={}",
                    orderNo, currentStatus, eventId);

            // 记录被忽略的状态流转
            orderRepository.saveIgnoredStateFlow(orderNo, currentStatus.getCode(),
                    OrderEvent.STOCK_RESERVED, eventId, ignoredReason);

            // 标记消费为已忽略
            mqConsumeLogRepository.markIgnored(eventId, CONSUMER_GROUP,
                    "乱序消息已忽略", ignoredReason, System.currentTimeMillis() - startTime);
            return;
        }

        // 3. CAS 更新订单状态：CREATED -> STOCK_RESERVED
        boolean updated = orderRepository.casUpdateStatusOnly(orderNo, OrderStatus.CREATED, OrderStatus.STOCK_RESERVED);

        if (updated) {
            // 更新成功，记录状态流转
            orderRepository.saveStateFlow(orderNo, OrderStatus.CREATED, OrderStatus.STOCK_RESERVED,
                    OrderEvent.STOCK_RESERVED, eventId, "SYSTEM", "库存预留成功");

            mqConsumeLogRepository.markSuccess(eventId, CONSUMER_GROUP, "状态更新成功: CREATED -> STOCK_RESERVED",
                    System.currentTimeMillis() - startTime);

            log.info("[StockReservedConsumer] Order status updated, orderNo={}, CREATED -> STOCK_RESERVED", orderNo);
        } else {
            // CAS 更新失败，说明订单状态已被其他事件推进（乱序）
            // 重新查询当前状态
            Order refreshedOrder = orderRepository.findByOrderNo(orderNo).orElse(order);
            String newStatus = refreshedOrder.getStatus();

            String ignoredReason = String.format("CAS更新失败，订单当前状态为[%s]，期望状态为[CREATED]，可能是并发或乱序",
                    newStatus);

            log.warn("[StockReservedConsumer] CAS update failed (out-of-order), orderNo={}, currentStatus={}, eventId={}",
                    orderNo, newStatus, eventId);

            // 记录被忽略的状态流转
            orderRepository.saveIgnoredStateFlow(orderNo, newStatus,
                    OrderEvent.STOCK_RESERVED, eventId, ignoredReason);

            // 标记消费为已忽略
            mqConsumeLogRepository.markIgnored(eventId, CONSUMER_GROUP,
                    "CAS更新失败，乱序消息已忽略", ignoredReason, System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 解析基础事件
     */
    private BaseEvent parseBaseEvent(MessageExt messageExt) {
        try {
            String body = new String(messageExt.getBody(), StandardCharsets.UTF_8);
            return objectMapper.readValue(body, BaseEvent.class);
        } catch (Exception e) {
            log.error("[StockReservedConsumer] Failed to parse BaseEvent, msgId={}",
                    messageExt.getMsgId(), e);
            return null;
        }
    }

    /**
     * 解析库存预留成功事件
     */
    private StockReservedEvent parseStockReservedEvent(String payload) {
        try {
            return objectMapper.readValue(payload, StockReservedEvent.class);
        } catch (Exception e) {
            log.error("[StockReservedConsumer] Failed to parse StockReservedEvent payload", e);
            return null;
        }
    }
}
