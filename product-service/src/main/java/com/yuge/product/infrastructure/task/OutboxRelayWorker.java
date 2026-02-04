package com.yuge.product.infrastructure.task;

import com.yuge.platform.infra.mq.ProducerTemplate;
import com.yuge.product.domain.entity.Outbox;
import com.yuge.product.domain.enums.EventType;
import com.yuge.product.infrastructure.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox Relay Worker
 * 定时扫描Outbox表，将待发送的事件发布到MQ
 * 实现最终一致性
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayWorker {

    private final OutboxRepository outboxRepository;
    private final ProducerTemplate producerTemplate;

    /**
     * 商品Topic
     */
    public static final String PRODUCT_TOPIC = "PRODUCT_TOPIC";

    @Value("${product.outbox.batch-size:100}")
    private int batchSize;

    @Value("${product.outbox.enabled:true}")
    private boolean enabled;

    /**
     * 定时扫描并发送Outbox消息
     * 每秒执行一次
     */
    @Scheduled(fixedDelayString = "${product.outbox.interval:1000}")
    public void relay() {
        if (!enabled) {
            return;
        }

        try {
            processOutboxMessages();
        } catch (Exception e) {
            log.error("Outbox relay error", e);
        }
    }

    /**
     * 处理Outbox消息
     */
    @Transactional(rollbackFor = Exception.class)
    public void processOutboxMessages() {
        // 查询待发送的消息（带行锁）
        List<Outbox> pendingList = outboxRepository.findPendingWithLock(batchSize);
        
        if (pendingList.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox messages", pendingList.size());

        for (Outbox outbox : pendingList) {
            try {
                sendMessage(outbox);
                // 标记为已发送
                boolean updated = outboxRepository.markAsSent(outbox.getId(), outbox.getVersion());
                if (updated) {
                    log.info("Outbox message sent successfully, eventId={}, eventType={}", 
                            outbox.getEventId(), outbox.getEventType());
                } else {
                    log.warn("Failed to mark outbox as sent (concurrent update), eventId={}", 
                            outbox.getEventId());
                }
            } catch (Exception e) {
                log.error("Failed to send outbox message, eventId={}", outbox.getEventId(), e);
                handleSendFailure(outbox, e);
            }
        }
    }

    /**
     * 发送消息到MQ
     */
    private void sendMessage(Outbox outbox) {
        String tag = getTagByEventType(outbox.getEventType());
        
        producerTemplate.sendSync(
                PRODUCT_TOPIC,
                tag,
                outbox.getEventId(),
                String.valueOf(outbox.getAggregateId()),
                outbox.getPayload()
        );
    }

    /**
     * 根据事件类型获取Tag
     */
    private String getTagByEventType(String eventType) {
        EventType type = EventType.fromCode(eventType);
        return switch (type) {
            case PRODUCT_PUBLISHED -> "PRODUCT_PUBLISHED";
            case PRODUCT_UPDATED -> "PRODUCT_UPDATED";
            case PRODUCT_OFFLINE -> "PRODUCT_OFFLINE";
        };
    }

    /**
     * 处理发送失败
     */
    private void handleSendFailure(Outbox outbox, Exception e) {
        int retryCount = outbox.getRetryCount() + 1;
        
        if (retryCount >= outbox.getMaxRetry()) {
            // 超过最大重试次数，标记为失败
            outboxRepository.markAsFailed(
                    outbox.getId(),
                    outbox.getVersion(),
                    e.getMessage(),
                    null
            );
            log.error("Outbox message exceeded max retry, eventId={}, retryCount={}", 
                    outbox.getEventId(), retryCount);
        } else {
            // 设置下次重试时间（指数退避）
            LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds((long) Math.pow(2, retryCount) * 10);
            outboxRepository.markAsFailed(
                    outbox.getId(),
                    outbox.getVersion(),
                    e.getMessage(),
                    nextRetryAt
            );
            log.warn("Outbox message will retry at {}, eventId={}, retryCount={}", 
                    nextRetryAt, outbox.getEventId(), retryCount);
        }
    }
}
