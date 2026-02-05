package com.yuge.aftersales.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.aftersales.application.AfterSaleService;
import com.yuge.aftersales.domain.event.RefundSucceededEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 退款成功事件消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "PAYMENT_TOPIC",
        selectorExpression = "REFUND_SUCCEEDED",
        consumerGroup = "aftersales-refund-succeeded-consumer"
)
public class RefundSucceededConsumer implements RocketMQListener<String> {

    private final AfterSaleService afterSaleService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        log.info("[RefundSucceededConsumer] Received message: {}", message);

        try {
            RefundSucceededEvent event = objectMapper.readValue(message, RefundSucceededEvent.class);
            
            String asNo = event.getAsNo();
            String refundNo = event.getRefundNo();
            LocalDateTime refundedAt = event.getRefundedAt();

            if (asNo == null || asNo.isEmpty()) {
                log.warn("[RefundSucceededConsumer] asNo is empty, skip processing, refundNo={}", refundNo);
                return;
            }

            log.info("[RefundSucceededConsumer] Processing refund success, asNo={}, refundNo={}", asNo, refundNo);

            // 处理退款成功
            afterSaleService.handleRefundSuccess(asNo, refundNo, refundedAt);

            log.info("[RefundSucceededConsumer] Process completed, asNo={}", asNo);

        } catch (Exception e) {
            log.error("[RefundSucceededConsumer] Failed to process message, error={}", e.getMessage(), e);
            throw new RuntimeException("Failed to process refund succeeded event", e);
        }
    }
}
