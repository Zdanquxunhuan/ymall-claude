package com.yuge.inventory.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 库存响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {

    /**
     * 库存ID
     */
    private Long id;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 可用库存
     */
    private Integer availableQty;

    /**
     * 预留库存
     */
    private Integer reservedQty;

    /**
     * 总库存（可用+预留）
     */
    private Integer totalQty;

    /**
     * Redis中的可用库存（用于对比）
     */
    private Integer redisAvailableQty;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
