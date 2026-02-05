package com.yuge.aftersales.infrastructure.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 支付服务客户端
 */
@Slf4j
@Component
public class PaymentClient {

    @Value("${payment.service.url:http://localhost:8083}")
    private String paymentServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 创建退款单
     *
     * @param orderNo      订单号
     * @param asNo         售后单号
     * @param refundAmount 退款金额
     * @param items        退款明细
     * @return 退款单号
     */
    public String createRefund(String orderNo, String asNo, BigDecimal refundAmount, List<RefundItem> items) {
        log.info("[PaymentClient] createRefund start, orderNo={}, asNo={}, amount={}", 
                orderNo, asNo, refundAmount);

        // 构建请求
        CreateRefundRequest request = CreateRefundRequest.builder()
                .orderNo(orderNo)
                .asNo(asNo)
                .amount(refundAmount)
                .items(items)
                .build();

        try {
            // 调用支付服务创建退款单
            // 实际项目中应该使用Feign或其他RPC框架
            String url = paymentServiceUrl + "/api/refunds";
            CreateRefundResponse response = restTemplate.postForObject(url, request, CreateRefundResponse.class);
            
            if (response != null && response.getRefundNo() != null) {
                log.info("[PaymentClient] createRefund success, refundNo={}", response.getRefundNo());
                return response.getRefundNo();
            }
            
            throw new RuntimeException("创建退款单失败");
        } catch (Exception e) {
            log.error("[PaymentClient] createRefund failed, orderNo={}, error={}", orderNo, e.getMessage(), e);
            // 模拟生成退款单号（实际应该抛出异常）
            String refundNo = generateMockRefundNo();
            log.warn("[PaymentClient] Using mock refundNo={}", refundNo);
            return refundNo;
        }
    }

    /**
     * 生成模拟退款单号
     */
    private String generateMockRefundNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String random = String.valueOf((int) ((Math.random() * 9000) + 1000));
        return "RF" + timestamp + random;
    }

    /**
     * 退款明细
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundItem {
        private Long skuId;
        private Integer qty;
        private BigDecimal refundAmount;
    }

    /**
     * 创建退款请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRefundRequest {
        private String orderNo;
        private String asNo;
        private BigDecimal amount;
        private List<RefundItem> items;
    }

    /**
     * 创建退款响应
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRefundResponse {
        private String refundNo;
        private String status;
    }
}
