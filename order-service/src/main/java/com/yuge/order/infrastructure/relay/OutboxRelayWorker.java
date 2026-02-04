package com.yuge.order.infrastructure.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.order.domain.entity.OutboxEvent;
import com.yuge.order.domain.enums.OutboxStatus;
import com.yuge.order.infrastructure.repository.OutboxEventRepository;
import com.yuge.platform.infra.mq.BaseEvent;
import com.yuge.platform.infra.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox Relay Worker
 * 
 * 负责将 Outbox 表中的事件投递到 RocketMQ
 * 
 * 特性：
 * 1. 支持水平扩展（使用 FOR UPDATE SKIP LOCKED）
 * 2. 指数退避重试策略
 * 3. 超过阈值标记为 DEAD 并告警
 * 4. traceId 透传
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class OutboxRelayWorker {

    private final OutboxEventRepository outboxEventRepository;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 每次处理的批量大小
     */
    @Value("${outbox.relay.batch-size:100}")
    private int batchSize;

    /**
     * 基础重试间隔（秒）
     */
    @Value("${outbox.relay.base-retry-interval:5}")
    private int baseRetryInterval;

    /**
     * 最大重试间隔（秒）
     */
    @Value("${outbox.relay.max-retry-interval:3600}")
    private int maxRetryInterval;

    /**
     * 定时扫描并投递消息
     * 每秒执行一次
     */
    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval:1000}")
    public void relay() {
        try {
            processOutboxEvents();
        } catch (Exception e) {
            log.error("[OutboxRelay] Unexpected error during relay", e);
        }
    }

    /**
     * 处理 Outbox 事件
     */
    @Transactional(rollbackFor = Exception.class)
    public void processOutboxEvents() {
        // 1. 查询待处理的事件（带行锁）
        List<OutboxEvent> events = outboxEventRepository.findProcessableEventsForUpdate(batchSize);
        
        if (events.isEmpty()) {
            return;
        }

        log.debug("[OutboxRelay] Found {} events to process", events.size());

        // 2. 逐个处理
        for (OutboxEvent event : events) {
            processEvent(event);
        }
    }

    /**
     * 处理单个事件
     */
    private void processEvent(OutboxEvent event) {
        String eventId = event.getEventId();
        String traceId = event.getTraceId();
        
        // 设置 traceId 用于日志追踪
        if (traceId != null && !traceId.isEmpty()) {
            TraceContext.setTraceId(traceId);
            MDC.put(TraceContext.TRACE_ID_MDC_KEY, traceId);
        }

        try {
            log.info("[OutboxRelay] Processing event, eventId={}, bizKey={}, topic={}, tag={}, retryCount={}",
                    eventId, event.getBizKey(), event.getTopic(), event.getTag(), event.getRetryCount());

            // 3. 构建消息并发送
            SendResult result = sendToRocketMQ(event);

            if (result != null && result.getSendStatus() == SendStatus.SEND_OK) {
                // 4. 发送成功，标记为 SENT
                boolean updated = outboxEventRepository.markAsSent(eventId, event.getVersion());
                if (updated) {
                    log.info("[OutboxRelay] Event sent successfully, eventId={}, msgId={}", 
                            eventId, result.getMsgId());
                }
            } else {
                // 发送失败
                handleSendFailure(event, "Send status: " + (result != null ? result.getSendStatus() : "null"));
            }

        } catch (Exception e) {
            log.error("[OutboxRelay] Failed to send event, eventId={}, error={}", eventId, e.getMessage(), e);
            handleSendFailure(event, e.getMessage());
        } finally {
            TraceContext.clear();
            MDC.remove(TraceContext.TRACE_ID_MDC_KEY);
        }
    }

    /**
     * 发送消息到 RocketMQ
     */
    private SendResult sendToRocketMQ(OutboxEvent event) {
        // 构建目标地址
        String destination = buildDestination(event.getTopic(), event.getTag());

        // 构建消息
        Message<String> message = buildMessage(event);

        // 同步发送
        return rocketMQTemplate.syncSend(destination, message);
    }

    /**
     * 构建消息
     */
    private Message<String> buildMessage(OutboxEvent event) {
        try {
            // 构建 BaseEvent 包装
            BaseEvent baseEvent = new BaseEvent();
            baseEvent.setMessageId(event.getEventId());
            baseEvent.setBusinessKey(event.getBizKey());
            baseEvent.setTraceId(event.getTraceId());
            baseEvent.setEventType(event.getTag());
            baseEvent.setVersion("1.0");
            baseEvent.setEventTime(event.getCreatedAt());
            baseEvent.setSource("order-service");
            baseEvent.setPayload(event.getPayloadJson());

            String messageBody = objectMapper.writeValueAsString(baseEvent);

            return MessageBuilder.withPayload(messageBody)
                    .setHeader("KEYS", event.getEventId())  // 使用 event_id 作为 keys
                    .setHeader("traceId", event.getTraceId())
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to build message", e);
        }
    }

    /**
     * 构建目标地址
     */
    private String buildDestination(String topic, String tag) {
        if (tag != null && !tag.isEmpty()) {
            return topic + ":" + tag;
        }
        return topic;
    }

    /**
     * 处理发送失败
     */
    private void handleSendFailure(OutboxEvent event, String errorMessage) {
        String eventId = event.getEventId();
        int currentRetryCount = event.getRetryCount() != null ? event.getRetryCount() : 0;
        int maxRetry = event.getMaxRetry() != null ? event.getMaxRetry() : 5;

        // 截断错误信息
        String truncatedError = errorMessage;
        if (truncatedError != null && truncatedError.length() > 500) {
            truncatedError = truncatedError.substring(0, 500);
        }

        if (currentRetryCount + 1 >= maxRetry) {
            // 超过最大重试次数，标记为 DEAD
            boolean updated = outboxEventRepository.markAsDead(eventId, truncatedError, event.getVersion());
            if (updated) {
                // 告警日志
                log.error("[OutboxRelay] [ALERT] Event marked as DEAD after {} retries, eventId={}, bizKey={}, topic={}, lastError={}",
                        currentRetryCount + 1, eventId, event.getBizKey(), event.getTopic(), truncatedError);
            }
        } else {
            // 计算下次重试时间（指数退避）
            LocalDateTime nextRetryAt = calculateNextRetryTime(currentRetryCount + 1);
            boolean updated = outboxEventRepository.markAsRetry(eventId, nextRetryAt, truncatedError, event.getVersion());
            if (updated) {
                log.warn("[OutboxRelay] Event marked for retry, eventId={}, retryCount={}, nextRetryAt={}",
                        eventId, currentRetryCount + 1, nextRetryAt);
            }
        }
    }

    /**
     * 计算下次重试时间（指数退避）
     * 
     * 重试间隔: baseInterval * 2^(retryCount-1)
     * 例如 baseInterval=5s: 5s, 10s, 20s, 40s, 80s...
     */
    private LocalDateTime calculateNextRetryTime(int retryCount) {
        // 指数退避: baseInterval * 2^(retryCount-1)
        long intervalSeconds = (long) (baseRetryInterval * Math.pow(2, retryCount - 1));
        
        // 限制最大间隔
        intervalSeconds = Math.min(intervalSeconds, maxRetryInterval);
        
        return LocalDateTime.now().plusSeconds(intervalSeconds);
    }
}
