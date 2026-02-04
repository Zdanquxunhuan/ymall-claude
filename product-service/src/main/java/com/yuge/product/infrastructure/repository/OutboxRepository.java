package com.yuge.product.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yuge.product.domain.entity.Outbox;
import com.yuge.product.domain.enums.OutboxStatus;
import com.yuge.product.infrastructure.mapper.OutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Outbox Repository
 */
@Repository
@RequiredArgsConstructor
public class OutboxRepository {

    private final OutboxMapper outboxMapper;

    /**
     * 保存Outbox记录
     */
    public void save(Outbox outbox) {
        if (outbox.getId() == null) {
            outbox.setCreatedAt(LocalDateTime.now());
            outbox.setUpdatedAt(LocalDateTime.now());
            outboxMapper.insert(outbox);
        } else {
            outbox.setUpdatedAt(LocalDateTime.now());
            outboxMapper.updateById(outbox);
        }
    }

    /**
     * 根据ID查询
     */
    public Optional<Outbox> findById(Long id) {
        return Optional.ofNullable(outboxMapper.selectById(id));
    }

    /**
     * 根据事件ID查询
     */
    public Optional<Outbox> findByEventId(String eventId) {
        LambdaQueryWrapper<Outbox> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Outbox::getEventId, eventId);
        return Optional.ofNullable(outboxMapper.selectOne(wrapper));
    }

    /**
     * 查询待发送的消息（带行锁，防止并发处理）
     */
    public List<Outbox> findPendingWithLock(int limit) {
        return outboxMapper.selectPendingWithLock(limit);
    }

    /**
     * 查询待发送的消息（不带锁，用于测试）
     */
    public List<Outbox> findPending(int limit) {
        LambdaQueryWrapper<Outbox> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Outbox::getStatus, OutboxStatus.PENDING.getCode())
                .and(w -> w.isNull(Outbox::getNextRetryAt)
                        .or()
                        .le(Outbox::getNextRetryAt, LocalDateTime.now()))
                .orderByAsc(Outbox::getCreatedAt)
                .last("LIMIT " + limit);
        return outboxMapper.selectList(wrapper);
    }

    /**
     * 更新状态为已发送
     */
    public boolean markAsSent(Long id, Integer version) {
        LambdaUpdateWrapper<Outbox> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Outbox::getId, id)
                .eq(Outbox::getVersion, version)
                .set(Outbox::getStatus, OutboxStatus.SENT.getCode())
                .set(Outbox::getUpdatedAt, LocalDateTime.now());
        return outboxMapper.update(null, wrapper) > 0;
    }

    /**
     * 更新状态为失败并设置下次重试时间
     */
    public boolean markAsFailed(Long id, Integer version, String errorMessage, LocalDateTime nextRetryAt) {
        LambdaUpdateWrapper<Outbox> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Outbox::getId, id)
                .eq(Outbox::getVersion, version)
                .set(Outbox::getStatus, OutboxStatus.FAILED.getCode())
                .set(Outbox::getErrorMessage, errorMessage)
                .set(Outbox::getNextRetryAt, nextRetryAt)
                .setSql("retry_count = retry_count + 1")
                .set(Outbox::getUpdatedAt, LocalDateTime.now());
        return outboxMapper.update(null, wrapper) > 0;
    }

    /**
     * 重置为待发送状态
     */
    public boolean resetToPending(Long id, Integer version) {
        LambdaUpdateWrapper<Outbox> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Outbox::getId, id)
                .eq(Outbox::getVersion, version)
                .set(Outbox::getStatus, OutboxStatus.PENDING.getCode())
                .set(Outbox::getUpdatedAt, LocalDateTime.now());
        return outboxMapper.update(null, wrapper) > 0;
    }
}
