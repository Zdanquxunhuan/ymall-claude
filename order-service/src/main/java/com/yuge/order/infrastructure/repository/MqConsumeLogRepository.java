package com.yuge.order.infrastructure.repository;

import com.yuge.order.domain.entity.MqConsumeLog;
import com.yuge.order.domain.enums.ConsumeStatus;
import com.yuge.order.infrastructure.mapper.MqConsumeLogMapper;
import com.yuge.platform.infra.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MQ消费日志仓储
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MqConsumeLogRepository {

    private final MqConsumeLogMapper mqConsumeLogMapper;

    /**
     * 尝试获取消费锁（幂等检查）
     * 
     * @param eventId 事件ID
     * @param consumerGroup 消费者组
     * @param topic 主题
     * @param tag 标签
     * @param bizKey 业务键
     * @return 如果已存在记录返回 Optional.of(record)，否则返回 Optional.empty()
     */
    public Optional<MqConsumeLog> tryAcquire(String eventId, String consumerGroup, 
                                              String topic, String tag, String bizKey) {
        // 1. 先查询是否已存在
        MqConsumeLog existing = mqConsumeLogMapper.selectByEventIdAndGroup(eventId, consumerGroup);
        if (existing != null) {
            log.info("[MqConsumeLog] Event already processed, eventId={}, consumerGroup={}, status={}",
                    eventId, consumerGroup, existing.getStatus());
            return Optional.of(existing);
        }

        // 2. 尝试插入（利用唯一索引保证幂等）
        try {
            MqConsumeLog consumeLog = new MqConsumeLog();
            consumeLog.setEventId(eventId);
            consumeLog.setConsumerGroup(consumerGroup);
            consumeLog.setStatus(ConsumeStatus.PROCESSING.getCode());
            consumeLog.setTopic(topic);
            consumeLog.setTag(tag);
            consumeLog.setBizKey(bizKey);
            consumeLog.setTraceId(TraceContext.getTraceId());
            
            mqConsumeLogMapper.insert(consumeLog);
            log.info("[MqConsumeLog] Acquired consume lock, eventId={}, consumerGroup={}", 
                    eventId, consumerGroup);
            return Optional.empty();
            
        } catch (DuplicateKeyException e) {
            // 并发插入，说明已被其他实例处理
            log.info("[MqConsumeLog] Event already being processed by another instance, eventId={}, consumerGroup={}",
                    eventId, consumerGroup);
            MqConsumeLog record = mqConsumeLogMapper.selectByEventIdAndGroup(eventId, consumerGroup);
            return Optional.ofNullable(record);
        }
    }

    /**
     * 标记消费成功
     */
    public boolean markSuccess(String eventId, String consumerGroup, String result, Long costMs) {
        int rows = mqConsumeLogMapper.markAsSuccess(eventId, consumerGroup, result, costMs);
        if (rows > 0) {
            log.info("[MqConsumeLog] Marked as SUCCESS, eventId={}, consumerGroup={}, costMs={}",
                    eventId, consumerGroup, costMs);
            return true;
        }
        return false;
    }

    /**
     * 标记消费失败
     */
    public boolean markFailed(String eventId, String consumerGroup, String result, Long costMs) {
        int rows = mqConsumeLogMapper.markAsFailed(eventId, consumerGroup, result, costMs);
        if (rows > 0) {
            log.warn("[MqConsumeLog] Marked as FAILED, eventId={}, consumerGroup={}, result={}",
                    eventId, consumerGroup, result);
            return true;
        }
        return false;
    }

    /**
     * 标记为已忽略（乱序消息）
     */
    public boolean markIgnored(String eventId, String consumerGroup, String result, 
                               String ignoredReason, Long costMs) {
        int rows = mqConsumeLogMapper.markAsIgnored(eventId, consumerGroup, result, ignoredReason, costMs);
        if (rows > 0) {
            log.warn("[MqConsumeLog] Marked as IGNORED, eventId={}, consumerGroup={}, reason={}",
                    eventId, consumerGroup, ignoredReason);
            return true;
        }
        return false;
    }

    /**
     * 查询消费记录
     */
    public MqConsumeLog findByEventIdAndGroup(String eventId, String consumerGroup) {
        return mqConsumeLogMapper.selectByEventIdAndGroup(eventId, consumerGroup);
    }
}
