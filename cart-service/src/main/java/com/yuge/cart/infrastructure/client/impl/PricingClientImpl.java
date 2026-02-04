package com.yuge.cart.infrastructure.client.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.cart.infrastructure.client.PricingClient;
import com.yuge.platform.infra.common.ErrorCode;
import com.yuge.platform.infra.exception.BizException;
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
    public LockResult lock(LockParam param) {
        try {
            String url = pricingServiceUrl + "/pricing/lock";
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("userId", param.getUserId());
            requestBody.put("lockMinutes", param.getLockMinutes() != null ? param.getLockMinutes() : 15);
            requestBody.put("userCouponNos", param.getUserCouponNos());
            
            List<Map<String, Object>> items = param.getItems().stream()
                    .map(item -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("skuId", item.getSkuId());
                        map.put("qty", item.getQty());
                        map.put("unitPrice", item.getUnitPrice());
                        map.put("title", item.getTitle());
                        map.put("categoryId", item.getCategoryId());
                        return map;
                    })
                    .collect(Collectors.toList());
            requestBody.put("items", items);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Integer code = (Integer) body.get("code");
                
                if (code != null && code == 0) {
                    Map<String, Object> data = (Map<String, Object>) body.get("data");
                    return buildLockResult(data);
                } else {
                    String message = (String) body.get("message");
                    return LockResult.builder()
                            .success(false)
                            .errorMessage(message != null ? message : "锁价失败")
                            .build();
                }
            }
            
            return LockResult.builder()
                    .success(false)
                    .errorMessage("调用定价服务失败")
                    .build();
                    
        } catch (Exception e) {
            log.error("[PricingClient] lock failed, userId={}, error={}", 
                    param.getUserId(), e.getMessage(), e);
            return LockResult.builder()
                    .success(false)
                    .errorMessage("调用定价服务异常: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public VerifyResult verify(String priceLockNo, String signature) {
        try {
            String url = pricingServiceUrl + "/pricing/lock/" + priceLockNo + "/verify?signature=" + signature;
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Integer code = (Integer) body.get("code");
                Boolean valid = (Boolean) body.get("data");
                
                return VerifyResult.builder()
                        .valid(code != null && code == 0 && Boolean.TRUE.equals(valid))
                        .build();
            }
            
            return VerifyResult.builder()
                    .valid(false)
                    .errorMessage("验证失败")
                    .build();
                    
        } catch (Exception e) {
            log.error("[PricingClient] verify failed, priceLockNo={}, error={}", 
                    priceLockNo, e.getMessage(), e);
            return VerifyResult.builder()
                    .valid(false)
                    .errorMessage("验证异常: " + e.getMessage())
                    .build();
        }
    }

    private LockResult buildLockResult(Map<String, Object> data) {
        LockResult.LockResultBuilder builder = LockResult.builder().success(true);
        
        builder.priceLockNo((String) data.get("priceLockNo"));
        builder.signature((String) data.get("signature"));
        builder.signVersion((Integer) data.get("signVersion"));
        
        if (data.get("originalAmount") != null) {
            builder.originalAmount(new BigDecimal(data.get("originalAmount").toString()));
        }
        if (data.get("totalDiscount") != null) {
            builder.totalDiscount(new BigDecimal(data.get("totalDiscount").toString()));
        }
        if (data.get("payableAmount") != null) {
            builder.payableAmount(new BigDecimal(data.get("payableAmount").toString()));
        }
        
        // 解析过期时间
        if (data.get("expireAt") != null) {
            builder.expireAt(LocalDateTime.parse(data.get("expireAt").toString()));
        }
        if (data.get("lockedAt") != null) {
            builder.lockedAt(LocalDateTime.parse(data.get("lockedAt").toString()));
        }
        
        // 解析促销命中
        List<Map<String, Object>> promotionHits = (List<Map<String, Object>>) data.get("promotionHits");
        if (promotionHits != null) {
            builder.promotionHits(promotionHits.stream()
                    .map(this::buildPromotionHit)
                    .collect(Collectors.toList()));
        }
        
        // 解析分摊明细
        List<Map<String, Object>> allocations = (List<Map<String, Object>>) data.get("allocations");
        if (allocations != null) {
            builder.allocations(allocations.stream()
                    .map(this::buildAllocationDetail)
                    .collect(Collectors.toList()));
        }
        
        return builder.build();
    }

    private LockResult.PromotionHit buildPromotionHit(Map<String, Object> map) {
        return LockResult.PromotionHit.builder()
                .ruleType((String) map.get("ruleType"))
                .ruleId(map.get("ruleId") != null ? ((Number) map.get("ruleId")).longValue() : null)
                .ruleName((String) map.get("ruleName"))
                .userCouponNo((String) map.get("userCouponNo"))
                .discountType((String) map.get("discountType"))
                .thresholdAmount(map.get("thresholdAmount") != null ? 
                        new BigDecimal(map.get("thresholdAmount").toString()) : null)
                .discountAmount(map.get("discountAmount") != null ? 
                        new BigDecimal(map.get("discountAmount").toString()) : null)
                .description((String) map.get("description"))
                .build();
    }

    private LockResult.AllocationDetail buildAllocationDetail(Map<String, Object> map) {
        return LockResult.AllocationDetail.builder()
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
