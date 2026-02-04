package com.yuge.cart.infrastructure.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 库存服务客户端
 * 调用inventory-service进行库存校验
 */
public interface InventoryClient {

    /**
     * 批量查询可售库存
     */
    List<StockInfo> queryAvailableStock(List<StockQuery> queries);

    /**
     * 库存查询参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class StockQuery {
        private Long skuId;
        private Long warehouseId;
        private Integer requestQty;
    }

    /**
     * 库存信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class StockInfo {
        private Long skuId;
        private Long warehouseId;
        private Integer availableQty;
        private Integer requestQty;
        private Boolean sufficient;
    }
}
