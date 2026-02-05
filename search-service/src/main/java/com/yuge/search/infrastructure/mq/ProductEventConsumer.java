package com.yuge.search.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.platform.infra.mq.BaseEvent;
import com.yuge.platform.infra.mq.ConsumerTemplate;
import com.yuge.search.domain.event.ProductPublishedEvent;
import com.yuge.search.domain.event.ProductUpdatedEvent;
import com.yuge.search.domain.model.ProductDocument;
import com.yuge.search.domain.service.SearchIndexService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 商品事件消费者
 * 消费 ProductPublished/ProductUpdated 事件，更新搜索索引
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "PRODUCT_TOPIC",
        selectorExpression = "PRODUCT_PUBLISHED || PRODUCT_UPDATED",
        consumerGroup = "search-consumer-group"
)
public class ProductEventConsumer extends ConsumerTemplate implements RocketMQListener<MessageExt> {

    @Autowired
    private SearchIndexService searchIndexService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageExt messageExt) {
        consumeMessage(messageExt);
    }

    @Override
    protected void doConsume(BaseEvent event, MessageExt messageExt) throws Exception {
        String eventType = event.getEventType();
        String payload = event.getPayload();
        String messageId = event.getMessageId();

        log.info("[ProductEventConsumer] Processing event: type={}, messageId={}", eventType, messageId);

        switch (eventType) {
            case "PRODUCT_PUBLISHED":
                handleProductPublished(payload, messageId);
                break;
            case "PRODUCT_UPDATED":
                handleProductUpdated(payload, messageId);
                break;
            default:
                log.warn("[ProductEventConsumer] Unknown event type: {}", eventType);
        }
    }

    /**
     * 处理商品发布事件
     */
    private void handleProductPublished(String payload, String messageId) throws Exception {
        ProductPublishedEvent event = objectMapper.readValue(payload, ProductPublishedEvent.class);
        
        Long skuId = event.getSkuId();
        String eventId = event.getEventId();

        // 幂等检查：按 eventId 去重
        if (searchIndexService.isEventProcessed(skuId, eventId)) {
            log.info("[ProductEventConsumer] Event already processed, skipping: skuId={}, eventId={}", 
                    skuId, eventId);
            return;
        }

        // 构建文档
        ProductDocument document = ProductDocument.builder()
                .skuId(skuId)
                .spuId(event.getSpuId())
                .title(event.getTitle())
                .attrsJson(event.getAttrsJson())
                .price(event.getPrice())
                .categoryId(event.getCategoryId())
                .brandId(event.getBrandId())
                .skuCode(event.getSkuCode())
                .status("PUBLISHED")
                .publishTime(event.getPublishTime())
                .indexTime(LocalDateTime.now())
                .eventVersion(event.getVersion())
                .lastEventId(eventId)
                .build();

        // 索引文档
        boolean success = searchIndexService.indexDocument(document);
        
        if (success) {
            log.info("[ProductEventConsumer] Product indexed successfully: skuId={}, title={}", 
                    skuId, event.getTitle());
        } else {
            log.error("[ProductEventConsumer] Failed to index product: skuId={}", skuId);
            throw new RuntimeException("Failed to index product: " + skuId);
        }
    }

    /**
     * 处理商品更新事件
     */
    private void handleProductUpdated(String payload, String messageId) throws Exception {
        ProductUpdatedEvent event = objectMapper.readValue(payload, ProductUpdatedEvent.class);
        
        Long skuId = event.getSkuId();
        String eventId = event.getEventId();

        // 幂等检查：按 eventId 去重
        if (searchIndexService.isEventProcessed(skuId, eventId)) {
            log.info("[ProductEventConsumer] Event already processed, skipping: skuId={}, eventId={}", 
                    skuId, eventId);
            return;
        }

        // 获取现有文档
        ProductDocument existingDoc = searchIndexService.getDocument(skuId);
        
        // 构建更新后的文档
        ProductDocument document = ProductDocument.builder()
                .skuId(skuId)
                .spuId(event.getSpuId())
                .title(event.getTitle())
                .attrsJson(event.getAttrsJson())
                .price(event.getPrice())
                .categoryId(event.getCategoryId())
                .brandId(event.getBrandId())
                .skuCode(event.getSkuCode())
                .status(event.getStatus())
                .publishTime(existingDoc != null ? existingDoc.getPublishTime() : null)
                .indexTime(LocalDateTime.now())
                .eventVersion(event.getVersion())
                .lastEventId(eventId)
                .build();

        // 索引文档
        boolean success = searchIndexService.indexDocument(document);
        
        if (success) {
            log.info("[ProductEventConsumer] Product updated successfully: skuId={}, updatedFields={}", 
                    skuId, event.getUpdatedFields());
        } else {
            log.error("[ProductEventConsumer] Failed to update product: skuId={}", skuId);
            throw new RuntimeException("Failed to update product: " + skuId);
        }
    }

    @Override
    protected boolean shouldRetry(Exception e, int reconsumeTimes) {
        // 最多重试5次
        return reconsumeTimes < 5;
    }
}
