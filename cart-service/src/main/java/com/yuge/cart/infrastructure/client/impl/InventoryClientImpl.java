package com.yuge.cart.infrastructure.client.impl;

import com.yuge.cart.infrastructure.client.InventoryClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 库存服务客户端实现
 * 实际生产环境应使用Feign或其他RPC框架
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryClientImpl implements InventoryClient {

    @Value("${service.inventory.url:http://localhost:8082}")
    private String inventoryServiceUrl;

    /**
     * 批量查询可售库存
     * 注：这里简化实现，实际应调用inventory-service的API
     */
    @Override
    public List<StockInfo> queryAvailableStock(List<StockQuery> queries) {
        List<StockInfo> results = new ArrayList<>();
        
        for (StockQuery query : queries) {
            // TODO: 实际应调用 GET /inventory/{warehouseId}/{skuId}/available
            // 这里模拟返回充足库存
            results.add(StockInfo.builder()
                    .skuId(query.getSkuId())
                    .warehouseId(query.getWarehouseId())
                    .requestQty(query.getRequestQty())
                    .availableQty(999) // 模拟数据
                    .sufficient(true)
                    .build());
        }
        
        log.debug("[InventoryClient] queryAvailableStock, queries={}, results={}", 
                queries.size(), results.size());
        
        return results;
    }
}
