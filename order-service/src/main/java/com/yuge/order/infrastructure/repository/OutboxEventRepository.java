package com.yuge.order.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.order.domain.entity.OutboxEvent;
import com.yuge.order.domain.enums.OutboxStatus;
import com.yuge.order.infrastructure.mapper.OutboxEventMapper;
import com.yuge.platform.infra.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Outbox事件仓储
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OutboxEventRepository {

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    /**
     * 订单Topic
     */
    public static final String TOPIC_ORDER = "ORDER_TOPIC";
    
    /**
     * 订单创建Tag
     */
    public static final String TAG_ORDER_CREATED = "ORDER_CREATED";
    
    /**
     * 订单取消Tag
     */
    public static final String TAG_ORDER_CANCELED = "ORDER_CANCELED";

    /**
     * 默认最大重试次数
     */
    private static final int DEFAULT_MAX_RETRY = 5;

    /**
     * 保存Outbox事件
     *
     * @param bizKey  业务键
     * @param topic   主题
     * @param tag     标签
     * @param payload 消息体
     * @return 事件ID
     */
    public String saveEvent(String bizKey, String topic, String tag, Object payload) {
        String eventId = generateEventId();
        
        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setBizKey(bizKey);
        event.setTopic(topic);
        event.setTag(tag);
        event.setStatus(OutboxStatus.NEW.getCode());
        event.setRetryCount(0);
        event.setMaxRetry(DEFAULT_MAX_RETRY);
        event.setVersion(0);
        event.setTraceId(TraceContext.getTraceId());
        
        try {
            event.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error("[Outbox] Failed to serialize payload, bizKey={}", bizKey, e);
            throw new RuntimeException("序列化消息体失败", e);
        }
        
        outboxEventMapper.insert(event);
        log.info("[Outbox] Event saved, eventId={}, bizKey={}, topic={}, tag={}", 
                eventId, bizKey, topic, tag);
        
        return eventId;
    }

    /**
     * 保存订单创建事件
     */
    public String saveOrderCreatedEvent(String orderNo, Object payload) {
        return saveEvent(orderNo, TOPIC_ORDER, TAG_ORDER_CREATED, payload);
    }

    /**
     * 保存订单取消事件
     */
    public String saveOrderCanceledEvent(String orderNo, Object payload) {
        return saveEvent(orderNo, TOPIC_ORDER, TAG_ORDER_CANCELED, payload);
    }

    /**
     * 查询待处理的事件（带行锁，用于Relay Worker）
     */
    public List<OutboxEvent> findProcessableEventsForUpdate(int limit) {
        return outboxEventMapper.selectProcessableEventsForUpdate(limit);
    }

    /**
     * 查询待处理的事件（不加锁，用于监控）
     */
    public List<OutboxEvent> findProcessableEvents(int limit) {
        return outboxEventMapper.selectProcessableEvents(limit);
    }

    /**
     * 标记为已发送
     */
    public boolean markAsSent(String eventId, Integer version) {
        int rows = outboxEventMapper.markAsSent(eventId, version);
        if (rows > 0) {
            log.info("[Outbox] Event marked as SENT, eventId={}", eventId);
            return true;
        }
        log.warn("[Outbox] Failed to mark event as SENT (concurrent update?), eventId={}", eventId);
        return false;
    }

    /**
     * 标记为重试
     */
    public boolean markAsRetry(String eventId, LocalDateTime nextRetryAt, String lastError, Integer version) {
        int rows = outboxEventMapper.markAsRetry(eventId, nextRetryAt, lastError, version);
        if (rows > 0) {
            log.info("[Outbox] Event marked as RETRY, eventId={}, nextRetryAt={}", eventId, nextRetryAt);
            return true;
        }
        log.warn("[Outbox] Failed to mark event as RETRY, eventId={}", eventId);
        return false;
    }

    /**
     * 标记为死信
     */
    public boolean markAsDead(String eventId, String lastError, Integer version) {
        int rows = outboxEventMapper.markAsDead(eventId, lastError, version);
        if (rows > 0) {
            log.warn("[Outbox] Event marked as DEAD, eventId={}, lastError={}", eventId, lastError);
            return true;
        }
        log.warn("[Outbox] Failed to mark event as DEAD, eventId={}", eventId);
        return false;
    }

    /**
     * 根据事件ID查询
     */
    public OutboxEvent findByEventId(String eventId) {
        return outboxEventMapper.selectByEventId(eventId);
    }

    /**
     * 查询死信事件
     */
    public List<OutboxEvent> findDeadEvents(int limit) {
        return outboxEventMapper.selectDeadEvents(limit);
    }

    /**
     * 生成事件ID
     */
    private String generateEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
