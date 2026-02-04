package com.yuge.order.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.order.domain.entity.MqConsumeLog;
import com.yuge.order.domain.enums.ConsumeStatus;
import com.yuge.order.infrastructure.repository.MqConsumeLogRepository;
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
 * 订单创建事件消费者
 * 
 * 演示消费幂等模板：
 * 1. 使用 t_mq_consume_log 表实现幂等
 * 2. 消费前检查是否已处理
 * 3. 消费后记录结果
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "ORDER_TOPIC",
        selectorExpression = "ORDER_CREATED",
        consumerGroup = "order-created-consumer-group"
)
public class OrderCreatedConsumer implements RocketMQListener<MessageExt> {

    private static final String CONSUMER_GROUP = "order-created-consumer-group";

    private final MqConsumeLogRepository mqConsumeLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageExt messageExt) {
        String msgId = messageExt.getMsgId();
        String topic = messageExt.getTopic();
        String tags = messageExt.getTags();
        int reconsumeTimes = messageExt.getReconsumeTimes();
        long startTime = System.currentTimeMillis();

        // 1. 解析消息
        BaseEvent event = parseMessage(messageExt);
        if (event == null) {
            log.error("[OrderCreatedConsumer] Failed to parse message, msgId={}", msgId);
            return; // 解析失败不重试
        }

        String eventId = event.getMessageId();
        String bizKey = event.getBusinessKey();
        String traceId = event.getTraceId();

        // 2. 设置 traceId
        if (traceId != null && !traceId.isEmpty()) {
            TraceContext.setTraceId(traceId);
            MDC.put(TraceContext.TRACE_ID_MDC_KEY, traceId);
        }

        try {
            log.info("[OrderCreatedConsumer] Received message, eventId={}, bizKey={}, msgId={}, reconsumeTimes={}",
                    eventId, bizKey, msgId, reconsumeTimes);

            // 3. 幂等检查（尝试获取消费锁）
            Optional<MqConsumeLog> existingRecord = mqConsumeLogRepository.tryAcquire(
                    eventId, CONSUMER_GROUP, topic, tags, bizKey);

            if (existingRecord.isPresent()) {
                MqConsumeLog record = existingRecord.get();
                if (ConsumeStatus.SUCCESS.getCode().equals(record.getStatus())) {
                    log.info("[OrderCreatedConsumer] Message already consumed successfully, eventId={}", eventId);
                    return;
                } else if (ConsumeStatus.PROCESSING.getCode().equals(record.getStatus())) {
                    log.info("[OrderCreatedConsumer] Message is being processed by another instance, eventId={}", eventId);
                    // 抛出异常触发重试
                    throw new RuntimeException("Message is being processed by another instance");
                }
                // FAILED 状态允许重试
            }

            // 4. 执行业务逻辑
            doConsume(event);

            // 5. 标记消费成功
            long costMs = System.currentTimeMillis() - startTime;
            mqConsumeLogRepository.markSuccess(eventId, CONSUMER_GROUP, "OK", costMs);

            log.info("[OrderCreatedConsumer] Message consumed successfully, eventId={}, bizKey={}, costMs={}",
                    eventId, bizKey, costMs);

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startTime;
            log.error("[OrderCreatedConsumer] Failed to consume message, eventId={}, error={}",
                    eventId, e.getMessage(), e);

            // 标记消费失败
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }
            mqConsumeLogRepository.markFailed(eventId, CONSUMER_GROUP, errorMsg, costMs);

            // 判断是否需要重试
            if (reconsumeTimes < 3) {
                throw new RuntimeException("消费失败，触发重试", e);
            } else {
                log.error("[OrderCreatedConsumer] Max retry reached, message will be discarded, eventId={}", eventId);
            }
        } finally {
            TraceContext.clear();
            MDC.remove(TraceContext.TRACE_ID_MDC_KEY);
        }
    }

    /**
     * 解析消息
     */
    private BaseEvent parseMessage(MessageExt messageExt) {
        try {
            String body = new String(messageExt.getBody(), StandardCharsets.UTF_8);
            return objectMapper.readValue(body, BaseEvent.class);
        } catch (Exception e) {
            log.error("[OrderCreatedConsumer] Failed to parse message body, msgId={}", 
                    messageExt.getMsgId(), e);
            return null;
        }
    }

    /**
     * 执行业务消费逻辑
     * 
     * 这里仅打印日志作为演示，实际业务可以：
     * - 更新库存
     * - 发送通知
     * - 同步到搜索引擎
     * - 等等
     */
    private void doConsume(BaseEvent event) {
        String eventId = event.getMessageId();
        String bizKey = event.getBusinessKey();
        String payload = event.getPayload();

        log.info("[OrderCreatedConsumer] ========================================");
        log.info("[OrderCreatedConsumer] Processing OrderCreated event");
        log.info("[OrderCreatedConsumer] Event ID: {}", eventId);
        log.info("[OrderCreatedConsumer] Business Key (Order No): {}", bizKey);
        log.info("[OrderCreatedConsumer] Trace ID: {}", event.getTraceId());
        log.info("[OrderCreatedConsumer] Event Time: {}", event.getEventTime());
        log.info("[OrderCreatedConsumer] Payload: {}", payload);
        log.info("[OrderCreatedConsumer] ========================================");

        // 模拟业务处理耗时
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
