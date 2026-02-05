package com.yuge.inventory.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.inventory.application.InventoryService;
import com.yuge.inventory.domain.event.AfterSaleRefundedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 售后退款完成事件消费者 - 库存回补
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "AFTERSALES_TOPIC",
        selectorExpression = "AFTERSALE_REFUNDED",
        consumerGroup = "inventory-aftersale-refunded-consumer"
)
public class AfterSaleRefundedConsumer implements RocketMQListener<String> {

    /**
     * 默认仓库ID（简化处理，实际应从订单明细获取）*/
    private static final Long DEFAULT_WAREHOUSE_ID = 1L;

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        log.info("[AfterSaleRefundedConsumer] Received message: {}", message);

        try {
            AfterSaleRefundedEvent event = objectMapper.readValue(message, AfterSaleRefundedEvent.class);
            
            String orderNo = event.getOrderNo();
            String asNo = event.getAsNo();
            List<AfterSaleRefundedEvent.RefundItemInfo> items = event.getItems();

            if (items == null || items.isEmpty()) {
                log.warn("[AfterSaleRefundedConsumer] No items to restore, asNo={}", asNo);
                return;
            }

            log.info("[AfterSaleRefundedConsumer] Processing inventory restore, orderNo={}, asNo={}, itemCount={}",
                    orderNo, asNo, items.size());

            // 转换为库存回补明细
            List<InventoryService.RefundRestoreItem> restoreItems = items.stream()
                    .map(item -> InventoryService.RefundRestoreItem.builder()
                            .skuId(item.getSkuId())
                            .warehouseId(DEFAULT_WAREHOUSE_ID) // 简化处理
                            .qty(item.getQty())
                            .build())
                    .collect(Collectors.toList());

            // 批量回补库存
            boolean success = inventoryService.batchRefundRestore(orderNo, asNo, restoreItems);

            if (success) {
                log.info("[AfterSaleRefundedConsumer] Inventory restore completed, orderNo={}, asNo={}", 
                        orderNo, asNo);
            } else {
                log.warn("[AfterSaleRefundedConsumer] Inventory restore partial failed, orderNo={}, asNo={}", 
                        orderNo, asNo);
            }

        } catch (Exception e) {
            log.error("[AfterSaleRefundedConsumer] Failed to process message, error={}", e.getMessage(), e);
            throw new RuntimeException("Failed to process after sale refunded event for inventory", e);
        }
    }
}
