package com.yuge.inventory.infrastructure.repository;

import com.yuge.inventory.domain.entity.MqConsumeLog;
import com.yuge.inventory.domain.enums.ConsumeStatus;
import com.yuge.inventory.infrastructure.mapper.MqConsumeLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * MQ消费日志仓储
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MqConsumeLogRepository {

    private final MqConsumeLogMapper consumeLogMapper;

    /**
     * 尝试获取消费锁（幂等检查）
     * 
     * @return 如果已存在记录则返回该记录，否则插入新记录并返回空
     */
    public Optional<MqConsumeLog> tryAcquire(String eventId, String consumerGroup, 
                                              String topic, String tags, String bizKey) {
        // 先查询是否已存在
        Optional<MqConsumeLog> existing = consumeLogMapper.findByEventIdAndConsumerGroup(eventId, consumerGroup);
        if (existing.isPresent()) {
            return existing;
        }

        // 尝试插入新记录
        MqConsumeLog log = new MqConsumeLog();
        log.setEventId(eventId);
        log.setConsumerGroup(consumerGroup);
        log.setTopic(topic);
        log.setTags(tags);
        log.setBizKey(bizKey);
        log.setStatus(ConsumeStatus.PROCESSING.getCode());
        log.setCreatedAt(LocalDateTime.now());
        log.setUpdatedAt(LocalDateTime.now());

        try {
            consumeLogMapper.insert(log);
            return Optional.empty(); // 插入成功，返回空表示可以继续处理
        } catch (DuplicateKeyException e) {
            // 并发插入，重新查询
            return consumeLogMapper.findByEventIdAndConsumerGroup(eventId, consumerGroup);
        }
    }

    /**
     * 标记消费成功
     */
    public void markSuccess(String eventId, String consumerGroup, String result, long costMs) {
        consumeLogMapper.markSuccess(eventId, consumerGroup, result, costMs);
    }

    /**
     * 标记消费失败
     */
    public void markFailed(String eventId, String consumerGroup, String result, long costMs) {
        consumeLogMapper.markFailed(eventId, consumerGroup, result, costMs);
    }
}
