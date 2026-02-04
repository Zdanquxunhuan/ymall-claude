package com.yuge.order.infrastructure.client.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.order.infrastructure.client.PricingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 定价服务客户端实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PricingClientImpl implements PricingClient {

    @Value("${service.pricing.url:http://localhost:8084}")
    private String pricingServiceUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public UsePriceLockResult usePriceLock(String priceLockNo, String orderNo, String signature) {
        try {
            String url = String.format("%s/pricing/lock/%s/use?orderNo=%s&signature=%s",
                    pricingServiceUrl, priceLockNo, orderNo, signature);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Integer code = (Integer) body.get("code");

                if (code != null && code == 0) {
                    Map<String, Object> data = (Map<String, Object>) body.get("data");
                    return UsePriceLockResult.builder()
                            .success(true)
                            .priceLockInfo(buildPriceLockInfo(data))
                            .build();
                } else {
                    String message = (String) body.get("message");
                    return UsePriceLockResult.builder()
                            .success(false)
                            .errorMessage(message != null ? message : "使用价格锁失败")
                            .build();
                }
            }

            return UsePriceLockResult.builder()
                    .success(false)
                    .errorMessage("调用定价服务失败")
                    .build();

        } catch (Exception e) {
            log.error("[PricingClient] usePriceLock failed, priceLockNo={}, orderNo={}, error={}",
                    priceLockNo, orderNo, e.getMessage(), e);
            return UsePriceLockResult.builder()
                    .success(false)
                    .errorMessage("调用定价服务异常: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public PriceLockInfo getPriceLock(String priceLockNo) {
        try {
            String url = pricingServiceUrl + "/pricing/lock/" + priceLockNo;

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Integer code = (Integer) body.get("code");

                if (code != null && code == 0) {
                    Map<String, Object> data = (Map<String, Object>) body.get("data");
                    return buildPriceLockInfo(data);
                }
            }

            return null;

        } catch (Exception e) {
            log.error("[PricingClient] getPriceLock failed, priceLockNo={}, error={}",
                    priceLockNo, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void cancelPriceLock(String priceLockNo) {
        try {
            String url = pricingServiceUrl + "/pricing/lock/" + priceLockNo + "/cancel";
            restTemplate.postForEntity(url, null, Map.class);
            log.info("[PricingClient] cancelPriceLock success, priceLockNo={}", priceLockNo);
        } catch (Exception e) {
            log.error("[PricingClient] cancelPriceLock failed, priceLockNo={}, error={}",
                    priceLockNo, e.getMessage(), e);
        }
    }

    private PriceLockInfo buildPriceLockInfo(Map<String, Object> data) {
        if (data == null) return null;

        PriceLockInfo.PriceLockInfoBuilder builder = PriceLockInfo.builder();

        builder.priceLockNo((String) data.get("priceLockNo"));
        builder.userId(data.get("userId") != null ? ((Number) data.get("userId")).longValue() : null);
        builder.status((String) data.get("status"));
        builder.signature((String) data.get("signature"));
        builder.signVersion(data.get("signVersion") != null ? ((Number) data.get("signVersion")).intValue() : null);

        if (data.get("originalAmount") != null) {
            builder.originalAmount(new BigDecimal(data.get("originalAmount").toString()));
        }
        if (data.get("totalDiscount") != null) {
            builder.totalDiscount(new BigDecimal(data.get("totalDiscount").toString()));
        }
        if (data.get("payableAmount") != null) {
            builder.payableAmount(new BigDecimal(data.get("payableAmount").toString()));
        }

        if (data.get("expireAt") != null) {
            builder.expireAt(LocalDateTime.parse(data.get("expireAt").toString()));
        }
        if (data.get("lockedAt") != null) {
            builder.lockedAt(LocalDateTime.parse(data.get("lockedAt").toString()));
        }

        List<Map<String, Object>> allocations = (List<Map<String, Object>>) data.get("allocations");
        if (allocations != null) {
            builder.allocations(allocations.stream()
                    .map(this::buildAllocationDetail)
                    .collect(Collectors.toList()));
        }

        return builder.build();
    }

    private AllocationDetail buildAllocationDetail(Map<String, Object> map) {
        return AllocationDetail.builder()
                .skuId(map.get("skuId") != null ? ((Number) map.get("skuId")).longValue() : null)
                .title((String) map.get("title"))
                .qty(map.get("qty") != null ? ((Number) map.get("qty")).intValue() : null)
                .unitPrice(map.get("unitPrice") != null ?
                        new BigDecimal(map.get("unitPrice").toString()) : null)
                .lineOriginalAmount(map.get("lineOriginalAmount") != null ?
                        new BigDecimal(map.get("lineOriginalAmount").toString()) : null)
                .lineDiscountAmount(map.get("lineDiscountAmount") != null ?
                        new BigDecimal(map.get("lineDiscountAmount").toString()) : null)
                .linePayableAmount(map.get("linePayableAmount") != null ?
                        new BigDecimal(map.get("linePayableAmount").toString()) : null)
                .build();
    }
}
