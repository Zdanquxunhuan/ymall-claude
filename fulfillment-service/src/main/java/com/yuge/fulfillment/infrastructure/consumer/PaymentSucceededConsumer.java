package com.yuge.fulfillment.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.fulfillment.application.ShipmentService;
import com.yuge.fulfillment.domain.entity.MqConsumeLog;
import com.yuge.fulfillment.domain.enums.ConsumeStatus;
import com.yuge.fulfillment.domain.event.PaymentSucceededEvent;
import com.yuge.fulfillment.infrastructure.repository.MqConsumeLogRepository;
import com.yuge.platform.infra.mq.BaseEvent;
import com.yuge.platform.infra.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 支付成功事件消费者
 * 
 * 消费 PaymentSucceeded 事件，自动创建发货单
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "PAYMENT_TOPIC",
        selectorExpression = "PAYMENT_SUCCEEDED",
        consumerGroup = "fulfillment-payment-succeeded-consumer-group"
)
public class PaymentSucceededConsumer implements RocketMQListener<MessageExt> {

    private static final String CONSUMER_GROUP = "fulfillment-payment-succeeded-consumer-group";

    private final MqConsumeLogRepository mqConsumeLogRepository;
    private final ShipmentService shipmentService;
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
            log.error("[PaymentSucceededConsumer] Failed to parse message, msgId={}", msgId);
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
            log.info("[PaymentSucceededConsumer] Received message, eventId={}, bizKey={}, msgId={}, reconsumeTimes={}",
                    eventId, bizKey, msgId, reconsumeTimes);

            // 3. 幂等检查
            Optional<MqConsumeLog> existingRecord = mqConsumeLogRepository.tryAcquire(
                    eventId, CONSUMER_GROUP, topic, tags, bizKey);

            if (existingRecord.isPresent()) {
                MqConsumeLog record = existingRecord.get();
                if (ConsumeStatus.SUCCESS.getCode().equals(record.getStatus()) ||
                    ConsumeStatus.IGNORED.getCode().equals(record.getStatus())) {
                    log.info("[PaymentSucceededConsumer] Message already processed, eventId={}, status={}",
                            eventId, record.getStatus());
                    return;
                } else if (ConsumeStatus.PROCESSING.getCode().equals(record.getStatus())) {
                    log.info("[PaymentSucceededConsumer] Message is being processed, eventId={}", eventId);
                    throw new RuntimeException("Message is being processed by another instance");
                }
            }

            // 4. 解析支付成功事件
            PaymentSucceededEvent paymentEvent = parsePaymentSucceededEvent(baseEvent.getPayload());
            if (paymentEvent == null) {
                log.error("[PaymentSucceededConsumer] Failed to parse PaymentSucceededEvent, eventId={}", eventId);
                mqConsumeLogRepository.markFailed(eventId, CONSUMER_GROUP, "解析事件失败",
                        System.currentTimeMillis() - startTime);
                return;
            }

            // 5. 创建发货单
            processPaymentSucceeded(paymentEvent, eventId, startTime);

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startTime;
            log.error("[PaymentSucceededConsumer] Failed to consume message, eventId={}, error={}",
                    eventId, e.getMessage(), e);

            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }
            mqConsumeLogRepository.markFailed(eventId, CONSUMER_GROUP, errorMsg, costMs);

            if (reconsumeTimes < 3) {
                throw new RuntimeException("消费失败，触发重试", e);
            } else {
                log.error("[PaymentSucceededConsumer] Max retry reached, eventId={}", eventId);
            }
        } finally {
            TraceContext.clear();
            MDC.remove(TraceContext.TRACE_ID_MDC_KEY);
        }
    }

    /**
     * 处理支付成功事件 - 创建发货单
     */
    private void processPaymentSucceeded(PaymentSucceededEvent event, String eventId, long startTime) {
        String orderNo = event.getOrderNo();
        String payNo = event.getPayNo();

        log.info("[PaymentSucceededConsumer] Processing PaymentSucceeded, orderNo={}, payNo={}, eventId={}",
                orderNo, payNo, eventId);

        try {
            // 创建发货单（幂等）
            String shipmentNo = shipmentService.createShipment(orderNo, eventId);

            mqConsumeLogRepository.markSuccess(eventId, CONSUMER_GROUP,
                    String.format("发货单创建成功: %s", shipmentNo),
                    System.currentTimeMillis() - startTime);

            log.info("[PaymentSucceededConsumer] Shipment created, orderNo={}, shipmentNo={}", orderNo, shipmentNo);

        } catch (IllegalStateException e) {
            // 订单已有发货单，标记为已忽略
            mqConsumeLogRepository.markIgnored(eventId, CONSUMER_GROUP,
                    "发货单已存在", e.getMessage(), System.currentTimeMillis() - startTime);
            log.info("[PaymentSucceededConsumer] Shipment already exists, orderNo={}", orderNo);
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
            log.error("[PaymentSucceededConsumer] Failed to parse BaseEvent, msgId={}",
                    messageExt.getMsgId(), e);
            return null;
        }
    }

    /**
     * 解析支付成功事件
     */
    private PaymentSucceededEvent parsePaymentSucceededEvent(String payload) {
        try {
            return objectMapper.readValue(payload, PaymentSucceededEvent.class);
        } catch (Exception e) {
            log.error("[PaymentSucceededConsumer] Failed to parse PaymentSucceededEvent payload", e);
            return null;
        }
    }
}
