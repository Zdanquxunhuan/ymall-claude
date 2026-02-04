package com.yuge.order.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.order.domain.entity.MqConsumeLog;
import com.yuge.order.domain.entity.Order;
import com.yuge.order.domain.enums.ConsumeStatus;
import com.yuge.order.domain.enums.OrderEvent;
import com.yuge.order.domain.enums.OrderStatus;
import com.yuge.order.domain.event.ShipmentDeliveredEvent;
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
 * 签收事件消费者
 * 
 * 消费 ShipmentDelivered 事件，推进订单状态：SHIPPED -> DELIVERED
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "FULFILLMENT_TOPIC",
        selectorExpression = "SHIPMENT_DELIVERED",
        consumerGroup = "order-shipment-delivered-consumer-group"
)
public class ShipmentDeliveredConsumer implements RocketMQListener<MessageExt> {

    private static final String CONSUMER_GROUP = "order-shipment-delivered-consumer-group";

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
            log.error("[ShipmentDeliveredConsumer] Failed to parse message, msgId={}", msgId);
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
            log.info("[ShipmentDeliveredConsumer] Received message, eventId={}, bizKey={}, msgId={}, reconsumeTimes={}",
                    eventId, bizKey, msgId, reconsumeTimes);

            // 3. 幂等检查
            Optional<MqConsumeLog> existingRecord = mqConsumeLogRepository.tryAcquire(
                    eventId, CONSUMER_GROUP, topic, tags, bizKey);

            if (existingRecord.isPresent()) {
                MqConsumeLog record = existingRecord.get();
                if (ConsumeStatus.SUCCESS.getCode().equals(record.getStatus()) ||
                    ConsumeStatus.IGNORED.getCode().equals(record.getStatus())) {
                    log.info("[ShipmentDeliveredConsumer] Message already processed, eventId={}, status={}",
                            eventId, record.getStatus());
                    return;
                } else if (ConsumeStatus.PROCESSING.getCode().equals(record.getStatus())) {
                    log.info("[ShipmentDeliveredConsumer] Message is being processed, eventId={}", eventId);
                    throw new RuntimeException("Message is being processed by another instance");
                }
            }

            // 4. 解析签收事件
            ShipmentDeliveredEvent deliveredEvent = parseShipmentDeliveredEvent(baseEvent.getPayload());
            if (deliveredEvent == null) {
                log.error("[ShipmentDeliveredConsumer] Failed to parse ShipmentDeliveredEvent, eventId={}", eventId);
                mqConsumeLogRepository.markFailed(eventId, CONSUMER_GROUP, "解析事件失败",
                        System.currentTimeMillis() - startTime);
                return;
            }

            // 5. 处理签收事件
            processShipmentDelivered(deliveredEvent, eventId, startTime);

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startTime;
            log.error("[ShipmentDeliveredConsumer] Failed to consume message, eventId={}, error={}",
                    eventId, e.getMessage(), e);

            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }
            mqConsumeLogRepository.markFailed(eventId, CONSUMER_GROUP, errorMsg, costMs);

            if (reconsumeTimes < 3) {
                throw new RuntimeException("消费失败，触发重试", e);
            } else {
                log.error("[ShipmentDeliveredConsumer] Max retry reached, eventId={}", eventId);
            }
        } finally {
            TraceContext.clear();
            MDC.remove(TraceContext.TRACE_ID_MDC_KEY);
        }
    }

    /**
     * 处理签收事件
     */
    @Transactional(rollbackFor = Exception.class)
    public void processShipmentDelivered(ShipmentDeliveredEvent event, String eventId, long startTime) {
        String orderNo = event.getOrderNo();
        String shipmentNo = event.getShipmentNo();
        String waybillNo = event.getWaybillNo();

        log.info("[ShipmentDeliveredConsumer] Processing ShipmentDelivered, orderNo={}, shipmentNo={}, waybillNo={}, eventId={}",
                orderNo, shipmentNo, waybillNo, eventId);

        // 1. 查询订单
        Optional<Order> orderOpt = orderRepository.findByOrderNo(orderNo);
        if (orderOpt.isEmpty()) {
            log.error("[ShipmentDeliveredConsumer] Order not found, orderNo={}", orderNo);
            mqConsumeLogRepository.markFailed(eventId, CONSUMER_GROUP, "订单不存在: " + orderNo,
                    System.currentTimeMillis() - startTime);
            return;
        }

        Order order = orderOpt.get();
        OrderStatus currentStatus = OrderStatus.of(order.getStatus());

        // 2. 幂等检查：如果已经是DELIVERED状态，直接返回成功
        if (currentStatus == OrderStatus.DELIVERED) {
            log.info("[ShipmentDeliveredConsumer] Order already delivered, orderNo={}", orderNo);
            mqConsumeLogRepository.markIgnored(eventId, CONSUMER_GROUP,
                    "订单已签收", "订单已处于DELIVERED状态",
                    System.currentTimeMillis() - startTime);
            return;
        }

        // 3. 检查状态机是否允许转换
        if (!orderStateMachine.canTransition(currentStatus, OrderEvent.DELIVER)) {
            String ignoredReason = String.format("当前状态[%s]不允许执行[%s]事件，可能是乱序消息",
                    currentStatus.getDesc(), OrderEvent.DELIVER.getDesc());
            
            log.warn("[ShipmentDeliveredConsumer] Out-of-order message detected, orderNo={}, currentStatus={}, eventId={}",
                    orderNo, currentStatus, eventId);

            orderRepository.saveIgnoredStateFlow(orderNo, currentStatus.getCode(),
                    OrderEvent.DELIVER, eventId, ignoredReason);

            mqConsumeLogRepository.markIgnored(eventId, CONSUMER_GROUP,
                    "乱序消息已忽略", ignoredReason, System.currentTimeMillis() - startTime);
            return;
        }

        // 4. CAS 更新订单状态：SHIPPED -> DELIVERED
        boolean updated = orderRepository.casUpdateStatusOnly(orderNo, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

        if (updated) {
            orderRepository.saveStateFlow(orderNo, OrderStatus.SHIPPED, OrderStatus.DELIVERED,
                    OrderEvent.DELIVER, eventId, "SYSTEM",
                    String.format("签收成功，发货单号: %s, 运单号: %s", shipmentNo, waybillNo));

            mqConsumeLogRepository.markSuccess(eventId, CONSUMER_GROUP,
                    "状态更新成功: SHIPPED -> DELIVERED",
                    System.currentTimeMillis() - startTime);

            log.info("[ShipmentDeliveredConsumer] Order status updated, orderNo={}, SHIPPED -> DELIVERED, shipmentNo={}",
                    orderNo, shipmentNo);
        } else {
            Order refreshedOrder = orderRepository.findByOrderNo(orderNo).orElse(order);
            String newStatus = refreshedOrder.getStatus();

            if (OrderStatus.DELIVERED.getCode().equals(newStatus)) {
                mqConsumeLogRepository.markIgnored(eventId, CONSUMER_GROUP,
                        "订单已签收", "CAS更新失败，订单已处于DELIVERED状态",
                        System.currentTimeMillis() - startTime);
                log.info("[ShipmentDeliveredConsumer] Order already delivered (concurrent), orderNo={}", orderNo);
                return;
            }

            String ignoredReason = String.format("CAS更新失败，订单当前状态为[%s]，期望状态为[SHIPPED]，可能是并发或乱序",
                    newStatus);

            log.warn("[ShipmentDeliveredConsumer] CAS update failed (out-of-order), orderNo={}, currentStatus={}, eventId={}",
                    orderNo, newStatus, eventId);

            orderRepository.saveIgnoredStateFlow(orderNo, newStatus,
                    OrderEvent.DELIVER, eventId, ignoredReason);

            mqConsumeLogRepository.markIgnored(eventId, CONSUMER_GROUP,
                    "CAS更新失败，乱序消息已忽略", ignoredReason, System.currentTimeMillis() - startTime);
        }
    }

    private BaseEvent parseBaseEvent(MessageExt messageExt) {
        try {
            String body = new String(messageExt.getBody(), StandardCharsets.UTF_8);
            return objectMapper.readValue(body, BaseEvent.class);
        } catch (Exception e) {
            log.error("[ShipmentDeliveredConsumer] Failed to parse BaseEvent, msgId={}",
                    messageExt.getMsgId(), e);
            return null;
        }
    }

    private ShipmentDeliveredEvent parseShipmentDeliveredEvent(String payload) {
        try {
            return objectMapper.readValue(payload, ShipmentDeliveredEvent.class);
        } catch (Exception e) {
            log.error("[ShipmentDeliveredConsumer] Failed to parse ShipmentDeliveredEvent payload", e);
            return null;
        }
    }
}
