package com.yuge.inventory.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.inventory.application.InventoryService;
import com.yuge.inventory.application.InventoryService.ReserveResponse;
import com.yuge.inventory.domain.entity.MqConsumeLog;
import com.yuge.inventory.domain.enums.ConsumeStatus;
import com.yuge.inventory.domain.event.OrderCreatedEvent;
import com.yuge.inventory.domain.event.StockReservedEvent;
import com.yuge.inventory.domain.event.StockReserveFailedEvent;
import com.yuge.inventory.infrastructure.redis.InventoryRedisService.ReserveItem;
import com.yuge.inventory.infrastructure.repository.MqConsumeLogRepository;
import com.yuge.platform.infra.mq.BaseEvent;
import com.yuge.platform.infra.mq.ProducerTemplate;
import com.yuge.platform.infra.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 订单创建事件消费者
 * 
 * 消费 OrderCreated 事件，执行库存预留：
 * 1. 幂等检查（t_mq_consume_log）
 * 2. 调用 InventoryService.tryBatchReserve
 * 3. 成功发布 StockReserved 事件
 * 4. 失败发布 StockReserveFailed 事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "ORDER_TOPIC",
        selectorExpression = "ORDER_CREATED",
        consumerGroup = "inventory-stock-reserve-group"
)
public class OrderCreatedConsumer implements RocketMQListener<MessageExt> {

    private static final String CONSUMER_GROUP = "inventory-stock-reserve-group";
    private static final String INVENTORY_TOPIC = "INVENTORY_TOPIC";
    private static final String STOCK_RESERVED_TAG = "STOCK_RESERVED";
    private static final String STOCK_RESERVE_FAILED_TAG = "STOCK_RESERVE_FAILED";

    /**
     * 默认仓库ID（实际场景应根据用户地址、库存分布等选择仓库）
     */
    private static final Long DEFAULT_WAREHOUSE_ID = 1L;

    private final MqConsumeLogRepository mqConsumeLogRepository;
    private final InventoryService inventoryService;
    private final ProducerTemplate producerTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageExt messageExt) {
        String msgId = messageExt.getMsgId();
        String topic = messageExt.getTopic();
        String tags = messageExt.getTags();
        int reconsumeTimes = messageExt.getReconsumeTimes();
        long startTime = System.currentTimeMillis();

        // 1. 解析消息
        BaseEvent baseEvent = parseBaseEvent(messageExt);
        if (baseEvent == null) {
            log.error("[OrderCreatedConsumer] Failed to parse message, msgId={}", msgId);
            return;
        }

        String eventId = baseEvent.getMessageId();
        String bizKey = baseEvent.getBusinessKey();
        String traceId = baseEvent.getTraceId();

        // 2. 设置 traceId
        if (traceId != null && !traceId.isEmpty()) {
            TraceContext.setTraceId(traceId);
            MDC.put(TraceContext.TRACE_ID_MDC_KEY, traceId);
        }

        try {
            log.info("[OrderCreatedConsumer] Received message, eventId={}, bizKey={}, msgId={}, reconsumeTimes={}",
                    eventId, bizKey, msgId, reconsumeTimes);

            // 3. 幂等检查
            Optional<MqConsumeLog> existingRecord = mqConsumeLogRepository.tryAcquire(
                    eventId, CONSUMER_GROUP, topic, tags, bizKey);

            if (existingRecord.isPresent()) {
                MqConsumeLog record = existingRecord.get();
                if (ConsumeStatus.SUCCESS.getCode().equals(record.getStatus())) {
                    log.info("[OrderCreatedConsumer] Message already consumed successfully, eventId={}", eventId);
                    return;
                } else if (ConsumeStatus.PROCESSING.getCode().equals(record.getStatus())) {
                    log.info("[OrderCreatedConsumer] Message is being processed, eventId={}", eventId);
                    throw new RuntimeException("Message is being processed by another instance");
                }
            }

            // 4. 解析订单创建事件
            OrderCreatedEvent orderEvent = parseOrderCreatedEvent(baseEvent.getPayload());
            if (orderEvent == null) {
                log.error("[OrderCreatedConsumer] Failed to parse OrderCreatedEvent, eventId={}", eventId);
                mqConsumeLogRepository.markFailed(eventId, CONSUMER_GROUP, "解析订单事件失败", 
                        System.currentTimeMillis() - startTime);
                return;
            }

            // 5. 执行库存预留
            doReserveStock(orderEvent, eventId, startTime);

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startTime;
            log.error("[OrderCreatedConsumer] Failed to consume message, eventId={}, error={}",
                    eventId, e.getMessage(), e);

            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }
            mqConsumeLogRepository.markFailed(eventId, CONSUMER_GROUP, errorMsg, costMs);

            if (reconsumeTimes < 3) {
                throw new RuntimeException("消费失败，触发重试", e);
            } else {
                log.error("[OrderCreatedConsumer] Max retry reached, eventId={}", eventId);
            }
        } finally {
            TraceContext.clear();
            MDC.remove(TraceContext.TRACE_ID_MDC_KEY);
        }
    }

    /**
     * 执行库存预留
     */
    private void doReserveStock(OrderCreatedEvent orderEvent, String eventId, long startTime) {
        String orderNo = orderEvent.getOrderNo();
        List<OrderCreatedEvent.OrderItemInfo> items = orderEvent.getItems();

        log.info("[OrderCreatedConsumer] Processing stock reserve, orderNo={}, itemCount={}",
                orderNo, items.size());

        // 构建预留项
        List<ReserveItem> reserveItems = items.stream()
                .map(item -> ReserveItem.builder()
                        .skuId(item.getSkuId())
                        .warehouseId(item.getWarehouseId() != null ? item.getWarehouseId() : DEFAULT_WAREHOUSE_ID)
                        .qty(item.getQty())
                        .build())
                .collect(Collectors.toList());

        // 调用库存服务预留
        ReserveResponse response = inventoryService.tryBatchReserve(orderNo, reserveItems);

        long costMs = System.currentTimeMillis() - startTime;

        if (response.isSuccess()) {
            // 预留成功
            log.info("[OrderCreatedConsumer] Stock reserve success, orderNo={}", orderNo);
            mqConsumeLogRepository.markSuccess(eventId, CONSUMER_GROUP, "库存预留成功", costMs);

            // 发布 StockReserved 事件
            publishStockReservedEvent(orderNo, reserveItems, orderEvent.getTraceId());
        } else {
            // 预留失败
            log.warn("[OrderCreatedConsumer] Stock reserve failed, orderNo={}, error={}",
                    orderNo, response.getMessage());
            mqConsumeLogRepository.markSuccess(eventId, CONSUMER_GROUP, 
                    "库存预留失败: " + response.getMessage(), costMs);

            // 发布 StockReserveFailed 事件
            publishStockReserveFailedEvent(orderNo, response, reserveItems, orderEvent.getTraceId());
        }
    }

    /**
     * 发布库存预留成功事件
     */
    private void publishStockReservedEvent(String orderNo, List<ReserveItem> items, String traceId) {
        try {
            List<StockReservedEvent.ReservedItem> reservedItems = items.stream()
                    .map(item -> StockReservedEvent.ReservedItem.builder()
                            .skuId(item.getSkuId())
                            .warehouseId(item.getWarehouseId())
                            .qty(item.getQty())
                            .build())
                    .collect(Collectors.toList());

            StockReservedEvent event = StockReservedEvent.builder()
                    .eventId(UUID.randomUUID().toString().replace("-", ""))
                    .orderNo(orderNo)
                    .items(reservedItems)
                    .eventTime(LocalDateTime.now())
                    .traceId(traceId)
                    .version("1.0")
                    .build();

            producerTemplate.syncSend(INVENTORY_TOPIC, STOCK_RESERVED_TAG, orderNo, event);
            log.info("[OrderCreatedConsumer] Published StockReserved event, orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("[OrderCreatedConsumer] Failed to publish StockReserved event, orderNo={}, error={}",
                    orderNo, e.getMessage(), e);
        }
    }

    /**
     * 发布库存预留失败事件
     */
    private void publishStockReserveFailedEvent(String orderNo, ReserveResponse response,
                                                 List<ReserveItem> items, String traceId) {
        try {
            List<StockReserveFailedEvent.RequestedItem> requestedItems = items.stream()
                    .map(item -> StockReserveFailedEvent.RequestedItem.builder()
                            .skuId(item.getSkuId())
                            .warehouseId(item.getWarehouseId())
                            .qty(item.getQty())
                            .build())
                    .collect(Collectors.toList());

            StockReserveFailedEvent event = StockReserveFailedEvent.builder()
                    .eventId(UUID.randomUUID().toString().replace("-", ""))
                    .orderNo(orderNo)
                    .errorCode(response.getErrorCode() != null ? response.getErrorCode().getCode() : "UNKNOWN")
                    .errorMessage(response.getMessage())
                    .requestedItems(requestedItems)
                    .eventTime(LocalDateTime.now())
                    .traceId(traceId)
                    .version("1.0")
                    .build();

            producerTemplate.syncSend(INVENTORY_TOPIC, STOCK_RESERVE_FAILED_TAG, orderNo, event);
            log.info("[OrderCreatedConsumer] Published StockReserveFailed event, orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("[OrderCreatedConsumer] Failed to publish StockReserveFailed event, orderNo={}, error={}",
                    orderNo, e.getMessage(), e);
        }
    }

    /**
     * 解析基础事件
     */
    private BaseEvent parseBaseEvent(MessageExt messageExt) {
        try {
            String body = new String(messageExt.getBody(), StandardCharsets.UTF_8);
            return objectMapper.readValue(body, BaseEvent.class);
        } catch (Exception e) {
            log.error("[OrderCreatedConsumer] Failed to parse BaseEvent, msgId={}",
                    messageExt.getMsgId(), e);
            return null;
        }
    }

    /**
     * 解析订单创建事件
     */
    private OrderCreatedEvent parseOrderCreatedEvent(String payload) {
        try {
            return objectMapper.readValue(payload, OrderCreatedEvent.class);
        } catch (Exception e) {
            log.error("[OrderCreatedConsumer] Failed to parse OrderCreatedEvent payload", e);
            return null;
        }
    }
}
