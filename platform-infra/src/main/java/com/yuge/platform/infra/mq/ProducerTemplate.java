package com.yuge.platform.infra.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.platform.infra.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * RocketMQ 生产者模板
 * 封装消息发送逻辑，自动注入 traceId、messageId 等
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProducerTemplate {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 同步发送消息
     */
    public SendResult syncSend(String topic, String tag, Object payload) {
        return syncSend(topic, tag, null, payload);
    }

    /**
     * 同步发送消息（带业务键）
     */
    public SendResult syncSend(String topic, String tag, String businessKey, Object payload) {
        String destination = buildDestination(topic, tag);
        Message<String> message = buildMessage(businessKey, payload);
        
        log.info("[MQ-Producer] Sending message, destination={}, businessKey={}, traceId={}",
                destination, businessKey, TraceContext.getTraceId());
        
        try {
            SendResult result = rocketMQTemplate.syncSend(destination, message);
            log.info("[MQ-Producer] Message sent successfully, msgId={}, destination={}",
                    result.getMsgId(), destination);
            return result;
        } catch (Exception e) {
            log.error("[MQ-Producer] Failed to send message, destination={}, error={}",
                    destination, e.getMessage(), e);
            throw new RuntimeException("消息发送失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步发送消息
     */
    public void asyncSend(String topic, String tag, Object payload, SendCallback callback) {
        asyncSend(topic, tag, null, payload, callback);
    }

    /**
     * 异步发送消息（带业务键）
     */
    public void asyncSend(String topic, String tag, String businessKey, Object payload, SendCallback callback) {
        String destination = buildDestination(topic, tag);
        Message<String> message = buildMessage(businessKey, payload);
        
        log.info("[MQ-Producer] Async sending message, destination={}, businessKey={}, traceId={}",
                destination, businessKey, TraceContext.getTraceId());
        
        rocketMQTemplate.asyncSend(destination, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("[MQ-Producer] Async message sent successfully, msgId={}, destination={}",
                        sendResult.getMsgId(), destination);
                if (callback != null) {
                    callback.onSuccess(sendResult);
                }
            }

            @Override
            public void onException(Throwable e) {
                log.error("[MQ-Producer] Async message send failed, destination={}, error={}",
                        destination, e.getMessage(), e);
                if (callback != null) {
                    callback.onException(e);
                }
            }
        });
    }

    /**
     * 发送单向消息（不关心结果）
     */
    public void sendOneWay(String topic, String tag, Object payload) {
        String destination = buildDestination(topic, tag);
        Message<String> message = buildMessage(null, payload);
        
        log.info("[MQ-Producer] Sending one-way message, destination={}, traceId={}",
                destination, TraceContext.getTraceId());
        
        rocketMQTemplate.sendOneWay(destination, message);
    }

    /**
     * 发送延迟消息
     * delayLevel: 1-18 对应 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     */
    public SendResult syncSendDelayMessage(String topic, String tag, Object payload, int delayLevel) {
        String destination = buildDestination(topic, tag);
        Message<String> message = buildMessage(null, payload);
        
        log.info("[MQ-Producer] Sending delay message, destination={}, delayLevel={}, traceId={}",
                destination, delayLevel, TraceContext.getTraceId());
        
        try {
          SendResult result = rocketMQTemplate.syncSend(destination, message, 3000, delayLevel);
            log.info("[MQ-Producer] Delay message sent successfully, msgId={}, destination={}",
                    result.getMsgId(), destination);
            return result;
        } catch (Exception e) {
            log.error("[MQ-Producer] Failed to send delay message, destination={}, error={}",
                    destination, e.getMessage(), e);
            throw new RuntimeException("延迟消息发送失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送顺序消息
     */
    public SendResult syncSendOrderly(String topic, String tag, Object payload, String hashKey) {
        String destination = buildDestination(topic, tag);
        Message<String> message = buildMessage(hashKey, payload);
        
        log.info("[MQ-Producer] Sending orderly message, destination={}, hashKey={}, traceId={}",
                destination, hashKey, TraceContext.getTraceId());
        
        try {
            SendResult result = rocketMQTemplate.syncSendOrderly(destination, message, hashKey);
            log.info("[MQ-Producer] Orderly message sent successfully, msgId={}, destination={}",
                    result.getMsgId(), destination);
            return result;
        } catch (Exception e) {
            log.error("[MQ-Producer] Failed to send orderly message, destination={}, error={}",
                    destination, e.getMessage(), e);
            throw new RuntimeException("顺序消息发送失败: " + e.getMessage(), e);
        }
    }

    private String buildDestination(String topic, String tag) {
        if (tag != null && !tag.isEmpty()) {
            return topic + ":" + tag;
        }
        return topic;
    }

    private Message<String> buildMessage(String businessKey, Object payload) {
        BaseEvent event = new BaseEvent();
        event.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        event.setBusinessKey(businessKey);
        event.setTraceId(TraceContext.getTraceId());
        event.setEventTime(LocalDateTime.now());
        event.setSource("demo-service");
        event.setVersion("1.0");
        
        try {
            if (payload instanceof String) {
                event.setPayload((String) payload);
            } else {
                event.setPayload(objectMapper.writeValueAsString(payload));
            }
            event.setEventType(payload.getClass().getSimpleName());
            
            String messageBody = objectMapper.writeValueAsString(event);
            
            return MessageBuilder.withPayload(messageBody)
                    .setHeader("KEYS", businessKey != null ? businessKey : event.getMessageId())
                    .setHeader("traceId", event.getTraceId())
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("消息序列化失败", e);
        }
    }
}
