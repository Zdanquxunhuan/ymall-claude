package com.yuge.order.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.order.domain.entity.Order;
import com.yuge.order.domain.enums.OrderStatus;
import com.yuge.order.domain.event.AfterSaleRefundedEvent;
import com.yuge.order.infrastructure.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 售后退款完成事件消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "AFTERSALES_TOPIC",
        selectorExpression = "AFTERSALE_REFUNDED",
        consumerGroup = "order-aftersale-refunded-consumer"
)
public class AfterSaleRefundedConsumer implements RocketMQListener<String> {

    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        log.info("[AfterSaleRefundedConsumer] Received message: {}", message);

        try {
            AfterSaleRefundedEvent event = objectMapper.readValue(message, AfterSaleRefundedEvent.class);
            
            String orderNo = event.getOrderNo();
            BigDecimal refundAmount = event.getRefundAmount();

            log.info("[AfterSaleRefundedConsumer] Processing refund, orderNo={}, refundAmount={}", 
                    orderNo, refundAmount);

            // 查询订单
            Order order = orderMapper.selectByOrderNo(orderNo);
            if (order == null) {
                log.warn("[AfterSaleRefundedConsumer] Order not found, orderNo={}", orderNo);
                return;
            }

            // 判断是全额退款还是部分退款
            String newStatus;
            if (refundAmount.compareTo(order.getAmount()) >= 0) {
                // 全额退款
                newStatus = OrderStatus.REFUNDED.getCode();
            } else {
                // 部分退款
                newStatus = OrderStatus.PARTIAL_REFUNDED.getCode();
            }

            // 更新订单状态
            int updated = orderMapper.casUpdateToRefunded(orderNo, newStatus);
            if (updated > 0) {
                log.info("[AfterSaleRefundedConsumer] Order status updated, orderNo={}, newStatus={}", 
                        orderNo, newStatus);
            } else {
                log.warn("[AfterSaleRefundedConsumer] Order status update failed, orderNo={}, currentStatus={}", 
                        orderNo, order.getStatus());
            }

        } catch (Exception e) {
            log.error("[AfterSaleRefundedConsumer] Failed to process message, error={}", e.getMessage(), e);
            throw new RuntimeException("Failed to process after sale refunded event", e);
        }
    }
}
