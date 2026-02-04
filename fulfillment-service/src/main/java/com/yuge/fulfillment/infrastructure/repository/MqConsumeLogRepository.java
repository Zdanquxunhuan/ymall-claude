package com.yuge.fulfillment.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.yuge.fulfillment.domain.entity.MqConsumeLog;
import com.yuge.fulfillment.domain.enums.ConsumeStatus;
import com.yuge.fulfillment.infrastructure.mapper.MqConsumeLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * 尝试获取消费锁
     *
     * @param eventId       事件ID
     * @param consumerGroup 消费者组
     * @param topic         主题
     * @param tags          标签
     * @param bizKey        业务键
     * @return 已存在的记录（如果有）
     */
    public Optional<MqConsumeLog> tryAcquire(String eventId, String consumerGroup,
                                              String topic, String tags, String bizKey) {
        // 先查询是否已存在
        MqConsumeLog existing = mqConsumeLogMapper.selectOne(
                new LambdaQueryWrapper<MqConsumeLog>()
                        .eq(MqConsumeLog::getEventId, eventId)
                        .eq(MqConsumeLog::getConsumerGroup, consumerGroup)
        );

        if (existing != null) {
            return Optional.of(existing);
        }

        // 尝试插入
        MqConsumeLog newLog = MqConsumeLog.builder()
                .id(IdWorker.getId())
                .eventId(eventId)
                .consumerGroup(consumerGroup)
                .topic(topic)
                .tags(tags)
                .bizKey(bizKey)
                .status(ConsumeStatus.PROCESSING.getCode())
                .build();

        int inserted = mqConsumeLogMapper.insertIgnore(newLog);
        if (inserted > 0) {
            // 插入成功，返回空表示可以继续处理
            return Optional.empty();
        }

        // 插入失败（并发），重新查询
        existing = mqConsumeLogMapper.selectOne(
                new LambdaQueryWrapper<MqConsumeLog>()
                        .eq(MqConsumeLog::getEventId, eventId)
                        .eq(MqConsumeLog::getConsumerGroup, consumerGroup)
        );
        return Optional.ofNullable(existing);
    }

    /**
     * 标记消费成功
     */
    public void markSuccess(String eventId, String consumerGroup, String resultMsg, Long costMs) {
        mqConsumeLogMapper.markSuccess(eventId, consumerGroup, resultMsg, costMs);
    }

    /**
     * 标记消费失败
     */
    public void markFailed(String eventId, String consumerGroup, String resultMsg, Long costMs) {
        mqConsumeLogMapper.markFailed(eventId, consumerGroup, resultMsg, costMs);
    }

    /**
     * 标记消费已忽略
     */
    public void markIgnored(String eventId, String consumerGroup, String resultMsg, String ignoredReason, Long costMs) {
        mqConsumeLogMapper.markIgnored(eventId, consumerGroup, resultMsg, ignoredReason, costMs);
    }
}
