package com.yuge.platform.infra.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.platform.infra.idempotent.IdempotencyService;
import com.yuge.platform.infra.trace.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * RocketMQ 消费者模板
 * 提供消息消费的通用处理逻辑：
 * 1. traceId 透传
 * 2. 消费幂等
 * 3. 异常处理与重试
 * 4. 结构化日志
 */
@Slf4j
public abstract class ConsumerTemplate {

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected IdempotencyService idempotencyService;

    /**
     * 消费消息入口
     * 子类实现 RocketMQListener 接口时调用此方法
     */
    protected void consumeMessage(MessageExt messageExt) {
        String msgId = messageExt.getMsgId();
        String topic = messageExt.getTopic();
        String tags = messageExt.getTags();
        int reconsumeTimes = messageExt.getReconsumeTimes();
        
        // 1. 解析消息体
        BaseEvent event = parseMessage(messageExt);
        if (event == null) {
            log.error("[MQ-Consumer] Failed to parse message, msgId={}, topic={}", msgId, topic);
            return; // 解析失败不重试
        }
        
        // 2. 设置 traceId
        String traceId = event.getTraceId();
        if (traceId == null || traceId.isEmpty()) {
            traceId = TraceContext.generateTraceId();
        }
        TraceContext.setTraceId(traceId);
        MDC.put(TraceContext.TRACE_ID_MDC_KEY, traceId);
        
        try {
            log.info("[MQ-Consumer] Received message, msgId={}, topic={}, tags={}, reconsumeTimes={}, traceId={}",
                    msgId, topic, tags, reconsumeTimes, traceId);
            
            // 3. 消费幂等检查
            String idempotentKey = "mq:" + topic + ":" + event.getMessageId();
            Optional<com.yuge.platform.infra.idempotent.IdempotentRecord> existingRecord = 
                    idempotencyService.tryAcquire(idempotentKey, 24, TimeUnit.HOURS);
            
            if (existingRecord.isPresent()) {
                log.info("[MQ-Consumer] Message already consumed, msgId={}, idempotentKey={}", 
                        msgId, idempotentKey);
                return;
            }
            
            // 4. 执行业务逻辑
            doConsume(event, messageExt);
            
            // 5. 标记消费成功
            idempotencyService.markSuccess(idempotentKey, null);
            
            log.info("[MQ-Consumer] Message consumed successfully, msgId={}, topic={}", msgId, topic);
            
        } catch (Exception e) {
            log.error("[MQ-Consumer] Failed to consume message, msgId={}, topic={}, reconsumeTimes={}, error={}",
                    msgId, topic, reconsumeTimes, e.getMessage(), e);
            
            // 判断是否需要重试
            if (shouldRetry(e, reconsumeTimes)) {
                throw new RuntimeException("消费失败，触发重试", e);
            } else {
                log.error("[MQ-Consumer] Message will be sent to DLQ, msgId={}, topic={}", msgId, topic);
                // 这里可以发送到死信队列或告警
                onConsumeFailed(event, messageExt, e);
            }
        } finally {
            TraceContext.clear();
            MDC.remove(TraceContext.TRACE_ID_MDC_KEY);
        }
    }

    /**
     * 解析消息
     */
    protected BaseEvent parseMessage(MessageExt messageExt) {
        try {
            String body = new String(messageExt.getBody(), StandardCharsets.UTF_;
            return objectMapper.readValue(body, BaseEvent.class);
        } catch (Exception e) {
            log.error("[MQ-Consumer] Failed to parse message body, msgId={}", messageExt.getMsgId(), e);
            return null;
        }
    }

    /**
     * 执行业务消费逻辑（子类实现）
     */
    protected abstract void doConsume(BaseEvent event, MessageExt messageExt) throws Exception;

    /**
     * 判断是否需要重试
     * 默认最多重试3次
     */
    protected boolean shouldRetry(Exception e, int reconsumeTimes) {
        return reconsumeTimes < 3;
    }

    /**
     * 消费失败处理（子类可覆盖）
     * 用于发送告警、记录日志等
     */
    protected void onConsumeFailed(BaseEvent event, MessageExt messageExt, Exception e) {
        log.error("[MQ-Consumer] Message consume failed finally, will be discarded. msgId={}, messageId={}",
                messageExt.getMsgId(), event != null ? event.getMessageId() : "null");
    }
}
